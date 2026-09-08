package com.gymstatistics.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A single occurrence of an action (from one session). */
data class ActionRecord(
    val date: String,
    val data: Double?,
    val unit: String,
    val count: Int?,
    val sets: Int?,
    val muscles: List<MuscleSelection> = emptyList(),
) {
    /** 总个数 = 个数 × 组数 (组数缺省按 1). */
    val totalCount: Int?
        get() {
            val c = count ?: return null
            val s = sets ?: 1
            return c * s
        }
}

/** Aggregates all occurrences sharing the same action name. */
data class ActionSummary(
    val name: String,
    val records: List<ActionRecord>,
) {
    /** 该动作被添加的次数. */
    val addedCount: Int get() = records.size

    /** 该动作最近一次被做过的日期 (yyyy-MM-dd),无记录时为空. */
    val lastDate: String? get() = records.maxOfOrNull { it.date }

    val muscles: List<MuscleSelection> get() = records.flatMap { it.muscles }.distinctBy { it.id }
}

/** 若某动作在过去不满 N 天内做过,则提示疲劳预警. */
const val FATIGUE_WINDOW_DAYS = 3

/** A chart series keyed by unit. */
data class ChartSeries(
    val label: String,
    val points: List<Pair<String, Double>>, // (date, value)
)

data class RecentAction(
    val name: String,
    val date: String,
    val whenText: String,
)

data class FatigueWarning(
    val muscle: MuscleSelection,
    val actions: List<RecentAction>,
)

/** Finds recent actions that share each muscle group with [action]. */
fun fatigueWarningsFor(
    action: ActionSummary,
    allActions: List<ActionSummary>,
    today: LocalDate,
): List<FatigueWarning> {
    val targetMuscles = action.muscles.associateBy { it.id }
    if (targetMuscles.isEmpty()) return emptyList()

    val matches = allActions.flatMap { other ->
        other.records.flatMap { record ->
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(record.date), today).toInt() }.getOrNull()
            if (days == null || days !in 0 until FATIGUE_WINDOW_DAYS) return@flatMap emptyList()
            val whenText = if (days == 0) "今天" else "${days}天前"
            record.muscles
                .filter { it.id in targetMuscles }
                .map { it.id to RecentAction(other.name, record.date, whenText) }
        }
    }
    return matches.groupBy({ it.first }, { it.second })
        .mapNotNull { (id, records) ->
            targetMuscles[id]?.let { muscle ->
                FatigueWarning(muscle, records.distinctBy { it.name to it.date }.sortedByDescending { it.date })
            }
        }
        .sortedBy { it.muscle.name }
}

/**
 * Group all session exercises by name (same name = same action).
 * Each action's records are sorted with [actionComparator]; the action list
 * itself is sorted by added count descending.
 */
fun buildActions(sessions: List<SessionRecord>): List<ActionSummary> {
    val grouped = sessions
        .flatMap { s -> s.exercises.map { it.name to ActionRecord(s.date, it.data, it.unit, it.count, it.sets, it.muscles) } }
        .groupBy({ it.first }, { it.second })
    return grouped.map { (name, recs) ->
        ActionSummary(name, recs.sortedWith(actionComparator()))
    }.sortedByDescending { it.addedCount }
}

/**
 * Within an action:
 *  unit 按字典序降序, 无单位(空)最低; 单位相同按 data 降序; data 相同按 totalCount(个数×组数)降序;
 *  totalCount 相同按 sets 降序; sets 相同按 count 降序.
 */
fun actionComparator(): Comparator<ActionRecord> =
    compareByDescending<ActionRecord> { if (it.unit.isEmpty()) 0 else 1 }
        .thenByDescending { it.unit }
        .thenByDescending { it.data ?: Double.NEGATIVE_INFINITY }
        .thenByDescending { it.totalCount ?: Int.MIN_VALUE }
        .thenByDescending { it.sets ?: Int.MIN_VALUE }
        .thenByDescending { it.count ?: Int.MIN_VALUE }

/** Per-unit (date -> data) series for this action. */
fun buildSeriesByData(a: ActionSummary): List<ChartSeries> =
    a.records.filter { it.data != null }
        .groupBy { it.unit }
        .map { (unit, recs) ->
            ChartSeries(
                label = if (unit.isEmpty()) "无单位" else unit,
                points = recs.sortedBy { it.date }.map { it.date to it.data!! },
            )
        }

/** Per-unit (date -> 总个数) series for this action. */
fun buildSeriesByTotalCount(a: ActionSummary): List<ChartSeries> =
    a.records.filter { it.totalCount != null }
        .groupBy { it.unit }
        .map { (unit, recs) ->
            ChartSeries(
                label = if (unit.isEmpty()) "无单位" else unit,
                points = recs.sortedBy { it.date }.map { it.date to it.totalCount!!.toDouble() },
            )
        }
