package com.gymstatistics.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.gymstatistics.GymViewModel
import com.gymstatistics.data.FoodAnalysisCalculator
import com.gymstatistics.data.FoodAnalysisItem
import com.gymstatistics.data.FoodAnalysisRecord
import java.io.File
import java.util.Locale

private enum class FoodPage { HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodAnalysisScreen(viewModel: GymViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = viewModel.uiState
    var page by remember { mutableStateOf<FoodPage?>(null) }
    var draft by remember { mutableStateOf<FoodAnalysisRecord?>(null) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var cameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraPermissionGranted = it
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.analyzeFoodImage(uri)
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state.foodAnalysisResult?.id) {
        state.foodAnalysisResult?.let { draft = it }
    }
    BackHandler {
        when {
            showKeyDialog -> showKeyDialog = false
            draft != null -> {
                draft = null
                viewModel.clearFoodAnalysisResult()
            }
            page != null -> page = null
            else -> onBack()
        }
    }

    Scaffold(
        topBar = {
            if (draft != null || page != null) {
                TopAppBar(
                    title = { Text(if (page != null) "食物分析历史" else "分析结果") },
                    navigationIcon = {
                        IconButton(onClick = {
                            when {
                                draft != null -> {
                                    draft = null
                                    viewModel.clearFoodAnalysisResult()
                                }
                                page != null -> page = null
                                else -> onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { padding ->
        when {
            draft != null -> FoodAnalysisEditor(
                record = draft!!,
                onChange = { draft = it },
                onSave = {
                    viewModel.saveFoodAnalysis(it)
                    draft = null
                },
                modifier = Modifier.padding(padding),
            )
            page != null -> FoodAnalysisHistory(
                records = state.foodAnalyses,
                onOpen = { draft = it },
                onDelete = { deleteId = it },
                modifier = Modifier.padding(padding),
            )
            else -> FoodCameraSurface(
                cameraPermissionGranted = cameraPermissionGranted,
                loading = state.foodAnalysisLoading,
                error = captureError ?: state.foodAnalysisError,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
                onSettings = {
                    keyInput = ""
                    showKeyDialog = true
                },
                onGallery = {
                    captureError = null
                    if (state.deepSeekKeyConfigured) galleryPicker.launch("image/*") else showKeyDialog = true
                },
                onCapture = { cameraView ->
                    if (!cameraPermissionGranted) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    } else if (!state.deepSeekKeyConfigured) {
                        showKeyDialog = true
                    } else if (cameraView == null) {
                        captureError = "相机正在启动，请稍后再试"
                    } else if (!state.foodAnalysisLoading) {
                        captureError = null
                        val file = createFoodCameraFile(context)
                        cameraView.takePicture(
                            file,
                            onSaved = {
                                viewModel.analyzeFoodImage(
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                                )
                            },
                            onError = {
                                file.delete()
                                captureError = "拍照失败：$it"
                            },
                        )
                    }
                },
                onHistory = { page = FoodPage.HISTORY },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("DeepSeek API 设置") },
            text = {
                Column {
                    Text("API Key 仅用于本机调用，并使用系统密钥库加密保存。", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDeepSeekApiKey(keyInput)
                    showKeyDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (state.deepSeekKeyConfigured) {
                        TextButton(onClick = {
                            viewModel.clearDeepSeekApiKey()
                            showKeyDialog = false
                        }) { Text("删除 Key") }
                    }
                    TextButton(onClick = { showKeyDialog = false }) { Text("取消") }
                }
            },
        )
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("删除分析记录") },
            text = { Text("确定删除这条食物分析记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFoodAnalysis(id)
                    deleteId = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun FoodCameraSurface(
    cameraPermissionGranted: Boolean,
    loading: Boolean,
    error: String?,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onGallery: () -> Unit,
    onCapture: (FoodCameraView?) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cameraView by remember { mutableStateOf<FoodCameraView?>(null) }
    DisposableEffect(Unit) {
        onDispose { cameraView?.stop() }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionGranted) {
            AndroidView(
                factory = {
                    FoodCameraView(it).also { view ->
                        cameraView = view
                        view.start()
                    }
                },
                update = { cameraView = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("需要相机权限才能拍摄食物照片", color = Color.White)
                TextButton(onClick = onRequestPermission) { Text("允许相机权限") }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .semantics { contentDescription = "配置 API Key" },
        ) {
            FoodKeyIcon(tint = Color.White)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraCornerAction(CameraActionIcon.GALLERY, "相册", onGallery)
            IconButton(
                onClick = { onCapture(cameraView) },
                enabled = !loading,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .semantics { contentDescription = "拍摄照片" },
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
            CameraCornerAction(CameraActionIcon.HISTORY, "历史", onHistory)
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("正在分析图片…", color = Color.White)
                }
            }
        }
        if (!error.isNullOrBlank()) {
            Text(
                error,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC8B0000))
                    .padding(12.dp),
                fontSize = 13.sp,
            )
        }
    }
}

private enum class CameraActionIcon { GALLERY, HISTORY }

@Composable
private fun CameraCornerAction(
    icon: CameraActionIcon,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (icon) {
            CameraActionIcon.GALLERY -> FoodPhotoIcon(tint = Color.White)
            CameraActionIcon.HISTORY -> FoodHistoryIcon(tint = Color.White)
        }
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun FoodAnalysisEditor(
    record: FoodAnalysisRecord,
    onChange: (FoodAnalysisRecord) -> Unit,
    onSave: (FoodAnalysisRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totals = FoodAnalysisCalculator.total(record)
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(record.foodName, style = MaterialTheme.typography.titleLarge)
                Text("识别类型：${foodTypeLabel(record.imageType)}", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                Text("总重量：${formatNumber(totals.weightG)} g", fontSize = 16.sp)
                Text("整份能量：${formatNumber(totals.nutrition.energyKcal)} kcal", fontSize = 16.sp)
                Text("营养数据均为每100g，可逐项修改", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                if (record.ingredientsText.isNotBlank()) {
                    Text("配料文字：${record.ingredientsText}", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                }
                if (record.uncertaintyNote.isNotBlank()) {
                    Text("说明：${record.uncertaintyNote}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            items(record.items.indices.toList(), key = { it }) { index ->
                val item = record.items[index]
                FoodItemEditor(
                    item = item,
                    onChange = { changed ->
                        val items = record.items.toMutableList()
                        items[index] = changed
                        onChange(record.copy(items = items))
                    },
                )
            }
        }
        Button(onClick = { onSave(record.copy(totalWeightG = totals.weightG)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("保存到食物分析历史")
        }
    }
}

@Composable
private fun FoodItemEditor(item: FoodAnalysisItem, onChange: (FoodAnalysisItem) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text("来源：${sourceLabel(item.nutritionSource)} · 置信度：${confidenceLabel(item.confidence)}", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
            NumericField("重量 (g)", item.weightG) { onChange(item.copy(weightG = it)) }
            NutrientField("能量 (kcal/100g)", item, item.nutritionPer100g.energyKcal) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(energyKcal = it))) }
            NutrientField("能量 (kJ/100g)", item, item.nutritionPer100g.energyKj) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(energyKj = it))) }
            NutrientField("蛋白质 (g/100g)", item, item.nutritionPer100g.proteinG) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(proteinG = it))) }
            NutrientField("脂肪 (g/100g)", item, item.nutritionPer100g.fatG) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(fatG = it))) }
            NutrientField("碳水化合物 (g/100g)", item, item.nutritionPer100g.carbohydrateG) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(carbohydrateG = it))) }
            NutrientField("膳食纤维 (g/100g)", item, item.nutritionPer100g.fiberG) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(fiberG = it))) }
            NutrientField("糖 (g/100g)", item, item.nutritionPer100g.sugarsG) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(sugarsG = it))) }
            NutrientField("钠 (mg/100g)", item, item.nutritionPer100g.sodiumMg) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(sodiumMg = it))) }
            NutrientField("胆固醇 (mg/100g)", item, item.nutritionPer100g.cholesterolMg) { onChange(item.copy(nutritionPer100g = item.nutritionPer100g.copy(cholesterolMg = it))) }
            Text("该项目实际能量：${formatNumber(FoodAnalysisCalculator.itemTotals(item).energyKcal)} kcal", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NutrientField(label: String, item: FoodAnalysisItem, value: Double?, onChange: (Double?) -> Unit) {
    NumericField(label, value, onChange)
}

@Composable
private fun NumericField(label: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.let(::formatNumber).orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.toDoubleOrNull()?.takeIf { number -> number >= 0 })
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FoodAnalysisHistory(
    records: List<FoodAnalysisRecord>,
    onOpen: (FoodAnalysisRecord) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂无食物分析记录", color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(records, key = { it.id }) { record ->
            val totals = FoodAnalysisCalculator.total(record)
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(record) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.foodName, style = MaterialTheme.typography.titleMedium)
                        Text("${foodTypeLabel(record.imageType)} · ${record.createdAt.replace('T', ' ').take(19)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("${formatNumber(totals.weightG)} g · ${formatNumber(totals.nutrition.energyKcal)} kcal", fontSize = 13.sp)
                    }
                    IconButton(onClick = { onDelete(record.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            }
        }
    }
}

private fun createFoodCameraFile(context: Context): File = File.createTempFile("food-analysis-", ".jpg", context.cacheDir)

private fun formatNumber(value: Double?): String = value?.let {
    String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.')
} ?: "未知"

private fun foodTypeLabel(type: String): String = when (type) {
    "meal_photo" -> "食物照片"
    "nutrition_label" -> "营养成分表"
    "ingredient_label" -> "配料表"
    "mixed" -> "食物和标签"
    else -> "未知图片"
}

private fun sourceLabel(source: String): String = when (source) {
    "estimated" -> "AI估算"
    "printed" -> "标签读取"
    "calculated" -> "换算结果"
    else -> "未知"
}

private fun confidenceLabel(confidence: String): String = when (confidence) {
    "high" -> "高"
    "medium" -> "中"
    else -> "低"
}
