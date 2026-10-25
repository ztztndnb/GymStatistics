package com.gymstatistics.data

import java.time.LocalDate

fun validateBodyData(data: BodyData) {
    require(data.metrics.map { it.id }.distinct().size == data.metrics.size) { "身体指标 ID 重复" }
    data.metrics.forEach {
        require(it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 30 && it.unit.isNotBlank() && it.unit.length <= 12) { "请填写有效的指标名称和单位" }
        defaultBodyMetrics().find { preset -> preset.id == it.id }?.let { preset ->
            require(it.unit == preset.unit) { "预设指标的单位不能更改：${preset.name}" }
        }
    }
    require(data.records.map { it.date }.distinct().size == data.records.size) { "身体记录日期重复" }
    val ids = data.metrics.map { it.id }.toSet()
    data.records.forEach { record ->
        require(runCatching { LocalDate.parse(record.date).toString() == record.date }.getOrDefault(false)) { "身体记录日期无效" }
        require(record.values.isNotEmpty()) { "身体记录至少需要一个数值" }
        record.values.forEach { (id, value) ->
            require(id in ids) { "身体记录包含未知指标：$id" }
            require(value.isFinite() && value > 0) { "身体数据必须是大于 0 的有限数值" }
        }
    }
}

fun saveBodyDay(data: BodyData, date: String, values: Map<String, Double>): BodyData {
    val result = data.copy(records = (data.records.filterNot { it.date == date } + BodyDayRecord(date, values)).sortedByDescending { it.date })
    validateBodyData(result)
    return result
}

fun updateBodyMetric(data: BodyData, metric: BodyMetric): BodyData {
    val existing = data.metrics.find { it.id == metric.id }
    require(existing == null || existing.unit == metric.unit || data.records.none { metric.id in it.values }) { "已有记录的指标不能更改单位，请新建指标" }
    val clean = metric.copy(name = metric.name.trim(), unit = metric.unit.trim())
    require(data.metrics.none { it.id != metric.id && it.name == clean.name }) { "指标名称已存在" }
    val result = data.copy(metrics = if (existing == null) data.metrics + clean else data.metrics.map { if (it.id == clean.id) clean else it })
    validateBodyData(result)
    return result
}

/** Null is an old backup with no body section; it must leave local body data intact. */
fun importBodyData(current: BodyData, incoming: BodyData?, mode: ImportMode, overwrite: Boolean): BodyImportResult {
    if (incoming == null) return BodyImportResult(current, ImportResult())
    validateBodyData(incoming)
    val selected = incoming.records.filter { mode is ImportMode.ALL || it.date == (mode as ImportMode.DATE).date }
    val ids = if (mode is ImportMode.ALL) incoming.metrics.map { it.id }.toSet() else selected.flatMap { it.values.keys }.toSet()
    val definitions = incoming.metrics.filter { it.id in ids }
    definitions.forEach { metric ->
        current.metrics.find { it.id == metric.id }?.let {
            require(it.unit == metric.unit) { "身体指标单位冲突：${it.name}（${it.unit} / ${metric.unit}）" }
        }
    }
    val defaults = defaultBodyMetrics().associateBy { it.id }
    val metrics = current.metrics.map { local ->
        val restored = definitions.find { it.id == local.id }
        // Fresh presets have no user customizations. An explicit overwrite also restores metadata.
        if (restored != null && (overwrite || (local == defaults[local.id] && current.records.none { local.id in it.values }))) restored else local
    } + definitions.filter { metric -> current.metrics.none { it.id == metric.id } }
    val records = current.records.toMutableList()
    var added = 0; var duplicates = 0; var skipped = 0; var overwritten = 0
    selected.forEach { record ->
        val index = records.indexOfFirst { it.date == record.date }
        if (index < 0) { records.add(record); added++ }
        else {
            duplicates++
            if (overwrite) { records[index] = record; overwritten++ } else skipped++
        }
    }
    return BodyImportResult(BodyData(metrics, records.sortedByDescending { it.date }), ImportResult(added, duplicates, skipped, overwritten))
}

fun bodyPoints(data: BodyData, metricId: String, days: Int?, today: LocalDate = LocalDate.now()): List<BodyPoint> =
    data.records.mapNotNull { record ->
        val date = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return@mapNotNull null
        val value = record.values[metricId]?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
        if (date > today || (days != null && date < today.minusDays(days.toLong() - 1))) null else BodyPoint(date, value)
    }.sortedBy { it.date }
