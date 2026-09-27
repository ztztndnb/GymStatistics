package com.gymstatistics.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekNavigationTest {
    @Test
    fun browsingWeeksDoesNotChangeTheTrainingDate() {
        val initial = WeekNavigation(LocalDate.of(2026, 9, 16))

        val next = initial.page(1)

        assertEquals(LocalDate.of(2026, 9, 16), next.selectedDate)
        assertEquals(LocalDate.of(2026, 9, 21), next.visibleWeekStart)
        assertFalse(next.isSelectedVisible)
        assertEquals(initial.selectedDate, next.page(-1).selectedDate)
        assertEquals(initial.visibleWeekStart, next.page(-1).visibleWeekStart)
        assertTrue(initial.isSelectedVisible)
    }

    @Test
    fun selectingADateReturnsTheVisibleWeekToThatDate() {
        val browsing = WeekNavigation(LocalDate.of(2026, 9, 16)).page(2)

        val selected = browsing.select(LocalDate.of(2026, 10, 1))

        assertEquals(LocalDate.of(2026, 10, 1), selected.selectedDate)
        assertEquals(LocalDate.of(2026, 9, 28), selected.visibleWeekStart)
        assertTrue(selected.isSelectedVisible)
    }

    @Test
    fun aPendingPageCannotOverrideASelectionMadeDuringItsAnimation() {
        val initial = WeekNavigation(LocalDate.of(2026, 9, 16))
        val pageStartedAt = initial.revision

        val tapped = initial.select(LocalDate.of(2026, 9, 16))

        assertEquals(tapped, tapped.pageIfRevision(pageStartedAt, 1))
        assertEquals(LocalDate.of(2026, 9, 21), initial.pageIfRevision(pageStartedAt, 1).visibleWeekStart)
    }
}
