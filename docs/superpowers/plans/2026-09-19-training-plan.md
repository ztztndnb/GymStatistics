# 训练计划 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有手动训练记录上增加一个可重复使用的周训练计划、主界面模式滑动切换，以及计划动作与实际记录的联动标识。

**Architecture:** 将一个当前生效的 `TrainingPlan` 放入现有 `WorkoutData`，使用稳定的 `PlannedExercise.id` 和 `ExerciseRecord.plannedExerciseId` 建立计划动作与真实训练记录的关系。主界面保留当前日期和日期滑动逻辑，通过“手动模式 / 计划模式”滑动按钮切换内容；计划完成后写入现有当天训练记录，手动模式用“计划”标识显示来源。

**Tech Stack:** Kotlin 2.0.21、Android Compose Material 3、kotlinx.serialization、现有 `GymViewModel`/`WorkoutRepository`、JUnit 4、Node.js built-in test runner。

**Spec:** `docs/superpowers/specs/2026-09-19-training-plan-design.md`

## Global Constraints

- 进入代码实施后，版本从 `1.3.18` 提升到 `1.3.19`，`versionCode` 从 `35` 提升到 `36`，最后一位不进位。
- 正式版 APK 必须命名为 `GymStatistics 1.3.19.apk`；debug APK 仍为 `app-debug.apk`。
- 周计划只有一个当前生效模板，星期固定为周一至周日。
- 默认模式必须保持“手动模式”。
- 计划完成必须通过明确的“完成并记录”操作，不能把点击折叠本身视为完成。
- 计划数据与现有 `WorkoutData` 一起持久化、导出和导入；旧 JSON 缺少新字段时必须正常读取。
- 计划动作生成的真实记录必须带 `plannedExerciseId`，手动记录默认不带该字段。
- 不修改食物分析、相机、疲劳提醒“严格不足 3 天”和日期跟手滑动的既有行为。
- 不新增第三方依赖；所有源文件编辑使用 `apply_patch`。
- 不自动执行 git commit；完成后提供符合项目约定的 commit 命令，commit message 只描述功能/UI/交互变化，不写测试或构建结果。

## File Map

- Modify `app/src/main/java/com/gymstatistics/data/Models.kt`: 为训练记录、工作数据、同步请求/响应增加兼容字段。
- Create `app/src/main/java/com/gymstatistics/data/TrainingPlan.kt`: 定义计划模型和不依赖 Android 的星期、完成、复制、历史预填充辅助函数。
- Modify `app/src/main/java/com/gymstatistics/GymViewModel.kt`: 增加计划模板 CRUD、计划动作完成、手动记录关联和带计划数据的导入。
- Modify `app/src/main/java/com/gymstatistics/server/LanSyncServer.kt`: 在局域网 JSON 导出和导入中传递计划模板。
- Modify `app/src/main/java/com/gymstatistics/ui/GymApp.kt`: 增加训练计划入口、计划编辑页、主界面模式滑动按钮、计划模式内容和“计划”来源标识，并在此文件定义 `WorkoutMode`。
- Create `app/src/test/java/com/gymstatistics/data/TrainingPlanTest.kt`: 覆盖计划核心规则和旧数据兼容性。
- Modify `tests/dashboard-import.test.mjs`: 覆盖同步 JSON 中计划字段的兼容约定。
- Create `tests/training-plan.test.mjs`: 覆盖 Kotlin 源码中的模式切换、来源标识、完成和版本约定。
- Modify `app/build.gradle.kts`: 更新版本号并保留正式版 APK 重命名任务。
- Modify `README.md`: 增加训练计划、两种模式和“计划”标识的使用说明。

### Task 1: Add serializable plan models and pure plan rules

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/data/Models.kt`
- Create: `app/src/main/java/com/gymstatistics/data/TrainingPlan.kt`
- Test: `app/src/test/java/com/gymstatistics/data/TrainingPlanTest.kt`

**Interfaces:**
- Produces `TrainingPlan`, `TrainingPlanDay`, `PlannedExercise` and pure helpers used by ViewModel/UI。
- Produces `ExerciseRecord.plannedExerciseId: String?` and `WorkoutData.trainingPlan: TrainingPlan?` with defaults.
- Produces `SyncPayload.trainingPlan: TrainingPlan?` and `ImportRequest.trainingPlan: TrainingPlan?` with defaults.

- [ ] **Step 1: Write failing unit tests for model compatibility and plan rules**

Create tests with these cases:

```kotlin
@Test
fun oldWorkoutJsonDecodesWithoutPlanFields() {
    val data = AppJson.json.decodeFromString<WorkoutData>("{\"sessions\":[]}")
    assertNull(data.trainingPlan)
    assertNull(ExerciseRecord("卧推").plannedExerciseId)
}

