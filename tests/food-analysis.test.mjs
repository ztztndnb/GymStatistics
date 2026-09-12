import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const root = new URL("../", import.meta.url);
const source = (path) => {
  const url = new URL(path, root);
  return fs.existsSync(url) ? fs.readFileSync(url, "utf8") : "";
};

const modelsSource = source("app/src/main/java/com/gymstatistics/data/FoodModels.kt");
const calculatorSource = source("app/src/main/java/com/gymstatistics/data/FoodAnalysisCalculator.kt");
const analyzerSource = source("app/src/main/java/com/gymstatistics/ai/DeepSeekFoodAnalyzer.kt");
const promptSource = source("app/src/main/java/com/gymstatistics/ai/FoodAnalysisPrompt.kt");
const repositorySource = source("app/src/main/java/com/gymstatistics/data/FoodAnalysisRepository.kt");
const imageStoreSource = source("app/src/main/java/com/gymstatistics/data/FoodAnalysisImageStore.kt");
const settingsSource = source("app/src/main/java/com/gymstatistics/data/AiSettingsRepository.kt");
const viewModelSource = source("app/src/main/java/com/gymstatistics/GymViewModel.kt");
const uiSource = source("app/src/main/java/com/gymstatistics/ui/GymApp.kt");
const foodUiSource = source("app/src/main/java/com/gymstatistics/ui/FoodAnalysisScreen.kt");
const cameraSource = source("app/src/main/java/com/gymstatistics/ui/FoodCameraView.kt");
const manifestSource = source("app/src/main/AndroidManifest.xml");
const readmeSource = source("README.md");

test("food models expose editable per-100g nutrition fields", () => {
  assert.match(modelsSource, /data class FoodAnalysisRecord/);
  assert.match(modelsSource, /data class FoodAnalysisItem/);
  assert.match(modelsSource, /data class NutritionPer100g/);
  assert.match(modelsSource, /val weightG: Double\?/);
  assert.match(modelsSource, /val energyKcal: Double\?/);
  assert.match(modelsSource, /val sodiumMg: Double\?/);
});

test("calculator scales nutrition by edited weight and calculates totals locally", () => {
  assert.match(calculatorSource, /weightG\?\.takeIf[\s\S]*div\(100\.0\)/);
  assert.match(calculatorSource, /fun total\(record: FoodAnalysisRecord\)/);
});

test("food history is stored in a separate repository and not workout sessions", () => {
  assert.match(repositorySource, /food-analysis-history\.json/);
  assert.match(repositorySource, /FoodAnalysisRecord\.serializer/);
  assert.doesNotMatch(repositorySource, /WorkoutData/);
  assert.match(viewModelSource, /FoodAnalysisRepository/);
  assert.match(viewModelSource, /saveFoodAnalysis/);
  assert.match(viewModelSource, /deleteFoodAnalysis/);
});

test("food analysis history keeps a private image copy and deletes it with its record", () => {
  assert.match(modelsSource, /image_file_name/);
  assert.match(imageStoreSource, /food-analysis-images/);
  assert.match(imageStoreSource, /Bitmap\.CompressFormat\.JPEG/);
  assert.match(viewModelSource, /imageStore\.save/);
  assert.match(viewModelSource, /imageStore\.delete/);
  assert.match(viewModelSource, /saveFoodAnalysisWithoutImage/);
});

test("DeepSeek API key is encrypted with Android Keystore", () => {
  assert.match(settingsSource, /AndroidKeyStore/);
  assert.match(settingsSource, /AES\/GCM\/NoPadding/);
  assert.match(settingsSource, /CIPHERTEXT/);
});

test("DeepSeek analyzer uses the current model and JSON image request", () => {
  assert.match(analyzerSource, /deepseek-flash/);
  assert.match(analyzerSource, /response_format/);
  assert.match(analyzerSource, /image_url/);
});

test("food prompt handles meal photos and labels with per-100g output", () => {
  assert.match(promptSource, /nutrition_label/);
  assert.match(promptSource, /ingredient_label/);
  assert.match(promptSource, /nutrition_per_100g/);
  assert.match(promptSource, /二维码|条形码/);
});

