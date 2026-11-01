package com.gymstatistics.ui

import android.content.Intent
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.*
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
import com.gymstatistics.data.NutritionFavoritesRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
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

    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun weightSheetRejectsInvalidInputAndEditsExistingMeal() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.onNodeWithText("罗森").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("搜索菜品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索菜品").performTextReplacement("香肠面包")
        compose.onNodeWithContentDescription("添加香肠面包").performClick()
        compose.onNodeWithText("食用重量（克）").performTextReplacement("NaN")
        compose.onNodeWithText("加入配餐").assertIsNotEnabled()
        compose.onNodeWithText("食用重量（克）").performTextReplacement("95")
        Espresso.closeSoftKeyboard()
        screenshot("nutrition-weight-sheet")
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithText("253 kcal").assertIsDisplayed()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithContentDescription("修改香肠面包重量").performClick()
        compose.onNodeWithText("食用重量（克）").performTextReplacement("190")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithText("506 kcal").assertIsDisplayed()
    }

    @Test fun lawsonWeightFavoritesAndBrandState() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("罗森").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("罗森").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("搜索菜品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索菜品").performTextReplacement("香肠面包")
        compose.onNodeWithContentDescription("添加香肠面包").performClick()
        compose.onNodeWithText("食用重量（克）").performTextReplacement("95")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithText("253 kcal").assertIsDisplayed()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("钠 未知 mg").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithContentDescription("收藏香肠面包").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("取消收藏香肠面包").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("麦当劳").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("搜索餐品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("罗森").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("取消收藏香肠面包").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("253 kcal").assertIsDisplayed()
        val saved = runBlocking { NutritionFavoritesRepository(instrumentation.targetContext).load() }
        assertTrue(saved.any { it.startsWith("lawson-cn-search-") })
    }

    @Test fun laoxiangjiCanCalculateNutritionByWeight() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("老乡鸡").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("老乡鸡").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("搜索菜品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索菜品").performTextReplacement("豉汁鱼块")
        compose.onNodeWithContentDescription("添加豉汁鱼块").performClick()
        compose.onNodeWithText("食用重量（克）").performTextReplacement("150")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithText("181.5 kcal").assertIsDisplayed()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("钠 1390.5 mg").assertIsDisplayed()
    }

    @Before fun openApp() {
        File(instrumentation.targetContext.filesDir, "nutrition-favorites.json").delete()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    }

    @After fun closeApp() {
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
        File(instrumentation.targetContext.filesDir, "nutrition-favorites.json").delete()
    }

    @Test fun officialPicturesAndFavoritesSurviveReopeningAndCanBeRemoved() {
        fun openLibrary() {
            compose.onNodeWithContentDescription("营养库").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("搜索餐品").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("搜索餐品").performTextReplacement("薯条")
            Espresso.closeSoftKeyboard()
            compose.waitForIdle()
        }
        openLibrary()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("薯条官方图片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("收藏薯条").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("取消收藏薯条").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("取消收藏薯条").assertIsDisplayed()
        instrumentation.runOnMainSync { activity.finish() }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        openLibrary()
        compose.onNodeWithContentDescription("取消收藏薯条").assertIsDisplayed()
        compose.onNodeWithText("收藏").performClick()
        compose.onNodeWithText("搜索餐品").performTextReplacement("巨无霸")
        compose.onNodeWithText("没有匹配的餐品").assertExists()
        compose.onNodeWithText("搜索餐品").performTextReplacement("薯条")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("添加薯条").performClick()
        compose.onNodeWithContentDescription("选择薯条规格").performClick()
        compose.onNodeWithText("小", useUnmergedTree = true).performClick()
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithContentDescription("取消收藏薯条").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("暂无收藏的餐品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("暂无收藏的餐品").assertIsDisplayed()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("薯条（小）").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("薯条官方图片").fetchSemanticsNodes().isNotEmpty() }
        Espresso.pressBack()
        compose.onNodeWithText("全部").performClick()
        compose.onNodeWithContentDescription("收藏薯条").assertIsDisplayed()
    }

    @Test fun failedFavoriteWriteKeepsThePreviousDiskStateAndCanBeRetried() {
        val directory = Files.createTempDirectory(instrumentation.targetContext.cacheDir.toPath(), "nutrition-favorites-").toFile()
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getFilesDir(): File = directory
        }
        try {
            val repository = NutritionFavoritesRepository(context)
            assertTrue(runBlocking { repository.save(setOf("49")) })
            val blocked = File(directory, "nutrition-favorites.json.new")
            assertTrue(blocked.mkdir())
            val blocker = File(blocked, "block-write").apply { writeText("test") }
            assertFalse(runBlocking { repository.save(setOf("21")) })
            assertEquals(setOf("49"), runBlocking { NutritionFavoritesRepository(context).load() })
            assertTrue(blocker.delete())
            assertTrue(blocked.delete())
            assertTrue(runBlocking { repository.save(setOf("21")) })
            assertEquals(setOf("21"), runBlocking { NutritionFavoritesRepository(context).load() })
        } finally { check(directory.deleteRecursively()) }
    }

    @Test fun failedFavoriteCommitIsNotReportedAsSaved() {
        val directory = Files.createTempDirectory(instrumentation.targetContext.cacheDir.toPath(), "nutrition-commit-").toFile()
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getFilesDir(): File = directory
        }
        try {
            // Allow writing .new, but prevent its final rename into the destination.
            val destination = File(directory, "nutrition-favorites.json").apply { check(mkdir()) }
            val blocker = File(destination, "block-rename").apply { writeText("test") }
            val repository = NutritionFavoritesRepository(context)
            assertFalse(runBlocking { repository.save(setOf("21")) })
            assertTrue(blocker.delete())
            assertTrue(destination.delete())
            assertTrue(runBlocking { repository.save(setOf("21")) })
            assertEquals(setOf("21"), runBlocking { NutritionFavoritesRepository(context).load() })
        } finally { check(directory.deleteRecursively()) }
    }

    @Test fun homeEntryLoadsOfficialDataAndReturnsHome() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.onNodeWithText("营养库").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("搜索餐品")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("nutrition-list")
        compose.onNodeWithText("搜索餐品").performTextReplacement("巨无霸")
        compose.onNodeWithContentDescription("添加巨无霸").performClick()
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("巨无霸（1个）").assertIsDisplayed()
        compose.onNodeWithText("2146 kJ").assertExists()
        // Real back input reaches the sheet's Dialog window before the Activity.
        Espresso.pressBack()
        compose.onNodeWithText("营养库").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("AI食物分析相机").assertIsDisplayed()
        assertEquals(compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x,
            compose.onNodeWithText("这一天没有训练记录").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        screenshot("gym-home")
    }

    @Test fun differentSizesCoexistAndHalfPortionsRecalculateBeforeClearing() {
        compose.onNodeWithContentDescription("营养库").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("搜索餐品")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索餐品").performTextReplacement("薯条")
        compose.onNodeWithContentDescription("添加薯条").performClick()
        compose.onNodeWithContentDescription("选择薯条规格").performClick()
        compose.onNodeWithText("小", useUnmergedTree = true).performClick()
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithContentDescription("添加薯条").performClick()
        compose.onNodeWithContentDescription("选择薯条规格").performClick()
        compose.onNodeWithText("大", useUnmergedTree = true).performClick()
        compose.onNodeWithText("加入配餐").performClick()
        compose.onNodeWithText("查看已选").performClick()
        compose.onNodeWithText("薯条（小）").assertIsDisplayed()
        compose.onNodeWithText("薯条（大）").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("添加巨无霸").performClick()
        compose.onNodeWithText("加入配餐").performSemanticsAction(SemanticsActions.OnClick) { click ->
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
