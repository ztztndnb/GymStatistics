package com.gymstatistics.ui

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gymstatistics.data.*
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsScreen(data: WorkoutData, onOpenDate: (LocalDate) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(60_000)
        }
    }
    val statistics = remember(data.sessions, today) { WorkoutStatistics(data.sessions, today) }
    Scaffold(topBar = { TopAppBar(title = { Text("统计") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "overview") { StatisticsOverviewGrid(statistics.overview) }
            item(key = "activity") { TrainingActivityCard(statistics, onOpenDate) }
            item(key = "muscles") { MuscleDistributionCard(statistics) }
            item(key = "plan") { WeeklyPlanCard(statistics, data.trainingPlan) }
            item(key = "progress") { ExerciseProgressCard(statistics) }
            item(key = "recent") { RecentTrainingCard(statistics.days.take(6), onOpenDate) }
            item(key = "rules") {
                Text(
                    "仅统计截至今天的实际动作记录。未填写组数时按 1 组计算；不同单位不混合计算。修改、删除或导入记录后，统计会同步更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatisticsOverviewGrid(overview: StatisticsOverview) {
    val tiles = listOf(
        Triple("累计训练", overview.trainingDays.toString(), "天"),
        Triple("本月训练", overview.monthDays.toString(), "天"),
        Triple("连续训练", overview.consecutiveWeeks.toString(), "周"),
        Triple("动作记录", overview.actionRecords.toString(), "条"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, unit) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(value, style = MaterialTheme.typography.headlineMedium)
                                Text(unit, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Text("连续周按周一至周日计算，本周尚未训练不会立即中断。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatisticsCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun StatisticsOptions(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            FilterChip(selected = index == selected, onClick = { onSelect(index) }, label = { Text(label) })
        }
    }
}

@Composable
private fun TrainingActivityCard(statistics: WorkoutStatistics, onOpenDate: (LocalDate) -> Unit) {
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val cells = remember(statistics) { statistics.activity(weekStart(statistics.today.minusWeeks(51))) }
    val positive = remember(cells, metric) { cells.map { if (metric == 0) it.actions else it.sets }.filter { it > 0 }.sorted() }
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    StatisticsCard("训练活动", "最近一年 · 左右滑动浏览；点击日期查看当天摘要") {
        StatisticsOptions(listOf("动作数", "记录组数"), metric) { metric = it }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.height(18.dp))
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.height(20.dp), style = MaterialTheme.typography.labelSmall) }
            }
            Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(7).forEachIndexed { index, week ->
                    val date = week.first().date
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (index == 0 || date.dayOfMonth <= 7) "${date.monthValue}月" else "",
                            Modifier.height(18.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        )
                        repeat(7) { dayIndex ->
                            val cell = week.getOrNull(dayIndex)
                            if (cell == null) Spacer(Modifier.size(20.dp)) else {
                                val value = if (metric == 0) cell.actions else cell.sets
                                val level = if (value <= 0 || positive.isEmpty()) 0 else when {
                                    value <= positive[(positive.lastIndex * .25).toInt()] -> 1
                                    value <= positive[(positive.lastIndex * .5).toInt()] -> 2
                                    value <= positive[(positive.lastIndex * .75).toInt()] -> 3
                                    else -> 4
                                }
                                val color by animateColorAsState(heatColor(level), label = "activity-color")
                                Box(
                                    Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(color)
                                        .then(if (selectedDate == cell.date.toString()) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)) else Modifier)
                                        .clickable { selectedDate = cell.date.toString() }
                                        .semantics { contentDescription = "${cell.date}，${cell.actions}条动作，${cell.sets}组" },
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("少", style = MaterialTheme.typography.labelSmall)
            (0..4).forEach { Box(Modifier.padding(horizontal = 2.dp).size(12.dp).background(heatColor(it), RoundedCornerShape(3.dp))) }
            Text("多", style = MaterialTheme.typography.labelSmall)
        }
        val selected = cells.firstOrNull { it.date.toString() == selectedDate }
        if (selected != null) {
            Text("${selected.date} · ${selected.actions}条动作 · ${selected.sets}组", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onOpenDate(selected.date) }) { Text("查看当天记录"); Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, Modifier.size(18.dp)) }
        } else Text("已训练 ${cells.count { it.actions > 0 }} 天", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun heatColor(level: Int): Color = if (level == 0) MaterialTheme.colorScheme.surfaceContainerHighest
else MaterialTheme.colorScheme.primary.copy(alpha = listOf(0f, .25f, .45f, .7f, 1f)[level])

@Composable
private fun MuscleDistributionCard(statistics: WorkoutStatistics) {
    var range by rememberSaveable { mutableIntStateOf(0) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val currentWeek = weekStart(statistics.today).plusWeeks(weekOffset.toLong())
    val from = when (range) { 0 -> currentWeek; 1 -> statistics.today.minusDays(29); 2 -> statistics.today.minusDays(89); else -> null }
    val to = if (range == 0) minOf(currentWeek.plusDays(6), statistics.today) else statistics.today
    val rows = remember(statistics, from, to) { statistics.muscles(from, to) }
    StatisticsCard("肌群分布", "按肌群覆盖组数统计；每个已选择肌群各计对应组数，不区分主次。") {
        StatisticsOptions(listOf("本周", "近30天", "近90天", "全部"), range) { range = it; selectedMuscle = null; expanded = false }
        if (range == 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { weekOffset--; selectedMuscle = null }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上一周") }
                Text("${shortStatisticsDate(currentWeek)} – ${shortStatisticsDate(currentWeek.plusDays(6))}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { weekOffset++; selectedMuscle = null }, enabled = weekOffset < 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下一周") }
            }
        }
        StatisticsBodyMap(rows, selectedMuscle) { selectedMuscle = if (selectedMuscle == it) null else it }
        if (rows.isEmpty()) Text("这段时间还没有已标注肌群的训练记录。", style = MaterialTheme.typography.bodyMedium)
        val selected = rows.firstOrNull { it.muscle.id == selectedMuscle }
        if (selectedMuscle != null) {
            Text(if (selected == null) "该部位在所选时间内没有训练记录" else "${selected.muscle.name} · ${selected.sets}组", color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { selectedMuscle = null }) { Text("查看全部肌群") }
        }
        val max = rows.maxOfOrNull { it.sets } ?: 1L
        val shown = if (selected != null) listOf(selected) else if (expanded) rows else rows.take(4)
        shown.forEach { row ->
            MuscleLoadRow(row.muscle.name, "${row.sets}组", row.sets.toDouble() / max, onClick = { selectedMuscle = if (selectedMuscle == row.muscle.id) null else row.muscle.id })
        }
        if (rows.size > 4 && selectedMuscle == null) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "查看全部 ${rows.size} 个肌群") }
        Text("肌群之间会重复覆盖，不能直接相加作为全身总组数。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatisticsBodyMap(rows: List<MuscleWorkload>, selected: String?, onSelect: (String) -> Unit) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val palette = MaterialTheme.colorScheme
    fun css(color: Color) = "#%02x%02x%02x".format((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    val payload = JSONObject().apply {
        put("rows", JSONArray().apply { rows.forEach { put(JSONObject().put("id", it.muscle.id).put("name", it.muscle.name).put("sets", it.sets)) } })
        put("selected", selected ?: JSONObject.NULL)
        put("primary", css(palette.primary)); put("background", css(palette.surfaceContainerLow))
        put("neutral", css(palette.outlineVariant)); put("text", css(palette.onSurface))
    }.toString()
    // onPageFinished reads the latest payload; updates can arrive while the asset loads.
    val currentPayload by rememberUpdatedState(payload)
    var attempt by remember { mutableIntStateOf(0) }
    var ready by remember(attempt) { mutableStateOf(false) }
    var loadError by remember(attempt) { mutableStateOf<String?>(null) }
    LaunchedEffect(attempt) {
        delay(10_000)
        if (!ready && loadError == null) loadError = "加载超时，请重试。"
    }
    Box(Modifier.fillMaxWidth().height(290.dp)) {
        key(attempt) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        // AndroidView's default WRAP_CONTENT makes WebView resolve CSS vh
                        // against content height, collapsing the SVG even with a Compose height.
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.allowContentAccess = false
                        addJavascriptInterface(object {
                            @JavascriptInterface fun onMuscle(id: String) { mainHandler.post { currentOnSelect(id) } }
                        }, "AndroidStatistics")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                if (url != "file:///android_asset/muscle-statistics/index.html") return
                                val script = "(function(){try{setStatistics($currentPayload);return document.querySelector('#front').getBoundingClientRect().height>0 && document.querySelectorAll('#front path').length>0}catch(e){return false}})()"
                                view.evaluateJavascript(script) { result ->
                                    ready = result == "true"
                                    loadError = if (ready) null else "人体图初始化失败，请重试。"
                                }
                            }
                            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                                if (request.isForMainFrame) { ready = false; loadError = "本地人体图页面无法加载，请重试。" }
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
                        }
                        loadUrl("file:///android_asset/muscle-statistics/index.html")
                    }
                },
                update = { if (ready) it.evaluateJavascript("window.setStatistics && setStatistics($payload)", null) },
                onRelease = { it.removeJavascriptInterface("AndroidStatistics"); it.destroy() },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "肌群训练分布图" },
            )
        }
        if (!ready || loadError != null) {
            Column(
                Modifier.fillMaxSize().background(palette.surfaceContainerLow).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loadError == null) Text("肌群图加载中…", style = MaterialTheme.typography.bodyMedium) else {
                    Text("肌群图加载失败", style = MaterialTheme.typography.titleSmall)
                    Text(loadError.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { attempt++ }) { Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun MuscleLoadRow(name: String, value: String, fraction: Double, plannedFraction: Double = 0.0, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(plannedFraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = .25f)))
            Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun WeeklyPlanCard(statistics: WorkoutStatistics, plan: TrainingPlan?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val planned = remember(plan) { plannedMuscleWorkloads(plan) }
    val actual = remember(statistics) { statistics.muscles(weekStart(statistics.today)) }.associateBy { it.muscle.id }
    val combined = (planned.map { it.muscle } + actual.values.map { it.muscle }).distinctBy { it.id }
        .sortedByDescending { muscle -> planned.firstOrNull { it.muscle.id == muscle.id }?.sets ?: 0L }
    val max = maxOf(planned.maxOfOrNull { it.sets } ?: 0L, actual.values.maxOfOrNull { it.sets } ?: 0L, 1L)
    StatisticsCard("每周计划训练量", "${plan?.name ?: "尚未设置计划"} · 本周累计 / 当前整周计划") {
        if (combined.isEmpty()) Text("设置训练计划并为动作选择肌群后，即可在这里查看。", style = MaterialTheme.typography.bodyMedium)
        (if (expanded) combined else combined.take(5)).forEach { muscle ->
            val target = planned.firstOrNull { it.muscle.id == muscle.id }?.sets ?: 0L
            val done = actual[muscle.id]?.sets ?: 0L
            MuscleLoadRow(muscle.name, "$done / $target 组", done.toDouble() / max, plannedFraction = target.toDouble() / max)
        }
        if (combined.size > 5) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开全部") }
        Text("浅色：整周计划 · 深色：本周累计", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("包含手动及计划生成的实际记录。计划是当前预设，不是历史计划快照；此对照不代表计划动作完成率。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseProgressCard(statistics: WorkoutStatistics) {
    val actions = remember(statistics) { statistics.days.flatMap { it.exercises }.map { it.name }.distinct().sorted() }
    var chosenAction by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenUnit by rememberSaveable { mutableStateOf<String?>(null) }
    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    var showPicker by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val action = chosenAction?.takeIf { it in actions } ?: actions.firstOrNull()
    val units = action?.let { statistics.units(it) }.orEmpty()
    val unit = chosenUnit?.takeIf { it in units } ?: units.firstOrNull().orEmpty()
    val metric = ProgressMetric.entries[metricIndex]
    val from = when (rangeIndex) { 0 -> statistics.today.minusDays(29); 1 -> statistics.today.minusDays(89); 2 -> statistics.today.minusYears(1).plusDays(1); else -> null }
    val points = remember(statistics, action, unit, metric, from) { if (action == null) emptyList() else statistics.progress(action, unit, metric, from) }
    val metricUnit = when (metric) { ProgressMetric.VALUE -> unit.ifEmpty { "无单位" }; ProgressMetric.TOTAL_COUNT -> "个"; ProgressMetric.SETS -> "组"; ProgressMetric.COEFFICIENT -> if (unit.isEmpty()) "系数" else "$unit·次" }
    StatisticsCard("动作进步", "每个训练日取所选指标最高值，不同单位分开查看。") {
        if (action == null) Text("记录第一个动作后，这里会显示训练曲线。", style = MaterialTheme.typography.bodyMedium) else {
            OutlinedButton(onClick = { query = ""; showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Search, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(action, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (units.size > 1) StatisticsOptions(units.map { it.ifEmpty { "无单位" } }, units.indexOf(unit)) { chosenUnit = units[it] }
            StatisticsOptions(ProgressMetric.entries.map { it.label }, metricIndex) { metricIndex = it }
            StatisticsOptions(listOf("1个月", "3个月", "1年", "全部"), rangeIndex) { rangeIndex = it }
            AnimatedContent(targetState = Triple(action, unit, metric), label = "progress-metric") { target ->
                val targetPoints = remember(statistics, target, from) { statistics.progress(target.first, target.second, target.third, from) }
                StatisticsLineChart(targetPoints, metricUnit)
            }
            if (points.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("范围内最佳", style = MaterialTheme.typography.labelMedium); Text("${statisticsNumber(points.maxOf { it.value })} $metricUnit", style = MaterialTheme.typography.titleMedium) }
                    Column(horizontalAlignment = Alignment.End) { Text("最近一次", style = MaterialTheme.typography.labelMedium); Text("${statisticsNumber(points.last().value)} $metricUnit", style = MaterialTheme.typography.titleMedium) }
                }
                Text("${points.size} 个训练日 · 最近记录 ${points.last().date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (metric == ProgressMetric.COEFFICIENT) Text("重量系数 = 数值 × 个数 × 组数；自定义单位仍按原单位计算，不等同于最大力量。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false }, title = { Text("选择动作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索动作或肌群") }, singleLine = true)
                    val matching = actions.filter { name -> name.contains(query, true) || statistics.days.any { day -> day.exercises.any { it.name == name && it.muscles.any { muscle -> muscle.name.contains(query, true) } } } }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        if (matching.isEmpty()) item { Text("没有匹配的动作") }
                        matching.forEach { name -> item(key = name) {
                            TextButton(onClick = { chosenAction = name; chosenUnit = null; showPicker = false }, modifier = Modifier.fillMaxWidth()) { Text(name, Modifier.fillMaxWidth()) }
                        } }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun StatisticsLineChart(points: List<ProgressPoint>, unit: String) {
    if (points.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text("这段时间没有该指标的有效记录", style = MaterialTheme.typography.bodyMedium) }
        return
    }
    val max = points.maxOf { it.value }
    val min = points.minOf { it.value }
    val span = (max - min).takeIf { it > 0 } ?: max.coerceAtLeast(1.0)
    val chartColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.height(160.dp).widthIn(min = 36.dp), verticalArrangement = if (max == min) Arrangement.Center else Arrangement.SpaceBetween) {
                Text(statisticsNumber(max), style = MaterialTheme.typography.labelSmall)
                if (max != min) Text(statisticsNumber(min), style = MaterialTheme.typography.labelSmall)
            }
            Canvas(Modifier.weight(1f).height(160.dp).semantics { contentDescription = "${points.size}个训练日，最低${statisticsNumber(min)}，最高${statisticsNumber(max)} $unit" }) {
                val inset = 6.dp.toPx()
                val width = (size.width - inset * 2).coerceAtLeast(1f)
                val height = size.height - inset * 2
                repeat(4) { index -> val y = inset + height * index / 3; drawLine(gridColor, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx()) }
                val first = points.first().date.toEpochDay()
                val daySpan = (points.last().date.toEpochDay() - first).coerceAtLeast(1)
                val locations = points.map { point ->
                    Offset(
                        if (points.size == 1) size.width / 2 else inset + width * ((point.date.toEpochDay() - first).toDouble() / daySpan).toFloat(),
                        inset + height * (if (max == min) .5f else ((max - point.value) / span).toFloat()),
                    )
                }
                val path = Path().apply { locations.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
                drawPath(path, chartColor, style = Stroke(width = 2.dp.toPx()))
                locations.forEach { drawCircle(chartColor, 3.dp.toPx(), it) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortStatisticsDate(points.first().date), style = MaterialTheme.typography.labelSmall)
            if (points.size > 1) Text(shortStatisticsDate(points.last().date), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecentTrainingCard(days: List<TrainingDay>, onOpenDate: (LocalDate) -> Unit) {
    StatisticsCard("最近训练") {
        if (days.isEmpty()) Text("还没有训练记录，去首页记下第一次训练吧。", style = MaterialTheme.typography.bodyMedium)
        days.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable { onOpenDate(day.date) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), style = MaterialTheme.typography.titleSmall)
                    Text("${day.exercises.size}条动作 · ${day.sets}组", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(day.exercises.map { it.name }.distinct().joinToString("、"), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "查看当天记录", Modifier.size(20.dp))
            }
        }
    }
}

private fun shortStatisticsDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("M/d"))
private fun statisticsNumber(value: Double): String = when {
    value >= 100_000_000 -> DecimalFormat("0.##").format(value / 100_000_000) + "亿"
    value >= 10_000 -> DecimalFormat("0.##").format(value / 10_000) + "万"
    else -> DecimalFormat("0.##").format(value)
}
