package com.gymstatistics.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gymstatistics.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only bundled data; never writes workout or food history. */
@RunWith(AndroidJUnit4::class)
class NutritionLibraryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun openApp() {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    }

    @After fun closeApp() {
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun homeEntryLoadsOfficialDataAndReturnsHome() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.onNodeWithText("麦当劳营养库").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("搜索餐品")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索餐品").performTextReplacement("巨无霸")
        compose.onNodeWithContentDescription("添加巨无霸（1个）").performClick()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("巨无霸 · 1个").assertIsDisplayed()
        compose.onNodeWithText("2146 kJ").assertExists()
        // Real back input reaches the sheet's Dialog window before the Activity.
        Espresso.pressBack()
        compose.onNodeWithText("麦当劳营养库").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("AI食物分析相机").assertIsDisplayed()
    }

    @Test fun differentSizesCoexistAndHalfPortionsRecalculateBeforeClearing() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("搜索餐品")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索餐品").performTextReplacement("薯条")
        compose.onNodeWithContentDescription("选择薯条规格").performClick()
        compose.onNodeWithText("小", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("添加薯条（小）").performClick()
        compose.onNodeWithContentDescription("选择薯条规格").performClick()
        compose.onNodeWithText("大", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("添加薯条（大）").performClick()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("薯条 · 小").assertIsDisplayed()
        compose.onNodeWithText("薯条 · 大").assertIsDisplayed()
        compose.onNodeWithContentDescription("减少薯条（小）份数").performClick()
        compose.onNodeWithText("0.5 份").assertIsDisplayed()
        compose.onNodeWithText("2025.5 kJ").assertExists()
        compose.onNodeWithText("清空").performClick()
        compose.onNodeWithText("尚未选择餐品").assertIsDisplayed()
    }

    @Test fun clicksWithinOneFrameDoNotLoseQuantityUpdates() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("搜索餐品")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索餐品").performTextReplacement("巨无霸")
        compose.onNodeWithContentDescription("添加巨无霸（1个）").performSemanticsAction(SemanticsActions.OnClick) { click ->
            repeat(3) { click() }
        }
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("3 份").assertIsDisplayed()
        compose.onNodeWithContentDescription("减少巨无霸（1个）份数").performSemanticsAction(SemanticsActions.OnClick) { click ->
            repeat(2) { click() }
        }
        compose.onNodeWithText("2 份").assertIsDisplayed()
        compose.onNodeWithText("4292 kJ").assertExists()
    }
}