@Test
fun dateMapsToMondayThroughSundayPlanIndex() {
    assertEquals(1, planDayIndex(LocalDate.parse("2026-09-21")))
    assertEquals(7, planDayIndex(LocalDate.parse("2026-09-27")))
}

@Test
fun completedPlanIdsComeFromLinkedExercisesOnTheSelectedDate() {
    val session = SessionRecord(
        id = "s1",
        date = "2026-09-21",
        exercises = listOf(ExerciseRecord("卧推", plannedExerciseId = "p1")),
    )
    assertTrue(completedPlanExerciseIds(session).contains("p1"))
    assertFalse(completedPlanExerciseIds(session).contains("p2"))
}

@Test
fun copiedDayGetsIndependentPlanExerciseIds() {
    val source = TrainingPlanDay(weekday = 1, exercises = listOf(PlannedExercise(id = "p1", name = "卧推")))
    val copied = copyPlanDay(source, targetWeekday = 4)
    assertEquals(4, copied.weekday)
    assertNotEquals("p1", copied.exercises.single().id)
}
```

- [ ] **Step 2: Run the focused unit test and verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.TrainingPlanTest
```

Expected: compile/test failure because the new models and helpers do not exist.

- [ ] **Step 3: Add the serializable plan models**

Add to `Models.kt`:

```kotlin
@Serializable
data class ExerciseRecord(
    val name: String,
    val id: String = "",
    val data: Double? = null,
    val unit: String = "",
    val count: Int? = null,
    val sets: Int? = null,
    val note: String = "",
    val muscles: List<MuscleSelection> = emptyList(),
    val plannedExerciseId: String? = null,
)

@Serializable
data class WorkoutData(
    val version: Int = 1,
    val sessions: List<SessionRecord> = emptyList(),
    val suppressedFatigue: Set<String> = emptySet(),
    val trainingPlan: TrainingPlan? = null,
)
```

Add matching optional `trainingPlan` fields to `SyncPayload` and `ImportRequest` so old dashboard payloads remain valid.

- [ ] **Step 4: Implement pure `TrainingPlan.kt` helpers**

Use Monday-based indexes independent of the existing Sunday-based date bar:

```kotlin
@Serializable
data class TrainingPlan(
    val name: String = "我的训练计划",
    val days: List<TrainingPlanDay> = (1..7).map { TrainingPlanDay(it) },
)

@Serializable
data class TrainingPlanDay(
    val weekday: Int,
    val restDay: Boolean = false,
    val exercises: List<PlannedExercise> = emptyList(),
)

@Serializable
data class PlannedExercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val data: Double? = null,
    val unit: String = "",
    val count: Int? = null,
    val sets: Int? = null,
    val note: String = "",
    val muscles: List<MuscleSelection> = emptyList(),
)

fun planDayIndex(date: LocalDate): Int = date.dayOfWeek.value

fun completedPlanExerciseIds(session: SessionRecord?): Set<String> =
    session?.exercises?.mapNotNull { it.plannedExerciseId }?.toSet().orEmpty()

fun exerciseRecordForPlan(planned: PlannedExercise, actualId: String): ExerciseRecord =
    ExerciseRecord(
        name = planned.name,
        id = actualId,
        data = planned.data,
        unit = planned.unit,
        count = planned.count,
        sets = planned.sets,
        note = planned.note,
        muscles = planned.muscles,
        plannedExerciseId = planned.id,
    )

fun copyPlanDay(source: TrainingPlanDay, targetWeekday: Int): TrainingPlanDay =
    source.copy(
        weekday = targetWeekday,
        exercises = source.exercises.map { it.copy(id = UUID.randomUUID().toString()) },
    )
```

Also add a helper that finds the latest `ExerciseRecord` for a selected action name so “从历史添加” can prefill the plan without losing unit/count/sets/muscles.

- [ ] **Step 5: Run the focused unit test and verify it passes**

