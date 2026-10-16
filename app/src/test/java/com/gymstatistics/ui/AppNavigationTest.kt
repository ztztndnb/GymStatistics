package com.gymstatistics.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun statisticsOpenedFromAnActionReturnsToThatAction() {
        val statistics = AppNavigationState().selectHistoryAction("卧推").open(Screen.STATS)
        assertEquals(MainTab.STATS, statistics.selectedTab)
        assertTrue(statistics.showBottomNavigation)
        val detail = statistics.back()
        assertEquals(Screen.HISTORY, detail.screen)
        assertEquals("卧推", detail.historyAction)
        assertFalse(detail.showBottomNavigation)
        assertNull(detail.back().historyAction)
    }

    @Test
    fun selectingAnotherTabClearsTheStatisticsReturnDestination() {
        val statistics = AppNavigationState().selectHistoryAction("卧推").open(Screen.STATS)
        assertEquals(AppNavigationState(), statistics.selectTab(MainTab.STATS).back())
        val second = statistics.back().selectHistoryAction("深蹲").open(Screen.STATS)
        assertEquals("深蹲", second.back().historyAction)
    }

    @Test
    fun launchOpensHomeWithTheBottomNavigation() {
        val state = AppNavigationState()
        assertEquals(Screen.MAIN, state.screen)
        assertEquals(MainTab.HOME, state.selectedTab)
        assertTrue(state.showBottomNavigation)
    }

    @Test
    fun allFourTabsOpenTheirPageAndRemainSelectable() {
        val cases = listOf(
            MainTab.HOME to Screen.MAIN,
            MainTab.PLAN to Screen.PLAN,
            MainTab.STATS to Screen.STATS,
            MainTab.LIBRARY to Screen.HISTORY,
        )
        assertEquals(cases.map { it.first }, MainTab.entries.toList())
        cases.forEach { (tab, expectedScreen) ->
            val state = AppNavigationState().selectTab(tab)
            assertEquals(tab.name, expectedScreen, state.screen)
            assertEquals(tab, state.selectedTab)
            assertTrue(state.showBottomNavigation)
        }
    }

    @Test
    fun actionDetailsHideTheBarAndBackReturnsToTheLibrary() {
        val detail = AppNavigationState().selectHistoryAction("卧推")
        assertEquals(MainTab.LIBRARY, detail.selectedTab)
        assertFalse(detail.showBottomNavigation)

        val library = detail.back()
        assertEquals(Screen.HISTORY, library.screen)
        assertNull(library.historyAction)
        assertTrue(library.showBottomNavigation)
    }

    @Test
    fun cameraAndSettingsHideTheBarAndReturnHome() {
        listOf(Screen.FOOD, Screen.SETTINGS).forEach { page ->
            val state = AppNavigationState().open(page)
            assertFalse(state.showBottomNavigation)
            assertEquals(MainTab.HOME, state.selectedTab)
            assertEquals(AppNavigationState(), state.back())
        }
    }

    @Test
    fun syncReturnsToSettingsBeforeReturningHome() {
        val sync = AppNavigationState().open(Screen.SETTINGS).open(Screen.SYNC)
        assertFalse(sync.showBottomNavigation)
        val settings = sync.back()
        assertEquals(Screen.SETTINGS, settings.screen)
        assertFalse(settings.showBottomNavigation)
        assertEquals(AppNavigationState(), settings.back())
    }

    @Test
    fun pickingHistoryDataCanReturnHomeWithoutLeavingASelectedAction() {
        val detail = AppNavigationState().selectHistoryAction("深蹲")
        val home = detail.selectTab(MainTab.HOME)
        assertEquals(Screen.MAIN, home.screen)
        assertNull(home.historyAction)
        assertTrue(home.showBottomNavigation)
    }

    @Test
    fun switchingTabsClearsDetailsWithoutMutatingThePreviousState() {
        val original = AppNavigationState().selectHistoryAction("硬拉")
        val plan = original.selectTab(MainTab.PLAN)
        assertEquals(Screen.PLAN, plan.screen)
        assertNull(plan.historyAction)
        assertTrue(plan.showBottomNavigation)
        assertEquals("硬拉", original.historyAction)
        assertEquals(Screen.HISTORY, original.screen)
    }

    @Test
    fun backFromAnotherRootTabReturnsHome() {
        listOf(MainTab.PLAN, MainTab.STATS, MainTab.LIBRARY).forEach { tab ->
            assertEquals(AppNavigationState(), AppNavigationState().selectTab(tab).back())
        }
    }
}
