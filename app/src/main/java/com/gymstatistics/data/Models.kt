package com.gymstatistics.data

import kotlinx.serialization.Serializable

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
