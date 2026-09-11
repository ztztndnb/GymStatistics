package com.gymstatistics.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraImageTransformTest {
    @Test
    fun portraitPreviewFillsHeightAndCropsTheSides() {
        val scale = calculatePreviewScale(
            viewWidth = 1080,
            viewHeight = 2376,
            bufferWidth = 4000,
            bufferHeight = 3000,
        )

        assertEquals(1.65f, scale.x, 0.0001f)
        assertEquals(1f, scale.y, 0.0001f)
    }

    @Test
    fun landscapePreviewFillsWidthAndCropsTopAndBottom() {
        val scale = calculatePreviewScale(
            viewWidth = 2400,
            viewHeight = 1080,
            bufferWidth = 1920,
            bufferHeight = 1080,
        )

        assertEquals(1f, scale.x, 0.0001f)
        assertEquals(1.25f, scale.y, 0.0001f)
    }

    @Test
    fun photoRotationDirectionsUseOppositeQuarterTurns() {
        assertEquals(-90f, FoodPhotoRotation.LEFT.degrees, 0f)
        assertEquals(90f, FoodPhotoRotation.RIGHT.degrees, 0f)
    }
}
