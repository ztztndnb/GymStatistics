package com.gymstatistics.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gymstatistics.GymViewModel
import com.gymstatistics.data.FoodAnalysisCalculator
import com.gymstatistics.data.FoodAnalysisItem
import com.gymstatistics.data.FoodAnalysisRecord
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FoodPage { HISTORY }

internal enum class FoodPhotoRotation(val degrees: Float) {
    LEFT(-90f),
    RIGHT(90f),
}

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
    var capturedPhoto by remember { mutableStateOf<File?>(null) }
    var cameraEntryReady by remember { mutableStateOf(false) }
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
        viewModel.clearFoodAnalysisResult()
        cameraEntryReady = true
        if (!cameraPermissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state.foodAnalysisResult?.id) {
        if (cameraEntryReady) {
            state.foodAnalysisResult?.let {
                capturedPhoto?.delete()
                capturedPhoto = null
                draft = it
            }
        }
    }
    BackHandler {
        when {
            showKeyDialog -> showKeyDialog = false
            capturedPhoto != null -> {
                capturedPhoto?.delete()
                capturedPhoto = null
                viewModel.clearFoodAnalysisResult()
            }
            draft != null -> {
                draft = null
                viewModel.clearFoodAnalysisResult()
            }
            page != null -> {
                page = null
                viewModel.clearFoodAnalysisResult()
            }
            else -> {
                viewModel.clearFoodAnalysisResult()
                onBack()
            }
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
                                page != null -> {
                                    page = null
                                    viewModel.clearFoodAnalysisResult()
                                }
                                else -> {
                                    viewModel.clearFoodAnalysisResult()
                                    onBack()
                                }
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
            capturedPhoto != null -> FoodPhotoConfirmation(
                photoFile = capturedPhoto!!,
                loading = state.foodAnalysisLoading,
                error = captureError ?: state.foodAnalysisError,
                onRetake = {
                    capturedPhoto?.delete()
                    capturedPhoto = null
                    captureError = null
                    viewModel.clearFoodAnalysisResult()
                },
                onConfirm = {
                    if (!state.deepSeekKeyConfigured) {
                        showKeyDialog = true
                    } else {
                        capturedPhoto?.let { photo ->
                            viewModel.analyzeFoodImage(
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo),
                            )
                        }
                    }
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
                onBack = {
                    viewModel.clearFoodAnalysisResult()
                    captureError = null
                    onBack()
                },
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
                            onSaved = { capturedPhoto = file },
                            onError = {
                                file.delete()
                                captureError = "拍照失败：$it"
                            },
                        )
                    }
                },
                onHistory = {
                    viewModel.clearFoodAnalysisResult()
                    page = FoodPage.HISTORY
                },
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val letterboxColor = MaterialTheme.colorScheme.surface.toArgb()
    val cameraControlColor = MaterialTheme.colorScheme.primary
    val cameraControlContentColor = MaterialTheme.colorScheme.onPrimary
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> cameraView?.stop()
                Lifecycle.Event.ON_RESUME -> cameraView?.start()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraView?.stop()
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (cameraPermissionGranted) {
            AndroidView(
                factory = {
                    FoodCameraView(it).also { view ->
                        view.setBackgroundColor(letterboxColor)
                        cameraView = view
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            view.start()
                        }
                    }
                },
                update = {
                    cameraView = it
                    it.setBackgroundColor(letterboxColor)
                },
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
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(cameraControlColor),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = cameraControlContentColor,
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(cameraControlColor)
                .semantics { contentDescription = "配置 API Key" },
        ) {
            FoodKeyIcon(tint = cameraControlContentColor, modifier = Modifier.size(30.dp))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraCornerAction(CameraActionIcon.GALLERY, "相册", onGallery,
                backgroundColor = cameraControlColor,
                contentColor = cameraControlContentColor,
            )
            IconButton(
                onClick = { onCapture(cameraView) },
                enabled = !loading,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(cameraControlColor)
                    .semantics { contentDescription = "拍摄照片" },
            ) {
                Box(
                    Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(cameraControlContentColor)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(cameraControlColor),
                )
            }
            CameraCornerAction(CameraActionIcon.HISTORY, "历史", onHistory,
                backgroundColor = cameraControlColor,
                contentColor = cameraControlContentColor,
            )
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

@Composable
private fun FoodPhotoConfirmation(
    photoFile: File,
    loading: Boolean,
    error: String?,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var photoRevision by remember(photoFile) { mutableStateOf(0) }
    var rotating by remember(photoFile) { mutableStateOf(false) }
    var rotationError by remember(photoFile) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val bitmap = remember(photoFile, photoRevision) { decodeFoodPhoto(photoFile) }
    fun rotate(direction: FoodPhotoRotation) {
        if (rotating || loading || bitmap == null) return
        rotating = true
        rotationError = null
        scope.launch {
            val rotated = withContext(Dispatchers.IO) { rotateFoodPhoto(photoFile, direction) }
            if (rotated) photoRevision += 1 else rotationError = "旋转照片失败，请重试"
            rotating = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text("确认照片", style = MaterialTheme.typography.titleLarge)
        Text(
            "确认图片清晰、主体完整后，再发送给 DeepSeek 分析。",
            color = MaterialTheme.colorScheme.outline,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "待确认的食物照片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("照片加载失败，请重新拍摄", color = MaterialTheme.colorScheme.error)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { rotate(FoodPhotoRotation.LEFT) },
                enabled = bitmap != null && !loading && !rotating,
                modifier = Modifier.weight(1f),
            ) {
                Text("向左旋转")
            }
            OutlinedButton(
                onClick = { rotate(FoodPhotoRotation.RIGHT) },
                enabled = bitmap != null && !loading && !rotating,
                modifier = Modifier.weight(1f),
            ) {
                Text("向右旋转")
            }
        }
        val visibleError = rotationError ?: error
        if (!visibleError.isNullOrBlank()) {
            Text(
                visibleError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRetake,
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) {
                Text("重新拍摄")
            }
            Button(
                onClick = onConfirm,
                enabled = bitmap != null && !loading && !rotating,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (loading) "正在分析…" else if (rotating) "正在旋转…" else "确认并分析")
            }
        }
    }
}

private fun decodeFoodPhoto(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 2048 || bounds.outHeight / sampleSize > 2048) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

private fun rotateFoodPhoto(file: File, direction: FoodPhotoRotation): Boolean {
    val bitmap = decodeFoodPhoto(file) ?: return false
    val rotated = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(direction.degrees) },
        true,
    )
    val temporary = File(file.parentFile, "${file.name}.rotating")
    return try {
        FileOutputStream(temporary).use { output ->
            check(rotated.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        temporary.copyTo(file, overwrite = true)
        true
    } catch (_: Exception) {
        false
    } finally {
        temporary.delete()
        if (rotated !== bitmap) rotated.recycle()
        bitmap.recycle()
    }
}

private enum class CameraActionIcon { GALLERY, HISTORY }

@Composable
private fun CameraCornerAction(
    icon: CameraActionIcon,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
) {
    Column(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (icon) {
            CameraActionIcon.GALLERY -> FoodPhotoIcon(tint = contentColor, modifier = Modifier.size(32.dp))
            CameraActionIcon.HISTORY -> FoodHistoryIcon(tint = contentColor, modifier = Modifier.size(32.dp))
        }
        Text(label, color = contentColor, fontSize = 13.sp)
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
