package com.gymstatistics.data

internal object NutritionCalculator {
    const val MAX_QUANTITY = 99.0

    fun setQuantity(portions: List<NutritionPortion>, productId: String, variantId: String, quantity: Double): List<NutritionPortion> {
        require(quantity.isFinite() && quantity in 0.0..MAX_QUANTITY)
        require(productId.isNotBlank() && variantId.isNotBlank())
        val matches: (NutritionPortion) -> Boolean = { it.productId == productId && it.variantId == variantId }
        if (quantity == 0.0) return portions.filterNot(matches)
        val updated = NutritionPortion(productId, variantId, quantity)
        return if (portions.any(matches)) portions.map { if (matches(it)) updated else it } else portions + updated
    }

    fun total(catalog: NutritionCatalog, portions: List<NutritionPortion>): NutritionTotals {
        require(portions.map { it.productId to it.variantId }.distinct().size == portions.size)
        val items = portions.map { portion ->
            require(portion.quantity.isFinite() && portion.quantity > 0 && portion.quantity <= MAX_QUANTITY)
            val product = requireNotNull(catalog.products.firstOrNull { it.id == portion.productId })
            val variant = requireNotNull(product.variants.firstOrNull { it.id == portion.variantId })
            variant.perServing to portion.quantity
        }
        // If any selected serving lacks a field, its total is unknown, not a partial sum.
        fun sum(field: (ServingNutrition) -> Double?): Double? {
            if (items.any { field(it.first) == null }) return null
            val value = items.sumOf { field(it.first)!! * it.second }
            return value.takeIf { it.isFinite() }
        }
        return NutritionTotals(portions.size, portions.sumOf { it.quantity }, ServingNutrition(
            sum { it.energyKj }, sum { it.proteinG }, sum { it.fatG },
            sum { it.carbohydrateG }, sum { it.sodiumMg }, sum { it.calciumMg },
        ))
    }
}
