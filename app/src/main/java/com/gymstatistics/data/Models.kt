package com.gymstatistics.data

import kotlinx.serialization.Serializable

@Serializable
data class MuscleSelection(
    val id: String,
    val name: String,
)

/**
 * One exercise (movement) recorded as a single line:
 *   data(value) + optional unit + count(个数) + sets(组数).
 * Display: "60kg · 10 × 3组" (or "60kg · 10个" when sets <= 1).
 */
@Serializable
data class ExerciseRecord(
    val name: String,
    val id: String = "",    // 唯一 id,用于单条修改/删除
    val data: Double? = null, // 数据(数值)
    val unit: String = "",    // 单位(kg/lbs/自定义/空)
    val count: Int? = null,   // 个数(每组次数)
    val sets: Int? = null,    // 组数
    val note: String = "",    // 该动作的备注
    val muscles: List<MuscleSelection> = emptyList(),
)

/** A workout for a given day. */
@Serializable
data class SessionRecord(
    val id: String,
    val date: String, // ISO yyyy-MM-dd
    val note: String = "",
    val exercises: List<ExerciseRecord> = emptyList(),
)

/** On-device persisted structure. */
@Serializable
data class WorkoutData(
    val version: Int = 1,
    val sessions: List<SessionRecord> = emptyList(),
    /** Actions whose fatigue warning has been turned off. */
    val suppressedFatigue: Set<String> = emptySet(),
)

/** Payload served by the embedded sync server for the PC dashboard. */
@Serializable
data class SyncPayload(
    val exportedAt: String,
    val sessions: List<SessionRecord>,
)

/** How an import is scoped. */
sealed interface ImportMode {
    data object ALL : ImportMode
    data class DATE(val date: String) : ImportMode
}

/** Result of an import. */
data class ImportResult(
    val added: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val overwritten: Int = 0,
)

/** Body of a POST /api/import request. */
@Serializable
data class ImportRequest(
    val mode: String = "all",            // "all" | "date"
    val date: String? = null,
    val overwriteDuplicates: Boolean = false,
    val sessions: List<SessionRecord> = emptyList(),
)

/** Response of a POST /api/import. */
@Serializable
data class ImportResponse(
    val added: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val overwritten: Int = 0,
)
