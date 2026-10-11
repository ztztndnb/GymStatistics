package com.gymstatistics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySortingTest {
    private val oldest = ActionRecord("2026-10-01", 100.0, "kg", 2, 2)
    private val middle = ActionRecord("2026-10-05", 30.0, "磅", 8, 1)
    private val newest = ActionRecord("2026-10-11", 60.0, "", 4, 3)
    private val records = listOf(middle, oldest, newest)

    @Test
    fun actionsDefaultToNewestDateFirstRegardlessOfUnitOrValue() {
        val sessions = records.mapIndexed { index, record ->
            SessionRecord(
                id = "session-$index",
                date = record.date,
                exercises = listOf(ExerciseRecord("卧推", data = record.data, unit = record.unit)),
            )
        }

        assertEquals(
            listOf(newest.date, middle.date, oldest.date),
            buildActions(sessions).single().records.map { it.date },
        )
    }

    @Test
    fun allFiveCriteriaSupportBothDirections() {
        val expectedDescending = mapOf(
            ActionRecordSort.TIME to listOf(newest, middle, oldest),
            ActionRecordSort.WEIGHT_COEFFICIENT to listOf(newest, oldest, middle),
            ActionRecordSort.VALUE to listOf(oldest, newest, middle),
            ActionRecordSort.COUNT to listOf(middle, newest, oldest),
            ActionRecordSort.SETS to listOf(newest, oldest, middle),
        )

        expectedDescending.forEach { (criterion, expected) ->
            assertEquals(criterion.name, expected, records.sortedWith(actionComparator(criterion)))
            assertEquals(
                "${criterion.name} ascending",
                expected.reversed(),
                records.sortedWith(actionComparator(criterion, descending = false)),
            )
        }
        assertEquals(listOf(middle, oldest, newest), records)
    }

    @Test
    fun missingMetricValuesRemainLastInBothDirections() {
        val missing = ActionRecord("2026-10-12", null, "kg", null, null)
        val input = listOf(missing, oldest, middle, newest)

        ActionRecordSort.entries.filter { it != ActionRecordSort.TIME }.forEach { criterion ->
            listOf(true, false).forEach { descending ->
                assertEquals(missing, input.sortedWith(actionComparator(criterion, descending)).last())
            }
        }
    }

    @Test
    fun equalMetricValuesPreferNewerDatesInBothDirections() {
        val earlier = oldest.copy(date = "2026-10-01")
        val later = oldest.copy(date = "2026-10-11")

        ActionRecordSort.entries.filter { it != ActionRecordSort.TIME }.forEach { criterion ->
            listOf(true, false).forEach { descending ->
                assertEquals(
                    listOf(later, earlier),
                    listOf(earlier, later).sortedWith(actionComparator(criterion, descending)),
                )
            }
        }
    }

    @Test
    fun coefficientUsesOneSetWhenSetsAreOmittedButRequiresValueAndCount() {
        val record = ActionRecord("2026-10-11", 10.0, "kg", 4, null)

        assertEquals(40.0, record.weightCoefficient!!, 0.0)
        assertNull(record.copy(data = null).weightCoefficient)
        assertNull(record.copy(count = null).weightCoefficient)
        assertEquals(0.0, record.copy(count = 0).weightCoefficient!!, 0.0)
    }

    @Test
    fun coefficientDoesNotOverflowIntegerMultiplication() {
        val record = ActionRecord("2026-10-11", 2.0, "kg", Int.MAX_VALUE, Int.MAX_VALUE)

        assertEquals(2.0 * Int.MAX_VALUE.toDouble() * Int.MAX_VALUE.toDouble(), record.weightCoefficient!!, 0.0)
        assertTrue(record.weightCoefficient!! > 0.0)
    }

    @Test
    fun nonFiniteValuesAreTreatedAsMissing() {
        val invalid = newest.copy(data = Double.NaN)
        val overflowing = newest.copy(data = Double.MAX_VALUE)

        assertNull(invalid.weightCoefficient)
        assertNull(overflowing.weightCoefficient)
        listOf(true, false).forEach { descending ->
            assertEquals(
                invalid,
                listOf(invalid, oldest).sortedWith(actionComparator(ActionRecordSort.VALUE, descending)).last(),
            )
            assertEquals(
                overflowing,
                listOf(overflowing, oldest).sortedWith(actionComparator(ActionRecordSort.WEIGHT_COEFFICIENT, descending)).last(),
            )
        }
    }

    @Test
    fun recordsOnTheSameDateKeepTheirExistingOrder() {
        val first = oldest.copy(date = newest.date)
        val second = middle.copy(date = newest.date)

        listOf(true, false).forEach { descending ->
            assertEquals(
                listOf(first, second),
                listOf(first, second).sortedWith(actionComparator(ActionRecordSort.TIME, descending)),
            )
        }
    }
}
