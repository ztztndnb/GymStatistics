package com.gymstatistics.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

class FoodAnalysisRepository(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "food-analysis-history.json")

    suspend fun load(): List<FoodAnalysisRecord> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            AppJson.json.decodeFromString(ListSerializer(FoodAnalysisRecord.serializer()), file.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    suspend fun saveAll(records: List<FoodAnalysisRecord>) = withContext(Dispatchers.IO) {
        file.writeText(
            AppJson.json.encodeToString(ListSerializer(FoodAnalysisRecord.serializer()), records),
            Charsets.UTF_8,
        )
    }
}
