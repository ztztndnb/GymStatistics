package com.gymstatistics.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gymstatistics.GymViewModel
import com.gymstatistics.data.ActionRecord
import com.gymstatistics.data.ActionSummary
import com.gymstatistics.data.AppJson
import com.gymstatistics.data.ChartSeries
import com.gymstatistics.data.ExerciseRecord
import com.gymstatistics.data.ImportMode
import com.gymstatistics.data.MuscleSelection
import com.gymstatistics.data.SessionRecord
import com.gymstatistics.data.SyncPayload
import com.gymstatistics.data.WorkoutData
import com.gymstatistics.data.buildActions
import com.gymstatistics.data.buildSeriesByData
import com.gymstatistics.data.buildSeriesByTotalCount
import com.gymstatistics.data.fatigueWarningsFor
import android.icu.text.Transliterator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class Screen { MAIN, HISTORY, SYNC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymApp(viewModel: GymViewModel) {
    val state = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var historySelected by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showCalendar by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editingExerciseId by remember { mutableStateOf<String?>(null) }
    var prefillExercise by remember { mutableStateOf<ExerciseRecord?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragDirection by remember { mutableStateOf(0) }
    var isSettling by remember { mutableStateOf(false) }
    val settleOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // forward: new screen slides in from the right; backward: from the left (like a back navigation)
            if (targetState.ordinal >= initialState.ordinal) {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
            } else {
                (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "screen",
    ) { scr ->
        when (scr) {
            Screen.MAIN -> {
                val dateKey = selectedDate.toString()
                val sessionForDay = state.data.sessions.firstOrNull { it.date == dateKey }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("GymStatistics", fontWeight = FontWeight.Bold) },
                            actions = { TextButton(onClick = { historySelected = null; screen = Screen.HISTORY }) { Text("历史") } },
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        DatePickerHeader(selectedDate = selectedDate, onOpenCalendar = { showCalendar = true })
                        WeekDayBar(selectedDate = selectedDate, onSelect = { selectedDate = it })
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            dragOffset = 0f
                                            dragDirection = 0
                                        },
                                        onDragEnd = {
                                            val direction = dragDirection
                                            val commit = direction != 0 &&
                                                ((direction > 0 && dragOffset < -100f) || (direction < 0 && dragOffset > 100f))
                                            val target = if (commit) -direction * size.width.toFloat() else 0f
                                            scope.launch {
                                                isSettling = true
                                                settleOffset.snapTo(dragOffset)
                                                settleOffset.animateTo(target, tween(220))
                                                if (commit) selectedDate = selectedDate.plusDays(direction.toLong())
                                                dragOffset = 0f
                                                dragDirection = 0
                                                settleOffset.snapTo(0f)
                                                isSettling = false
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                isSettling = true
                                                settleOffset.snapTo(dragOffset)
                                                settleOffset.animateTo(0f, tween(220))
                                                dragOffset = 0f
                                                dragDirection = 0
                                                settleOffset.snapTo(0f)
                                                isSettling = false
                                            }
                                        },
                                    ) { change, amount ->
                                        if (dragDirection == 0 && amount != 0f) {
                                            dragDirection = if (amount < 0f) 1 else -1
                                        }
                                        dragOffset = (dragOffset + amount).coerceIn(-size.width.toFloat(), size.width.toFloat())
                                    }
                                },
                        ) {
                            val pageWidth = with(LocalDensity.current) { maxWidth.toPx() }
                            val contentOffset = if (isSettling) settleOffset.value else dragOffset
                            Box(Modifier.fillMaxSize()) {
                                if (dragDirection != 0) {
                                    WorkoutDayContent(
                                        date = selectedDate.plusDays(dragDirection.toLong()),
                                        sessions = state.data.sessions,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { translationX = contentOffset + pageWidth * dragDirection },
                                        onEdit = { ex -> editingExerciseId = ex.id; prefillExercise = ex; showAdd = true },
                                        onDelete = { confirmDeleteId = it },
                                    )
                                }
                                WorkoutDayContent(
                                    date = selectedDate,
                                    sessions = state.data.sessions,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = contentOffset },
                                    onEdit = { ex -> editingExerciseId = ex.id; prefillExercise = ex; showAdd = true },
                                    onDelete = { confirmDeleteId = it },
                                )
                            }
                        }
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ElevatedButton(
                                onClick = { editingExerciseId = null; prefillExercise = null; showAdd = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("记录动作")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { screen = Screen.SYNC }, modifier = Modifier.fillMaxWidth()) {
                                Text("局域网同步")
                            }
                        }
                    }
                }

                if (showCalendar) {
                    MonthCalendarDialog(
                        initial = selectedDate,
                        onDismiss = { showCalendar = false },
                        onSelect = { selectedDate = it; showCalendar = false },
                    )
                }
                if (showAdd) {
                    AddSessionDialog(
                        defaultDate = selectedDate,
                        initial = prefillExercise,
                        initialNote = prefillExercise?.note ?: "",
                        isEditing = editingExerciseId != null,
                        onDismiss = { showAdd = false; editingExerciseId = null; prefillExercise = null },
                        onConfirm = { date, note, exercise ->
                            if (editingExerciseId != null) viewModel.updateExercise(date, editingExerciseId!!, exercise, note)
                            else viewModel.addExercise(date, note, exercise)
                            showAdd = false; editingExerciseId = null; prefillExercise = null
                        },
                    )
                }
                if (confirmDeleteId != null) {
                    AlertDialog(
                        onDismissRequest = { confirmDeleteId = null },
                        title = { Text("删除动作") },
                        text = { Text("确定删除这条动作记录吗?") },
                        confirmButton = { TextButton(onClick = { viewModel.deleteExercise(dateKey, confirmDeleteId!!); confirmDeleteId = null }) { Text("删除") } },
                        dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("取消") } },
                    )
                }
            }

            Screen.HISTORY -> HistoryScreen(
                data = state.data,
                initial = historySelected,
                onSetFatigueSuppressed = { name, sup -> viewModel.setFatigueSuppressed(name, sup) },
                onEditMuscles = { name, muscles -> viewModel.updateMusclesForAction(name, muscles) },
                onBack = { screen = Screen.MAIN },
                onPickFill = { name, record ->
                    prefillExercise = ExerciseRecord(name = name, data = record.data, unit = record.unit, count = record.count, sets = record.sets, muscles = record.muscles)
                    editingExerciseId = null
                    screen = Screen.MAIN
                    showAdd = true
                },
            )

            Screen.SYNC -> SyncPage(viewModel = viewModel, onBack = { screen = Screen.MAIN })
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
}

