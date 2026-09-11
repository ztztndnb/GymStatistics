package com.gymstatistics.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.gymstatistics.data.AppJson
import com.gymstatistics.data.FoodAnalysisItem
import com.gymstatistics.data.FoodAnalysisRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekFoodAnalyzer(private val context: Context) {

    companion object {
        const val MODEL = "deepseek-flash"
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val MAX_IMAGE_SIDE = 1600
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 90_000
    }

    suspend fun analyze(uri: Uri, apiKey: String): FoodAnalysisRecord = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "请先配置 DeepSeek API Key" }
        val dataUri = imageDataUri(uri)
        val request = buildJsonObject {
            put("model", MODEL)
            put("temperature", 0.1)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", FoodAnalysisPrompt.system)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "请分析这张食品图片，严格按照系统要求返回 JSON。")
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", dataUri) })
                        })
                    })
                })
            })
        }

        val responseText = post(request.toString(), apiKey)
        val content = AppJson.json.parseToJsonElement(responseText).jsonObject
            .getValue("choices").jsonArray.first()
            .jsonObject.getValue("message").jsonObject
            .getValue("content").jsonPrimitive.content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val result = AppJson.json.decodeFromString(FoodAnalysisRecord.serializer(), content)
            .copy(id = UUID.randomUUID().toString(), createdAt = Instant.now().toString())
        validate(result)
        result
    }

    private fun post(body: String, apiKey: String): String {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IOException("DeepSeek 请求失败（HTTP ${connection.responseCode}）")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun imageDataUri(uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法读取图片")
        val sample = calculateSample(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("无法读取图片")
        val scaled = scale(bitmap)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return "data:image/jpeg;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}"
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_IMAGE_SIDE * 2 || height / sample > MAX_IMAGE_SIDE * 2) sample *= 2
        return sample
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_IMAGE_SIDE) return bitmap
        val ratio = MAX_IMAGE_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun validate(result: FoodAnalysisRecord) {
        if (result.items.isEmpty()) throw IOException("模型没有返回可编辑的食物项目")
        result.items.forEach { item: FoodAnalysisItem ->
            if (item.name.isBlank()) throw IOException("模型返回了空的食物名称")
            if (item.weightG != null && item.weightG < 0) throw IOException("模型返回了无效重量")
            val nutrients = listOf(
                item.nutritionPer100g.energyKcal,
                item.nutritionPer100g.energyKj,
                item.nutritionPer100g.proteinG,
                item.nutritionPer100g.fatG,
                item.nutritionPer100g.carbohydrateG,
                item.nutritionPer100g.fiberG,
                item.nutritionPer100g.sugarsG,
                item.nutritionPer100g.sodiumMg,
                item.nutritionPer100g.cholesterolMg,
            )
            if (nutrients.any { it != null && (it < 0 || !it.isFinite()) }) {
                throw IOException("模型返回了无效营养数据")
            }
        }
    }
}
