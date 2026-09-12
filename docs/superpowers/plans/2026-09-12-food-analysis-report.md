# 食物 AI 营养分析报告实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 AI 食物分析结果与历史记录重构为带原图、可编辑、可持久保存的营养报告页。

**Architecture:** `FoodAnalysisRecord` 保存应用私有图片的相对文件名；`FoodAnalysisRepository` 负责图片压缩、读取和删除。`FoodAnalysisScreen` 持有当前分析图片 URI，展示报告页；历史记录优先读取持久图片，旧记录或缺图记录降级为无图报告。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android `BitmapFactory`/`ContentResolver`、kotlinx.serialization、JUnit 4。

**Spec:** `docs/superpowers/specs/2026-09-12-food-analysis-report-design.md`

## Global Constraints

- 保持 AI 结果与训练日期、日常饮食记录分离。
- 不新增第三方依赖；图片处理只使用 Android 平台 API。
- 图片最长边限制为 2048px，保存为 JPEG 私有副本。
- 正式版版本为 `1.3.9`、versionCode 为 `26`，APK 名称为 `GymStatistics 1.3.9.apk`。
- 不自动提交；完成后向用户提供含新增、修改、删除功能/UI/交互说明的 PowerShell commit 指令，且提交说明不写测试或构建结果。

---

### Task 1: 保存可恢复的图片引用与受限 JPEG 副本

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/data/FoodModels.kt`
- Create: `app/src/main/java/com/gymstatistics/data/FoodAnalysisImageStore.kt`
- Create: `app/src/test/java/com/gymstatistics/data/FoodAnalysisImageStoreTest.kt`

**Interfaces:**
- Produces `FoodAnalysisRecord.imageFileName: String`，默认空字符串，保证旧 JSON 可反序列化。
- Produces `FoodAnalysisImageStore.save(uri: Uri, recordId: String): String?`，成功返回相对文件名，失败返回 `null`。
- Produces `FoodAnalysisImageStore.file(name: String): File?` 与 `delete(name: String)`。
- Produces `scaledImageSize(width: Int, height: Int, maxEdge: Int = 2048): android.util.Size`，供本地单元测试验证尺寸约束。

- [ ] **Step 1: 写入失败测试**

```kotlin
@Test
fun scaledImageSizeLimitsTheLongestEdgeAndPreservesAspectRatio() {
    assertEquals(Size(2048, 1536), scaledImageSize(4000, 3000))
    assertEquals(Size(1000, 750), scaledImageSize(1000, 750))
}

@Test
fun oldFoodAnalysisJsonUsesAnEmptyImageFileName() {
    val record = AppJson.json.decodeFromString<FoodAnalysisRecord>("{\"id\":\"old\"}")
    assertEquals("", record.imageFileName)
}
```

- [ ] **Step 2: 运行测试，确认失败原因是缺少图片字段和尺寸函数**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.FoodAnalysisImageStoreTest`

Expected: 编译失败，提示 `imageFileName` 与 `scaledImageSize` 未定义。

- [ ] **Step 3: 添加数据字段和最小图片存储实现**

```kotlin
@SerialName("image_file_name")
val imageFileName: String = "",
```

```kotlin
internal fun scaledImageSize(width: Int, height: Int, maxEdge: Int = 2048): Size {
    if (width <= maxEdge && height <= maxEdge) return Size(width, height)
    val scale = maxEdge.toFloat() / maxOf(width, height)
    return Size((width * scale).toInt(), (height * scale).toInt())
}
```

`FoodAnalysisImageStore` 使用两次 `ContentResolver.openInputStream`：第一次读取 bounds，第二次解码；用 `Bitmap.createScaledBitmap` 压到 `scaledImageSize`，然后写入 `filesDir/food-analysis-images/<recordId>.jpg`。写入失败时删除半成品文件并返回 `null`。

- [ ] **Step 4: 运行测试，确认通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.FoodAnalysisImageStoreTest`

Expected: PASS。

- [ ] **Step 5: 记录待提交文件，不自动提交**

保留变更，最终与后续任务一并给出单一 commit 指令。

### Task 2: 保存和删除历史记录时管理图片副本

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/GymViewModel.kt`
- Modify: `app/src/test/java/com/gymstatistics/data/FoodAnalysisImageStoreTest.kt`

**Interfaces:**
- Consumes `FoodAnalysisImageStore.save`, `FoodAnalysisImageStore.delete`。
- Changes `saveFoodAnalysis(record: FoodAnalysisRecord, sourceImage: Uri?, onImageSaveFailed: () -> Unit)`。
- Produces `saveFoodAnalysisWithoutImage(record: FoodAnalysisRecord)`，用于用户在图片保存失败后明确选择仅保存文本。
- Keeps `deleteFoodAnalysis(id: String)`，但在删除 JSON 记录前删除其关联图片。

- [ ] **Step 1: 写入失败测试**

```kotlin
@Test
fun imageFileNameUsesTheRecordIdAndJpegExtension() {
    assertEquals("record-42.jpg", foodImageFileName("record-42"))
}

@Test
fun blankRecordIdDoesNotProduceAnImageFileName() {
    assertEquals("", foodImageFileName(""))
}
```

