package com.gymstatistics.ui

import java.time.LocalDate

/** The viewed week can move independently of the date whose workout is displayed. */
internal data class WeekNavigation(
    val selectedDate: LocalDate,
    val visibleWeekStart: LocalDate = weekStart(selectedDate),
    val revision: Long = 0L,
) {
    val isSelectedVisible: Boolean
        get() = !selectedDate.isBefore(visibleWeekStart) && selectedDate.isBefore(visibleWeekStart.plusWeeks(1))

    fun page(weeks: Int): WeekNavigation = copy(
        visibleWeekStart = visibleWeekStart.plusWeeks(weeks.toLong()),
        revision = revision + 1,
    )

    fun pageIfRevision(expectedRevision: Long, weeks: Int): WeekNavigation =
        if (revision == expectedRevision) page(weeks) else this

    fun select(date: LocalDate): WeekNavigation = WeekNavigation(date, revision = revision + 1)
}

/** Monday-based start of the week containing [date]. */
internal fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
