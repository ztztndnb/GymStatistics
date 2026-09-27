package com.gymstatistics.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
import com.gymstatistics.data.PlannedExercise
import com.gymstatistics.data.TrainingPlan
import com.gymstatistics.data.TrainingPlanDay
import com.gymstatistics.data.WorkoutData
import com.gymstatistics.data.buildActions
import com.gymstatistics.data.buildSeriesByData
import com.gymstatistics.data.buildSeriesByTotalCount
import com.gymstatistics.data.fatigueWarningsFor
import com.gymstatistics.data.completedPlanExerciseIds
import com.gymstatistics.data.displayOrderForManualMode
import com.gymstatistics.data.latestExerciseForName
import com.gymstatistics.data.planDayIndex
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

private enum class Screen { MAIN, PLAN, HISTORY, SETTINGS, SYNC, FOOD }

private enum class WorkoutMode { MANUAL, PLAN }

private const val MIN_SWIPE_DISTANCE = 100f
private const val MIN_FLING_VELOCITY = 1000f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymApp(viewModel: GymViewModel) {
    val state = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var historySelected by remember { mutableStateOf<String?>(null) }
    var weekNavigation by remember { mutableStateOf(WeekNavigation(LocalDate.now())) }
    val selectedDate = weekNavigation.selectedDate
    val currentSelectedDate by rememberUpdatedState(selectedDate)
    var workoutMode by remember { mutableStateOf(WorkoutMode.MANUAL) }
    var showCalendar by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editingExerciseId by remember { mutableStateOf<String?>(null) }
    var prefillExercise by remember { mutableStateOf<ExerciseRecord?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingPlanAssociation by remember { mutableStateOf<Pair<String, String>?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragDirection by remember { mutableStateOf(0) }
    var lastDragDirection by remember { mutableStateOf(0) }
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
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val drawerWidth = minOf(LocalConfiguration.current.screenWidthDp.dp * 0.7f, 320.dp)
                BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                            Text("GymStatistics", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
                            NavigationDrawerItem(
                                label = { Text("训练计划") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        screen = Screen.PLAN
                                    }
                                },
                                icon = { Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("设置") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        screen = Screen.SETTINGS
                                    }
                                },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    },
                ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "GymStatistics",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { scope.launch { drawerState.open() } }
                                        .semantics { contentDescription = "打开侧边栏" },
                                )
                            },
                            actions = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { historySelected = null; screen = Screen.HISTORY }) { Text("历史") }
                                    IconButton(
                                        onClick = { screen = Screen.FOOD },
                                        modifier = Modifier.semantics { contentDescription = "AI食物分析相机" },
                                    ) {
                                        Icon(
                                            Icons.Rounded.PhotoCamera,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        DatePickerHeader(
                            selectedDate = selectedDate,
                            visibleWeekStart = weekNavigation.visibleWeekStart,
                            onOpenCalendar = { showCalendar = true },
                        )
                        WeekDayBar(
                            selectedDate = selectedDate,
                            visibleWeekStart = weekNavigation.visibleWeekStart,
                            revision = weekNavigation.revision,
                            onSelect = { weekNavigation = weekNavigation.select(it) },
                            onPage = { revision, direction ->
                                weekNavigation = weekNavigation.pageIfRevision(revision, direction)
                            },
                        )
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    val velocityTracker = VelocityTracker()
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            dragOffset = 0f
                                            dragDirection = 0
                                            lastDragDirection = 0
                                            velocityTracker.resetTracking()
                                        },
                                        onDragEnd = {
                                            val direction = dragDirection
                                            val signedDistance = -dragOffset * direction
                                            val signedVelocity = -velocityTracker.calculateVelocity().x * direction
                                            val isMovingBack = direction == 0 ||
                                                lastDragDirection == direction ||
                                                signedVelocity < -MIN_FLING_VELOCITY
                                            val commit = !isMovingBack &&
                                                (signedDistance >= MIN_SWIPE_DISTANCE || signedVelocity >= MIN_FLING_VELOCITY)
                                            val target = if (commit) -direction * size.width.toFloat() else 0f
                                            velocityTracker.resetTracking()
                                            scope.launch {
                                                isSettling = true
                                                settleOffset.snapTo(dragOffset)
                                                settleOffset.animateTo(target, tween(220))
                                                if (commit) weekNavigation = weekNavigation.select(currentSelectedDate.plusDays(direction.toLong()))
                                                dragOffset = 0f
                                                dragDirection = 0
                                                settleOffset.snapTo(0f)
                                                isSettling = false
                                            }
                                        },
                                        onDragCancel = {
                                            velocityTracker.resetTracking()
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
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        if (dragDirection == 0 && amount != 0f) {
                                            dragDirection = if (amount < 0f) 1 else -1
                                        }
                                        if (amount != 0f) lastDragDirection = if (amount < 0f) -1 else 1
                                        dragOffset = (dragOffset + amount).coerceIn(-size.width.toFloat(), size.width.toFloat())
                                    }
                                },
                        ) {
                            val pageWidth = with(LocalDensity.current) { maxWidth.toPx() }
                            val contentOffset = if (isSettling) settleOffset.value else dragOffset
                            Box(Modifier.fillMaxSize()) {
                                if (dragDirection != 0) {
                                    DayModeContent(
                                        mode = workoutMode,
                                        date = selectedDate.plusDays(dragDirection.toLong()),
                                        sessions = state.data.sessions,
                                        plan = state.data.trainingPlan,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { translationX = contentOffset + pageWidth * dragDirection },
                                        onEdit = { ex -> editingExerciseId = ex.id; prefillExercise = ex; showAdd = true },
                                        onDelete = { confirmDeleteId = it },
                                        onReorderManual = { id, direction -> viewModel.reorderExercises(selectedDate.plusDays(dragDirection.toLong()).toString(), id, direction) },
                                        onCompletePlan = { id -> viewModel.completePlannedExercise(selectedDate.plusDays(dragDirection.toLong()).toString(), id) },
                                        onCancelComplete = { id -> viewModel.cancelPlannedExercise(selectedDate.plusDays(dragDirection.toLong()).toString(), id) },
                                    )
                                }
                                DayModeContent(
                                    mode = workoutMode,
                                    date = selectedDate,
                                    sessions = state.data.sessions,
                                    plan = state.data.trainingPlan,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = contentOffset },
                                        onEdit = { ex -> editingExerciseId = ex.id; prefillExercise = ex; showAdd = true },
                                        onDelete = { confirmDeleteId = it },
                                        onReorderManual = { id, direction -> viewModel.reorderExercises(dateKey, id, direction) },
                                        onCompletePlan = { id -> viewModel.completePlannedExercise(dateKey, id) },
                                        onCancelComplete = { id -> viewModel.cancelPlannedExercise(dateKey, id) },
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
                        }
                    }
                }
                }

                if (showCalendar) {
                    MonthCalendarDialog(
                        initial = selectedDate,
                        onDismiss = { showCalendar = false },
                        onSelect = { weekNavigation = weekNavigation.select(it); showCalendar = false },
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
                            else {
                                val planDay = state.data.trainingPlan?.days?.firstOrNull { it.weekday == planDayIndex(selectedDate) }
                                val completed = completedPlanExerciseIds(state.data.sessions.firstOrNull { it.date == date })
                                val matchingPlan = planDay?.exercises?.firstOrNull {
                                    it.name == exercise.name && it.id !in completed && exercise.plannedExerciseId == null
                                }
                                viewModel.addExercise(date, note, exercise)
                                if (matchingPlan != null) pendingPlanAssociation = exercise.id to matchingPlan.id
                            }
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

            Screen.PLAN -> TrainingPlanPage(viewModel = viewModel, onBack = { screen = Screen.MAIN })

            Screen.SETTINGS -> SettingsPage(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                selectedWorkoutMode = workoutMode,
                onWorkoutModeChange = { workoutMode = it },
                onBack = { screen = Screen.MAIN },
                onSync = { screen = Screen.SYNC },
            )

            Screen.SYNC -> SyncPage(viewModel = viewModel, onBack = { screen = Screen.SETTINGS })

            Screen.FOOD -> FoodAnalysisScreen(viewModel = viewModel, onBack = { screen = Screen.MAIN })
        }
    }

    val association = pendingPlanAssociation
    if (association != null) {
        AlertDialog(
            onDismissRequest = { pendingPlanAssociation = null },
            title = { Text("关联训练计划") },
            text = { Text("检测到同名的计划动作，是否将本次手动记录关联到计划？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.associateExerciseWithPlan(selectedDate.toString(), association.first, association.second)
                    pendingPlanAssociation = null
                }) { Text("关联") }
            },
            dismissButton = { TextButton(onClick = { pendingPlanAssociation = null }) { Text("暂不关联") } },
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WorkoutDayContent(
    date: LocalDate,
    sessions: List<SessionRecord>,
    modifier: Modifier = Modifier,
    onEdit: (ExerciseRecord) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (String, Int) -> Unit,
) {
    val session = sessions.firstOrNull { it.date == date.toString() }
    val dayExercises = displayOrderForManualMode(session?.exercises.orEmpty())
    val listState = rememberLazyListState()
    var activeDraggedId by remember(date) { mutableStateOf<String?>(null) }
    val fallbackRowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    if (dayExercises.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("这一天没有训练记录", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
        }
    } else {
        Column(modifier = modifier) {
            session?.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
            val duplicateKeys = dayExercises
                .groupingBy { it.id.ifEmpty { it.name } }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(dayExercises, key = { index, exercise ->
                    val base = exercise.id.ifEmpty { exercise.name }
                    if (base in duplicateKeys) "$base#$index" else base
                }) { _, ex ->
                    ActionItemCard(
                        exercise = ex,
                        modifier = if (ex.id == activeDraggedId) Modifier else Modifier.animateItem(),
                        onEdit = { onEdit(ex) },
                        onDelete = { onDelete(ex.id) },
                        onMove = if (ex.plannedExerciseId == null) {
                            { direction ->
                                val currentIndex = dayExercises.indexOfFirst { it.id == ex.id }
                                val targetIndex = currentIndex + direction
                                val manualCount = dayExercises.count { it.plannedExerciseId == null }
                                if (currentIndex < 0 || targetIndex !in 0 until manualCount) {
                                    0f
                                } else {
                                    val movedDistance = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == targetIndex }?.size?.toFloat()
                                        ?: fallbackRowHeightPx
                                    onReorder(ex.id, direction)
                                    movedDistance
                                }
                            }
                        } else {
                            null
                        },
                        onDraggingChanged = { dragging ->
                            activeDraggedId = if (dragging) ex.id else null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayModeContent(
    mode: WorkoutMode,
    date: LocalDate,
    sessions: List<SessionRecord>,
    plan: TrainingPlan?,
    modifier: Modifier = Modifier,
    onEdit: (ExerciseRecord) -> Unit,
    onDelete: (String) -> Unit,
    onReorderManual: (String, Int) -> Unit,
    onCompletePlan: (String) -> Unit,
    onCancelComplete: (String) -> Unit,
) {
    if (mode == WorkoutMode.MANUAL) {
        WorkoutDayContent(
            date = date,
            sessions = sessions,
            modifier = modifier,
            onEdit = onEdit,
            onDelete = onDelete,
            onReorder = onReorderManual,
        )
    } else {
        PlannedDayContent(
            date = date,
            sessions = sessions,
            plan = plan,
            modifier = modifier,
            onComplete = onCompletePlan,
            onCancelComplete = onCancelComplete,
        )
    }
}

@Composable
private fun PlannedDayContent(
    date: LocalDate,
    sessions: List<SessionRecord>,
    plan: TrainingPlan?,
    modifier: Modifier = Modifier,
    onComplete: (String) -> Unit,
    onCancelComplete: (String) -> Unit,
) {
    val day = plan?.days?.firstOrNull { it.weekday == planDayIndex(date) }
    val completed = completedPlanExerciseIds(sessions.firstOrNull { it.date == date.toString() })
    val pendingExercises = day?.exercises?.filterNot { it.id in completed }.orEmpty()
    val completedExercises = day?.exercises?.filter { it.id in completed }.orEmpty()
    var completedExpanded by remember(date) { mutableStateOf(false) }
    if (day == null || (day.exercises.isEmpty() && !day.restDay)) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("这一天还没有训练计划", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
        }
        return
    }
    Column(modifier = modifier) {
        Text(
            if (day.restDay) "${planWeekdayLabel(day.weekday)} · 休息日" else "${planWeekdayLabel(day.weekday)} · 计划动作",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 13.sp,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(pendingExercises, key = { index, exercise -> "pending-${exercise.id}#$index" }) { _, exercise ->
                PlannedExerciseCard(
                    exercise = exercise,
                    completed = false,
                    onComplete = { onComplete(exercise.id) },
                    onCancelComplete = { onCancelComplete(exercise.id) },
                )
            }
            if (completedExercises.isNotEmpty()) {
                item(key = "completed-plan-group") {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { completedExpanded = !completedExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("已完成动作", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${completedExercises.size} 个动作已收起",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Icon(
                                    if (completedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (completedExpanded) "收起已完成动作" else "展开已完成动作",
                                )
                            }
                            AnimatedVisibility(
                                visible = completedExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column {
                                    completedExercises.forEach { exercise ->
                                        PlannedExerciseCard(
                                            exercise = exercise,
                                            completed = true,
                                            onComplete = { onComplete(exercise.id) },
                                            onCancelComplete = { onCancelComplete(exercise.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedExerciseCard(
    exercise: PlannedExercise,
    completed: Boolean,
    onComplete: () -> Unit,
    onCancelComplete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (completed) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(exercise.name, modifier = Modifier.weight(1f).padding(start = 8.dp), color = MaterialTheme.colorScheme.outline)
                Text("已完成", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                TextButton(onClick = onCancelComplete) { Text("取消完成") }
            }
        } else {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        exerciseLine(exercise),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    exercise.note.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Button(onClick = onComplete) { Text("完成并记录") }
            }
        }
    }
}

private fun exerciseLine(ex: PlannedExercise): String = exerciseLine(
    ExerciseRecord(
        name = ex.name,
        data = ex.data,
        unit = ex.unit,
        count = ex.count,
        sets = ex.sets,
        note = ex.note,
        muscles = ex.muscles,
    )
)

@Composable
private fun WorkoutModeSwitch(
    selected: WorkoutMode,
    onSelected: (WorkoutMode) -> Unit,
    horizontalPadding: Dp = 16.dp,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth().padding(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val segmentWidth = maxWidth / 2
                val selectedX by animateDpAsState(
                    targetValue = if (selected == WorkoutMode.MANUAL) 0.dp else segmentWidth,
                    animationSpec = tween(220),
                    label = "workout-mode-slider",
                )
                Box(
                    modifier = Modifier
                        .offset(x = selectedX)
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    TextButton(onClick = { onSelected(WorkoutMode.MANUAL) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text("手动模式", color = if (selected == WorkoutMode.MANUAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onSelected(WorkoutMode.PLAN) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text("计划模式", color = if (selected == WorkoutMode.PLAN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun planWeekdayLabel(weekday: Int): String =
    listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(weekday) { "星期$weekday" }

// ---------- Date selection (week bar + month calendar) ----------

private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private fun dateHeaderLabel(date: LocalDate): String =
    if (date == LocalDate.now()) "今天"
    else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

@Composable
private fun DatePickerHeader(selectedDate: LocalDate, visibleWeekStart: LocalDate, onOpenCalendar: () -> Unit) {
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
        weekRangeLabel(visibleWeekStart),
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
private fun WeekDayBar(
    selectedDate: LocalDate,
    visibleWeekStart: LocalDate,
    revision: Long,
    onSelect: (LocalDate) -> Unit,
    onPage: (Long, Int) -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragDirection by remember { mutableStateOf(0) }
    var isSettling by remember { mutableStateOf(false) }
    val settleOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnPage by rememberUpdatedState(onPage)
    val currentRevision by rememberUpdatedState(revision)
    val selectDate: (LocalDate) -> Unit = { date ->
        if (isSettling) {
            isSettling = false
            dragOffset = 0f
            dragDirection = 0
        }
        onSelect(date)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clipToBounds()) {
        val pageWidth = with(LocalDensity.current) { maxWidth.toPx() }
        val offset = if (isSettling) settleOffset.value else dragOffset
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(pageWidth) {
                    val velocityTracker = VelocityTracker()
                    var dragStartedRevision = currentRevision
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffset = 0f
                            dragDirection = 0
                            dragStartedRevision = currentRevision
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            val direction = dragDirection
                            val expectedRevision = dragStartedRevision
                            val forwardDistance = -dragOffset * direction
                            val forwardVelocity = -velocityTracker.calculateVelocity().x * direction
                            val commit = direction != 0 &&
                                forwardVelocity > -MIN_FLING_VELOCITY &&
                                (forwardDistance >= pageWidth * 0.25f || forwardVelocity >= MIN_FLING_VELOCITY)
                            val target = if (commit) -direction * pageWidth else 0f
                            velocityTracker.resetTracking()
                            scope.launch {
                                isSettling = true
                                settleOffset.snapTo(dragOffset)
                                settleOffset.animateTo(target, tween(220))
                                if (commit) currentOnPage(expectedRevision, direction)
                                dragOffset = 0f
                                dragDirection = 0
                                settleOffset.snapTo(0f)
                                isSettling = false
                            }
                        },
                        onDragCancel = {
                            velocityTracker.resetTracking()
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
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        dragOffset = (dragOffset + amount).coerceIn(-pageWidth, pageWidth)
                        if (dragOffset != 0f) dragDirection = if (dragOffset < 0f) 1 else -1
                    }
                },
        ) {
            if (dragDirection != 0) {
                WeekDateCells(
                    start = visibleWeekStart.plusWeeks(dragDirection.toLong()),
                    selectedDate = selectedDate,
                    onSelect = selectDate,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = offset + pageWidth * dragDirection },
                )
            }
            WeekDateCells(
                start = visibleWeekStart,
                selectedDate = selectedDate,
                onSelect = selectDate,
                modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = offset },
            )
        }
    }
}

@Composable
private fun WeekDateCells(
    start: LocalDate,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val selectedIndex = ChronoUnit.DAYS.between(start, selectedDate).toInt()
    BoxWithConstraints(modifier = modifier) {
        val cellWidth = maxWidth / 7
        if (selectedIndex in 0..6) {
            val highlightX = (cellWidth - 34.dp) / 2 + cellWidth * selectedIndex
            val animatedHighlightX by animateDpAsState(
                targetValue = highlightX,
                animationSpec = tween(durationMillis = 260),
                label = "selected date highlight",
            )
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
                val leading = first.dayOfWeek.value - 1
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

// ---------- Training plan ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingPlanPage(viewModel: GymViewModel, onBack: () -> Unit) {
    val state = viewModel.uiState
    val plan = state.data.trainingPlan ?: TrainingPlan()
    var editing by remember { mutableStateOf<Pair<Int, PlannedExercise?>?>(null) }
    var historyPickerDay by remember { mutableStateOf<Int?>(null) }

    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("训练计划", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Text(
                    "每周计划会重复使用；在主界面切换到计划模式后，可逐项完成并记录。",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
            }
            items((1..7).toList(), key = { it }) { weekday ->
                val day = plan.days.firstOrNull { it.weekday == weekday } ?: TrainingPlanDay(weekday)
                PlanDayEditor(
                    day = day,
                    onRestDayChange = { viewModel.setPlanRestDay(weekday, it) },
                    onAdd = { editing = weekday to null },
                    onAddFromHistory = { historyPickerDay = weekday },
                    onEdit = { editing = weekday to it },
                    onDelete = { viewModel.deletePlannedExercise(weekday, it) },
                    onMove = { id, direction -> viewModel.reorderPlannedExercises(weekday, id, direction) },
                    onCopyNext = { viewModel.copyTrainingPlanDay(weekday, if (weekday == 7) 1 else weekday + 1) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    editing?.let { (weekday, initial) ->
        PlannedExerciseEditorDialog(
            initial = initial,
            onDismiss = { editing = null },
            onConfirm = { exercise ->
                if (initial == null) viewModel.addPlannedExercise(weekday, exercise)
                else viewModel.updatePlannedExercise(weekday, exercise)
                editing = null
            },
        )
    }

    historyPickerDay?.let { weekday ->
        HistoryActionPickerPage(
            sessions = state.data.sessions,
            onDismiss = { historyPickerDay = null },
            onPick = { exercise ->
                viewModel.addPlannedExercise(weekday, exercise)
                historyPickerDay = null
            },
        )
    }
}

@Composable
private fun PlanDayEditor(
    day: TrainingPlanDay,
    onRestDayChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onAddFromHistory: () -> Unit,
    onEdit: (PlannedExercise) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onCopyNext: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(planWeekdayLabel(day.weekday), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("休息日", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Switch(checked = day.restDay, onCheckedChange = onRestDayChange)
            }
            if (day.restDay) {
                Text("当天不安排计划动作", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
            day.exercises.forEachIndexed { index, exercise ->
                key(exercise.id) {
                    PlanDayExerciseRow(
                        exercise = exercise,
                        index = index,
                        lastIndex = day.exercises.lastIndex,
                        onMove = onMove,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("添加动作") }
                OutlinedButton(onClick = onAddFromHistory, modifier = Modifier.weight(1f)) { Text("从历史动作选择") }
            }
            TextButton(onClick = onCopyNext, modifier = Modifier.fillMaxWidth()) {
                Text("复制到${planWeekdayLabel(if (day.weekday == 7) 1 else day.weekday + 1)}")
            }
        }
    }
}

@Composable
private fun PlanDayExerciseRow(
    exercise: PlannedExercise,
    index: Int,
    lastIndex: Int,
    onMove: (String, Int) -> Unit,
    onEdit: (PlannedExercise) -> Unit,
    onDelete: (String) -> Unit,
) {
    var isDragging by remember(exercise.id) { mutableStateOf(false) }
    var dragOffset by remember(exercise.id) { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        animationSpec = tween(140),
        label = "plan-drag-scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 10f else 0f,
        animationSpec = tween(140),
        label = "plan-drag-elevation",
    )
    val dragBackground = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f) else Color.Transparent
    val rowHeightPx = with(LocalDensity.current) { 48.dp.toPx() }
    val placementOffset = remember(exercise.id) { Animatable(0f) }
    var previousIndex by remember(exercise.id) { mutableStateOf(index) }
    LaunchedEffect(index) {
        val previousPosition = previousIndex
        previousIndex = index
        if (previousPosition != index && !isDragging) {
            placementOffset.snapTo((previousPosition - index) * rowHeightPx)
            placementOffset.animateTo(0f, animationSpec = tween(180))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (isDragging) dragOffset else placementOffset.value
                shadowElevation = elevation
                shape = RoundedCornerShape(16.dp)
                clip = isDragging
            }
            .background(dragBackground),
    ) {
        DragHandle(
            onMove = { direction ->
                val targetIndex = index + direction
                if (targetIndex !in 0..lastIndex) {
                    0f
                } else {
                    onMove(exercise.id, direction)
                    rowHeightPx
                }
            },
            onDragOffsetChanged = { dragOffset = it },
            onDraggingChanged = { isDragging = it },
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 5.dp)) {
            Text(exercise.name, fontWeight = FontWeight.SemiBold)
            Text(exerciseLine(exercise), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = { onEdit(exercise) }) { Icon(Icons.Filled.Edit, contentDescription = "编辑计划动作") }
        IconButton(onClick = { onDelete(exercise.id) }) { Icon(Icons.Filled.Delete, contentDescription = "删除计划动作") }
    }
}

private class PlannedExerciseDraft(initial: PlannedExercise? = null) {
    var name by mutableStateOf(initial?.name ?: "")
    var data by mutableStateOf(initial?.data?.let { fmtNum(it) } ?: "")
    var unit by mutableStateOf(initial?.unit ?: "磅")
    var count by mutableStateOf(initial?.count?.toString() ?: "")
    var sets by mutableStateOf(initial?.sets?.toString() ?: "")
    var note by mutableStateOf(initial?.note ?: "")
    var muscles by mutableStateOf(initial?.muscles ?: emptyList())
    val id: String = initial?.id ?: UUID.randomUUID().toString()
}

@Composable
private fun PlannedExerciseEditorDialog(
    initial: PlannedExercise?,
    onDismiss: () -> Unit,
    onConfirm: (PlannedExercise) -> Unit,
) {
    var showMuscles by remember { mutableStateOf(false) }
    val draft = remember(initial?.id) { PlannedExerciseDraft(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加计划动作" else "编辑计划动作") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(draft.name, { draft.name = it }, label = { Text("动作名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(draft.data, { draft.data = it }, label = { Text("数据") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(draft.unit, { draft.unit = it }, label = { Text("单位") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { draft.unit = "" }) { Text("无单位") }
                    TextButton(onClick = { draft.unit = "磅" }) { Text("磅") }
                    TextButton(onClick = { draft.unit = "kg" }) { Text("kg") }
                    TextButton(onClick = { draft.unit = "lbs" }) { Text("lbs") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(draft.count, { draft.count = it }, label = { Text("个数(每组)") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(draft.sets, { draft.sets = it }, label = { Text("组数") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(draft.note, { draft.note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { showMuscles = true }, modifier = Modifier.fillMaxWidth()) { Text("选择锻炼肌群") }
                if (draft.muscles.isNotEmpty()) Text("已选择肌肉：${draft.muscles.joinToString("、") { it.name }}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(PlannedExercise(draft.id, draft.name.trim(), draft.data.toDoubleOrNull(), draft.unit.trim(), draft.count.toIntOrNull(), draft.sets.toIntOrNull(), draft.note.trim(), draft.muscles))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (showMuscles) {
        MusclePickerDemo(draft.muscles, { draft.muscles = it }, { showMuscles = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryActionPickerPage(
    sessions: List<SessionRecord>,
    onDismiss: () -> Unit,
    onPick: (PlannedExercise) -> Unit,
) {
    val actions = buildActions(sessions)
    var searchQuery by remember { mutableStateOf("") }
    val filteredActions = actions.filter { actionMatchesQuery(it.name, searchQuery, it.muscles) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("选择历史动作", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { padding ->
            if (actions.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无已添加的动作", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("搜索动作或肌群") },
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
                        items(filteredActions, key = { it.name }) { action ->
                            ActionRow(
                                a = action,
                                fatigueInfo = null,
                                onClick = {
                                    val latest = latestExerciseForName(sessions, action.name)
                                    onPick(
                                        PlannedExercise(
                                            name = action.name,
                                            data = latest?.data,
                                            unit = latest?.unit ?: "",
                                            count = latest?.count,
                                            sets = latest?.sets,
                                            muscles = latest?.muscles ?: action.muscles,
                                        )
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Settings and sync ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(
    viewModel: GymViewModel,
    snackbarHostState: SnackbarHostState,
    selectedWorkoutMode: WorkoutMode,
    onWorkoutModeChange: (WorkoutMode) -> Unit,
    onBack: () -> Unit,
    onSync: () -> Unit,
) {
    val keyConfigured = viewModel.uiState.deepSeekKeyConfigured
    var showKeyDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("训练模式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            WorkoutModeSwitch(
                selected = selectedWorkoutMode,
                onSelected = onWorkoutModeChange,
                horizontalPadding = 0.dp,
            )
            Spacer(Modifier.height(24.dp))
            Text("AI 食物分析", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DeepSeek API Key", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (keyConfigured) "已配置；可在这里或相机内修改" else "尚未配置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { showKeyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (keyConfigured) "修改 API Key" else "配置 API Key")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("数据与同步", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("局域网同步") }
        }
    }

    if (showKeyDialog) {
        DeepSeekApiKeyDialog(
            configured = keyConfigured,
            onSave = viewModel::setDeepSeekApiKey,
            onDelete = viewModel::clearDeepSeekApiKey,
            onDismiss = { showKeyDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncPage(viewModel: GymViewModel, onBack: () -> Unit) {
    val state = viewModel.uiState
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(state.syncUrl) { copied = false }

    // import flow state
    var pendingImport by remember { mutableStateOf<ImportBundle?>(null) }
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
            val parsed = parseImportData(json)
            if (parsed.sessions.isNotEmpty() || parsed.trainingPlan != null) {
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
            sessions = pendingImport?.sessions ?: emptyList(),
            mode = importMode,
            onModeChange = { importMode = it },
            date = importDate,
            onDateChange = { importDate = it },
            onDismiss = { showImportDialog = false; pendingImport = null },
            onImport = {
                val dup = viewModel.importPreview(pendingImport?.sessions ?: emptyList(), importMode)
                if (dup > 0) { showImportDialog = false; showDupDialog = true }
                else {
                    val bundle = pendingImport
                    viewModel.importSessions(bundle?.sessions ?: emptyList(), importMode, false, if (importMode is ImportMode.ALL) bundle?.trainingPlan else null)
                    showImportDialog = false
                    pendingImport = null
                }
            },
        )
    }

    if (showDupDialog) {
        AlertDialog(
            onDismissRequest = { showDupDialog = false; pendingImport = null },
            title = { Text("检测到重复") },
            text = { Text("导入内容中有日期已存在数据,如何处理?") },
            confirmButton = {
                TextButton(onClick = {
                    val bundle = pendingImport
                    viewModel.importSessions(bundle?.sessions ?: emptyList(), importMode, true, if (importMode is ImportMode.ALL) bundle?.trainingPlan else null)
                    showDupDialog = false
                    pendingImport = null
                }) { Text("合并(覆盖)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val bundle = pendingImport
                    viewModel.importSessions(bundle?.sessions ?: emptyList(), importMode, false, if (importMode is ImportMode.ALL) bundle?.trainingPlan else null)
                    showDupDialog = false
                    pendingImport = null
                }) { Text("跳过重复") }
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

private data class ImportBundle(
    val sessions: List<SessionRecord> = emptyList(),
    val trainingPlan: TrainingPlan? = null,
)

/** Parse an exported JSON (WorkoutData or SyncPayload) into sessions and an optional plan. */
private fun parseImportData(json: String): ImportBundle {
    if (json.isBlank()) return ImportBundle()
    return try {
        val data = AppJson.json.decodeFromString(WorkoutData.serializer(), json)
        ImportBundle(data.sessions, data.trainingPlan)
    } catch (e: Exception) {
        try {
            val payload = AppJson.json.decodeFromString(SyncPayload.serializer(), json)
            ImportBundle(payload.sessions, payload.trainingPlan)
        } catch (e2: Exception) {
            ImportBundle()
        }
    }
}

// ---------- Action item card / Add flow ----------

@Composable
private fun Modifier.reorderHandle(
    onMove: (Int) -> Float,
    onDragOffsetChanged: (Float) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { 32.dp.toPx() }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragOffsetChanged by rememberUpdatedState(onDragOffsetChanged)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)
    return pointerInput(Unit) {
        var accumulated = 0f
        var dragOffset = 0f
        detectDragGesturesAfterLongPress(
            onDragStart = {
                accumulated = 0f
                dragOffset = 0f
                currentOnDragOffsetChanged(0f)
                currentOnDraggingChanged(true)
            },
            onDragEnd = {
                currentOnDraggingChanged(false)
                accumulated = 0f
            },
            onDragCancel = {
                currentOnDraggingChanged(false)
                accumulated = 0f
            },
            onDrag = { change, amount ->
                change.consume()
                dragOffset += amount.y
                accumulated += amount.y
                currentOnDragOffsetChanged(dragOffset)
                if (accumulated >= thresholdPx) {
                    val movedDistance = currentOnMove(1)
                    dragOffset -= movedDistance
                    currentOnDragOffsetChanged(dragOffset)
                    accumulated -= thresholdPx
                } else if (accumulated <= -thresholdPx) {
                    val movedDistance = currentOnMove(-1)
                    dragOffset += movedDistance
                    currentOnDragOffsetChanged(dragOffset)
                    accumulated += thresholdPx
                }
            },
        )
    }
}

@Composable
private fun DragHandle(
    onMove: (Int) -> Float,
    onDragOffsetChanged: (Float) -> Unit = {},
    onDraggingChanged: (Boolean) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .reorderHandle(onMove, onDragOffsetChanged, onDraggingChanged)
            .semantics { contentDescription = "拖动排序" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ActionItemCard(
    exercise: ExerciseRecord,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: ((Int) -> Float)? = null,
    onDraggingChanged: (Boolean) -> Unit = {},
) {
    var isDragging by remember(exercise.id) { mutableStateOf(false) }
    var dragOffset by remember(exercise.id) { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        animationSpec = tween(140),
        label = "action-drag-scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 12f else 0f,
        animationSpec = tween(140),
        label = "action-drag-elevation",
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (isDragging) dragOffset else 0f
                shadowElevation = elevation
            },
        shape = RoundedCornerShape(if (isDragging) 16.dp else 12.dp),
        colors = if (isDragging) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f))
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            onMove?.let {
                DragHandle(
                    onMove = it,
                    onDragOffsetChanged = { dragOffset = it },
                    onDraggingChanged = {
                        isDragging = it
                        onDraggingChanged(it)
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (exercise.plannedExerciseId != null) {
                        Text(
                            "计划",
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                        )
                    }
                }
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
    var unit by mutableStateOf(initial?.unit ?: "磅")
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
                TextButton(onClick = { draft.unit = "磅" }) { Text("磅") }
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
    val filteredActions = actions.filter { actionMatchesQuery(it.name, searchQuery, it.muscles) }
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
                                label = { Text("搜索动作或肌群") },
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

private fun actionMatchesQuery(
    name: String,
    rawQuery: String,
    muscles: List<MuscleSelection> = emptyList(),
): Boolean {
    val query = normalizeSearchText(rawQuery)
    if (query.isEmpty()) return true
    return searchFieldMatches(name, rawQuery, query) ||
        muscles.any { muscle -> searchFieldMatches(muscle.name, rawQuery, query) }
}

private fun searchFieldMatches(name: String, rawQuery: String, query: String): Boolean {
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
