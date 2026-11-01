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
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
        androidx.test.espresso.Espresso.pressBack()
        compose.onAllNodesWithContentDescription("动作操作")[0].performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("编辑动作").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onAllNodesWithContentDescription("动作操作")[0].performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除动作").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("测试A卧推").assertDoesNotExist()
        compose.onNodeWithText("测试Z深蹲").assertIsDisplayed()
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
        awaitPicker("(document.querySelector('[data-muscle-id=\"biceps-left\"]').dispatchEvent(new MouseEvent('click')), selected.size)", "1")
        awaitPicker("(document.querySelector('[data-muscle-id=\"biceps-left\"]').focus(), document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter'})), selected.size === 0 && document.activeElement.dataset.muscleId === 'biceps-left')", "true")
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
