package com.gymstatistics.ui

import android.content.Intent
import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gymstatistics.GymViewModel
import com.gymstatistics.MainActivity
import com.gymstatistics.data.ExerciseRecord
import java.time.LocalDate
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real app navigation and ViewModel using emulator-only training fixtures. */
@RunWith(AndroidJUnit4::class)
class HistoryStatisticsNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var fixtureDirectory: File
    private lateinit var model: GymViewModel
    private lateinit var modelStore: ViewModelStore

    @Before fun openApp() {
        val context = instrumentation.targetContext.applicationContext
        fixtureDirectory = Files.createTempDirectory(context.cacheDir.toPath(), "history-statistics-").toFile()
        val fixtureApplication = object : Application() {
            init { attachBaseContext(context) }
            override fun getFilesDir(): File = fixtureDirectory
        }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync {
            modelStore = ViewModelStore()
            model = ViewModelProvider(modelStore, ViewModelProvider.AndroidViewModelFactory(fixtureApplication))[GymViewModel::class.java]
            activity.setContent { GymApp(model) }
        }
        compose.waitUntil(10_000) { model.uiState.loaded }
        compose.runOnIdle {
            model.addSession(LocalDate.now().toString(), "", listOf(
                ExerciseRecord("测试A卧推", data = 11.0, unit = "磅", count = 10, sets = 3),
                ExerciseRecord("测试Z深蹲", data = 88.0, unit = "磅", count = 8, sets = 4),
            ))
            model.addSession(LocalDate.now().minusDays(1).toString(), "", listOf(
                ExerciseRecord("测试Z深蹲", data = 66.0, unit = "磅", count = 6, sets = 2),
            ))
            model.addSession(LocalDate.now().plusDays(1).toString(), "", listOf(
                ExerciseRecord("测试未来动作", data = 99.0, unit = "磅", count = 1),
            ))
        }
    }

    @After fun closeApp() {
        // Finish asynchronous saves before releasing the model and its isolated directory.
        if (::model.isInitialized) runBlocking {
            model.viewModelScope.coroutineContext[Job]?.children?.toList().orEmpty().joinAll()
        }
        instrumentation.runOnMainSync {
            if (::activity.isInitialized) activity.finish()
            if (::modelStore.isInitialized) modelStore.clear()
        }
        if (::fixtureDirectory.isInitialized) check(fixtureDirectory.deleteRecursively())
    }

    @Test fun detailButtonSelectsItsActionAndBackRestoresTheDetail() {
        openDetail("测试Z深蹲")
        compose.onNodeWithText("排序：时间").assertIsDisplayed()
        compose.onNodeWithText("最新在前").assertIsDisplayed()
        openStatistics()
        assertSelected("测试Z深蹲", "2个训练日，最低66，最高88 磅")
        back()
        compose.onNodeWithText("测试Z深蹲").assertIsDisplayed()
        compose.onNodeWithText("排序：时间").assertIsDisplayed()
        compose.onNodeWithText("数据趋势", substring = true).assertDoesNotExist()
        compose.onNodeWithText("总个数趋势", substring = true).assertDoesNotExist()
    }

    @Test fun openingAnotherActionDoesNotKeepThePreviousSelection() {
        openDetail("测试Z深蹲")
        openStatistics()
        assertSelected("测试Z深蹲", "2个训练日，最低66，最高88 磅")
        back()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("搜索动作或肌群").performTextReplacement("测试A卧推")
        compose.onNode(hasText("测试A卧推") and !hasSetTextAction()).performClick()
        openStatistics()
        assertSelected("测试A卧推", "1个训练日，最低11，最高11 磅")
    }

    @Test fun actionWithoutEligibleRecordsDoesNotFallBackToAnotherAction() {
        openDetail("测试未来动作")
        openStatistics()
        compose.onNodeWithText("动作进步").assertIsDisplayed()
        compose.onNodeWithText("测试未来动作").assertIsDisplayed()
        compose.onNodeWithText("这段时间没有该指标的有效记录").assertIsDisplayed()
        compose.onNodeWithText("测试A卧推").assertDoesNotExist()
    }

    private fun openDetail(name: String) {
        compose.onNodeWithText("动作库").performClick()
        compose.onNodeWithText("搜索动作或肌群").performTextReplacement(name)
        compose.onNode(hasText(name) and !hasSetTextAction()).performClick()
    }

    private fun openStatistics() {
        compose.onNodeWithText("查看该动作统计").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("动作进步").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertSelected(name: String, chartDescription: String) {
        compose.onNodeWithText("动作进步").assertIsDisplayed()
        compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsDisplayed()
        compose.onNodeWithContentDescription(chartDescription).assertExists()
    }

    private fun back() {
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
