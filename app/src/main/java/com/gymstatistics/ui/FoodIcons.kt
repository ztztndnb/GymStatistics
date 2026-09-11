package com.gymstatistics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

@Composable
fun FoodCameraIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        withTransform({ scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) }) {
            val stroke = Stroke(width = 2f)
            drawRoundRect(tint, Offset(2f, 7f), Size(20f, 14f), CornerRadius(3f), style = stroke)
            drawRoundRect(tint, Offset(8f, 4f), Size(6f, 4f), CornerRadius(1f), style = stroke)
            drawCircle(tint, 4f, Offset(12f, 14f), style = stroke)
        }
    }
}

@Composable
fun FoodKeyIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        withTransform({ scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) }) {
            val stroke = Stroke(width = 2f)
            drawCircle(tint, 4f, Offset(7f, 9f), style = stroke)
            drawLine(tint, Offset(10f, 12f), Offset(20f, 22f), strokeWidth = 2f)
            drawLine(tint, Offset(16f, 18f), Offset(19f, 15f), strokeWidth = 2f)
            drawLine(tint, Offset(18f, 20f), Offset(21f, 17f), strokeWidth = 2f)
        }
    }
}

@Composable
fun FoodPhotoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val unit = size.minDimension / 28f
        withTransform({ scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) }) {
            val stroke = Stroke(width = 2f)
            drawRoundRect(tint, Offset(3f, 5f), Size(22f, 18f), CornerRadius(2f), style = stroke)
            drawCircle(tint, 2f, Offset(9f, 11f), style = stroke)
            drawPath(
                Path().apply {
                    moveTo(5f, 20f)
                    lineTo(11f, 14f)
                    lineTo(15f, 18f)
                    lineTo(18f, 15f)
                    lineTo(23f, 20f)
                },
                color = tint,
                style = stroke,
            )
        }
    }
}

@Composable
fun FoodHistoryIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val unit = size.minDimension / 28f
        withTransform({ scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) }) {
            val stroke = Stroke(width = 2f)
            drawArc(tint, -60f, 300f, false, Offset(4f, 4f), Size(20f, 20f), style = stroke)
            drawLine(tint, Offset(4f, 4f), Offset(4f, 10f), strokeWidth = 2f)
            drawLine(tint, Offset(4f, 4f), Offset(10f, 4f), strokeWidth = 2f)
            drawLine(tint, Offset(14f, 14f), Offset(14f, 9f), strokeWidth = 2f)
            drawLine(tint, Offset(14f, 14f), Offset(18f, 16f), strokeWidth = 2f)
        }
    }
}
