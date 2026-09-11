package com.gymstatistics.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodAnalysisCalculatorTest {

    @Test
    fun itemNutritionScalesFromPer100gToEditedWeight() {
        val item = FoodAnalysisItem(
            name = "鸡胸肉",
            weightG = 150.0,
            nutritionPer100g = NutritionPer100g(
                energyKcal = 165.0,
                energyKj = 690.0,
                proteinG = 31.0,
                fatG = 3.6,
                carbohydrateG = 0.0,
                fiberG = 0.0,
                sugarsG = 0.0,
                sodiumMg = 74.0,
                cholesterolMg = 85.0,
            ),
        )

        val actual = FoodAnalysisCalculator.itemTotals(item)

        assertEquals(247.5, actual.energyKcal!!, 0.0001)
        assertEquals(46.5, actual.proteinG!!, 0.0001)
    }

    @Test
    fun totalWeightAndNutritionUseOnlyKnownItems() {
        val record = FoodAnalysisRecord(
            id = "1",
            createdAt = "now",
            foodName = "餐盘",
            imageType = "meal_photo",
            items = listOf(
                FoodAnalysisItem(
                    name = "米饭",
                    weightG = 100.0,
                    nutritionPer100g = NutritionPer100g(
                        energyKcal = 116.0,
                        energyKj = 0.0,
                        proteinG = 2.6,
                        fatG = 0.3,
                        carbohydrateG = 25.9,
                        fiberG = 0.0,
                        sugarsG = 0.0,
                        sodiumMg = 0.0,
                        cholesterolMg = 0.0,
                    ),
                ),
                FoodAnalysisItem(
                    name = "未知配料",
                    weightG = null,
                    nutritionPer100g = NutritionPer100g(
                        energyKcal = 50.0,
                        energyKj = 0.0,
                        proteinG = 1.0,
                        fatG = 1.0,
                        carbohydrateG = 2.0,
                        fiberG = 0.0,
                        sugarsG = 0.0,
                        sodiumMg = 0.0,
                        cholesterolMg = 0.0,
                    ),
                ),
            ),
        )

        val total = FoodAnalysisCalculator.total(record)

        assertEquals(100.0, total.weightG, 0.0001)
        assertEquals(116.0, total.nutrition.energyKcal!!, 0.0001)
    }
}
