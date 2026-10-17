package com.gymstatistics.data

import org.junit.Assert.*
import org.junit.Test

class NutritionCalculatorTest {
    private val small = NutritionVariant("small", "小", perServing = ServingNutrition(877.0, 3.0, 9.0, 28.0, 120.0, 13.0))
    private val large = NutritionVariant("large", "大", perServing = ServingNutrition(1587.0, 6.0, 16.0, 50.0, 216.0, 23.0))
    private val catalog = NutritionCatalog(
        brandName = "麦当劳", sourceUrl = "https://www.mcdonalds.com.cn/nutrition_calculator",
        sourceUpdated = "2025-04", collectedAt = "2026-10-17T00:00:00Z", sourceSha256 = "a".repeat(64),
        categories = listOf(NutritionCategory("snacks", "小食", listOf("fries"))),
        products = listOf(NutritionProduct("fries", "薯条", variants = listOf(small, large))),
    )

    @Test fun differentSizesAndHalfPortionsProduceIndependentAccurateTotals() {
        val portions = listOf(NutritionPortion("fries", "small", 0.5), NutritionPortion("fries", "large", 1.0))
        val totals = NutritionCalculator.total(catalog, portions)
        assertEquals(1.5, totals.portions, 0.0)
        assertEquals(2, totals.itemCount)
        assertEquals(2025.5, totals.nutrition.energyKj!!, 0.0001)
        assertEquals(7.5, totals.nutrition.proteinG!!, 0.0001)
        assertEquals(20.5, totals.nutrition.fatG!!, 0.0001)
        assertEquals(64.0, totals.nutrition.carbohydrateG!!, 0.0001)
        assertEquals(276.0, totals.nutrition.sodiumMg!!, 0.0001)
        assertEquals(29.5, totals.nutrition.calciumMg!!, 0.0001)
        assertEquals(484.1061, totals.nutrition.energyKcal!!, 0.0001)
    }

    @Test fun missingNutrientsRemainUnknownEvenWhenOtherItemsHaveKnownValues() {
        val withUnknown = catalog.copy(products = listOf(catalog.products.single().copy(variants = listOf(
            small.copy(perServing = small.perServing.copy(sodiumMg = null)), large,
        ))))
        val totals = NutritionCalculator.total(withUnknown, listOf(NutritionPortion("fries", "small", 1.0), NutritionPortion("fries", "large", 1.0)))
        assertNull(totals.nutrition.sodiumMg)
        assertEquals(2464.0, totals.nutrition.energyKj!!, 0.0)
    }

    @Test fun quantityChangesAndRemovalOnlyAffectTheMatchingSize() {
        var portions = NutritionCalculator.setQuantity(emptyList(), "fries", "small", 1.0)
        portions = NutritionCalculator.setQuantity(portions, "fries", "large", 1.0)
        portions = NutritionCalculator.setQuantity(portions, "fries", "small", 0.5)
        assertEquals(listOf(0.5, 1.0), portions.map { it.quantity })
        portions = NutritionCalculator.setQuantity(portions, "fries", "small", 0.0)
        assertEquals("large", portions.single().variantId)
        assertEquals(0, NutritionCalculator.total(catalog, emptyList()).itemCount)
        assertEquals(0.0, NutritionCalculator.total(catalog, emptyList()).nutrition.energyKj!!, 0.0)
    }

    @Test fun invalidQuantitiesAndUnknownReferencesCannotPoisonTotals() {
        for (quantity in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, 100.0)) {
            assertThrows(IllegalArgumentException::class.java) { NutritionCalculator.setQuantity(emptyList(), "fries", "small", quantity) }
        }
        assertThrows(IllegalArgumentException::class.java) { NutritionCalculator.total(catalog, listOf(NutritionPortion("missing", "small", 1.0))) }
        assertThrows(IllegalArgumentException::class.java) { NutritionCalculator.total(catalog, listOf(NutritionPortion("fries", "missing", 1.0))) }
    }

    @Test fun catalogValidationRejectsBrokenReferencesAndNegativeValues() {
        assertEquals(catalog, catalog.validated())
        assertThrows(IllegalArgumentException::class.java) { catalog.copy(categories = listOf(NutritionCategory("x", "小食", listOf("missing")))).validated() }
        assertThrows(IllegalArgumentException::class.java) { catalog.copy(products = listOf(catalog.products.single().copy(variants = listOf(small.copy(perServing = ServingNutrition(-1.0)))))).validated() }
        assertThrows(IllegalArgumentException::class.java) { catalog.copy(products = emptyList()).validated() }
    }
}
