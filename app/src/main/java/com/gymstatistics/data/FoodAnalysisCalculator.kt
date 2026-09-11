package com.gymstatistics.data

object FoodAnalysisCalculator {

    fun itemTotals(item: FoodAnalysisItem): NutritionPer100g {
        val factor = item.weightG?.takeIf { it >= 0 }?.div(100.0) ?: return NutritionPer100g()
        return item.nutritionPer100g.scale(factor)
    }

    fun total(record: FoodAnalysisRecord): FoodTotals {
        val knownItems = record.items.filter { it.weightG != null && it.weightG >= 0 }
        return FoodTotals(
            weightG = knownItems.sumOf { it.weightG ?: 0.0 },
            nutrition = knownItems.map { itemTotals(it) }.reduceOrNull { left, right -> left + right }
                ?: NutritionPer100g(),
        )
    }

    private fun NutritionPer100g.scale(factor: Double): NutritionPer100g = NutritionPer100g(
        energyKcal = energyKcal?.times(factor),
        energyKj = energyKj?.times(factor),
        proteinG = proteinG?.times(factor),
        fatG = fatG?.times(factor),
        carbohydrateG = carbohydrateG?.times(factor),
        fiberG = fiberG?.times(factor),
        sugarsG = sugarsG?.times(factor),
        sodiumMg = sodiumMg?.times(factor),
        cholesterolMg = cholesterolMg?.times(factor),
    )

    private operator fun NutritionPer100g.plus(other: NutritionPer100g): NutritionPer100g = NutritionPer100g(
        energyKcal = addKnown(energyKcal, other.energyKcal),
        energyKj = addKnown(energyKj, other.energyKj),
        proteinG = addKnown(proteinG, other.proteinG),
        fatG = addKnown(fatG, other.fatG),
        carbohydrateG = addKnown(carbohydrateG, other.carbohydrateG),
        fiberG = addKnown(fiberG, other.fiberG),
        sugarsG = addKnown(sugarsG, other.sugarsG),
        sodiumMg = addKnown(sodiumMg, other.sodiumMg),
        cholesterolMg = addKnown(cholesterolMg, other.cholesterolMg),
    )

    private fun addKnown(left: Double?, right: Double?): Double? = when {
        left != null && right != null -> left + right
        left != null -> left
        else -> right
    }
}
