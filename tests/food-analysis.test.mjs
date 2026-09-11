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
const settingsSource = source("app/src/main/java/com/gymstatistics/data/AiSettingsRepository.kt");
const viewModelSource = source("app/src/main/java/com/gymstatistics/GymViewModel.kt");
const uiSource = source("app/src/main/java/com/gymstatistics/ui/GymApp.kt");
const foodUiSource = source("app/src/main/java/com/gymstatistics/ui/FoodAnalysisScreen.kt");
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

test("food screen offers camera and gallery sources", () => {
  assert.match(foodUiSource, /TakePicture/);
  assert.match(foodUiSource, /GetContent/);
  assert.match(foodUiSource, /从相册选择/);
  assert.match(uiSource, /AI食物分析/);
});

test("food result editing recalculates editable nutrients and saves separate history", () => {
  assert.match(foodUiSource, /FoodAnalysisEditor/);
  assert.match(foodUiSource, /重量 \(g\)/);
  assert.match(foodUiSource, /kcal\/100g/);
  assert.match(foodUiSource, /保存到食物分析历史/);
  assert.match(foodUiSource, /FoodAnalysisCalculator\.total/);
  assert.match(viewModelSource, /foodRepo\.saveAll/);
});

test("camera uses a FileProvider and no barcode scanner dependency is introduced", () => {
  assert.match(manifestSource, /FileProvider/);
  assert.doesNotMatch(analyzerSource, /barcode|QR/i);
});

test("release metadata is 1.3.0 with the requested APK name", () => {
  assert.match(source("app/build.gradle.kts"), /versionCode = 17/);
  assert.match(source("app/build.gradle.kts"), /versionName = "1\.3\.0"/);
  assert.match(readmeSource, /GymStatistics 1\.3\.0\.apk/);
});
