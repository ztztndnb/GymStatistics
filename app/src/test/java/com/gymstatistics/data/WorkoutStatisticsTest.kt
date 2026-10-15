package com.gymstatistics.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutStatisticsTest {
    private val today = LocalDate.parse("2026-01-07")
    private val chest = MuscleSelection("chest-left", "左胸肌")
    private val back = MuscleSelection("back-left", "左背肌")
    private fun exercise(value: Double? = 60.0, unit: String = "kg", count: Int? = 10, sets: Int? = 3) =
        ExerciseRecord("卧推", data = value, unit = unit, count = count, sets = sets, muscles = listOf(chest))
    private fun session(date: String, vararg exercises: ExerciseRecord) =
        SessionRecord(date, date, exercises = exercises.toList())

    @Test fun overviewMergesSameDayAndExcludesEmptyMalformedAndFutureDays() {
        val stats = WorkoutStatistics(listOf(
            session("2026-01-06", exercise()), session("2026-01-06", exercise()),
            session("2025-12-31", exercise()), session("2026-01-07"),
            session("2026-01-08", exercise()), session("not-a-date", exercise()),
            session("2026-01-05", exercise().copy(name = "  ")),
        ), today)
        assertEquals(2, stats.overview.trainingDays)
        assertEquals(1, stats.overview.monthDays)
        assertEquals(3L, stats.overview.actionRecords)
        assertEquals(listOf("2026-01-06", "2025-12-31"), stats.days.map { it.date.toString() })
    }

    @Test fun streakCrossesYearAndDoesNotBreakBeforeCurrentWeekEnds() {
        val stats = WorkoutStatistics(listOf(session("2026-01-04", exercise()), session("2025-12-22", exercise())), today)
        assertEquals(2, stats.overview.consecutiveWeeks)
        val broken = WorkoutStatistics(listOf(session("2025-12-22", exercise())), today)
        assertEquals(0, broken.overview.consecutiveWeeks)
    }

    @Test fun streakIncludesCurrentWeekAndStopsAtFirstMissingWeek() {
        val stats = WorkoutStatistics(listOf(
            session("2026-01-07", exercise()), session("2025-12-29", exercise()),
            session("2025-12-15", exercise()),
        ), today)
        assertEquals(2, stats.overview.consecutiveWeeks)
    }

    @Test fun activityIncludesRestDaysAndUsesOneGroupWhenMissing() {
        val stats = WorkoutStatistics(listOf(
            session("2026-01-06", exercise(sets = null)),
            session("2026-01-06", exercise(sets = 3)),
        ), today)
        val cells = stats.activity(LocalDate.parse("2026-01-05"))
        assertEquals(3, cells.size)
        assertEquals(listOf(0L, 2L, 0L), cells.map { it.actions })
        assertEquals(listOf(0L, 4L, 0L), cells.map { it.sets })
    }

    @Test fun muscleCoverageCountsEachMuscleOncePerRecordAndRespectsRange() {
        val stats = WorkoutStatistics(listOf(
            session("2026-01-06", exercise().copy(muscles = listOf(chest, chest, back))),
            session("2025-12-31", exercise(sets = 2)),
        ), today)
        assertEquals(5L, stats.muscles().first { it.muscle.id == chest.id }.sets)
        assertEquals(3L, stats.muscles(LocalDate.parse("2026-01-05")).first { it.muscle.id == chest.id }.sets)
        assertTrue(stats.muscles(to = LocalDate.parse("2025-12-01")).isEmpty())
    }

    @Test fun planCoverageSkipsRestDaysAndDoesNotNeedActualRecords() {
        val plan = TrainingPlan(days = listOf(
            TrainingPlanDay(1, exercises = listOf(PlannedExercise(name = "卧推", sets = 4, muscles = listOf(chest, back)))),
            TrainingPlanDay(2, restDay = true, exercises = listOf(PlannedExercise(name = "忽略", sets = 9, muscles = listOf(chest)))),
            TrainingPlanDay(3, exercises = listOf(PlannedExercise(name = "卧推", muscles = listOf(chest)))),
        ))
        val loads = plannedMuscleWorkloads(plan)
        assertEquals(5L, loads.first { it.muscle.id == chest.id }.sets)
        assertEquals(4L, loads.first { it.muscle.id == back.id }.sets)
        assertTrue(plannedMuscleWorkloads(null).isEmpty())
    }

    @Test fun progressKeepsUnitsSeparateAndUsesDailyBestInChronologicalOrder() {
        val stats = WorkoutStatistics(listOf(
            session("2026-01-06", exercise(70.0), exercise(100.0, "lbs"), exercise(65.0)),
            session("2026-01-05", exercise(60.0)),
        ), today)
        assertEquals(listOf("kg", "lbs"), stats.units("卧推"))
        val points = stats.progress("卧推", "kg", ProgressMetric.VALUE)
        assertEquals(listOf(60.0, 70.0), points.map { it.value })
        assertEquals(listOf("2026-01-05", "2026-01-06"), points.map { it.date.toString() })
        assertEquals(100.0, stats.progress("卧推", "lbs", ProgressMetric.VALUE).single().value, 0.0)
    }

    @Test fun progressSupportsCoefficientsCountsSetsAndInclusiveRange() {
        val stats = WorkoutStatistics(listOf(
            session("2026-01-05", exercise(50.0, count = 8, sets = 2)),
            session("2026-01-06", exercise(60.0, count = 10, sets = null)),
        ), today)
        assertEquals(listOf(800.0, 600.0), stats.progress("卧推", "kg", ProgressMetric.COEFFICIENT).map { it.value })
        assertEquals(listOf(16.0, 10.0), stats.progress("卧推", "kg", ProgressMetric.TOTAL_COUNT).map { it.value })
        assertEquals(listOf(2.0, 1.0), stats.progress("卧推", "kg", ProgressMetric.SETS).map { it.value })
        assertEquals(1, stats.progress("卧推", "kg", ProgressMetric.VALUE, LocalDate.parse("2026-01-06")).size)
    }

    @Test fun malformedNumbersCannotPoisonChartsAndCountsDoNotOverflowInt() {
        val stats = WorkoutStatistics(listOf(session("2026-01-06",
            exercise(Double.NaN), exercise(Double.POSITIVE_INFINITY), exercise(-1.0),
            exercise(60.0, count = -10), exercise(60.0, sets = -2),
            exercise(1.0, count = 50000, sets = 50000),
        )), today)
        assertEquals(listOf(2_500_000_000.0), stats.progress("卧推", "kg", ProgressMetric.COEFFICIENT).map { it.value })
        assertEquals(2_500_000_000.0, stats.progress("卧推", "kg", ProgressMetric.TOTAL_COUNT).single().value, 0.0)
        assertTrue(stats.progress("卧推", "missing", ProgressMetric.VALUE).isEmpty())
    }

    @Test fun emptyDataProducesZeroOverviewAndNoInventedSeries() {
        val stats = WorkoutStatistics(emptyList(), today)
        assertEquals(StatisticsOverview(), stats.overview)
        assertTrue(stats.days.isEmpty())
        assertTrue(stats.muscles().isEmpty())
        assertTrue(stats.units("卧推").isEmpty())
        assertTrue(stats.activity(today.plusDays(1)).isEmpty())
    }
}