- [ ] **Step 2: 运行测试，确认失败原因是文件名和查找接口尚不存在**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.FoodAnalysisImageStoreTest`

Expected: 编译失败，提示 `foodImageFileName` 或 `file` 未定义。

- [ ] **Step 3: 让 ViewModel 在 IO 协程中保存图片与 JSON**

```kotlin
fun saveFoodAnalysis(record: FoodAnalysisRecord, sourceImage: Uri?, onImageSaveFailed: () -> Unit) {
    viewModelScope.launch {
        val imageName = sourceImage?.let { withContext(Dispatchers.IO) { imageStore.save(it, record.id) } }
        if (sourceImage != null && imageName == null) {
            onImageSaveFailed()
            return@launch
        }
        saveFoodAnalysisWithoutImage(record.copy(imageFileName = imageName ?: record.imageFileName))
    }
}
```

`saveFoodAnalysisWithoutImage` 在主线程保留现有同 ID 替换、排序、更新 UI 的行为，并在其协程中写入 JSON。`deleteFoodAnalysis` 读取被删除记录的 `imageFileName`，在 IO 协程中调用 `imageStore.delete` 再写入 JSON。旧记录的空文件名不会触发文件操作。

- [ ] **Step 4: 运行数据测试与既有分析测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.gymstatistics.data.FoodAnalysisImageStoreTest`

Expected: PASS。

- [ ] **Step 5: 记录待提交文件，不自动提交**

保留变更，最终与后续任务一并给出单一 commit 指令。

### Task 3: 将相机、相册和历史入口统一为报告状态

**Files:**
- Modify: `app/src/main/java/com/gymstatistics/ui/FoodAnalysisScreen.kt`
- Modify: `tests/food-analysis.test.mjs`

**Interfaces:**
- Consumes `FoodAnalysisRecord.imageFileName` 和 `FoodAnalysisImageStore` 的图片目录约定。
- Produces `FoodAnalysisReport(record, imageFile, onBack, onEdit, onSave)`。
- Produces `FoodReportNutrient(label: String, value: Double, unit: String)` 与 `reportNutrients(nutrition: NutritionPer100g)`；只返回非空值。

- [ ] **Step 1: 写入失败检查**

```javascript
test("food result uses a photo-backed report and only renders returned nutrients", () => {
  assert.match(foodUiSource, /FoodAnalysisReport/);
  assert.match(foodUiSource, /reportNutrients/);
  assert.match(foodUiSource, /保存到食物分析历史/);
  assert.doesNotMatch(foodUiSource, /一键记录|我要吐槽|体重管理建议/);
});
```

并将现有“food result editing”检查改为期待报告入口和独立编辑入口。

- [ ] **Step 2: 运行检查，确认失败原因是报告页尚不存在**

Run: `node --test tests/food-analysis.test.mjs`

Expected: FAIL，断言 `FoodAnalysisReport` 未匹配。

- [ ] **Step 3: 实现报告页与状态切换**

报告页使用 `Box` 放置原图背景、半透明遮罩和白色 `LazyColumn` 报告面板；照片可用时使用 `ContentScale.Crop`，不可用时只显示白色报告布局。报告面板包含：

```kotlin
val totals = FoodAnalysisCalculator.total(record)
Text("${formatNumber(totals.weightG)} g")
Text("${formatNumber(totals.nutrition.energyKcal)} 千卡")
```

三大营养素来自 `totals.nutrition`；食材明细使用 `record.items`；营养网格使用 `reportNutrients` 返回的非空值。底部固定放置“编辑分析结果”和“保存到食物分析历史”。

相册选图后保存 URI 并直接分析；相机确认后保存 `FileProvider` URI 并分析。模型返回后保留该 URI 进入报告页，不再在成功回调中删除相机临时照片。离开未保存报告时删除相机缓存文件；相册 URI 不删除。历史打开时根据 `record.imageFileName` 解析私有文件。图片保存失败显示对话框，提供“重试”和“仅保存文本”。

- [ ] **Step 4: 运行分析页面检查**

Run: `node --test tests/food-analysis.test.mjs`

Expected: PASS。

- [ ] **Step 5: 记录待提交文件，不自动提交**

保留变更，最终与后续任务一并给出单一 commit 指令。

### Task 4: 升级版本、更新说明并执行完整验证

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `tests/food-analysis.test.mjs`

**Interfaces:**
- Version: `versionCode = 26`、`versionName = "1.3.9"`。
- Release artifact: `app/build/outputs/apk/release/GymStatistics 1.3.9.apk`。

- [ ] **Step 1: 写入失败版本检查**

```javascript
assert.match(source("app/build.gradle.kts"), /versionCode = 26/);
assert.match(source("app/build.gradle.kts"), /versionName = "1\\.3\\.9"/);
assert.match(readmeSource, /GymStatistics 1\\.3\\.9\\.apk/);
```

- [ ] **Step 2: 运行检查，确认版本仍为 1.3.8 而失败**

Run: `node --test tests/food-analysis.test.mjs`

Expected: FAIL，版本元数据不匹配。

- [ ] **Step 3: 更新版本和 README**

```kotlin
versionCode = 26
versionName = "1.3.9"
```

README 更新为 1.3.9 与对应正式版 APK 名称。

- [ ] **Step 4: 执行完整验证与正式版构建**

Run: `node --test tests/food-analysis.test.mjs tests/dashboard-import.test.mjs`

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`

然后将 `app-release.apk` 复制为 `GymStatistics 1.3.9.apk`，验证正式版 APK 签名。

- [ ] **Step 5: 交付文件与 commit 指令**

运行 `git diff --check`，确认无空白错误；提供正式版 APK 链接和一条由用户执行的 PowerShell commit 指令。

## Plan Self-Review

- 规格覆盖：Task 1-2 实现图片保存、更新、删除、旧记录与失败回退；Task 3 实现报告布局、动态营养项目和历史打开交互；Task 4 实现版本、APK 命名与验证。
- 占位检查：没有未定义的后续步骤。
- 类型一致性：`imageFileName` 由记录承载；`sourceImage: Uri?` 只在当前报告保存时使用；历史记录无需外部 URI。
