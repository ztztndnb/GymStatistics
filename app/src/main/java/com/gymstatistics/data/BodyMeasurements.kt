package com.gymstatistics.data

import kotlinx.serialization.Serializable

@Serializable
data class BodyMetric(
    val id: String,
    val name: String,
    val unit: String,
    val enabled: Boolean = true,
)

@Serializable
data class BodyDayRecord(val date: String, val values: Map<String, Double>)

@Serializable
data class BodyData(
    val metrics: List<BodyMetric> = defaultBodyMetrics(),
    val records: List<BodyDayRecord> = emptyList(),
)

fun defaultBodyMetrics(): List<BodyMetric> = listOf(
    BodyMetric("weight", "体重", "kg"),
    BodyMetric("waist", "腰围", "cm"),
    BodyMetric("hip", "臀围", "cm"),
    BodyMetric("chest", "胸围", "cm"),
    BodyMetric("thigh", "大腿围", "cm"),
    BodyMetric("calf", "小腿围", "cm"),
    BodyMetric("arm", "上臂围", "cm"),
)

data class BodyImportResult(val data: BodyData, val counts: ImportResult)

data class BodyPoint(val date: java.time.LocalDate, val value: Double)