test("main menu replaces the food button with a camera icon beside history", () => {
  assert.match(uiSource, /TextButton\(onClick = \{ historySelected = null; screen = Screen\.HISTORY \}\) \{ Text\("历史"\) \}/);
  assert.match(uiSource, /IconButton\(\s*onClick = \{ screen = Screen\.FOOD \},/);
  assert.match(uiSource, /Icons\.Rounded\.PhotoCamera/);
  assert.doesNotMatch(uiSource, /Text\("AI食物分析"\)/);
});

test("food screen opens an in-app camera with the requested corner actions", () => {
  assert.match(foodUiSource, /FoodCameraView/);
  assert.match(cameraSource, /CameraManager/);
  assert.match(cameraSource, /SurfaceTextureListener/);
  assert.match(cameraSource, /ImageReader/);
  assert.match(cameraSource, /LENS_FACING_BACK/);
  assert.match(foodUiSource, /ActivityResultContracts\.GetContent/);
  assert.match(foodUiSource, /Icons\.Rounded\.Key/);
  assert.match(foodUiSource, /Icons\.Rounded\.PhotoLibrary/);
  assert.match(foodUiSource, /Icons\.Rounded\.History/);
  assert.match(foodUiSource, /CameraCornerAction\(CameraActionIcon\.GALLERY, "相册"/);
  assert.match(foodUiSource, /onCapture =/);
  assert.doesNotMatch(foodUiSource, /TakePicture|LifecycleCameraController|切换摄像头/);
  assert.doesNotMatch(cameraSource, /LENS_FACING_FRONT|DEFAULT_FRONT_CAMERA|switchCamera/);
  assert.match(manifestSource, /android\.permission\.CAMERA/);
});

test("food camera controls use rounded Material icons instead of custom canvas drawings", () => {
  assert.match(foodUiSource, /Icons\.AutoMirrored\.Rounded\.RotateLeft/);
  assert.match(foodUiSource, /Icons\.AutoMirrored\.Rounded\.RotateRight/);
  assert.equal(source("app/src/main/java/com/gymstatistics/ui/FoodIcons.kt"), "");
});

test("camera serializes surface lifecycle before creating a capture session", () => {
  assert.match(cameraSource, /activeSurfaceTexture/);
  assert.match(cameraSource, /SurfaceTexture\? = null/);
  assert.match(cameraSource, /synchronized\(cameraLock\)/);
  assert.match(cameraSource, /if \(!started \|\| activeSurfaceTexture !== surface \|\| !textureView\.isAvailable\)/);
  assert.match(cameraSource, /openCamera\(surface: SurfaceTexture, generation: Long\)/);
  assert.match(cameraSource, /startPreview\(surface: SurfaceTexture, generation: Long\)/);
  assert.match(cameraSource, /IllegalArgumentException/);
});

test("camera queues a tap until the capture session is ready", () => {
  assert.match(cameraSource, /pendingCapture/);
  assert.match(cameraSource, /submitPendingCapture/);
  assert.match(cameraSource, /postDelayed/);
  assert.match(cameraSource, /captureSession = session[\s\S]*submitPendingCapture/);
});

test("camera releases on pause and restarts when the app resumes", () => {
  assert.match(foodUiSource, /LifecycleEventObserver/);
  assert.match(foodUiSource, /Lifecycle\.Event\.ON_PAUSE -> cameraView\?\.stop\(\)/);
  assert.match(foodUiSource, /Lifecycle\.Event\.ON_RESUME -> cameraView\?\.start\(\)/);
  assert.match(foodUiSource, /removeObserver/);
  assert.doesNotMatch(foodUiSource, /DisposableEffect\(cameraView\)/);
});

test("camera preview preserves its aspect ratio with centered cropping", () => {
  assert.match(cameraSource, /calculatePreviewScale/);
  assert.match(cameraSource, /setScale/);
  assert.doesNotMatch(cameraSource, /setRectToRect/);
  assert.match(cameraSource, /setTransform/);
  assert.match(foodUiSource, /MaterialTheme\.colorScheme\.surface/);
  assert.match(foodUiSource, /setBackgroundColor\(letterboxColor\)/);
});

test("camera controls stay visible on light previews and use larger touch targets", () => {
  assert.match(foodUiSource, /val cameraControlColor = MaterialTheme\.colorScheme\.primary/);
  assert.match(foodUiSource, /val cameraControlContentColor = MaterialTheme\.colorScheme\.onPrimary/);
  assert.match(foodUiSource, /\.size\(56\.dp\)[\s\S]*\.background\(cameraControlColor\)/);
  assert.match(foodUiSource, /\.size\(84\.dp\)[\s\S]*\.background\(cameraControlColor\)/);
  assert.match(foodUiSource, /CameraCornerAction\([\s\S]*backgroundColor = cameraControlColor/);
});

test("photo confirmation offers left and right rotation before analysis", () => {
  assert.match(foodUiSource, /向左旋转/);
  assert.match(foodUiSource, /向右旋转/);
  assert.match(foodUiSource, /rotateFoodPhoto/);
});

test("camera entry clears stale food analysis state", () => {
  assert.match(foodUiSource, /LaunchedEffect\(Unit\) \{\s*viewModel\.clearFoodAnalysisResult\(\)/);
});

test("captured camera photos require confirmation before DeepSeek analysis", () => {
  assert.match(foodUiSource, /capturedPhoto/);
  assert.match(foodUiSource, /FoodPhotoConfirmation/);
  assert.match(foodUiSource, /确认并分析/);
  assert.match(foodUiSource, /重新拍摄/);
  assert.doesNotMatch(foodUiSource, /onSaved = \{\s*viewModel\.analyzeFoodImage/);
});

test("food result uses a photo-backed report with editable nutrients and separate history", () => {
  assert.match(foodUiSource, /FoodAnalysisReport/);
  assert.match(foodUiSource, /reportNutrients/);
  assert.match(foodUiSource, /分析原图/);
  assert.match(foodUiSource, /编辑分析结果/);
  assert.match(foodUiSource, /FoodAnalysisEditor/);
  assert.match(foodUiSource, /重量 \(g\)/);
  assert.match(foodUiSource, /kcal\/100g/);
  assert.match(foodUiSource, /保存到食物分析历史/);
  assert.match(foodUiSource, /FoodAnalysisCalculator\.total/);
  assert.doesNotMatch(foodUiSource, /一键记录|我要吐槽|体重管理建议/);
  assert.match(viewModelSource, /foodRepo\.saveAll/);
});

test("food report scrolls the original image away before showing the compact header", () => {
  assert.match(foodUiSource, /if \(bitmap != null\) \{\s*item(?:\(key = "analysis-image"\))? \{/s);
  assert.match(foodUiSource, /compactHeaderIndex = if \(bitmap != null\) 3 else 2/);
  assert.match(foodUiSource, /firstVisibleItemIndex >= compactHeaderIndex/);
  assert.doesNotMatch(foodUiSource, /padding\(top = if \(bitmap != null\) 208\.dp else 0\.dp\)/);
});

test("food ingredients have isolated editor updates that are included in the saved record", () => {
  assert.match(foodUiSource, /replaceFoodAnalysisItem/);
  assert.match(foodUiSource, /editingItemIndex/);
  assert.match(foodUiSource, /onSave\(editedRecord/);
  assert.match(viewModelSource, /private suspend fun persistFoodAnalysis/);
  assert.match(viewModelSource, /persistFoodAnalysis\(record\.copy\(/);
});

test("camera uses a FileProvider and no barcode scanner dependency is introduced", () => {
  assert.match(manifestSource, /FileProvider/);
  assert.doesNotMatch(analyzerSource, /barcode|QR/i);
});

test("release metadata is 1.3.10 with the requested APK name", () => {
  assert.match(source("app/build.gradle.kts"), /versionCode = 27/);
  assert.match(source("app/build.gradle.kts"), /versionName = "1\.3\.10"/);
  assert.match(readmeSource, /GymStatistics 1\.3\.10\.apk/);
});
