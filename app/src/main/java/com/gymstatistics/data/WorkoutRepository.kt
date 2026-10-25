package com.gymstatistics.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import android.util.AtomicFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
}

/** Offline-first JSON persistence in the app's private files dir. */
class WorkoutRepository(private val context: Context) {
    private val mutex = Mutex()

    private val file: File
        get() = File(context.filesDir, "workouts.json")

    suspend fun load(): WorkoutData = withContext(Dispatchers.IO) { mutex.withLock {
        val atomic = AtomicFile(file)
        if (file.exists() || File(file.path + ".bak").exists()) {
            try {
                normalizeWorkoutIds(AppJson.json.decodeFromString(WorkoutData.serializer(), atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }))
            } catch (e: Exception) {
                WorkoutData()
            }
        } else {
            WorkoutData()
        }
    } }

    suspend fun save(data: WorkoutData) = withContext(Dispatchers.IO) { mutex.withLock {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(AppJson.json.encodeToString(WorkoutData.serializer(), data).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    } }
}