Run the same Gradle test command. Expected: all `TrainingPlanTest` cases pass.

### Task 2: Add ViewModel plan operations and actual-record linkage

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/GymViewModel.kt`
- Test: `app/src/test/java/com/gymstatistics/data/TrainingPlanTest.kt`

**Interfaces:**
- Produces `saveTrainingPlan`, `addPlannedExercise`, `updatePlannedExercise`, `deletePlannedExercise`, `reorderPlannedExercises`, `copyTrainingPlanDay`, `setPlanRestDay`.
- Produces `completePlannedExercise(date: String, plannedExerciseId: String)`.
- Produces `associateExerciseWithPlan(date: String, exerciseId: String, plannedExerciseId: String)`.
- Extends `importSessions` with an optional plan argument while preserving existing callers.

- [ ] **Step 1: Add failing tests for completion, duplicate prevention and plan editing**

Add tests for the pure plan-to-record transformation and completion lookup. The required assertions are:

```kotlin
@Test
fun plannedRecordKeepsPlanIdAndUsesNewActualId() {
    val record = exerciseRecordForPlan(PlannedExercise(id = "p1", name = "卧推"), actualId = "actual-1")
    assertEquals("p1", record.plannedExerciseId)
    assertEquals("actual-1", record.id)
}

@Test
fun completingAlreadyLinkedPlanActionIsDetectedBeforeAppending() {
    val session = SessionRecord("s1", "2026-09-21", exercises = listOf(ExerciseRecord("卧推", plannedExerciseId = "p1")))
    assertTrue("p1" in completedPlanExerciseIds(session))
}

@Test
fun deletingLinkedExerciseMakesPlanActionIncompleteAgain() {
    val session = SessionRecord("s1", "2026-09-21", exercises = listOf(ExerciseRecord("卧推", plannedExerciseId = "p1")))
    assertTrue("p1" in completedPlanExerciseIds(session))
    assertFalse("p1" in completedPlanExerciseIds(session.copy(exercises = emptyList())))
}
```

The tests must compare resulting `SessionRecord.exercises`, not only UI messages.

- [ ] **Step 2: Run the focused unit test and verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.TrainingPlanTest
```

Expected: failure for missing ViewModel/reducer operations.

- [ ] **Step 3: Implement plan persistence operations through the existing `persist` path**

Every plan mutation must update `uiState.data` and call the existing repository persistence path. Use immutable copies and keep plan days ordered by weekday. Reject blank action names and duplicate planned IDs.

`completePlannedExercise` must:

1. Parse the selected date.
2. Find the plan day by `planDayIndex(date)` and the requested plan action.
3. Return without changing data if that action is already linked in that date’s session.
4. Copy the planned values into a new `ExerciseRecord` with a new actual ID and `plannedExerciseId` set to the plan ID.
5. Merge it into the existing session or create a new session.
6. Persist once and expose a user message.

`updateExercise` must preserve the existing record’s `plannedExerciseId` even though the editor creates a new `ExerciseRecord` value.

- [ ] **Step 4: Implement explicit manual association**

Add `associateExerciseWithPlan` that only changes the selected actual exercise’s `plannedExerciseId`, rejects an already-completed plan ID for that date, persists once, and leaves the rest of the exercise fields unchanged.

- [ ] **Step 5: Extend import handling**

Keep date-scoped import limited to sessions. For an all-data import, replace the current plan only when the incoming JSON contains a non-null plan. Preserve the current plan for date-only imports. Keep existing duplicate-date behavior unchanged for sessions.

- [ ] **Step 6: Run the focused and existing Android tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.TrainingPlanTest
```

Expected: focused plan tests and all existing unit tests pass.

### Task 3: Extend LAN export/import for plan data

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/server/LanSyncServer.kt`
- Modify: `app/src/main/java/com/gymstatistics/ui/GymApp.kt`
- Modify: `tests/dashboard-import.test.mjs`

**Interfaces:**
- `/api/workouts` and `/api/export.json` return `SyncPayload(trainingPlan = dataProvider().trainingPlan)`.
- `ImportRequest.trainingPlan` is forwarded to the ViewModel on all-data imports.

- [ ] **Step 1: Add failing source and payload tests**

Add assertions that `LanSyncServer.kt` serializes `trainingPlan`, `GymApp.kt` parses both `WorkoutData.trainingPlan` and `SyncPayload.trainingPlan`, and date-only imports do not pass the plan to the ViewModel.

