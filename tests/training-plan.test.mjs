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

test("manual mode keeps plan-generated actions at the end during reordering", () => {
  assert.match(planSource, /fun displayOrderForManualMode\(exercises: List<ExerciseRecord>\)/);
  assert.match(planSource, /fun reorderManualExercises\(exercises: List<ExerciseRecord>, exerciseId: String, direction: Int\)/);
  assert.match(viewModelSource, /fun reorderExercises\(date: String, exerciseId: String, direction: Int\)/);
  assert.match(viewModelSource, /reorderManualExercises\(session\.exercises, exerciseId, direction\)/);
  assert.match(gymAppSource, /displayOrderForManualMode\(session\?\.exercises\.orEmpty\(\)\)/);
});

test("training plan and manual action rows expose long-press drag handles", () => {
  assert.match(gymAppSource, /detectDragGesturesAfterLongPress/);
  assert.match(gymAppSource, /拖动排序/);
  assert.match(gymAppSource, /reorderHandle\(/);
});

test("drag sorting follows the pointer while reordered rows animate", () => {
  assert.match(gymAppSource, /onDragOffsetChanged/);
  assert.match(gymAppSource, /translationY = if \(isDragging\) dragOffset else (?:0f|placementOffset\.value)/);
  assert.match(gymAppSource, /dragOffset -= movedDistance/);
  assert.match(gymAppSource, /dragOffset \+= movedDistance/);
  assert.doesNotMatch(gymAppSource, /animatedDragOffset/);
  assert.match(gymAppSource, /rememberUpdatedState\(onMove\)/);
  assert.match(gymAppSource, /pointerInput\(Unit\)/);
  assert.match(gymAppSource, /key\(exercise\.id\)\s*\{\s*PlanDayExerciseRow\(/);
  assert.doesNotMatch(gymAppSource, /targetState = exercise,/);
  assert.match(gymAppSource, /activeDraggedId/);
  assert.match(gymAppSource, /animateFloatAsState/);
  assert.match(gymAppSource, /\.animateItem\(\)/);
  assert.match(gymAppSource, /placementOffset\.animateTo\(0f/);
});

test("dragged plan and manual actions use opaque rounded card backgrounds", () => {
  const planRow = gymAppSource.split("private fun PlanDayExerciseRow(")[1].split("private class PlannedExerciseDraft")[0];
  const manualCard = gymAppSource.split("private fun ActionItemCard(")[1].split("private fun exerciseLine(")[0];
  assert.match(planRow, /val dragBackground = if \(isDragging\) MaterialTheme\.colorScheme\.surfaceContainerHigh\.copy\(alpha = 1f\) else Color\.Transparent/);
  assert.match(planRow, /shape = RoundedCornerShape\(16\.dp\)/);
  assert.match(planRow, /\.background\(dragBackground\)/);
  assert.match(manualCard, /CardDefaults\.cardColors\(containerColor = MaterialTheme\.colorScheme\.surfaceContainerHigh\.copy\(alpha = 1f\)\)/);
});

test("plan action row keeps drag, edit and delete but no move arrows", () => {
  const planRow = gymAppSource.split("private fun PlanDayExerciseRow(")[1].split("private class PlannedExerciseDraft")[0];
  assert.match(planRow, /DragHandle\(/);
  assert.match(planRow, /contentDescription = "编辑计划动作"/);
  assert.match(planRow, /contentDescription = "删除计划动作"/);
  assert.doesNotMatch(planRow, /contentDescription = "(?:上移|下移)"/);
});

test("mode switch calculates the selected background inside the same padded track", () => {
  assert.match(gymAppSource, /Box\(Modifier\.fillMaxWidth\(\)\.padding\(4\.dp\)\)/);
  assert.match(gymAppSource, /val segmentWidth = maxWidth \/ 2/);
  assert.match(gymAppSource, /\.offset\(x = selectedX\)[\s\S]*\.width\(segmentWidth\)/);
  assert.match(gymAppSource, /\.height\(56\.dp\)/);
  assert.match(gymAppSource, /\.fillMaxHeight\(\)/);
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

test("completed plan actions can be cancelled and become pending again", () => {
  assert.match(gymAppSource, /取消完成/);
  assert.match(gymAppSource, /onCancelComplete/);
  assert.match(viewModelSource, /fun cancelPlannedExercise\(date: String, plannedExerciseId: String\)/);
  assert.match(viewModelSource, /filterNot \{ it\.plannedExerciseId == plannedExerciseId \}/);
});

test("completed plan actions collapse into a separate expandable group below pending actions", () => {
  assert.match(gymAppSource, /val pendingExercises = day\?\.exercises\?\.filterNot \{ it\.id in completed \}/);
  assert.match(gymAppSource, /val completedExercises = day\?\.exercises\?\.filter \{ it\.id in completed \}/);
  assert.match(gymAppSource, /已完成动作/);
  assert.match(gymAppSource, /AnimatedVisibility\(\s*visible = completedExpanded/);
  assert.match(gymAppSource, /completedExercises\.forEach/);
});

test("new workout and plan action drafts default to the pound unit", () => {
  assert.match(gymAppSource, /private class ExerciseDraft[\s\S]*initial\?\.unit \?\: "磅"/);
  assert.match(gymAppSource, /private class PlannedExerciseDraft[\s\S]*initial\?\.unit \?\: "磅"/);
});

test("training plan action editor exposes unit choices", () => {
  assert.match(gymAppSource, /PlannedExerciseEditorDialog[\s\S]*draft\.unit = "磅"/);
  assert.match(gymAppSource, /PlannedExerciseEditorDialog[\s\S]*draft\.unit = "kg"/);
  assert.match(gymAppSource, /PlannedExerciseEditorDialog[\s\S]*draft\.unit = "lbs"/);
});

test("LAN sync includes plan data and keeps date imports session-scoped", () => {
  assert.match(serverSource, /trainingPlan = dataProvider\(\)\.trainingPlan/);
  assert.match(serverSource, /importHandler\(req\.sessions, mode, req\.overwriteDuplicates, req\.trainingPlan\)/);
  assert.match(viewModelSource, /mode is ImportMode\.ALL && trainingPlan != null/);
});

test("release metadata and README are bumped for the current release", () => {
  assert.match(buildSource, /versionCode = 46/);
  assert.match(buildSource, /versionName = "1\.3\.29"/);
  assert.match(readmeSource, /GymStatistics 1\.3\.29\.apk/);
});
