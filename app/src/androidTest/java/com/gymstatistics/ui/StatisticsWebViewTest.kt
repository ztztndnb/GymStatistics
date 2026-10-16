package com.gymstatistics.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gymstatistics.MainActivity
import com.gymstatistics.data.MuscleSelection
import com.gymstatistics.data.MuscleWorkload
import com.gymstatistics.data.ExerciseRecord
import com.gymstatistics.data.SessionRecord
import com.gymstatistics.data.WorkoutData
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Compose host, bundled SVG library and Android WebView, not an HTTP preview. */
@RunWith(AndroidJUnit4::class)
class StatisticsWebViewTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private val rows = mutableStateOf(listOf(MuscleWorkload(MuscleSelection("chest-upper-left", "左上胸"), 3)))
    private val selected = mutableStateOf<String?>(null)
    private val visible = mutableStateOf(true)
    private val clicked = AtomicReference<String?>()

    @Before fun openHost() {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync {
            activity.setContent {
                MaterialTheme {
                    if (visible.value) StatisticsBodyMap(rows.value, selected.value) { clicked.set(it); selected.value = it }
                }
            }
        }
    }

    @After fun closeHost() {
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun packagedMapLoadsBothSidesAndAppliesUpdatedData() {
        awaitJavascript("document.querySelectorAll('#front path').length") { it == "40" }
        awaitJavascript("document.querySelectorAll('#back path').length") { it == "49" }
        awaitJavascript("document.querySelector('#front').getBoundingClientRect().height >= 200") { it == "true" }
        assertMapReady()
        awaitJavascript("document.querySelector('[data-muscle-id=\"chest-upper-left\"]').getAttribute('aria-label')") { it.contains("3组") }
        instrumentation.runOnMainSync { rows.value = listOf(MuscleWorkload(MuscleSelection("chest-upper-left", "左上胸"), 8)) }
        awaitJavascript("document.querySelector('[data-muscle-id=\"chest-upper-left\"]').getAttribute('aria-label')") { it.contains("8组") }
        evaluate("document.querySelector('[data-muscle-id=\"chest-upper-left\"]').dispatchEvent(new MouseEvent('click', {bubbles:true}))")
        awaitJavascript("document.querySelector('[data-muscle-id=\"chest-upper-left\"]').getAttribute('aria-pressed')") { it == "\"true\"" }
        assertEquals("chest-upper-left", clicked.get())
    }

    @Test fun mapRecreatedAfterLeavingUsesLatestData() {
        awaitJavascript("document.querySelectorAll('#front path').length") { it == "40" }
        val original = awaitWebView()
        instrumentation.runOnMainSync { visible.value = false }
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline && webViewOrNull() != null) SystemClock.sleep(50)
        assertNull("Leaving the screen must release its WebView", webViewOrNull())
        instrumentation.runOnMainSync {
            rows.value = listOf(MuscleWorkload(MuscleSelection("chest-upper-left", "左上胸"), 12))
            visible.value = true
        }
        awaitJavascript("document.querySelector('[data-muscle-id=\"chest-upper-left\"]').getAttribute('aria-label')") { it.contains("12组") }
        awaitJavascript("document.querySelector('#back').getBoundingClientRect().height >= 200") { it == "true" }
        assertMapReady()
        assertNotSame(original, awaitWebView())
    }

    @Test fun failedPageCanBeRetriedWithoutLeavingStatistics() {
        awaitJavascript("document.querySelectorAll('#front path').length") { it == "40" }
        val original = awaitWebView()
        instrumentation.runOnMainSync { original.loadUrl("file:///android_asset/missing-statistics-page.html") }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("肌群图加载失败").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("重试").performClick()
        awaitJavascript("document.querySelectorAll('#front path').length") { it == "40" }
        awaitJavascript("document.querySelector('#front').getBoundingClientRect().height >= 200") { it == "true" }
        assertMapReady()
        assertNotSame(original, awaitWebView())
    }

    @Test fun fullStatisticsPageShowsMapWhenScrolledIntoView() {
        val data = WorkoutData(sessions = listOf(SessionRecord("fixture", LocalDate.now().toString(), exercises = listOf(
            ExerciseRecord("测试卧推", sets = 7, muscles = listOf(MuscleSelection("chest-upper-left", "左上胸"))),
        ))))
        // A shorter viewport keeps the third lazy item off-screen initially.
        instrumentation.runOnMainSync { activity.setContent { MaterialTheme { Box(Modifier.height(600.dp)) { StatisticsScreen(data, {}, {}) } } } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("肌群训练分布图"))
        compose.onNodeWithContentDescription("肌群训练分布图").assertIsDisplayed()
        try {
            awaitJavascript("document.querySelector('[data-muscle-id=\"chest-upper-left\"]').getAttribute('aria-label')") { it.contains("7组") }
            awaitJavascript("document.querySelectorAll('#front path').length + document.querySelectorAll('#back path').length") { it == "89" }
            awaitJavascript("document.querySelector('#front').getBoundingClientRect().height >= 200") { it == "true" }
            assertMapReady()
        } finally {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            screenshot?.let { bitmap ->
                File(instrumentation.targetContext.getExternalFilesDir(null), "statistics-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    private fun webViewOrNull(): WebView? {
        // The test clock must drive recomposition before querying the native View tree.
        compose.waitForIdle()
        val result = AtomicReference<WebView?>()
        instrumentation.runOnMainSync { result.set(findWebView(activity.window.decorView)) }
        return result.get()
    }

    private fun assertMapReady() {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("肌群图加载中…").fetchSemanticsNodes().isEmpty() }
        assertTrue("Map must not be covered by an error panel", compose.onAllNodesWithText("肌群图加载失败").fetchSemanticsNodes().isEmpty())
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun awaitWebView(): WebView {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) { webViewOrNull()?.let { return it }; SystemClock.sleep(50) }
        error("Statistics WebView was not attached")
    }

    private fun evaluate(script: String): String {
        val webView = awaitWebView()
        val latch = CountDownLatch(1)
        val value = AtomicReference("no callback")
        instrumentation.runOnMainSync { webView.evaluateJavascript("(function(){try{return ($script)}catch(e){return 'ERROR: '+e.message}})()") { value.set(it); latch.countDown() } }
        assertTrue("JavaScript callback timed out", latch.await(5, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJavascript(script: String, matches: (String) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var last = ""
        while (SystemClock.uptimeMillis() < deadline) {
            last = evaluate(script)
            if (matches(last)) return
            SystemClock.sleep(100)
        }
        val geometry = evaluate("({viewport:innerHeight,map:document.querySelector('#maps')?.getBoundingClientRect().height,svg:document.querySelector('#front')?.getBoundingClientRect().height})")
        val native = AtomicReference("")
        instrumentation.runOnMainSync { val view = findWebView(activity.window.decorView); native.set("height=${view?.height}; layoutHeight=${view?.layoutParams?.height}") }
        fail("DOM condition not met: $script; last result: $last; geometry=$geometry; native=${native.get()}")
    }
}