- [ ] **Step 2: Run the Node tests and verify the new assertions fail**

Run:

```powershell
node --test tests/dashboard-import.test.mjs
```

Expected: failure because the server and import parser currently only handle sessions.

- [ ] **Step 3: Update LAN response/request construction**

Pass the plan in `SyncPayload` responses and update the `LanSyncServer` import handler signature to pass the decoded optional plan along with sessions, import mode and overwrite choice.

- [ ] **Step 4: Update Android file import state**

Replace `parseImportSessions` with an `ImportBundle` result containing `sessions` and `trainingPlan`. Store the plan while the import dialog is open. Pass it only when `ImportMode.ALL` is selected.

- [ ] **Step 5: Run the focused and full Node tests**

Run:

```powershell
node --test tests/dashboard-import.test.mjs
node --test
```

Expected: all existing dashboard tests and all new plan assertions pass.

### Task 4: Build the training-plan editor UI

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/ui/GymApp.kt`
- Test: `tests/training-plan.test.mjs`

**Interfaces:**
- `TrainingPlanPage(viewModel: GymViewModel, onBack: () -> Unit)` displays the single current weekly template.
- `PlanDayEditor` renders one `TrainingPlanDay` and calls the ViewModel plan mutation methods.
- `HistoryExercisePicker` selects an `ActionSummary` and receives the latest matching `ExerciseRecord` as a prefill.

- [ ] **Step 1: Add failing UI source tests**

Create `tests/training-plan.test.mjs` with checks for:

```js
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
```

- [ ] **Step 2: Run the new Node test and verify it fails**

Run:

```powershell
node --test tests/training-plan.test.mjs
```

Expected: failure because the plan entry and composables do not exist.

- [ ] **Step 3: Add the main-screen plan entry and plan screen navigation**

Extend `Screen` with `PLAN`, add a `训练计划` action to the main `TopAppBar`, and render `TrainingPlanPage` with a back callback to `Screen.MAIN`. Keep existing history and camera actions unchanged.

- [ ] **Step 4: Implement the weekly plan editor**

Render Monday through Sunday in order. Each day must support:

- add a blank action through the existing `ExerciseEditor` fields;
- add from history using `buildActions(state.data.sessions)` and the latest matching record;
- edit/delete a planned action;
- reorder actions with explicit up/down controls;
- copy one day to another with new plan IDs;
- toggle rest day.

Use a modal editor for a planned action so editing a plan never writes an actual session record.

- [ ] **Step 5: Run UI source tests and Android compilation**

Run:

```powershell
node --test tests/training-plan.test.mjs
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all new source tests pass and Compose code compiles.

### Task 5: Add the main-interface mode switch and plan execution view

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/ui/GymApp.kt`
- Modify: `app/src/main/java/com/gymstatistics/GymViewModel.kt` if the manual association callback needs a dedicated state query.
- Modify: `tests/training-plan.test.mjs`

**Interfaces:**
- `WorkoutMode.MANUAL` and `WorkoutMode.PLAN` remain local UI state, defaulting to `MANUAL`.
- `WorkoutModeSwitch(selected: WorkoutMode, onSelected: (WorkoutMode) -> Unit)` is the sliding control on the main screen.
- `PlannedExerciseCard` shows pending/completed state and calls `completePlannedExercise` only from “完成并记录”.
- `ActionItemCard` displays a “计划” source chip when `exercise.plannedExerciseId != null`.

- [ ] **Step 1: Add failing tests for mode switching and source marking**

Extend `tests/training-plan.test.mjs` with assertions for the exact UI contract:

```js
test("main screen switches modes without changing the selected date", () => {
  assert.match(gymAppSource, /WorkoutMode/);
  assert.match(gymAppSource, /WorkoutModeSwitch/);
  assert.match(gymAppSource, /default.*MANUAL|mutableStateOf\(WorkoutMode\.MANUAL\)/s);
});

