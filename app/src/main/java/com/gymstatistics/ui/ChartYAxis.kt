package com.gymstatistics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import kotlin.math.abs

internal fun chartAxisValues(minimum: Double, maximum: Double): List<Double> {
    if (minimum == maximum) return listOf(maximum)
    val top = BigDecimal.valueOf(maximum)
    val step = top.subtract(BigDecimal.valueOf(minimum)).divide(BigDecimal.valueOf(3), MathContext.DECIMAL64)
    return listOf(maximum, top.subtract(step).toDouble(), top.subtract(step.multiply(BigDecimal.valueOf(2))).toDouble(), minimum)
}

internal fun chartAxisNumber(value: Double): String =
    DecimalFormat(if (value != 0.0 && abs(value) < .01) "0.##E0" else "0.##").format(value)

@Composable
internal fun ChartYAxis(minimum: Double, maximum: Double, height: Dp, format: (Double) -> String = ::chartAxisNumber) {
    Column(
        Modifier.height(height).widthIn(min = 36.dp),
        verticalArrangement = if (maximum == minimum) Arrangement.Center else Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        chartAxisValues(minimum, maximum).forEach { value ->
            Text(format(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
