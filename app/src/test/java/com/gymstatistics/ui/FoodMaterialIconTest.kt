package com.gymstatistics.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import org.junit.Assert.assertSame
import org.junit.Test

class FoodMaterialIconTest {
    @Test
    fun cameraCornerActionsUseTheMatchingRoundedMaterialIcons() {
        assertSame(Icons.Rounded.PhotoLibrary, CameraActionIcon.GALLERY.imageVector)
        assertSame(Icons.Rounded.History, CameraActionIcon.HISTORY.imageVector)
    }
}
