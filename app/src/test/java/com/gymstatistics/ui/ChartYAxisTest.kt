package com.gymstatistics.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartYAxisTest {
    @Test fun valuesFollowTheFourGridLinesFromTopToBottom() {
        assertArrayEquals(doubleArrayOf(88.0, 80.6666666667, 73.3333333333, 66.0), chartAxisValues(66.0, 88.0).toDoubleArray(), 1e-8)
    }

    @Test fun equalValuesHaveOneCenteredTick() {
        assertEquals(listOf(65.5), chartAxisValues(65.5, 65.5))
        assertEquals(listOf(0.0), chartAxisValues(0.0, 0.0))
    }

    @Test fun negativeValuesRemainInDescendingOrder() {
        assertArrayEquals(doubleArrayOf(3.0, 1.0, -1.0, -3.0), chartAxisValues(-3.0, 3.0).toDoubleArray(), 1e-8)
    }

    @Test fun fractionalBodyMeasurementsKeepTheirRange() {
        assertArrayEquals(doubleArrayOf(66.0, 65.8333333333, 65.6666666667, 65.5), chartAxisValues(65.5, 66.0).toDoubleArray(), 1e-8)
    }

    @Test fun fractionalRangeCrossingZeroHasAnExactZeroTick() {
        assertEquals(0.0, chartAxisValues(-.2, .1)[1], 0.0)
        assertEquals(0.0, chartAxisValues(-.1, .2)[2], 0.0)
    }

    @Test fun smallNonzeroValuesAreNotDisplayedAsZero() {
        assertEquals("1E-4", chartAxisNumber(.0001))
    }
}
