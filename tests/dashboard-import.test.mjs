import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const dashboardHtml = fs.readFileSync(new URL("../app/src/main/assets/dashboard.html", import.meta.url), "utf8");
const dashboardScript = dashboardHtml.match(/<script>([\s\S]*)<\/script>/)[1];
const gymAppSource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/ui/GymApp.kt", import.meta.url), "utf8");
const repositorySource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/data/WorkoutRepository.kt", import.meta.url), "utf8");
const viewModelSource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/GymViewModel.kt", import.meta.url), "utf8");

function createDashboard(fetchImpl) {
  const listeners = new Map();
  const elements = new Map();
  const ids = [
    "exported", "nSessions", "nExercises", "nSets", "totalCount", "content",
    "dlJson", "dlCsv", "refreshBtn", "importFile", "importFileInfo", "importAll", "importByDate", "importDate",
    "importBtn", "importStatus",
  ];

  for (const id of ids) {
    elements.set(id, {
      id,
      value: "",
      textContent: "",
      innerHTML: "",
      disabled: false,
      checked: false,
      files: [],
      addEventListener(type, handler) {
        listeners.set(`${id}:${type}`, handler);
      },
    });
  }
  elements.get("importDate").value = "";

  const document = {
    getElementById(id) {
      if (!elements.has(id)) throw new Error(`missing element ${id}`);
      return elements.get(id);
    },
    createElement(tag) {
      return {
        tagName: tag,
        className: "",
        textContent: "",
        appendChild() {},
        remove() {},
        click() {},
      };
    },
    body: { appendChild() {} },
  };
  const context = {
    document,
    window: {},
    fetch: fetchImpl,
    setInterval() {},
    setTimeout,
    URL: { createObjectURL() { return "blob:test"; }, revokeObjectURL() {} },
    Blob,
    console,
  };
  context.window = context;
  vm.runInNewContext(dashboardScript, context);
  return { elements, listeners, context };
}

test("dashboard import parser accepts the phone sync payload", () => {
  const { context } = createDashboard(async () => ({ ok: true, json: async () => ({ sessions: [] }) }));
  const sessions = context.GymStatisticsImport.parseSessions(JSON.stringify({
    exportedAt: "2026-09-07T12:00:00+08:00",
    sessions: [{ id: "s1", date: "2026-09-07", exercises: [] }],
  }));
  assert.equal(sessions.length, 1);
  assert.equal(sessions[0].date, "2026-09-07");
});

test("Android file import decodes UTF-8 bytes and removes an optional BOM", () => {
  assert.match(gymAppSource, /readBytes\(\)\.toString\(Charsets\.UTF_8\)\.removePrefix\("\\uFEFF"\)/);
});

test("workout persistence uses UTF-8 explicitly", () => {
  assert.match(repositorySource, /file\.readText\(Charsets\.UTF_8\)/);
  assert.match(repositorySource, /file\.writeText\([\s\S]*, Charsets\.UTF_8\)/);
});

test("main action list cannot crash on duplicate exercise IDs", () => {
  assert.match(gymAppSource, /itemsIndexed\(dayExercises, key = \{ index, exercise ->/);
  assert.match(gymAppSource, /exercise\.id\.ifEmpty \{ exercise\.name \}.*index/);
});

test("import regenerates blank and colliding exercise IDs", () => {
  assert.match(viewModelSource, /usedExerciseIds/);
  assert.match(viewModelSource, /if \(id\.isBlank\(\) \|\| !usedExerciseIds\.add\(id\)\)/);
});

test("dashboard import detects duplicate dates before sending one request", async () => {
  const requests = [];
  const { elements, listeners, context } = createDashboard(async (url, options) => {
    requests.push({ url, options });
    if (url === "api/workouts") {
      return { ok: true, json: async () => ({ exportedAt: "now", sessions: [{ id: "old", date: "2026-09-06", exercises: [] }] }) };
    }
    return { ok: true, json: async () => ({ added: 1, duplicates: 1, skipped: 0, overwritten: 1 }) };
  });

  await new Promise((resolve) => setImmediate(resolve));
  const sessions = [
    { id: "old-copy", date: "2026-09-06", exercises: [] },
    { id: "new", date: "2026-09-07", exercises: [] },
  ];
  assert.deepEqual(Array.from(context.GymStatisticsImport.duplicateDates(sessions, [{ id: "old", date: "2026-09-06" }], "all", "")), ["2026-09-06"]);

  elements.get("importFile").files = [{ name: "gymstatistics.json", text: async () => JSON.stringify({ sessions }) }];
  await listeners.get("importFile:change")({ target: elements.get("importFile") });
  context.confirm = () => true;
  await listeners.get("importBtn:click")();

  const importRequests = requests.filter((request) => request.url === "api/import");
  assert.equal(importRequests.length, 1);
  assert.equal(JSON.parse(importRequests[0].options.body).overwriteDuplicates, true);
});
