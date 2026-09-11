# AI 食物分析 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement AI food analysis with camera/gallery input, DeepSeek JSON analysis, editable per-100g nutrition, separate history, and API-key protection.

**Architecture:** Keep workout persistence unchanged and add a focused food-analysis repository backed by a separate JSON file. Use native Android camera/gallery contracts, native `HttpURLConnection`, Android Keystore AES-GCM storage, pure Kotlin calculation helpers, and Compose screens attached to the existing single-activity navigation.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlin Serialization, Android Activity Result APIs, Android Keystore, `HttpURLConnection`, existing Node source-pattern tests plus JVM unit tests.

**Spec:** `docs/superpowers/specs/2026-09-11-ai-food-analysis.md`

## Global Constraints

- Any code modification bumps the app version from `1.2.7` to `1.3.0` exactly as requested; set `versionCode` to `17`.
- Formal APK name is `GymStatistics 1.3.0.apk`; debug naming remains unchanged.
- Do not add QR/barcode scanning, food database lookup, CameraX, or unrelated dependencies.
- Do not write food analyses into `WorkoutData.sessions` or the current training date.
- Every numeric nutrition value is stored per 100g; final totals are calculated locally.
- Do not log or persist the raw DeepSeek API Key; store it encrypted with Android Keystore.

---

### Task 1: Add data contract, calculator, and failing tests

**Files:**
- Create: `app/src/main/java/com/gymstatistics/data/FoodModels.kt`
- Create: `app/src/main/java/com/gymstatistics/data/FoodAnalysisCalculator.kt`
- Create: `app/src/test/java/com/gymstatistics/data/FoodAnalysisCalculatorTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- `FoodAnalysisRecord`, `FoodAnalysisItem`, `NutritionPer100g`, `LabelInfo`, `FoodTotals`.
- `FoodAnalysisCalculator.itemTotals(item): NutritionPer100g?` and `FoodAnalysisCalculator.total(record): FoodTotals`.

- [ ] **Step 1: Write the failing calculator tests**

```kotlin
@Test fun itemNutritionScalesFromPer100gToEditedWeight() {
    val item = FoodAnalysisItem(
        name = "鸡胸肉", weightG = 150.0,
        nutritionPer100g = NutritionPer100g(165.0, 690.0, 31.0, 3.6, 0.0, 0.0, 0.0, 74.0, 85.0)
    )
    assertEquals(246.0, FoodAnalysisCalculator.itemTotals(item).energyKcal)
    assertEquals(46.5, FoodAnalysisCalculator.itemTotals(item).proteinG)
}

