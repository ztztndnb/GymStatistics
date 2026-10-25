package com.gymstatistics.data

import java.util.UUID

/** Stable IDs are required before UI edits can be rebased. Existing valid IDs and values are retained. */
fun normalizeWorkoutIds(data: WorkoutData): WorkoutData {
    val sessions = mutableSetOf<String>()
    val exercises = mutableSetOf<String>()
    fun unique(id: String, used: MutableSet<String>): String {
        if (id.isNotBlank() && used.add(id)) return id
        var generated: String
        do { generated = UUID.randomUUID().toString() } while (!used.add(generated))
        return generated
    }
    return data.copy(sessions = data.sessions.map { session ->
        session.copy(id = unique(session.id, sessions), exercises = session.exercises.map { exercise ->
            exercise.copy(id = unique(exercise.id, exercises))
        })
    })
}

/** Replay only this edit, without carrying a previous failed edit into a later save. */
fun rebaseWorkoutChanges(before: WorkoutData, after: WorkoutData, current: WorkoutData): WorkoutData = current.copy(
    version = if (before.version == after.version) current.version else after.version,
    sessions = rebaseSessions(before.sessions, after.sessions, current.sessions),
    suppressedFatigue = (current.suppressedFatigue - (before.suppressedFatigue - after.suppressedFatigue)) + (after.suppressedFatigue - before.suppressedFatigue),
    trainingPlan = rebasePlan(before.trainingPlan, after.trainingPlan, current.trainingPlan),
    bodyData = if (before.bodyData == after.bodyData) current.bodyData else after.bodyData,
)

private fun rebaseSessions(before: List<SessionRecord>, after: List<SessionRecord>, current: List<SessionRecord>): List<SessionRecord> {
    if (before == after) return current
    fun byDate(records: List<SessionRecord>) = records.groupBy { it.date }.mapValues { (_, days) ->
        days.first().copy(note = days.map { it.note }.filter { it.isNotBlank() }.distinct().joinToString("\n"), exercises = days.flatMap { it.exercises })
    }
    val old = byDate(before)
    val desired = byDate(after)
    val stored = byDate(current).toMutableMap()
    (old.keys + desired.keys).forEach { date ->
        val previous = old[date]
        val next = desired[date]
        if (previous == next) return@forEach
        if (next == null) {
            val base = stored[date] ?: return@forEach
            val remaining = rebaseList(previous?.exercises.orEmpty(), emptyList(), base.exercises, { it.id }, ::rebaseExercise)
            if (remaining.isEmpty()) stored.remove(date) else stored[date] = base.copy(exercises = remaining)
        } else {
            val base = stored[date] ?: SessionRecord(next.id, date)
            stored[date] = base.copy(
                note = changed(previous?.note ?: "", next.note, base.note),
                exercises = rebaseList(previous?.exercises.orEmpty(), next.exercises, base.exercises, { it.id }, ::rebaseExercise),
            )
        }
    }
    return desired.keys.mapNotNull { stored[it] } + stored.filterKeys { it !in desired }.values
}

private fun rebaseExercise(old: ExerciseRecord?, new: ExerciseRecord, stored: ExerciseRecord?): ExerciseRecord {
    val base = stored ?: ExerciseRecord(new.name, id = new.id)
    return base.copy(
        name = changed(old?.name, new.name, base.name),
        data = changed(old?.data, new.data, base.data),
        unit = changed(old?.unit, new.unit, base.unit),
        count = changed(old?.count, new.count, base.count),
        sets = changed(old?.sets, new.sets, base.sets),
        note = changed(old?.note, new.note, base.note),
        muscles = changed(old?.muscles, new.muscles, base.muscles),
        plannedExerciseId = changed(old?.plannedExerciseId, new.plannedExerciseId, base.plannedExerciseId),
    )
}

private fun rebasePlan(old: TrainingPlan?, new: TrainingPlan?, stored: TrainingPlan?): TrainingPlan? {
    if (old == new) return stored
    if (new == null) return null
    val previousPlan = old ?: TrainingPlan()
    val base = stored ?: TrainingPlan()
    return base.copy(
        name = changed(previousPlan.name, new.name, base.name),
        days = rebaseList(previousPlan.days, new.days, base.days, { it.weekday.toString() }) { previous, next, saved ->
            val day = saved ?: TrainingPlanDay(next.weekday)
            day.copy(
                restDay = changed(previous?.restDay, next.restDay, day.restDay),
                exercises = rebaseList(previous?.exercises.orEmpty(), next.exercises, day.exercises, { it.id }) { prior, planned, recorded ->
                    val exercise = recorded ?: PlannedExercise(id = planned.id, name = planned.name)
                    exercise.copy(
                        name = changed(prior?.name, planned.name, exercise.name),
                        data = changed(prior?.data, planned.data, exercise.data),
                        unit = changed(prior?.unit, planned.unit, exercise.unit),
                        count = changed(prior?.count, planned.count, exercise.count),
                        sets = changed(prior?.sets, planned.sets, exercise.sets),
                        note = changed(prior?.note, planned.note, exercise.note),
                        muscles = changed(prior?.muscles, planned.muscles, exercise.muscles),
                    )
                },
            )
        },
    )
}

private fun <T> changed(old: T?, new: T, stored: T): T = if (old == new) stored else new

private fun <T> rebaseList(before: List<T>, after: List<T>, current: List<T>, key: (T) -> String, update: (T?, T, T?) -> T): List<T> {
    if (before == after) return current
    // Old records without unique IDs can still rebase field edits when their positions are stable.
    if (listOf(before, after, current).any { list -> list.any { key(it).isBlank() } || list.map(key).distinct().size != list.size }) {
        return if (before.size == after.size && current.size == after.size) after.mapIndexed { index, item -> update(before[index], item, current[index]) } else after
    }
    val old = before.associateBy(key)
    val desired = after.associateBy(key)
    val stored = current.associateBy(key).toMutableMap()
    (old.keys - desired.keys).forEach { stored.remove(it) }
    after.forEach { item ->
        val id = key(item)
        if (old[id] != item) stored[id] = update(old[id], item, stored[id])
    }
    val common = old.keys.intersect(desired.keys)
    val reordered = before.map(key).filter { it in common } != after.map(key).filter { it in common }
    return if (reordered) {
        after.mapNotNull { stored[key(it)] } + current.mapNotNull { item -> stored[key(item)]?.takeIf { key(item) !in desired } }
    } else {
        current.mapNotNull { stored[key(it)] } + after.mapNotNull { item -> stored[key(item)]?.takeIf { key(item) !in current.map(key).toSet() } }
    }
}
