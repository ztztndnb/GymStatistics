package com.gymstatistics.data

import org.junit.Assert.*
import org.junit.Test

class LaoxiangjiNutritionTest {
    private val food = LaoxiangjiFood("lxj-2026-001", 1, "菜品", "菜", 100, 121.0, 8.0, 4.0, 10.0, 927.0)

    @Test fun scalesByActualWeight() {
        val totals = laoxiangjiTotals(listOf(LaoxiangjiPortion(food, 150.0)))
        assertEquals(181.5, totals.energyKcal!!, 0.001)
        assertEquals(1390.5, totals.sodiumMg!!, 0.001)
    }

    @Test fun unknownIsNotCountedAsZero() {
        val totals = laoxiangjiTotals(listOf(LaoxiangjiPortion(food, 100.0), LaoxiangjiPortion(food.copy(sodiumMg = null), 100.0)))
        assertNull(totals.sodiumMg)
        assertEquals(242.0, totals.energyKcal!!, 0.001)
    }

    @Test fun invalidWeightsAreRejected() {
        for (weight in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 100001.0)) {
            assertThrows(IllegalArgumentException::class.java) { laoxiangjiTotals(listOf(LaoxiangjiPortion(food, weight))) }
        }
    }
}
