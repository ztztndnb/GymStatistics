import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const dashboardHtml = fs.readFileSync(new URL("../app/src/main/assets/dashboard.html", import.meta.url), "utf8");
const dashboardScript = dashboardHtml.match(/<script>([\s\S]*)<\/script>/)[1];
const gymAppSource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/ui/GymApp.kt", import.meta.url), "utf8");
const modelsSource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/data/Models.kt", import.meta.url), "utf8");
const repositorySource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/data/WorkoutRepository.kt", import.meta.url), "utf8");
const viewModelSource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/GymViewModel.kt", import.meta.url), "utf8");
const gradleSource = fs.readFileSync(new URL("../app/build.gradle.kts", import.meta.url), "utf8");
const musclePickerSource = fs.existsSync(new URL("../app/src/main/assets/muscle-picker/index.html", import.meta.url))
  ? fs.readFileSync(new URL("../app/src/main/assets/muscle-picker/index.html", import.meta.url), "utf8")
  : "";

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

test("exercise notes are stored on and loaded from the individual exercise", () => {
  assert.match(modelsSource, /data class ExerciseRecord\([\s\S]*val note: String = ""/);
  assert.match(viewModelSource, /updated\.copy\(id = exerciseId, note = note\.trim\(\)\)/);
  assert.match(gymAppSource, /initialNote = prefillExercise\?\.note \?: ""/);
  assert.match(gymAppSource, /note = note\.trim\(\)/);
});

test("date changes keep the header fixed and animate the day content separately", () => {
  assert.match(gymAppSource, /DatePickerHeader\(selectedDate = selectedDate/);
  assert.match(gymAppSource, /label = "workout records"/);
  assert.match(gymAppSource, /animateDpAsState\(/);
  assert.match(gymAppSource, /animatedHighlightX/);
  assert.doesNotMatch(gymAppSource, /AnimatedContent\(\s*targetState = selectedDate,[\s\S]*label = "date"/);
});

test("selected date background shares the day row vertical alignment", () => {
  assert.match(gymAppSource, /matchParentSize\(\)/);
  assert.doesNotMatch(gymAppSource, /offset\(x = animatedHighlightX, y = 28\.dp\)/);
});

test("history action search supports fuzzy, full-pinyin, and initials matching", () => {
  assert.match(gymAppSource, /var searchQuery by remember \{ mutableStateOf\(""\) \}/);
  assert.match(gymAppSource, /actions\.filter \{ actionMatchesQuery\(it\.name, searchQuery\) \}/);
  assert.match(gymAppSource, /Transliterator\.getInstance\("Han-Latin"\)/);
  assert.match(gymAppSource, /query\.all \{ char -> name\.indexOf\(char, index, ignoreCase = true\)\.also \{ index = it \+ 1 \} >= 0 \}/);
  assert.match(gymAppSource, /label = \{ Text\("搜索动作"\) \}/);
  assert.match(gymAppSource, /没有匹配的动作/);
});

test("muscle picker is full-screen, zoomable, and uses exact Chinese muscle names", () => {
  assert.match(gymAppSource, /Text\("选择锻炼肌群"\)/);
  assert.doesNotMatch(gymAppSource, /选择锻炼肌群\(演示\)/);
  assert.match(gymAppSource, /WebView\(context\)/);
  assert.match(gymAppSource, /DialogProperties\(usePlatformDefaultWidth = false\)/);
  assert.match(gymAppSource, /setSupportZoom\(false\)/);
  assert.match(gymAppSource, /builtInZoomControls = false/);
  assert.match(gradleSource, /buildConfig = true/);
  assert.match(gymAppSource, /loadUrl\("file:\/\/\/android_asset\/muscle-picker\/index\.html"\)/);
  assert.match(musclePickerSource, /body-muscles\.umd\.min\.js/);
  assert.match(musclePickerSource, /BodyMuscles\.FRONT_MUSCLES/);
  assert.match(musclePickerSource, /document\.createElementNS\(SVG_NS, 'path'\)/);
  assert.match(musclePickerSource, /const MUSCLE_NAMES = \{/);
  assert.match(musclePickerSource, /'traps-upper-left': '左斜方肌（上部）'/);
  assert.match(musclePickerSource, /MUSCLE_NAMES\[muscle\.id\]/);
  assert.doesNotMatch(musclePickerSource, /\|back\|/);
  assert.match(musclePickerSource, /ViewSide\.FRONT/);
  assert.match(musclePickerSource, /ViewSide\.BACK/);
  assert.match(musclePickerSource, /window\.addEventListener\('error'/);
  assert.match(musclePickerSource, /id="error"/);
  assert.match(musclePickerSource, /#body-map \{ height: calc\(100vh - 156px\);/);
});

test("muscle selections are persisted with each exercise and restored in the editor", () => {
  assert.match(modelsSource, /data class MuscleSelection\([\s\S]*val id: String,[\s\S]*val name: String/);
  assert.match(modelsSource, /val muscles: List<MuscleSelection> = emptyList\(\)/);
  assert.match(gymAppSource, /muscles = exercise\.muscles/);
  assert.match(gymAppSource, /addJavascriptInterface\(/);
  assert.match(gymAppSource, /onSelectionChanged =/);
  assert.match(musclePickerSource, /AndroidMusclePicker\.onSelectionChanged/);
  assert.match(musclePickerSource, /function setSelectedMuscles\(items\)/);
});

test("history records retain muscles for muscle-based fatigue matching", () => {
  const historySource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/data/History.kt", import.meta.url), "utf8");
  assert.match(historySource, /val muscles: List<MuscleSelection> = emptyList\(\)/);
  assert.match(historySource, /ActionRecord\(s\.date, it\.data, it\.unit, it\.count, it\.sets, it\.muscles\)/);
  assert.match(gymAppSource, /fatigueWarningsFor\(a, allActions, today/);
  assert.match(gymAppSource, /肌群/);
});

test("editing an action's muscles updates every record with that action name", () => {
  assert.match(viewModelSource, /fun updateMusclesForAction\(name: String, muscles: List<MuscleSelection>\)/);
  assert.match(viewModelSource, /if \(exercise\.name == name\) exercise\.copy\(muscles = clean\)/);
  assert.match(gymAppSource, /onEditMuscles/);
  assert.match(gymAppSource, /编辑锻炼肌群/);
});

test("fatigue warning excludes the third day", () => {
  const historySource = fs.readFileSync(new URL("../app/src/main/java/com/gymstatistics/data/History.kt", import.meta.url), "utf8");
  assert.match(historySource, /days\s+!in\s+0\s+until\s+FATIGUE_WINDOW_DAYS/);
  assert.doesNotMatch(historySource, /days\s+!in\s+0\.\.FATIGUE_WINDOW_DAYS/);
});

test("muscle picker zoom is scoped to the body map", () => {
  assert.match(gymAppSource, /setSupportZoom\(false\)/);
  assert.match(gymAppSource, /builtInZoomControls = false/);
  assert.match(musclePickerSource, /user-scalable=no/);
  assert.match(musclePickerSource, /touchstart/);
  assert.match(musclePickerSource, /touchmove/);
  assert.match(musclePickerSource, /applyMapTransform/);
});

test("muscle picker pans at the default scale", () => {
  assert.match(musclePickerSource, /else if \(event\.touches\.length === 1 && gesture\?\.type === 'pan'\) \{/);
});

test("add action dialog omits the destination date label", () => {
  assert.doesNotMatch(gymAppSource, /将添加到\s*[:：]/);
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