@Composable
private fun WorkoutDayContent(
    date: LocalDate,
    sessions: List<SessionRecord>,
    modifier: Modifier = Modifier,
    onEdit: (ExerciseRecord) -> Unit,
    onDelete: (String) -> Unit,
) {
    val session = sessions.firstOrNull { it.date == date.toString() }
    val dayExercises = session?.exercises ?: emptyList()
    if (dayExercises.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("这一天没有训练记录", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
        }
    } else {
        Column(modifier = modifier) {
            session?.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(dayExercises, key = { index, exercise -> "${exercise.id.ifEmpty { exercise.name }}#$index" }) { _, ex ->
                    ActionItemCard(
                        exercise = ex,
                        onEdit = { onEdit(ex) },
                        onDelete = { onDelete(ex.id) },
                    )
                }
            }
        }
    }
}

// ---------- Date selection (week bar + month calendar) ----------

private val weekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/** Sunday-based start of the week containing [date]. */
private fun weekStart(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value % 7).toLong())

private fun dateHeaderLabel(date: LocalDate): String =
    if (date == LocalDate.now()) "今天"
    else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

@Composable
private fun DatePickerHeader(selectedDate: LocalDate, onOpenCalendar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCalendar)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            dateHeaderLabel(selectedDate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text("▾", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
    }
    Text(
        weekRangeLabel(selectedDate),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outline,
        fontSize = 12.sp,
    )
}

private fun weekRangeLabel(date: LocalDate): String {
    val start = weekStart(date)
    val end = start.plusDays(6)
    return "${start.monthValue}月${start.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
}

