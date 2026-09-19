package com.gymstatistics.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingPlanTest {
    @Test
    fun oldWorkoutJsonDecodesWithoutPlanFields() {
        val data = AppJson.json.decodeFromString<WorkoutData>("{\"sessions\":[]}")

        assertNull(data.trainingPlan)
        assertNull(ExerciseRecord("卧推").plannedExerciseId)
    }

    @Test
    fun dateMapsToMondayThroughSundayPlanIndex() {
        assertEquals(1, planDayIndex(LocalDate.parse("2026-09-21")))
        assertEquals(7, planDayIndex(LocalDate.parse("2026-09-27")))
    }

    @Test
    fun completedPlanIdsComeFromLinkedExercisesOnTheSelectedDate() {
        val session = SessionRecord(
            id = "s1",
            date = "2026-09-21",
            exercises = listOf(ExerciseRecord("卧推", plannedExerciseId = "p1")),
        )

        assertTrue(completedPlanExerciseIds(session).contains("p1"))
        assertFalse(completedPlanExerciseIds(session).contains("p2"))
    }

    @Test
    fun copiedDayGetsIndependentPlanExerciseIds() {
        val source = TrainingPlanDay(
            weekday = 1,
            exercises = listOf(PlannedExercise(id = "p1", name = "卧推")),
        )

        val copied = copyPlanDay(source, targetWeekday = 4)

        assertEquals(4, copied.weekday)
        assertNotEquals("p1", copied.exercises.single().id)
    }

    @Test
    fun plannedRecordKeepsPlanIdAndUsesNewActualId() {
        val record = exerciseRecordForPlan(
            PlannedExercise(id = "p1", name = "卧推"),
            actualId = "actual-1",
        )

        assertEquals("p1", record.plannedExerciseId)
        assertEquals("actual-1", record.id)
    }

    @Test
    fun completingAlreadyLinkedPlanActionIsDetectedBeforeAppending() {
        val session = SessionRecord(
            "s1",
            "2026-09-21",
            exercises = listOf(ExerciseRecord("卧推", plannedExerciseId = "p1")),
        )

        assertTrue("p1" in completedPlanExerciseIds(session))
    }

    @Test
    fun deletingLinkedExerciseMakesPlanActionIncompleteAgain() {
        val session = SessionRecord(
            "s1",
            "2026-09-21",
            exercises = listOf(ExerciseRecord("卧推", plannedExerciseId = "p1")),
        )

        assertTrue("p1" in completedPlanExerciseIds(session))
        assertFalse("p1" in completedPlanExerciseIds(session.copy(exercises = emptyList())))
    }
}
