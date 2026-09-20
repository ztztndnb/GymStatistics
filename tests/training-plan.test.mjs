import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = (path) => fs.readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const gymAppSource = read("app/src/main/java/com/gymstatistics/ui/GymApp.kt");
const viewModelSource = read("app/src/main/java/com/gymstatistics/GymViewModel.kt");
const serverSource = read("app/src/main/java/com/gymstatistics/server/LanSyncServer.kt");
const modelsSource = read("app/src/main/java/com/gymstatistics/data/Models.kt");
const planSource = fs.existsSync(new URL("../app/src/main/java/com/gymstatistics/data/TrainingPlan.kt", import.meta.url))
  ? read("app/src/main/java/com/gymstatistics/data/TrainingPlan.kt")
  : "";
const buildSource = read("app/build.gradle.kts");
const readmeSource = read("README.md");

test("training plan models preserve optional compatibility fields", () => {
  assert.match(modelsSource, /val plannedExerciseId: String\? = null/);
  assert.match(modelsSource, /val trainingPlan: TrainingPlan\? = null/);
  assert.match(modelsSource, /data class SyncPayload[\s\S]*val trainingPlan: TrainingPlan\? = null/);
  assert.match(modelsSource, /data class ImportRequest[\s\S]*val trainingPlan: TrainingPlan\? = null/);
});

test("main UI exposes a training plan entry and mode switch", () => {
  assert.match(gymAppSource, /训练计划/);
  assert.match(gymAppSource, /手动模式/);
  assert.match(gymAppSource, /计划模式/);
});

test("plan UI supports history actions, planned completion and source labels", () => {
  assert.match(gymAppSource, /从历史动作选择/);
  assert.match(gymAppSource, /完成并记录/);
  assert.match(gymAppSource, /计划/);
  assert.match(gymAppSource, /plannedExerciseId/);
});

test("main screen switches modes without changing the selected date", () => {
  assert.match(gymAppSource, /WorkoutMode/);
  assert.match(gymAppSource, /WorkoutModeSwitch/);
  assert.match(gymAppSource, /mutableStateOf\(WorkoutMode\.MANUAL\)/);
});

test("manual action cards mark plan-generated records", () => {
  assert.match(gymAppSource, /plannedExerciseId/);
  assert.match(gymAppSource, /计划/);
});

test("mode switch calculates the selected background inside the same padded track", () => {
  assert.match(gymAppSource, /Box\(Modifier\.fillMaxWidth\(\)\.padding\(4\.dp\)\)/);
  assert.match(gymAppSource, /val segmentWidth = maxWidth \/ 2/);
  assert.match(gymAppSource, /\.offset\(x = selectedX\)[\s\S]*\.width\(segmentWidth\)/);
});

test("history action selection opens a searchable full-screen history-style page", () => {
  assert.match(gymAppSource, /HistoryActionPickerPage/);
  assert.match(gymAppSource, /搜索动作或肌群/);
  assert.match(gymAppSource, /DialogProperties\(usePlatformDefaultWidth = false\)/);
  assert.match(gymAppSource, /actionMatchesQuery\(it\.name, searchQuery, it\.muscles\)/);
});

test("plan completion and manual association use the ViewModel persistence path", () => {
  assert.match(viewModelSource, /fun completePlannedExercise\(date: String, plannedExerciseId: String\)/);
  assert.match(viewModelSource, /fun associateExerciseWithPlan\(date: String, exerciseId: String, plannedExerciseId: String\)/);
  assert.match(viewModelSource, /exerciseRecordForPlan\(planned, UUID\.randomUUID\(\)\.toString\(\)\)/);
  assert.match(viewModelSource, /plannedExerciseId = updated\.plannedExerciseId \?: it\.plannedExerciseId/);
});

test("LAN sync includes plan data and keeps date imports session-scoped", () => {
  assert.match(serverSource, /trainingPlan = dataProvider\(\)\.trainingPlan/);
  assert.match(serverSource, /importHandler\(req\.sessions, mode, req\.overwriteDuplicates, req\.trainingPlan\)/);
  assert.match(viewModelSource, /mode is ImportMode\.ALL && trainingPlan != null/);
});

test("release metadata and README are bumped for the current release", () => {
  assert.match(buildSource, /versionCode = 37/);
  assert.match(buildSource, /versionName = "1\.3\.20"/);
  assert.match(readmeSource, /GymStatistics 1\.3\.20\.apk/);
});