test("manual action cards mark plan-generated records", () => {
  assert.match(gymAppSource, /plannedExerciseId/);
  assert.match(gymAppSource, /计划/);
});
```

- [ ] **Step 2: Run the Node test and verify it fails**

Run `node --test tests/training-plan.test.mjs`. Expected: failure for missing mode switch and source-label implementation.

- [ ] **Step 3: Implement the sliding mode control**

Use a Material-themed rounded container with two equal clickable labels and an animated selected background. Keep `selectedDate` untouched when the mode changes, and default the state to `WorkoutMode.MANUAL` whenever the main screen is first entered.

- [ ] **Step 4: Pass mode and plan state into the day content**

Keep the existing date swipe callbacks and adjacent-day preview. In manual mode render the existing `WorkoutDayContent`; in plan mode render the selected weekday’s plan cards. Do not change the date swipe threshold, velocity, direction or settling logic.

- [ ] **Step 5: Implement completed-plan folding and manual-mode labels**

In plan mode, derive completion from the selected date’s session and `plannedExerciseId`. Pending cards show “完成并记录”; completed cards render a compact folded state and cannot be completed again. In manual mode, update `ActionItemCard` to show a small `计划` chip only when `plannedExerciseId` is non-null.

- [ ] **Step 6: Add the manual association prompt**

After saving a manual action, if the selected date has an uncompleted plan action with the same name, show a confirmation dialog. Confirm calls `associateExerciseWithPlan`; cancel leaves the action unlinked. Do not show the prompt for plan-generated records or when no matching planned action exists.

- [ ] **Step 7: Run Android and Node tests**

Run:

```powershell
node --test
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all tests pass, including existing date-swipe and fatigue behavior tests.

### Task 6: Update version, README and release naming

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `tests/food-analysis.test.mjs`
- Modify: `tests/training-plan.test.mjs`

- [ ] **Step 1: Add failing release metadata assertions**

Update the expected release metadata to:

```js
assert.match(buildSource, /versionCode = 36/);
assert.match(buildSource, /versionName = "1\.3\.19"/);
assert.match(readmeSource, /GymStatistics 1\.3\.19\.apk/);
```

Run `node --test tests/food-analysis.test.mjs tests/training-plan.test.mjs`; expected failure until metadata is updated.

- [ ] **Step 2: Update version and user-facing documentation**

Set `versionCode = 36` and `versionName = "1.3.19"`. Keep the existing `copyReleaseApkWithVersion` task so release output becomes `GymStatistics 1.3.19.apk` while debug remains `app-debug.apk`. Update README sections for training-plan entry, the main-screen mode switch, planned-action completion, and the `计划` label.

- [ ] **Step 3: Run metadata and documentation checks**

Run:

```powershell
node --test tests/food-analysis.test.mjs tests/training-plan.test.mjs
git diff --check
```

Expected: tests pass and `git diff --check` reports no whitespace errors.

### Task 7: Full verification and handoff

**Files:**
- Verify all changed files from Tasks 1–6.

- [ ] **Step 1: Run all Node tests**

```powershell
node --test
```

Expected: all tests pass.

- [ ] **Step 2: Run Android unit tests and both APK builds**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Expected: build succeeds and creates:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/GymStatistics 1.3.19.apk`

- [ ] **Step 3: Verify the final files and diff**

```powershell
Get-Item 'app/build/outputs/apk/release/GymStatistics 1.3.19.apk','app/build/outputs/apk/debug/app-debug.apk'
git diff --check
git status --short
```

- [ ] **Step 4: Prepare the requested commit command without executing it**

Use a message that describes the functional and UI changes without test/build results:

```bash
git add app/build.gradle.kts app/src/main/java/com/gymstatistics/data/Models.kt app/src/main/java/com/gymstatistics/data/TrainingPlan.kt app/src/main/java/com/gymstatistics/GymViewModel.kt app/src/main/java/com/gymstatistics/server/LanSyncServer.kt app/src/main/java/com/gymstatistics/ui/GymApp.kt app/src/test/java/com/gymstatistics/data/TrainingPlanTest.kt tests/dashboard-import.test.mjs tests/training-plan.test.mjs tests/food-analysis.test.mjs README.md docs/superpowers/specs/2026-09-19-training-plan-design.md docs/superpowers/plans/2026-09-19-training-plan.md
git commit -m "feat(plan): 发布 1.3.19 训练计划与模式切换" -m "新增：周一至周日的可重复训练计划、历史动作预填充、计划动作完成并记录、计划编辑与日程复制；修改：主界面增加手动模式与计划模式滑动切换，计划生成的实际动作在手动模式中显示计划标识，并与现有历史、肌群和疲劳提醒联动。"
```
