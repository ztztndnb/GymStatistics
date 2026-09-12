package com.gymstatistics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FoodAnalysisEditorStateTest {
    @Test
    fun replacingOneIngredientKeepsOtherIngredientsUnchanged() {
        val first = FoodAnalysisItem(name = "米饭", weightG = 100.0)
        val second = FoodAnalysisItem(name = "鸡蛋", weightG = 50.0)
        val record = FoodAnalysisRecord(id = "record", items = listOf(first, second))

        val changed = replaceFoodAnalysisItem(record, 1, second.copy(weightG = 80.0))

        assertEquals(100.0, changed.items[0].weightG ?: -1.0, 0.0)
        assertEquals(80.0, changed.items[1].weightG ?: -1.0, 0.0)
        assertNotEquals(record.items, changed.items)
    }
}
