package com.gymstatistics.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodAnalysisImageStoreTest {
    @Test
    fun scaledImageSizeLimitsTheLongestEdgeAndPreservesAspectRatio() {
        assertEquals(FoodImageSize(2048, 1536), scaledImageSize(4000, 3000))
        assertEquals(FoodImageSize(1000, 750), scaledImageSize(1000, 750))
    }

    @Test
    fun oldFoodAnalysisJsonUsesAnEmptyImageFileName() {
        val record = AppJson.json.decodeFromString<FoodAnalysisRecord>("{\"id\":\"old\"}")

        assertEquals("", record.imageFileName)
    }

    @Test
    fun imageFileNameUsesTheRecordIdAndJpegExtension() {
        assertEquals("record-42.jpg", foodImageFileName("record-42"))
        assertEquals("", foodImageFileName(""))
    }
}
