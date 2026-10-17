package com.gymstatistics.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled snapshot only: no network, keys, cookies, or changes to existing histories. */
internal class NutritionCatalogRepository(private val context: Context) {
    suspend fun load(): NutritionCatalog = withContext(Dispatchers.IO) {
        val text = context.assets.open("nutrition/mcdonalds-cn.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        decodeNutritionCatalog(text)
    }
}

internal fun decodeNutritionCatalog(text: String): NutritionCatalog =
    AppJson.json.decodeFromString<NutritionCatalog>(text).validated()
