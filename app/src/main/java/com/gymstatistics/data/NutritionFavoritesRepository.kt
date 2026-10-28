package com.gymstatistics.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

/** Product IDs only. Independent of workout backups and the current meal. */
internal class NutritionFavoritesRepository(context: Context) {
    private val file = File(context.filesDir, "nutrition-favorites.json")
    private companion object { val mutex = Mutex() }

    suspend fun load(): Set<String> = withContext(Dispatchers.IO) { mutex.withLock {
        if (!file.exists()) emptySet()
        else file.bufferedReader(Charsets.UTF_8).use {
            AppJson.json.decodeFromString<Set<String>>(it.readText())
        }
    } }

    suspend fun save(productIds: Set<String>): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        val temporary = File(file.path + ".new")
        try {
            temporary.outputStream().use { stream ->
                stream.write(AppJson.json.encodeToString(productIds).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Unlike AtomicFile.finishWrite, atomic move reports a failed commit to the caller.
            Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            true
        } catch (_: IOException) {
            if (temporary.isFile) temporary.delete()
            false
        }
    } }
}
