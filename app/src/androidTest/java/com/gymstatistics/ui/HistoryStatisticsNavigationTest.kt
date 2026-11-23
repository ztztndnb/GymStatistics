package com.gymstatistics.ui

import android.content.Intent
import android.graphics.Bitmap
import android.app.Application
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.gymstatistics.data.MuscleSelection
import com.gymstatistics.data.PlannedExercise
import java.time.LocalDate
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                ExerciseRecord("测试Z深蹲", data = 30.0, unit = "kg", count = 6, sets = 2),
            ))
            model.addSession(LocalDate.now().plusDays(1).toString(), "", listOf(
                ExerciseRecord("测试未来动作", data = 99.0, unit = "磅", count = 1),
            ))
            model.clearMessage()
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
        assertSelected("测试Z深蹲", "2个训练日，最低30，最高39.92 kg")
        listOf("39.92", "36.61", "33.31", "30").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        screenshot("ui-axis-statistics")
        back()
        compose.onNodeWithText("测试Z深蹲").assertIsDisplayed()
        compose.onNodeWithText("排序：时间").assertIsDisplayed()
        compose.onNodeWithText("数据趋势", substring = true).assertDoesNotExist()
        compose.onNodeWithText("总个数趋势", substring = true).assertDoesNotExist()
    }

    @Test fun homeModeButtonTogglesContentAndSharesTheSettingsMode() {
        compose.runOnIdle {
            model.addPlannedExercise(LocalDate.now().dayOfWeek.value,
                PlannedExercise(id = "home-mode-test", name = "计划模式测试动作", data = 20.0, unit = "kg", count = 10, sets = 3))
        }
        val sessions = model.uiState.data.sessions
        compose.onNodeWithText("测试A卧推").assertIsDisplayed()
        compose.onNodeWithText("手动模式").performClick()
        compose.onNodeWithText("计划模式").assertIsDisplayed()
        compose.onNodeWithText("计划模式测试动作").assertIsDisplayed()
        compose.onNodeWithText("测试A卧推").assertDoesNotExist()
        compose.onNodeWithText("今天").assertIsDisplayed()

        compose.onNodeWithText("计划模式").performClick()
        compose.onNodeWithText("手动模式").assertIsDisplayed()
        compose.onNodeWithText("测试A卧推").assertIsDisplayed()

        compose.onNodeWithText("手动模式").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("手动模式").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("手动模式").assertIsDisplayed()
        compose.onNodeWithText("测试A卧推").assertIsDisplayed()
        compose.onNodeWithText("计划模式测试动作").assertDoesNotExist()
        compose.onNodeWithText("今天").assertIsDisplayed()
        assertEquals(sessions, model.uiState.data.sessions)
    }

    @Test fun homeRowsAndTheirMenuStillEditAndDelete() {
        compose.onNodeWithText("测试A卧推").assertIsDisplayed()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.cacheDir, "gym-home-workouts.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
        compose.onNodeWithText("测试A卧推").performClick()
        compose.onNodeWithText("编辑动作").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onAllNodesWithContentDescription("动作操作")[0].performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("编辑动作").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onAllNodesWithContentDescription("动作操作")[0].performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除动作").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("测试A卧推").assertDoesNotExist()
        compose.onNodeWithText("测试Z深蹲").assertIsDisplayed()
    }

    @Test fun addingOnAnotherDateShowsTheDateAndSavesThere() {
        val today = LocalDate.now()
        val target = if (today.dayOfWeek.value == 1) today.plusDays(1) else today.minusDays(1)
        compose.onNodeWithText(target.dayOfMonth.toString()).performClick()
        compose.onNodeWithText("记录动作").performClick()
        val reminder = compose.onNodeWithText("记录日期为 $target（非今天）")
        compose.waitUntil(5_000) { reminder.isDisplayed() }
        compose.onNodeWithTag("record-action-name", useUnmergedTree = true).performTextReplacement("异日测试动作")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("异日测试动作").assertIsDisplayed()
        compose.runOnIdle {
            val recorded = model.uiState.data.sessions.single { session ->
                session.exercises.any { it.name == "异日测试动作" }
            }
            assertEquals(target.toString(), recorded.date)
        }
    }

    @Test fun homeAddSuggestsAtMostThreeNamesAndFillsTheLatestRecord() {
        val muscle = MuscleSelection("shoulder-side-left", "左侧三角肌中束")
        compose.runOnIdle {
            model.addSession(LocalDate.now().minusDays(7).toString(), "", (1..5).map {
                ExerciseRecord("候选肩推$it", data = 50.0, unit = "kg")
            })
            model.addSession(LocalDate.now().minusDays(2).toString(), "", listOf(
                ExerciseRecord("候选肩推2", data = 42.5, unit = "lbs", count = 8, sets = 4,
                    note = "最近一次的备注", muscles = listOf(muscle)),
            ))
            model.clearMessage()
        }
        compose.onNodeWithText("记录动作").performClick()
        val name = compose.onNodeWithTag("record-action-name", useUnmergedTree = true)
        compose.waitUntil(5_000) { name.isDisplayed() }
        compose.onNodeWithText("非今天", substring = true).assertDoesNotExist()
        val suggestions = hasText("候选肩推", substring = true) and !hasSetTextAction()
        name.performClick()
        assertTrue(compose.onAllNodes(suggestions).fetchSemanticsNodes().isEmpty())
        name.performTextReplacement("候选肩推")
        assertEquals(3, compose.onAllNodes(suggestions).fetchSemanticsNodes().size)
        name.assertIsFocused()

        val data = compose.onNode(hasText("数据") and hasSetTextAction())
        data.performTextReplacement("25")
        assertTrue(compose.onAllNodes(suggestions).fetchSemanticsNodes().isEmpty())

        name.performTextReplacement("候选肩推2")
        compose.onNode(hasText("候选肩推2") and !hasSetTextAction()).performClick()
        name.assertTextContains("候选肩推2")
        data.assertTextContains("42.5")
        assertTrue(compose.onAllNodes(suggestions).fetchSemanticsNodes().isEmpty())

        data.performTextReplacement("45")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            val saved = model.uiState.data.sessions.single { it.date == LocalDate.now().toString() }
                .exercises.single { it.name == "候选肩推2" }
            assertEquals(45.0, saved.data!!, 0.0)
            assertEquals("lbs", saved.unit)
            assertEquals(8, saved.count)
            assertEquals(4, saved.sets)
            assertEquals("最近一次的备注", saved.note)
            assertEquals(listOf(muscle), saved.muscles)
        }
    }

    @Test fun homeActionMenuOpensItsOwnLibraryDetail() {
        compose.onNodeWithText("测试Z深蹲").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("动作操作")[1].performClick()
        compose.onNodeWithText("在动作库中查看").performClick()
        compose.onNodeWithText("查看该动作统计").assertIsDisplayed()
        compose.onNodeWithText("测试Z深蹲").assertIsDisplayed()
        compose.onNodeWithText("测试A卧推").assertDoesNotExist()

        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("搜索动作或肌群").assertIsDisplayed()
        compose.onNodeWithText("测试A卧推").assertIsDisplayed()
    }

    @Test fun openingAnotherActionDoesNotKeepThePreviousSelection() {
        openDetail("测试Z深蹲")
        openStatistics()
        assertSelected("测试Z深蹲", "2个训练日，最低30，最高39.92 kg")
        back()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("搜索动作或肌群").performTextReplacement("测试A卧推")
        compose.onNode(hasText("测试A卧推") and !hasSetTextAction()).performClick()
        openStatistics()
        assertSelected("测试A卧推", "1个训练日，最低4.99，最高4.99 kg")
    }

    @Test fun actionWithoutEligibleRecordsDoesNotFallBackToAnotherAction() {
        openDetail("测试未来动作")
        openStatistics()
        compose.onNodeWithText("动作进步").assertIsDisplayed()
        compose.onNodeWithText("测试未来动作").assertIsDisplayed()
        compose.onNodeWithText("这段时间没有该指标的有效记录").assertIsDisplayed()
        compose.onNodeWithText("测试A卧推").assertDoesNotExist()
    }

    @Test fun historyPlanSelectionRequiresConfirmationAndKeepsItsData() {
        compose.onNodeWithText("计划").performClick()
        compose.onNodeWithText("从历史动作选择").performScrollTo().performClick()
        compose.onNodeWithText("搜索动作或肌群").performTextReplacement("测试Z深蹲")
        compose.onNode(hasText("测试Z深蹲") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("添加计划动作").assertIsDisplayed()
        assertTrue(model.uiState.data.trainingPlan?.days.orEmpty().all { it.exercises.isEmpty() })
        compose.onNodeWithText("取消").performClick()
        assertTrue(model.uiState.data.trainingPlan?.days.orEmpty().all { it.exercises.isEmpty() })
        compose.onNodeWithText("从历史动作选择").performScrollTo().performClick()
        compose.onNodeWithText("搜索动作或肌群").performTextReplacement("测试Z深蹲")
        compose.onNode(hasText("测试Z深蹲") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { model.uiState.data.trainingPlan?.days?.first()?.exercises?.size == 1 }
        val saved = model.uiState.data.trainingPlan!!.days.first().exercises.single()
        assertEquals(88.0, saved.data!!, 0.0)
        assertEquals("磅", saved.unit)
        assertEquals(8, saved.count)
        assertEquals(4, saved.sets)
        screenshot("ui-plan")
        compose.onNodeWithText("周二").performScrollTo().performClick()
        compose.onNodeWithText("测试Z深蹲").assertDoesNotExist()
        compose.onNodeWithText("周一").performClick()
        compose.onNodeWithText("测试Z深蹲").assertIsDisplayed()
        compose.onNodeWithText("动作库").performClick()
        screenshot("ui-library")
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("训练概览").assertIsDisplayed()
        screenshot("ui-statistics")
        compose.onNodeWithText("身体").performClick()
        compose.onNodeWithText("添加记录").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("保存身体数据").performClick()
        compose.waitUntil(5_000) { model.uiState.data.bodyData?.records?.isNotEmpty() == true }
        compose.onNodeWithText("最新 65.5 kg（${LocalDate.now()}）").assertIsDisplayed()
        screenshot("ui-body")
    }

    @Test fun muscleMapCancelDiscardsAndDoneKeepsPlanDraftFields() {
        compose.onNodeWithText("计划").performClick()
        compose.onNodeWithText("添加动作").performScrollTo().performClick()
        compose.onNodeWithText("动作名称").performTextReplacement("图选测试")
        compose.onNodeWithText("数据").performTextReplacement("25")
        compose.onNodeWithText("个数(每组)").performTextReplacement("12")
        compose.onNodeWithText("组数").performTextReplacement("3")
        compose.onNodeWithText("备注").performScrollTo().performTextReplacement("保留备注")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("选择锻炼肌群").performScrollTo().performClick()
        awaitPicker("document.querySelectorAll('#body-map path').length", "40")
        awaitPicker("(document.querySelector('[data-muscle-id=\"biceps-left\"]').dispatchEvent(new MouseEvent('click')), selected.size)", "1")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("已选择肌肉：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("选择锻炼肌群").performClick()
        awaitPicker("document.querySelectorAll('#body-map path').length", "40")
        awaitPicker("document.querySelector('#body-map svg').getBoundingClientRect().height > 200", "true")
        awaitPicker("(document.body.style.height='200px', document.querySelector('#body-map').getBoundingClientRect().height >= 180 && document.body.scrollHeight > document.body.clientHeight)", "true")
        awaitPicker("(document.body.style.height='', document.querySelector('#body-map svg').getBoundingClientRect().height > 200)", "true")
        awaitPicker("""
            (() => {
              const path = document.querySelector('[data-muscle-id="biceps-left"]');
              const before = getComputedStyle(path).fill;
              path.dispatchEvent(new MouseEvent('click'));
              return path.isConnected && path.getAttribute('aria-pressed') === 'true' &&
                getComputedStyle(path).fill !== before && getComputedStyle(path).webkitTapHighlightColor === 'rgba(0, 0, 0, 0)';
            })()
        """.trimIndent(), "true")
        awaitPicker("(document.querySelector('[data-muscle-id=\"biceps-left\"]').focus(), document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter'})), selected.size === 0 && document.activeElement.dataset.muscleId === 'biceps-left' && getComputedStyle(document.activeElement).outlineStyle === 'none')", "true")
        awaitPicker("(document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:' '})), selected.size === 1 && document.activeElement.dataset.muscleId === 'biceps-left')", "true")
        awaitPicker("(document.querySelector('[data-view=back]').click(), document.querySelectorAll('#body-map path').length)", "49")
        awaitPicker("(document.querySelector('[data-view=front]').click(), document.querySelector('[data-muscle-id=\"biceps-left\"]').getAttribute('aria-pressed'))", "\"true\"")
        screenshot("ui-muscle-picker")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("已选择肌肉：左侧肱二头肌").assertIsDisplayed()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { model.uiState.data.trainingPlan?.days?.first()?.exercises?.size == 1 }
        val saved = model.uiState.data.trainingPlan!!.days.first().exercises.single()
        assertEquals("图选测试", saved.name)
        assertEquals(25.0, saved.data!!, 0.0)
        assertEquals("磅", saved.unit)
        assertEquals(12, saved.count)
        assertEquals(3, saved.sets)
        assertEquals("保留备注", saved.note)
        assertEquals(listOf("biceps-left"), saved.muscles.map { it.id })
    }

    private fun screenshot(name: String) {
        // A target screen can enter semantics before its slide transition is drawn.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val frameReady = CountDownLatch(1)
        instrumentation.runOnMainSync {
            activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { frameReady.countDown() }
            }
        }
        check(frameReady.await(5, TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun awaitPicker(script: String, expected: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var last = ""
        while (SystemClock.uptimeMillis() < deadline) {
            compose.waitForIdle()
            val latch = CountDownLatch(1)
            val result = AtomicReference("")
            instrumentation.runOnMainSync {
                val webView = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { findWebView(it) }
                if (webView == null) latch.countDown()
                else webView.evaluateJavascript("(function(){try{return ($script)}catch(e){return null}})()") { result.set(it); latch.countDown() }
            }
            check(latch.await(5, TimeUnit.SECONDS))
            last = result.get()
            if (last == expected) return
            SystemClock.sleep(100)
        }
        error("Picker condition failed: $script, expected $expected, actual $last")
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
