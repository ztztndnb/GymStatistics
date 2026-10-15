package com.gymstatistics.data

import java.time.LocalDate

data class StatisticsOverview(val trainingDays: Int = 0, val monthDays: Int = 0, val consecutiveWeeks: Int = 0, val actionRecords: Long = 0)
data class TrainingDay(val date: LocalDate, val exercises: List<ExerciseRecord>, val sets: Long)
data class TrainingActivity(val date: LocalDate, val actions: Long, val sets: Long)
data class MuscleWorkload(val muscle: MuscleSelection, val sets: Long)
data class ProgressPoint(val date: LocalDate, val value: Double)
enum class ProgressMetric(val label: String) { VALUE("数值"), TOTAL_COUNT("总个数"), SETS("组数"), COEFFICIENT("重量系数") }

class WorkoutStatistics(sessions: List<SessionRecord>, val today: LocalDate) {
    // Dates, rather than session IDs, define a training day. Imports may contain
    // several sessions on one date, malformed dates, or future scheduled entries.
    val days: List<TrainingDay> = sessions.mapNotNull { session ->
        val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
            ?.takeUnless { it.isAfter(today) } ?: return@mapNotNull null
        date to session.exercises.filter { it.name.isNotBlank() }
    }.groupBy({ it.first }, { it.second }).mapNotNull { (date, groups) ->
        val exercises = groups.flatten()
        if (exercises.isEmpty()) null else TrainingDay(date, exercises, exercises.sumOf { recordedSets(it.sets) })
    }.sortedByDescending { it.date }

    val overview: StatisticsOverview = run {
        val weeks = days.map { statisticsWeekStart(it.date) }.toSet()
        var cursor = statisticsWeekStart(today)
        if (cursor !in weeks) cursor = cursor.minusWeeks(1)
        var streak = 0
        while (cursor in weeks) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        StatisticsOverview(
            trainingDays = days.size,
            monthDays = days.count { !it.date.isBefore(today.withDayOfMonth(1)) },
            consecutiveWeeks = streak,
            actionRecords = days.sumOf { it.exercises.size.toLong() },
        )
    }

    fun activity(from: LocalDate): List<TrainingActivity> {
        if (from.isAfter(today)) return emptyList()
        val byDate = days.associateBy { it.date }
        return generateSequence(from) { if (it < today) it.plusDays(1) else null }.map { date ->
            val day = byDate[date]
            TrainingActivity(date, day?.exercises?.size?.toLong() ?: 0L, day?.sets ?: 0L)
        }.toList()
    }

    fun muscles(from: LocalDate? = null, to: LocalDate = today): List<MuscleWorkload> =
        workloads(days.filter { (from == null || it.date >= from) && it.date <= to }
            .flatMap { it.exercises }.map { it.muscles to recordedSets(it.sets) })

    fun units(name: String): List<String> = days.flatMap { it.exercises }
        .filter { it.name == name }.map { it.unit.trim() }.distinct().sorted()

    /** Each date contributes its best recorded value, within ONE exact unit. */
    fun progress(name: String, unit: String, metric: ProgressMetric, from: LocalDate? = null): List<ProgressPoint> =
        days.filter { from == null || it.date >= from }.mapNotNull { day ->
            val best = day.exercises.asSequence().filter { it.name == name && it.unit.trim() == unit }
                .mapNotNull { metricValue(it, metric) }.maxOrNull()
            best?.let { ProgressPoint(day.date, it) }
        }.sortedBy { it.date }
}

fun plannedMuscleWorkloads(plan: TrainingPlan?): List<MuscleWorkload> = workloads(
    plan?.days.orEmpty().filter { !it.restDay && it.weekday in 1..7 }
        .flatMap { it.exercises }.filter { it.name.isNotBlank() }
        .map { it.muscles to recordedSets(it.sets) },
)

private fun statisticsWeekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

private fun recordedSets(sets: Int?): Long = (sets ?: 1).coerceAtLeast(0).toLong()

private fun workloads(records: List<Pair<List<MuscleSelection>, Long>>): List<MuscleWorkload> =
    records.flatMap { (muscles, sets) -> muscles.distinctBy { it.id }.filter { it.id.isNotBlank() }.map { it to sets } }
        .groupBy { it.first.id }.values.map { rows -> MuscleWorkload(rows.first().first, rows.sumOf { it.second }) }
        .filter { it.sets > 0 }.sortedWith(compareByDescending<MuscleWorkload> { it.sets }.thenBy { it.muscle.name })

private fun metricValue(record: ExerciseRecord, metric: ProgressMetric): Double? {
    val value = record.data?.takeIf { it.isFinite() && it >= 0 }
    val count = record.count?.takeIf { it >= 0 }?.toDouble()
    val sets = (record.sets ?: 1).takeIf { it >= 0 }?.toDouble()
    return when (metric) {
        ProgressMetric.VALUE -> value
        ProgressMetric.TOTAL_COUNT -> if (count != null && sets != null) count * sets else null
        ProgressMetric.SETS -> sets
        ProgressMetric.COEFFICIENT -> if (value != null && count != null && sets != null) value * count * sets else null
    }?.takeIf { it.isFinite() }
}
