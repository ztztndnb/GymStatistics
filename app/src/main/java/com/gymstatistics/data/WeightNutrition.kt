package com.gymstatistics.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WeightNutritionFood(
    val id: String,
    @SerialName("source_row") val sourceRow: Int,
    val category: String,
    val name: String,
    @SerialName("basis_g") val basisG: Int,
    @SerialName("energy_kcal") val energyKcal: Double?,
    @SerialName("protein_g") val proteinG: Double?,
    @SerialName("fat_g") val fatG: Double?,
    @SerialName("carbohydrate_g") val carbohydrateG: Double?,
    @SerialName("sodium_mg") val sodiumMg: Double?,
    val flags: List<String> = emptyList(),
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
internal data class WeightNutritionCatalog(val products: List<WeightNutritionFood>) {
    fun validated(lawson: Boolean = false): WeightNutritionCatalog {
        require(products.isNotEmpty() && products.map { it.id }.distinct().size == products.size)
        products.forEach { p ->
            val validId = if (lawson) p.id.matches(Regex("lawson-cn-search-[0-9a-f]{16}")) else p.id == "lxj-2026-${p.sourceRow.toString().padStart(3, '0')}"
            require(validId && p.sourceRow > 0 && p.basisG == 100 && p.name.isNotBlank() && p.category.isNotBlank())
            if (lawson) require(p.sourceUrl?.let { runCatching { java.net.URI(it).let { uri -> uri.scheme == "https" && uri.host == "www.fatsecret.cn" } }.getOrDefault(false) } == true)
            require(listOf(p.energyKcal, p.proteinG, p.fatG, p.carbohydrateG, p.sodiumMg).all { it == null || (it.isFinite() && it >= 0) })
        }
        return this
    }
}

internal data class WeightNutritionPortion(val food: WeightNutritionFood, val grams: Double)
internal data class WeightNutritionTotals(val energyKcal: Double?, val proteinG: Double?, val fatG: Double?, val carbohydrateG: Double?, val sodiumMg: Double?)

internal fun weightNutritionTotals(items: List<WeightNutritionPortion>): WeightNutritionTotals {
    require(items.all { it.grams.isFinite() && it.grams > 0 && it.grams <= 100_000 })
    fun sum(field: (WeightNutritionFood) -> Double?): Double? {
        if (items.any { field(it.food) == null }) return null
        return items.sumOf { field(it.food)!! * it.grams / 100.0 }.takeIf { it.isFinite() }
    }
    return WeightNutritionTotals(sum { it.energyKcal }, sum { it.proteinG }, sum { it.fatG }, sum { it.carbohydrateG }, sum { it.sodiumMg })
}
