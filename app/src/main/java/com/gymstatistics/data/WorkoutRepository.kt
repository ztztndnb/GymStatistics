package com.gymstatistics.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

object AppJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
}

/** Offline-first JSON persistence in the app's private files dir. */
class WorkoutRepository(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "workouts.json")

    suspend fun load(): WorkoutData = withContext(Dispatchers.IO) {
        if (file.exists()) {
            try {
                AppJson.json.decodeFromString(WorkoutData.serializer(), file.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                WorkoutData()
            }
        } else {
            WorkoutData()
        }
    }

    suspend fun save(data: WorkoutData) = withContext(Dispatchers.IO) {
        file.writeText(AppJson.json.encodeToString(WorkoutData.serializer(), data), Charsets.UTF_8)
    }
}
