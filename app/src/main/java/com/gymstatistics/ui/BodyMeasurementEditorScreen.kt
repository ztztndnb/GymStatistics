package com.gymstatistics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gymstatistics.GymViewModel
import com.gymstatistics.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyMeasurementEditorScreen(
    model: GymViewModel,
    initialDate: LocalDate,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val body = model.uiState.data.bodyData ?: BodyData()
    var dateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    val date = LocalDate.parse(dateText)
    val existing = body.records.find { it.date == dateText }
    val originalInputs = rememberSaveable(dateText) { HashMap(existing?.values.orEmpty().mapValues { bodyNumber(it.value) }) }
    var inputs by rememberSaveable(dateText) { mutableStateOf(HashMap(originalInputs)) }
    var showMetrics by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember(dateText) { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<String?>(null) }
    val hasChanges = inputs.mapValues { it.value.trim() }.filterValues { it.isNotBlank() } != originalInputs
    fun requestLeave(nextDate: String? = null) {
        if (busy || nextDate == dateText) return
        if (hasChanges) { pendingDate = nextDate; showDiscard = true }
        else if (nextDate != null) dateText = nextDate else onBack()
    }
    BackHandler { requestLeave() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(
            title = { Text(if (existing == null) "添加身体记录" else "编辑身体记录") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = { IconButton(onClick = { requestLeave() }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回身体数据") } },
            actions = { IconButton(onClick = { showMetrics = true }, enabled = !busy) { Icon(Icons.Default.Settings, "管理身体指标") } },
        ) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                Button(
                enabled = !busy && model.uiState.loaded,
                onClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    val values = existing?.values.orEmpty().filterKeys { id -> body.metrics.none { it.id == id && it.enabled } }.toMutableMap()
                    error = null
                    for (metric in body.metrics.filter { it.enabled }) {
                        val text = inputs[metric.id].orEmpty().trim()
                        if (text.isBlank()) continue
                        val value = text.toDoubleOrNull()
                        if (value == null || !value.isFinite() || value <= 0) {
                            error = "请为${metric.name}填写大于 0 的数值"
                            break
                        }
                        values[metric.id] = value
                    }
                    if (error == null && values.isEmpty()) error = "请至少填写一项身体数据"
                    if (error == null) {
                        busy = true
                        model.saveBodyRecord(dateText, values) { success ->
                            busy = false
                            if (success) onSaved() else error = "保存失败，输入已保留，请重试"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (busy) "保存中…" else "保存身体数据") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("body-editor"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { requestLeave(date.minusDays(1).toString()) }, enabled = !busy) { Text("前一天") }
                    TextButton(onClick = { showCalendar = true }, enabled = !busy) { Text(dateText) }
                    TextButton(onClick = { requestLeave(date.plusDays(1).toString()) }, enabled = !busy) { Text("后一天") }
                }
                Text("仅记录当天测量值；留空不补零。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(body.metrics.filter { it.enabled }, key = { it.id }) { metric ->
                TextField(
                    value = inputs[metric.id].orEmpty(),
                    onValueChange = { inputs = HashMap(inputs).apply { put(metric.id, it) } },
                    enabled = !busy,
                    label = { Text("${metric.name} (${metric.unit})") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
            }
            if (body.metrics.none { it.enabled }) item { Text("暂无启用指标，可在右上角管理指标。") }
            if (existing != null) {
                val hidden = body.metrics.filter { !it.enabled && it.id in existing.values }
                if (hidden.isNotEmpty()) item { Text("已停用指标的历史数值将保留：" + hidden.joinToString { "${it.name} ${bodyNumber(existing.values.getValue(it.id))} ${it.unit}" }, style = MaterialTheme.typography.bodySmall) }
                item { TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("删除当天记录", color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
    if (showCalendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showCalendar = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { requestLeave(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; showCalendar = false }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("取消") } }) { DatePicker(picker) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!busy) confirmDelete = false }, title = { Text("删除当天身体数据？") },
        text = { Text("将删除 $dateText 的所有身体测量值。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            model.deleteBodyRecord(dateText) { success ->
                busy = false; confirmDelete = false
                if (success) onSaved() else error = "删除失败，请重试"
            }
        }) { Text("确认删除") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("取消") } },
    )
    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false; pendingDate = null },
        title = { Text("放弃未保存的修改？") },
        text = { Text("当前输入尚未保存，离开后将丢弃这些修改。") },
        confirmButton = { TextButton(onClick = {
            val nextDate = pendingDate
            showDiscard = false; pendingDate = null
            if (nextDate != null) dateText = nextDate else onBack()
        }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { showDiscard = false; pendingDate = null }) { Text("继续编辑") } },
    )
    if (showMetrics) BodyMetricManager(model) { showMetrics = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyMetricManager(model: GymViewModel, onBack: () -> Unit) {
    val data = model.uiState.data.bodyData ?: BodyData()
    var editing by remember { mutableStateOf<BodyMetric?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!busy) onBack() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text("身体指标") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回指标编辑") }
        }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("body-metrics"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("停用指标后不再显示输入框，已记录的数值仍保留。", style = MaterialTheme.typography.bodySmall) }
                item { OutlinedButton(enabled = !busy, onClick = { editing = BodyMetric(UUID.randomUUID().toString(), "", "") }) { Text("添加指标") } }
                if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                items(data.metrics, key = { it.id }) { metric ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(metric.name); Text(metric.unit, style = MaterialTheme.typography.bodySmall) }
                        IconButton(enabled = !busy, onClick = { editing = metric }) { Icon(Icons.Default.Edit, "编辑指标${metric.name}") }
                        Switch(checked = metric.enabled, enabled = !busy, onCheckedChange = { enabled ->
                            busy = true
                            model.saveBodyMetric(metric.copy(enabled = enabled)) { success ->
                                busy = false; error = if (success) null else model.uiState.message ?: "保存失败，请重试"
                            }
                        }, modifier = Modifier.semantics { contentDescription = (if (metric.enabled) "停用" else "启用") + metric.name })
                    }
                }
            }
        }
        editing?.let { metric ->
            var name by remember(metric.id) { mutableStateOf(metric.name) }
            var unit by remember(metric.id) { mutableStateOf(metric.unit) }
            var dialogError by remember(metric.id) { mutableStateOf<String?>(null) }
            val unitLocked = defaultBodyMetrics().any { it.id == metric.id } || data.records.any { metric.id in it.values }
            AlertDialog(onDismissRequest = { if (!busy) editing = null }, title = { Text(if (metric.name.isBlank()) "新建身体指标" else "编辑身体指标") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val fieldColors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    )
                    TextField(name, { name = it }, label = { Text("指标名称") }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(14.dp), colors = fieldColors)
                    TextField(unit, { unit = it }, label = { Text("单位") }, singleLine = true, enabled = !busy && !unitLocked, shape = RoundedCornerShape(14.dp), colors = fieldColors)
                    if (unitLocked) Text("已有记录或预设指标的单位不能更改；需要其他单位时请新建指标。", style = MaterialTheme.typography.bodySmall)
                    if (dialogError != null) Text(dialogError!!, color = MaterialTheme.colorScheme.error)
                }
            }, confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                model.saveBodyMetric(metric.copy(name = name, unit = unit)) { success ->
                    busy = false
                    if (success) editing = null else dialogError = model.uiState.message ?: "保存失败，请重试"
                }
            }) { Text("保存指标") } }, dismissButton = { TextButton(enabled = !busy, onClick = { editing = null }) { Text("取消") } })
        }
    }
}
