package com.gymstatistics.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class NutritionPer100g(
    @SerialName("energy_kcal")
    val energyKcal: Double? = null,
    @SerialName("energy_kj")
    val energyKj: Double? = null,
    @SerialName("protein_g")
    val proteinG: Double? = null,
    @SerialName("fat_g")
    val fatG: Double? = null,
    @SerialName("carbohydrate_g")
    val carbohydrateG: Double? = null,
    @SerialName("fiber_g")
    val fiberG: Double? = null,
    @SerialName("sugars_g")
    val sugarsG: Double? = null,
    @SerialName("sodium_mg")
    val sodiumMg: Double? = null,
    @SerialName("cholesterol_mg")
    val cholesterolMg: Double? = null,
)

@Serializable
data class LabelInfo(
    @SerialName("original_basis")
    val originalBasis: String = "unknown",
    @SerialName("serving_size_g")
    val servingSizeG: Double? = null,
    @SerialName("net_weight_g")
    val netWeightG: Double? = null,
    @SerialName("conversion_note")
    val conversionNote: String = "",
)

@Serializable
data class FoodAnalysisItem(
    val name: String,
    @SerialName("weight_g")
    val weightG: Double? = null,
    @SerialName("weight_source")
    val weightSource: String = "unknown",
    @SerialName("nutrition_per_100g")
    val nutritionPer100g: NutritionPer100g = NutritionPer100g(),
    @SerialName("nutrition_source")
    val nutritionSource: String = "unknown",
    val confidence: String = "low",
    @SerialName("is_aggregate")
    val isAggregate: Boolean = false,
    val note: String = "",
)

@Serializable
data class FoodAnalysisRecord(
    val id: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("food_name")
    val foodName: String = "未命名食物",
    @SerialName("image_type")
    val imageType: String = "unknown",
    @SerialName("ingredients_text")
    val ingredientsText: String = "",
    @SerialName("total_weight_g")
    val totalWeightG: Double? = null,
    @SerialName("total_weight_source")
    val totalWeightSource: String = "unknown",
    @SerialName("label_info")
    val labelInfo: LabelInfo = LabelInfo(),
    val items: List<FoodAnalysisItem> = emptyList(),
    @SerialName("uncertainty_note")
    val uncertaintyNote: String = "",
)

data class FoodTotals(
    val weightG: Double,
    val nutrition: NutritionPer100g,
)