@Composable
private fun WeekDayBar(selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    val start = weekStart(selectedDate)
    val today = LocalDate.now()
    val selectedIndex = ChronoUnit.DAYS.between(start, selectedDate).toInt().coerceIn(0, 6)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val cellWidth = maxWidth / 7
        val highlightX = (cellWidth - 34.dp) / 2 + cellWidth * selectedIndex
        val animatedHighlightX by animateDpAsState(
            targetValue = highlightX,
            animationSpec = tween(durationMillis = 260),
            label = "selected date highlight",
        )
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = animatedHighlightX)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { i ->
                val d = start.plusDays(i.toLong())
                val isSelected = d == selectedDate
                val isToday = d == today
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelect(d) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        weekdayLabels[i],
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isToday && !isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendarDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    var ym by remember { mutableStateOf(YearMonth.from(initial)) }
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ym = ym.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    "${ym.year}年${ym.monthValue}月",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { ym = ym.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                }
            }
        },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdayLabels.forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                val first = ym.atDay(1)
                val leading = first.dayOfWeek.value % 7
                val total = ym.lengthOfMonth()
                val cellCount = ((leading + total + 6) / 7) * 7

                for (r in 0 until cellCount / 7) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c
                            val dayNum = idx - leading + 1
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (dayNum in 1..total) {
                                    val d = ym.atDay(dayNum)
                                    val isSelected = d == initial
                                    val isToday = d == today
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable { onSelect(d) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            dayNum.toString(),
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("选中日期更新下方记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = { ym = YearMonth.now() }) { Text("回今天") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---------- Sync page ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncPage(viewModel: GymViewModel, onBack: () -> Unit) {
    val state = viewModel.uiState
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(state.syncUrl) { copied = false }

    // import flow state
    var pendingImport by remember { mutableStateOf<List<SessionRecord>?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf<ImportMode>(ImportMode.ALL) }
    var importDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDupDialog by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
                } ?: ""
            } catch (e: Exception) { "" }
            val parsed = parseImportSessions(json)
            if (parsed.isNotEmpty()) {
                pendingImport = parsed
                importMode = ImportMode.ALL
                importDate = LocalDate.now().toString()
                showImportDialog = true
            }
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("局域网同步", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("局域网同步", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = state.syncEnabled, onCheckedChange = { viewModel.setSync(it) })
                    }
                    Text(
                        if (state.syncEnabled) "开启中" else "已关闭",
                        fontSize = 12.sp,
                        color = if (state.syncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val url = state.syncUrl
            if (state.syncEnabled && url != null) {
                Text("电脑浏览器访问(同一 WiFi/局域网):", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        url,
                        modifier = Modifier.padding(14.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(url))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (copied) "已复制链接" else "复制链接")
                }
            } else {
                Text(
                    "开启后,同一局域网内的电脑可在浏览器查看并导出训练数据",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "需与手机处于同一 WiFi/局域网",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { filePicker.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("从文件导入数据")
            }
        }
    }

    if (showImportDialog) {
        ImportDialog(
            sessions = pendingImport ?: emptyList(),
            mode = importMode,
            onModeChange = { importMode = it },
            date = importDate,
            onDateChange = { importDate = it },
            onDismiss = { showImportDialog = false; pendingImport = null },
            onImport = {
                val dup = viewModel.importPreview(pendingImport ?: emptyList(), importMode)
                if (dup > 0) { showImportDialog = false; showDupDialog = true }
                else { viewModel.importSessions(pendingImport ?: emptyList(), importMode, false); showImportDialog = false; pendingImport = null }
            },
        )
    }

    if (showDupDialog) {
        AlertDialog(
            onDismissRequest = { showDupDialog = false; pendingImport = null },
            title = { Text("检测到重复") },
            text = { Text("导入内容中有日期已存在数据,如何处理?") },
            confirmButton = {
                TextButton(onClick = { viewModel.importSessions(pendingImport ?: emptyList(), importMode, true); showDupDialog = false; pendingImport = null }) { Text("合并(覆盖)") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.importSessions(pendingImport ?: emptyList(), importMode, false); showDupDialog = false; pendingImport = null }) { Text("跳过重复") }
            },
        )
    }
}

