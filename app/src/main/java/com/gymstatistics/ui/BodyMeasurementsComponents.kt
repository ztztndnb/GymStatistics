package com.gymstatistics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymstatistics.data.*
import java.math.BigDecimal
import java.time.LocalDate

internal fun bodyNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal fun bodySummary(body: BodyData, record: BodyDayRecord): String = body.metrics.mapNotNull { metric ->
    record.values[metric.id]?.let { "${metric.name} ${bodyNumber(it)} ${metric.unit}" }
}.joinToString("，")

@Composable
internal fun BodyDaySummary(data: BodyData?, date: LocalDate, loaded: Boolean, onOpen: () -> Unit) {
    val body = data ?: BodyData()
    val record = body.records.find { it.date == date.toString() }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("身体数据", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (!loaded) "正在读取…" else record?.let { bodySummary(body, it) } ?: "这一天尚未记录",
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen, enabled = loaded) { Text(if (record == null) "记录身体数据" else "编辑身体数据") }
        }
    }
}

@Composable
internal fun BodyChangesCard(body: BodyData?, today: LocalDate) {
    val data = body ?: BodyData()
    var metricId by remember { mutableStateOf("weight") }
    val metric = data.metrics.find { it.id == metricId } ?: data.metrics.firstOrNull()
    var range by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val points = remember(data, metric, range, today) { bodyPoints(data, metric?.id.orEmpty(), listOf(30, 90, null)[range], today) }
    val all = remember(data, metric, today) { bodyPoints(data, metric?.id.orEmpty(), null, today) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("身体变化", style = MaterialTheme.typography.titleMedium)
        Text("按实际测量日期展示；未记录日期不补零。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            FilledTonalButton(
                onClick = { menu = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(metric?.let { "${it.name}（${it.unit}）" } ?: "选择指标", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                data.metrics.forEach { option ->
                    DropdownMenuItem(text = { Text("${option.name}（${option.unit}）" + if (option.enabled) "" else "（已停用）") }, onClick = { metricId = option.id; menu = false })
                }
            }
        }
        val latest = all.lastOrNull()
        if (latest != null) {
            Text(buildAnnotatedString {
                append("最新 ")
                withStyle(SpanStyle(fontSize = 38.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                    append(bodyNumber(latest.value))
                }
                append(" ${metric?.unit.orEmpty()}（${latest.date}）")
            }, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 46.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val previous = all.dropLast(1).lastOrNull()
            Text(if (previous == null) "尚无上次测量可比较" else {
                val difference = BigDecimal.valueOf(latest.value).subtract(BigDecimal.valueOf(previous.value))
                "较上次（${previous.date}） ${if (difference.signum() > 0) "+" else ""}${difference.stripTrailingZeros().toPlainString()} ${metric?.unit.orEmpty()}"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GymOptionRow(options = listOf("近30天", "近90天", "全部"), selected = range, onSelect = { range = it })
        BodyTrendChart(points, metric?.unit.orEmpty())
    }
}

@Composable
private fun BodyTrendChart(points: List<BodyPoint>, unit: String) {
    if (points.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { Text("这段时间没有身体测量记录") }
        return
    }
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Text("${bodyNumber(min)} – ${bodyNumber(max)} $unit", style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "${points.size}个测量日，最低${bodyNumber(min)}，最高${bodyNumber(max)} $unit" }) {
        val inset = 8.dp.toPx()
        val width = (size.width - inset * 2).coerceAtLeast(1f)
        val height = (size.height - inset * 2).coerceAtLeast(1f)
        repeat(4) { index -> val y = inset + height * index / 3; drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx()) }
        val first = points.first().date.toEpochDay()
        val days = (points.last().date.toEpochDay() - first).coerceAtLeast(1)
        val locations = points.map {
            Offset(if (points.size == 1) size.width / 2 else inset + width * ((it.date.toEpochDay() - first).toDouble() / days).toFloat(),
                inset + height * if (max == min) .5f else ((max - it.value) / (max - min)).toFloat())
        }
        val path = Path().apply { locations.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        locations.forEach { drawCircle(color, 3.dp.toPx(), it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.first().date.toString(), style = MaterialTheme.typography.labelSmall)
        if (points.size > 1) Text(points.last().date.toString(), style = MaterialTheme.typography.labelSmall)
    }
}