@Test fun totalWeightAndNutritionUseOnlyKnownItems() {
    val record = FoodAnalysisRecord(
        id = "1", createdAt = "now", foodName = "餐盘", imageType = "meal_photo",
        items = listOf(
            FoodAnalysisItem("米饭", 100.0, nutritionPer100g = NutritionPer100g(116.0, 0.0, 2.6, 0.3, 25.9, 0.0, 0.0, 0.0, 0.0)),
            FoodAnalysisItem("未知配料", null, nutritionPer100g = NutritionPer100g(50.0, 0.0, 1.0, 1.0, 2.0, 0.0, 0.0, 0.0, 0.0)),
        ),
    )
    val total = FoodAnalysisCalculator.total(record)
    assertEquals(100.0, total.weightG)
    assertEquals(116.0, total.energyKcal)
}
```

- [ ] **Step 2: Run the focused test and confirm the expected missing-symbol failure**

Run: `.gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.FoodAnalysisCalculatorTest`

Expected: FAIL because `FoodAnalysisItem`, `NutritionPer100g`, and `FoodAnalysisCalculator` do not exist yet.

- [ ] **Step 3: Add the minimal serializable models and calculator**

Use nullable `Double` values for unreadable nutrition fields, multiply known values by `weightG / 100`, and sum only items with a known weight.

- [ ] **Step 4: Run the focused test and confirm it passes**

Run: `.gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.FoodAnalysisCalculatorTest`

Expected: PASS.

### Task 2: Add separate history persistence and encrypted API settings

**Files:**
- Create: `app/src/main/java/com/gymstatistics/data/FoodAnalysisRepository.kt`
- Create: `app/src/main/java/com/gymstatistics/data/AiSettingsRepository.kt`
- Modify: `app/src/main/java/com/gymstatistics/GymViewModel.kt`

**Interfaces:**
- `FoodAnalysisRepository.load(): List<FoodAnalysisRecord>`
- `FoodAnalysisRepository.saveAll(records: List<FoodAnalysisRecord>)`
- `AiSettingsRepository.getApiKey(): String?`, `setApiKey(value: String)`, `clearApiKey()`.
- ViewModel methods: `analyzeFoodImage(uri: Uri)`, `saveFoodAnalysis(record)`, `deleteFoodAnalysis(id)`, `setDeepSeekApiKey(key)`, `clearDeepSeekApiKey()`.

- [ ] **Step 1: Write source-level tests for separate persistence and non-training storage**
- [ ] **Step 2: Run `node --test tests/food-analysis.test.mjs` and confirm failure**
- [ ] **Step 3: Implement the repository and AES-GCM KeyStore wrapper**
- [ ] **Step 4: Load history and key status in `GymViewModel` without changing `WorkoutRepository`**
- [ ] **Step 5: Run the focused test and confirm it passes**

### Task 3: Add DeepSeek request, prompt, image preparation, and response validation

**Files:**
- Create: `app/src/main/java/com/gymstatistics/ai/DeepSeekFoodAnalyzer.kt`
- Create: `app/src/main/java/com/gymstatistics/ai/FoodAnalysisPrompt.kt`
- Modify: `app/src/main/java/com/gymstatistics/GymViewModel.kt`
- Modify: `tests/food-analysis.test.mjs`

**Interfaces:**
- `DeepSeekFoodAnalyzer.analyze(uri: Uri, apiKey: String): FoodAnalysisRecord`
- `FoodAnalysisPrompt.system: String`

- [ ] **Step 1: Add failing source tests for the model identifier, per-100g fields, label handling, and absence of barcode flow**
- [ ] **Step 2: Run `node --test tests/food-analysis.test.mjs` and confirm failure**
- [ ] **Step 3: Implement image compression, data-URI request construction, JSON response parsing, and schema validation**
- [ ] **Step 4: Implement the exact prompt rules for meal photos, nutrition labels, ingredient labels, nulls, confidence, and local total calculation**
- [ ] **Step 5: Connect ViewModel loading/error state and delete temporary camera files after completion**
- [ ] **Step 6: Run the source tests and confirm they pass**

### Task 4: Add camera and gallery entry points

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/ui/GymApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`

**Interfaces:**
- Add `Screen.FOOD` navigation.
- Add `FoodAnalysisScreen` callbacks for `TakePicture`, `GetContent`, API settings, analysis, history, and back.

- [ ] **Step 1: Add failing source tests for both camera and gallery launchers and FileProvider configuration**
- [ ] **Step 2: Run `node --test tests/food-analysis.test.mjs` and confirm failure**
- [ ] **Step 3: Add the temporary camera URI provider and native activity-result launchers**
- [ ] **Step 4: Add the main-page AI food-analysis button and the Food screen**
- [ ] **Step 5: Run source tests and compile the app**

### Task 5: Add result editor and independent food-analysis history UI

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/ui/GymApp.kt`
- Modify: `app/src/main/java/com/gymstatistics/data/FoodAnalysisCalculator.kt`

**Interfaces:**
- `FoodAnalysisEditor(record, onChange, onSave, onBack)`.
- `FoodAnalysisHistoryScreen(records, onOpen, onDelete, onBack)`.

- [ ] **Step 1: Add failing source tests for editable weight/nutrient fields, visible total weight, save-to-history, and no session-date writes**
- [ ] **Step 2: Run `node --test tests/food-analysis.test.mjs` and confirm failure**
- [ ] **Step 3: Implement item editors using numeric text fields and a pure `updateItem` copy path**
- [ ] **Step 4: Render locally calculated item and overall totals after every edit**
- [ ] **Step 5: Add history list, reopen, delete confirmation, loading, and error states**
- [ ] **Step 6: Run source tests and compile the app**

### Task 6: Version, documentation, and verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `tests/food-analysis.test.mjs`

- [ ] **Step 1: Set `versionName = "1.3.0"` and `versionCode = 17`**
- [ ] **Step 2: Update README as a user-facing guide for AI food analysis, camera/gallery input, editing, and separate history**
- [ ] **Step 3: Run all Node tests**

Run: `node --test tests/*.test.mjs`

- [ ] **Step 4: Run the debug compile and unit tests**

Run: `.gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

- [ ] **Step 5: Run the release build and rename the APK**

Run: `.gradlew.bat :app:assembleRelease`

Expected artifact: `app/build/outputs/apk/release/GymStatistics 1.3.0.apk`.

- [ ] **Step 6: Review the diff and prepare the requested commit command**

Commit message must describe added/modified/deleted functionality, UI, and interaction logic, and must not include test or build results.
