package com.gymstatistics.ui

internal enum class Screen { MAIN, PLAN, START, STATS, HISTORY, SETTINGS, SYNC, FOOD }

internal enum class MainTab(val label: String, val screen: Screen) {
    HOME("首页", Screen.MAIN),
    PLAN("计划", Screen.PLAN),
    START("开始", Screen.START),
    STATS("统计", Screen.STATS),
    LIBRARY("动作库", Screen.HISTORY),
}

/** Navigation owns no workout data or selected date. */
internal data class AppNavigationState(
    val screen: Screen = Screen.MAIN,
    val historyAction: String? = null,
) {
    val selectedTab: MainTab
        get() = MainTab.entries.firstOrNull { it.screen == screen } ?: MainTab.HOME

    val showBottomNavigation: Boolean
        get() = MainTab.entries.any { it.screen == screen } &&
            (screen != Screen.HISTORY || historyAction == null)

    fun selectTab(tab: MainTab): AppNavigationState =
        copy(screen = tab.screen, historyAction = null)

    fun open(screen: Screen): AppNavigationState = copy(screen = screen)

    fun selectHistoryAction(name: String?): AppNavigationState =
        copy(screen = Screen.HISTORY, historyAction = name)

    fun back(): AppNavigationState = when {
        screen == Screen.HISTORY && historyAction != null -> copy(historyAction = null)
        screen == Screen.SYNC -> copy(screen = Screen.SETTINGS)
        else -> AppNavigationState()
    }
}
