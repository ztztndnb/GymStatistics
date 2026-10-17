package com.gymstatistics.data

import java.time.Instant
import java.time.YearMonth
import kotlinx.serialization.Serializable

/** Official values for ONE specified serving, never coefficients per 100 g. */
@Serializable
internal data class ServingNutrition(
    val energyKj: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbohydrateG: Double? = null,
    val sodiumMg: Double? = null,
    val calciumMg: Double? = null,
) {
    val energyKcal: Double? get() = energyKj?.div(4.184)
}

@Serializable
internal data class NutritionVariant(val id: String, val name: String, val note: String = "", val perServing: ServingNutrition)

@Serializable
internal data class NutritionProduct(val id: String, val name: String, val note: String = "", val variants: List<NutritionVariant>)

@Serializable
internal data class NutritionCategory(val id: String, val name: String, val productIds: List<String>)

@Serializable
internal data class NutritionCatalog(
    val schemaVersion: Int = 1,
    val brandName: String,
    val sourceUrl: String,
    val sourceUpdated: String,
    val collectedAt: String,
    val sourceSha256: String,
    val disclaimer: String = "",
    val categories: List<NutritionCategory>,
    val products: List<NutritionProduct>,
) {
    fun validated(): NutritionCatalog {
        require(schemaVersion == 1 && brandName.isNotBlank())
        require(sourceUrl == "https://www.mcdonalds.com.cn/nutrition_calculator")
        YearMonth.parse(sourceUpdated)
        Instant.parse(collectedAt)
        require(sourceSha256.matches(Regex("[a-f0-9]{64}")))
        require(categories.isNotEmpty() && products.isNotEmpty())
        require(categories.map { it.id }.distinct().size == categories.size)
        require(products.map { it.id }.distinct().size == products.size)
        val productIds = products.map { it.id }.toSet()
        categories.forEach { category ->
            require(category.id.isNotBlank() && category.name.isNotBlank() && category.productIds.isNotEmpty())
            require(category.productIds.distinct().size == category.productIds.size && category.productIds.all { it in productIds })
        }
        val categorized = categories.flatMap { it.productIds }.toSet()
        require(productIds.all { it in categorized })
        products.forEach { product ->
            require(product.id.isNotBlank() && product.name.isNotBlank() && product.variants.isNotEmpty())
            require(product.variants.map { it.id }.distinct().size == product.variants.size)
            product.variants.forEach { variant ->
                require(variant.id.isNotBlank() && variant.name.isNotBlank())
                with(variant.perServing) {
                    require(listOf(energyKj, proteinG, fatG, carbohydrateG, sodiumMg, calciumMg).all { it == null || (it.isFinite() && it >= 0) })
                }
            }
        }
        return this
    }
}

@Serializable
internal data class NutritionPortion(val productId: String, val variantId: String, val quantity: Double)

internal data class NutritionTotals(val itemCount: Int, val portions: Double, val nutrition: ServingNutrition)
