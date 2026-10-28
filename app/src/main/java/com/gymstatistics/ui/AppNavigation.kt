package com.gymstatistics.ui

internal enum class Screen { MAIN, PLAN, STATS, HISTORY, SETTINGS, SYNC, FOOD, NUTRITION, BODY, BODY_EDITOR }

internal enum class MainTab(val label: String, val screen: Screen) {
    HOME("首页", Screen.MAIN),
    PLAN("计划", Screen.PLAN),
    BODY("身体", Screen.BODY),
    STATS("统计", Screen.STATS),
    LIBRARY("动作库", Screen.HISTORY),
}

/** Navigation owns no workout data or selected date. */
internal data class AppNavigationState(
    val screen: Screen = Screen.MAIN,
    val historyAction: String? = null,
) {
    val selectedTab: MainTab
        get() = if (screen == Screen.BODY_EDITOR) MainTab.BODY
        else MainTab.entries.firstOrNull { it.screen == screen } ?: MainTab.HOME

    val showBottomNavigation: Boolean
        get() = MainTab.entries.any { it.screen == screen } &&
            (screen != Screen.HISTORY || historyAction == null)

    fun selectTab(tab: MainTab): AppNavigationState =
        copy(screen = tab.screen, historyAction = null)

    fun open(screen: Screen): AppNavigationState = copy(screen = screen)

    fun selectHistoryAction(name: String?): AppNavigationState =
        copy(screen = Screen.HISTORY, historyAction = name)

    fun back(): AppNavigationState = when {
        screen == Screen.STATS && historyAction != null -> copy(screen = Screen.HISTORY)
        screen == Screen.HISTORY && historyAction != null -> copy(historyAction = null)
        screen == Screen.SYNC -> copy(screen = Screen.SETTINGS)
        screen == Screen.BODY_EDITOR -> copy(screen = Screen.BODY, historyAction = null)
        else -> AppNavigationState()
    }
}
