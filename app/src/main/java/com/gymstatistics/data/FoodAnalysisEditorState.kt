package com.gymstatistics.data

internal fun replaceFoodAnalysisItem(
    record: FoodAnalysisRecord,
    index: Int,
    changed: FoodAnalysisItem,
): FoodAnalysisRecord {
    if (index !in record.items.indices) return record
    return record.copy(items = record.items.toMutableList().also { it[index] = changed })
}