@Composable
private fun ImportDialog(
    sessions: List<SessionRecord>,
    mode: ImportMode,
    onModeChange: (ImportMode) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    val dates = sessions.map { it.date }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入数据") },
        text = {
            Column {
                Text("文件含 ${dates.size} 个日期 / ${sessions.size} 条记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onModeChange(ImportMode.ALL) }, modifier = Modifier.weight(1f)) {
                        Text("全部导入", fontWeight = if (mode is ImportMode.ALL) FontWeight.Bold else FontWeight.Normal)
                    }
                    OutlinedButton(onClick = { onModeChange(ImportMode.DATE(date)) }, modifier = Modifier.weight(1f)) {
                        Text("按日期导入", fontWeight = if (mode is ImportMode.DATE) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (mode is ImportMode.DATE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = date,
                        onValueChange = onDateChange,
                        label = { Text("导入日期 (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onModeChange(if (mode is ImportMode.ALL) ImportMode.ALL else ImportMode.DATE(date))
                onImport()
            }) { Text("开始导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** Parse an exported JSON (WorkoutData or SyncPayload) into sessions. */
private fun parseImportSessions(json: String): List<SessionRecord> {
    if (json.isBlank()) return emptyList()
    return try {
        AppJson.json.decodeFromString(WorkoutData.serializer(), json).sessions
    } catch (e: Exception) {
        try {
            AppJson.json.decodeFromString(SyncPayload.serializer(), json).sessions
        } catch (e2: Exception) {
            emptyList()
        }
    }
}

// ---------- Action item card / Add flow ----------

@Composable
private fun ActionItemCard(exercise: ExerciseRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    exerciseLine(exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exercise.note.takeIf { it.isNotBlank() }?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
        }
    }
}

/** "60kg · 10 × 3组" (or "60kg · 10个" when 组数<=1). */
private fun exerciseLine(ex: ExerciseRecord): String {
    val data = buildString {
        ex.data?.let { append(fmtNum(it)) }
        append(ex.unit)
    }
    val parts = mutableListOf<String>()
    if (data.isNotBlank()) parts += data
    val count = ex.count
    val sets = ex.sets
    if (count != null) {
        parts += if (sets == null || sets <= 1) "${count}个" else "${count} × ${sets}组"
    }
    return parts.joinToString(" · ")
}

private fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

// ---------- Add flow (one action per record) ----------

private class ExerciseDraft(initial: ExerciseRecord? = null) {
    var name by mutableStateOf(initial?.name ?: "")
    var data by mutableStateOf(initial?.data?.let { fmtNum(it) } ?: "")
    var unit by mutableStateOf(initial?.unit ?: "")
    var count by mutableStateOf(initial?.count?.toString() ?: "")
    var sets by mutableStateOf(initial?.sets?.toString() ?: "")
    var muscles by mutableStateOf(initial?.muscles ?: emptyList())
}

@Composable
private fun AddSessionDialog(
    defaultDate: LocalDate,
    initial: ExerciseRecord?,
    initialNote: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ExerciseRecord) -> Unit,
) {
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var showMuscleDemo by remember { mutableStateOf(false) }
    val exercise = remember(initial?.id) { ExerciseDraft(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑动作" else "记录训练") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ExerciseEditor(draft = exercise, onChooseMuscles = { showMuscleDemo = true })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注(可选)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rec = ExerciseRecord(
                    name = exercise.name.trim(),
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    note = note.trim(),
                    data = exercise.data.trim().toDoubleOrNull(),
                    unit = exercise.unit.trim(),
                    count = exercise.count.trim().toIntOrNull(),
                    sets = exercise.sets.trim().toIntOrNull(),
                    muscles = exercise.muscles,
                )
                onConfirm(defaultDate.toString(), note, rec)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showMuscleDemo) {
        MusclePickerDemo(
            initialMuscles = exercise.muscles,
            onSelectionChanged = { exercise.muscles = it },
            onDismiss = { showMuscleDemo = false },
        )
    }
}

@Composable
private fun ExerciseEditor(draft: ExerciseDraft, onChooseMuscles: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft.name = it },
                label = { Text("动作名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.data,
                    onValueChange = { draft.data = it },
                    label = { Text("数据") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = draft.unit,
                    onValueChange = { draft.unit = it },
                    label = { Text("单位") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = { draft.unit = "" }) { Text("无单位") }
                TextButton(onClick = { draft.unit = "kg" }) { Text("kg") }
                TextButton(onClick = { draft.unit = "lbs" }) { Text("lbs") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.count,
                    onValueChange = { draft.count = it },
                    label = { Text("个数(每组)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = draft.sets,
                    onValueChange = { draft.sets = it },
                    label = { Text("组数") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (draft.muscles.isNotEmpty()) {
                Text(
                    "已选择肌肉：${draft.muscles.joinToString("、") { it.name }}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            TextButton(onClick = onChooseMuscles, modifier = Modifier.fillMaxWidth()) { Text("选择锻炼肌群") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusclePickerDemo(
    initialMuscles: List<MuscleSelection>,
    onSelectionChanged: (List<MuscleSelection>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val initialJson = remember(initialMuscles) {
        AppJson.json.encodeToString(ListSerializer(MuscleSelection.serializer()), initialMuscles)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("选择锻炼肌群") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = { TextButton(onClick = onDismiss) { Text("完成") } },
                )
            },
        ) { padding ->
            AndroidView(
                factory = {
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                        }
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onSelectionChanged(json: String) {
                                mainHandler.post {
                                    runCatching {
                                        AppJson.json.decodeFromString<List<MuscleSelection>>(json)
                                    }.onSuccess(onSelectionChanged)
                                }
                            }
                        }, "AndroidMusclePicker")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                view.evaluateJavascript("setSelectedMuscles($initialJson)", null)
                            }
                        }
                        loadUrl("file:///android_asset/muscle-picker/index.html")
                    }
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }
}

// ---------- History / charts ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    data: WorkoutData,
    initial: String?,
    onSetFatigueSuppressed: (String, Boolean) -> Unit,
    onEditMuscles: (String, List<MuscleSelection>) -> Unit,
    onBack: () -> Unit,
    onPickFill: (name: String, record: ActionRecord) -> Unit,
) {
    val actions = remember(data) { buildActions(data.sessions) }
    var selectedName by remember(initial) { mutableStateOf(initial) }
    var searchQuery by remember { mutableStateOf("") }
    var editingMusclesFor by remember { mutableStateOf<String?>(null) }
    var pendingMuscles by remember { mutableStateOf<List<MuscleSelection>>(emptyList()) }
    val filteredActions = actions.filter { actionMatchesQuery(it.name, searchQuery) }
    val selected = actions.firstOrNull { it.name == selectedName }
    val editingAction = actions.firstOrNull { it.name == editingMusclesFor }
    val today = LocalDate.now()

    BackHandler { if (selected != null) selectedName = null else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.name ?: "历史动作", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selectedName = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = selected?.name,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
            },
            label = "detail",
        ) { name ->
            val act = name?.let { n -> actions.firstOrNull { it.name == n } }
            if (act == null) {
                if (actions.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("暂无已添加的动作", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("搜索动作") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        if (filteredActions.isEmpty()) {
                            item {
                                Text(
                                    "没有匹配的动作",
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            items(filteredActions, key = { it.name }) { a ->
                                ActionRow(
                                    a = a,
                                    fatigueInfo = fatigueInfoOf(a, actions, today, data.suppressedFatigue),
                                    onClick = { selectedName = a.name },
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "共添加 ${act.addedCount} 次 · 点某条记录可填入添加页",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (act.muscles.isEmpty()) "锻炼肌群：未选择"
                            else "锻炼肌群：${act.muscles.joinToString("、") { it.name }}",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                        )
                        TextButton(onClick = {
                            pendingMuscles = act.muscles
                            editingMusclesFor = act.name
                        }) { Text("编辑锻炼肌群") }
                    }

                    // fatigue warning toggle (can be turned off per action)
                    val suppressed = data.suppressedFatigue.contains(act.name)
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("疲劳预警", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (suppressed) "已关闭" else "三天内做过将在列表中显示黄色警示",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Switch(
                                checked = !suppressed,
                                onCheckedChange = { onSetFatigueSuppressed(act.name, !it) },
                            )
                        }
                    }
                    if (!suppressed) {
                        fatigueInfoOf(act, actions, today, emptySet())?.let { info ->
                            Text(
                                info,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                color = Color(0xFFB26A00),
                            )
                        }
                    }

                    act.records.forEach { r ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { onPickFill(act.name, r) },
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    r.date,
                                    modifier = Modifier.width(84.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 12.sp,
                                )
                                Text(recordLine(r), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // Charts split by unit (one chart per unit).
                    buildSeriesByData(act).forEach { s ->
                        TrendChart("数据趋势(${s.label})", listOf(s), yAxisLabel = s.label)
                    }
                    buildSeriesByTotalCount(act).forEach { s ->
                        TrendChart("总个数趋势(${s.label})", listOf(s), yAxisLabel = "个")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    editingAction?.let { action ->
        MusclePickerDemo(
            initialMuscles = action.muscles,
            onSelectionChanged = { pendingMuscles = it },
            onDismiss = {
                onEditMuscles(action.name, pendingMuscles)
                editingMusclesFor = null
            },
        )
    }
}

private val hanLatinTransliterator = Transliterator.getInstance("Han-Latin")

private fun actionMatchesQuery(name: String, rawQuery: String): Boolean {
    val query = normalizeSearchText(rawQuery)
    if (query.isEmpty()) return true
    val pinyin = normalizeSearchText(hanLatinTransliterator.transliterate(name))
    return normalizeSearchText(name).contains(query) ||
        pinyin.contains(query) ||
        fuzzyMatches(name, rawQuery.trim()) ||
        fuzzyMatches(pinyin, query) ||
        fuzzyMatches(pinyinInitials(name), query)
}

private fun normalizeSearchText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).filter { it.isLetterOrDigit() }.lowercase()

private fun fuzzyMatches(name: String, query: String): Boolean {
    var index = 0
    return query.all { char -> name.indexOf(char, index, ignoreCase = true).also { index = it + 1 } >= 0 }
}

private fun pinyinInitials(name: String): String =
    hanLatinTransliterator.transliterate(name).split(Regex("\\s+")).joinToString("") { it.firstOrNull()?.toString().orEmpty() }

/** Formats recent matching muscle groups and the actions that trained them. */
private fun fatigueInfoOf(
    a: ActionSummary,
    allActions: List<ActionSummary>,
    today: LocalDate,
    suppressed: Set<String>,
): String? {
    if (a.name in suppressed) return null
    return fatigueWarningsFor(a, allActions, today)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("\n") { warning ->
            "${warning.muscle.name}：${warning.actions.joinToString("、") { "${it.name}（${it.whenText}）" }}"
        }
}

@Composable
private fun ActionRow(a: ActionSummary, fatigueInfo: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium)
                    Text("${a.addedCount} 次", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            if (fatigueInfo != null) {
                Text("⚠ 疲劳预警\n$fatigueInfo", color = Color(0xFFB26A00), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun recordLine(r: ActionRecord): String {
    val data = buildString {
        r.data?.let { append(fmtNum(it)) }
        append(r.unit)
    }
    val parts = mutableListOf<String>()
    if (data.isNotBlank()) parts += data
    val c = r.count
    val s = r.sets
    if (c != null) parts += if (s == null || s <= 1) "${c}个" else "${c} × ${s}组"
    return parts.joinToString(" · ")
}

private val chartColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935),
    Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00ACC1),
)

@Composable
private fun TrendChart(title: String, series: List<ChartSeries>, yAxisLabel: String) {
    val nonEmpty = series.filter { it.points.isNotEmpty() }
    val textMeasurer = rememberTextMeasurer()
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (nonEmpty.isEmpty()) {
                Text("暂无数据", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            } else {
                    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        val labelH = 20.dp.toPx()
                        val chartH = size.height - labelH
                        val axisStyle = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA2B1))
                        val gridColor = Color(0xFFE9EDF4)
                        val allDates = nonEmpty.flatMap { it.points.map { p -> p.first } }.distinct().sorted()
                        val dateIndex = allDates.mapIndexed { i, d -> d to i }.toMap()
                        val allY = nonEmpty.flatMap { it.points.map { p -> p.second } }
                        val yMin = allY.minOrNull()!!
                        val yMax = allY.maxOrNull()!!
                        val ySpan = (yMax - yMin).takeIf { it > 0 } ?: 1.0

                        // nice y-axis ticks
                        val rawStep = ySpan / 4.0
                        val mag = 10.0.pow(floor(log10(rawStep)))
                        val norm = rawStep / mag
                        val step = when {
                            norm <= 1 -> mag
                            norm <= 2 -> mag * 2
                            norm <= 5 -> mag * 5
                            else -> mag * 10
                        }
                        val start = floor(yMin / step) * step
                        val ticks = mutableListOf<Double>()
                        var tv = start
                        while (tv <= yMax + step / 1e6 && ticks.size <= 6) {
                            ticks.add(tv)
                            tv += step
                        }
                        val tickMeasured = ticks.map { textMeasurer.measure(fmtTick(it), axisStyle) }
                        val maxTickW = (tickMeasured.maxOfOrNull { it.size.width } ?: 0).toFloat()

                        val unitBand = 24.dp.toPx()
                        val plotLeft = unitBand + maxTickW + 6f
                        val innerW = size.width - plotLeft

                        fun xOf(date: String): Float {
                            val idx = dateIndex[date] ?: 0
                            return if (allDates.size <= 1) plotLeft + innerW / 2f
                            else plotLeft + innerW * idx / (allDates.size - 1).toFloat()
                        }
                        fun yOf(v: Double): Float = (chartH * (1.0 - (v - yMin) / ySpan)).toFloat()

                        // unit label (horizontal, centered over the y-axis number band, at top)
                        val unitStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF9AA2B1))
                        val um = textMeasurer.measure(yAxisLabel, unitStyle)
                        drawText(um, topLeft = Offset((unitBand + plotLeft) / 2f - um.size.width / 2f, 2f))

                        // horizontal grid lines + y-axis numbers
                        ticks.forEachIndexed { i, v ->
                            val y = yOf(v)
                            drawLine(color = gridColor, start = Offset(plotLeft, y), end = Offset(size.width, y), strokeWidth = 1f)
                            val m = tickMeasured[i]
                            val nx = (plotLeft - m.size.width - 4f).coerceAtLeast(unitBand)
                            drawText(m, topLeft = Offset(nx, y - m.size.height / 2f))
                        }

                        nonEmpty.forEachIndexed { si, s ->
                            val color = chartColors[si % chartColors.size]
                            val path = Path()
                            s.points.forEachIndexed { i, p ->
                                val x = xOf(p.first)
                                val y = yOf(p.second)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path = path, color = color, style = Stroke(width = 3f))
                            s.points.forEach { p ->
                                drawCircle(color = color, radius = 4f, center = Offset(xOf(p.first), yOf(p.second)))
                            }
                        }

                        // baseline + date labels
                        drawLine(color = Color(0xFFE1E4EC), start = Offset(plotLeft, chartH), end = Offset(size.width, chartH), strokeWidth = 1f)
                        allDates.forEach { d ->
                            val label = shortDate(d)
                            val m = textMeasurer.measure(label, axisStyle)
                            val cx = xOf(d)
                            val w = m.size.width.toFloat()
                            val tx = (cx - w / 2f).coerceIn(plotLeft, size.width - w)
                            drawText(m, topLeft = Offset(tx, chartH + 4f))
                        }
                    }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    nonEmpty.forEachIndexed { si, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(chartColors[si % chartColors.size], CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(s.label, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** "2026-09-01" -> "9/1" */
private fun shortDate(d: String): String {
    val p = d.split("-")
    return if (p.size == 3) "${p[1].toIntOrNull() ?: p[1]}/${p[2].toIntOrNull() ?: p[2]}" else d
}

/** Axis tick label: up to 1 decimal, trailing ".0" trimmed. */
private fun fmtTick(v: Double): String {
    val r = (v * 10).roundToInt() / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}
