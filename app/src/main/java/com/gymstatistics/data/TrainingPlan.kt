package com.gymstatistics.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class TrainingPlan(
    val name: String = "我的训练计划",
    val days: List<TrainingPlanDay> = (1..7).map { TrainingPlanDay(it) },
)

@Serializable
data class TrainingPlanDay(
    val weekday: Int,
    val restDay: Boolean = false,
    val exercises: List<PlannedExercise> = emptyList(),
)

@Serializable
data class PlannedExercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val data: Double? = null,
    val unit: String = "",
    val count: Int? = null,
    val sets: Int? = null,
    val note: String = "",
    val muscles: List<MuscleSelection> = emptyList(),
)

/** Monday is 1 and Sunday is 7, independent of the app's date-bar ordering. */
fun planDayIndex(date: LocalDate): Int = date.dayOfWeek.value

fun completedPlanExerciseIds(session: SessionRecord?): Set<String> =
    session?.exercises?.mapNotNull { it.plannedExerciseId }?.toSet().orEmpty()

fun exerciseRecordForPlan(planned: PlannedExercise, actualId: String): ExerciseRecord =
    ExerciseRecord(
        name = planned.name,
        id = actualId,
        data = planned.data,
        unit = planned.unit,
        count = planned.count,
        sets = planned.sets,
        note = planned.note,
        muscles = planned.muscles,
        plannedExerciseId = planned.id,
    )

fun copyPlanDay(source: TrainingPlanDay, targetWeekday: Int): TrainingPlanDay =
    source.copy(
        weekday = targetWeekday,
        exercises = source.exercises.map { it.copy(id = UUID.randomUUID().toString()) },
    )

fun latestExerciseForName(sessions: List<SessionRecord>, name: String): ExerciseRecord? =
    sessions
        .asSequence()
        .sortedByDescending { it.date }
        .flatMap { it.exercises.asSequence() }
        .firstOrNull { it.name == name }
