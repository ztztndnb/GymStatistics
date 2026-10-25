package com.gymstatistics.data

import org.junit.Assert.*
import org.junit.Test

class WorkoutChangesTest {
    private val a = ExerciseRecord("A", id = "a", data = 10.0)
    private val b = ExerciseRecord("B", id = "b", data = 20.0)
    private val imported = ExerciseRecord("导入", id = "imported", data = 30.0)
    private fun workout(vararg exercises: ExerciseRecord) = WorkoutData(sessions = listOf(SessionRecord("day", "2026-10-25", exercises = exercises.toList())))

    @Test fun unrelatedWorkoutEditKeepsTheDurablySavedBodyRecord() {
        val body = BodyData(records = listOf(BodyDayRecord("2026-10-25", mapOf("weight" to 65.5))))
        val result = rebaseWorkoutChanges(workout(a), workout(a, b), workout(a).copy(bodyData = body))
        assertEquals(body, result.bodyData)
        assertEquals(listOf("a", "b"), result.sessions.single().exercises.map { it.id })
    }

    @Test fun laterAdditionDoesNotResurrectAnEarlierFailedAddition() {
        val result = rebaseWorkoutChanges(workout(a), workout(a, b), WorkoutData())
        assertEquals(listOf("b"), result.sessions.single().exercises.map { it.id })
    }

    @Test fun laterEditKeepsExercisesImportedWhileItWasWaiting() {
        val result = rebaseWorkoutChanges(workout(a), workout(a.copy(data = 12.0), b), workout(a, imported))
        assertEquals(listOf("a", "imported", "b"), result.sessions.single().exercises.map { it.id })
        assertEquals(12.0, result.sessions.single().exercises.first().data!!, 0.0)
    }

    @Test fun deletionAndReorderRemainScopedToTheirOriginalIds() {
        val result = rebaseWorkoutChanges(workout(a, b), workout(b), workout(a, b, imported))
        assertEquals(listOf("b", "imported"), result.sessions.single().exercises.map { it.id })
        val reordered = rebaseWorkoutChanges(workout(a, b), workout(b, a), workout(a, b, imported))
        assertEquals(listOf("b", "a", "imported"), reordered.sessions.single().exercises.map { it.id })
    }

    @Test fun deletingLastVisibleExercisePreservesAnExerciseImportedIntoThatDay() {
        val result = rebaseWorkoutChanges(workout(a), WorkoutData(), workout(a, imported))
        assertEquals(listOf("imported"), result.sessions.single().exercises.map { it.id })
    }

    @Test fun newExerciseMergesIntoASameDateSessionCreatedByImport() {
        val saved = workout(imported).let { it.copy(sessions = it.sessions.map { day -> day.copy(id = "imported-day") }) }
        val result = rebaseWorkoutChanges(WorkoutData(), workout(a), saved)
        assertEquals(1, result.sessions.size)
        assertEquals("imported-day", result.sessions.single().id)
        assertEquals(setOf("a", "imported"), result.sessions.single().exercises.map { it.id }.toSet())
    }

    @Test fun countOnlyEditDoesNotCarryTheEarlierFailedWeightEdit() {
        val result = rebaseWorkoutChanges(workout(a.copy(data = 12.0, count = 10)), workout(a.copy(data = 12.0, count = 15)), workout(a.copy(count = 10)))
        assertEquals(10.0, result.sessions.single().exercises.single().data!!, 0.0)
        assertEquals(15, result.sessions.single().exercises.single().count)
    }

    @Test fun restDayOnlyEditDoesNotCarryAnEarlierFailedPlanRename() {
        val original = TrainingPlan(name = "已保存名称")
        val failed = original.copy(name = "失败的名称")
        val after = failed.copy(days = failed.days.map { if (it.weekday == 1) it.copy(restDay = true) else it })
        val result = rebaseWorkoutChanges(WorkoutData(trainingPlan = failed), WorkoutData(trainingPlan = after), WorkoutData(trainingPlan = original))
        assertEquals("已保存名称", result.trainingPlan!!.name)
        assertTrue(result.trainingPlan!!.days.first().restDay)
    }

    @Test fun addingToADayCreatedByImportKeepsItsNote() {
        val stored = workout(imported).let { it.copy(sessions = it.sessions.map { day -> day.copy(id = "imported-day", note = "导入的备注") }) }
        val result = rebaseWorkoutChanges(WorkoutData(), workout(a), stored)
        assertEquals("导入的备注", result.sessions.single().note)
    }

    @Test fun firstRestDayEditDoesNotResetAnImportedPlan() {
        val stored = TrainingPlan(name = "导入的计划", days = TrainingPlan().days.map { if (it.weekday == 3) it.copy(restDay = true) else it })
        val new = TrainingPlan(days = TrainingPlan().days.map { if (it.weekday == 1) it.copy(restDay = true) else it })
        val result = rebaseWorkoutChanges(WorkoutData(), WorkoutData(trainingPlan = new), WorkoutData(trainingPlan = stored))
        assertEquals("导入的计划", result.trainingPlan!!.name)
        assertTrue(result.trainingPlan!!.days.first { it.weekday == 1 }.restDay)
        assertTrue(result.trainingPlan!!.days.first { it.weekday == 3 }.restDay)
    }

    @Test fun fieldEditDoesNotCarryAFailedSort() {
        val result = rebaseWorkoutChanges(workout(b, a), workout(b.copy(count = 15), a), workout(a, b))
        assertEquals(listOf("a", "b"), result.sessions.single().exercises.map { it.id })
        assertEquals(15, result.sessions.single().exercises.last().count)
    }
}
