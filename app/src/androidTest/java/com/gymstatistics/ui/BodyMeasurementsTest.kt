package com.gymstatistics.ui

import android.app.Application
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gymstatistics.GymViewModel
import com.gymstatistics.MainActivity
import com.gymstatistics.data.WorkoutRepository
import com.gymstatistics.data.*
import com.gymstatistics.server.LanSyncServer
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import android.os.Looper
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BodyMeasurementsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var directory: File
    private lateinit var fixtureApp: Application
    private lateinit var store: ViewModelStore
    private lateinit var model: GymViewModel

    @Before fun openApp() {
        val context = instrumentation.targetContext.applicationContext
        directory = Files.createTempDirectory(context.cacheDir.toPath(), "body-measurements-").toFile()
        fixtureApp = object : Application() {
            init { attachBaseContext(context) }
            override fun getFilesDir(): File = directory
        }
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync {
            store = ViewModelStore()
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(fixtureApp))[GymViewModel::class.java]
            activity.setContent { GymApp(model) }
        }
        compose.waitUntil(10_000) { model.uiState.loaded }
    }

    @After fun closeApp() {
        if (::model.isInitialized) runBlocking { model.viewModelScope.coroutineContext[Job]?.children?.toList().orEmpty().joinAll() }
        instrumentation.runOnMainSync {
            if (::activity.isInitialized) activity.finish()
            if (::store.isInitialized) store.clear()
        }
        if (::directory.isInitialized) check(directory.deleteRecursively())
    }

    @Test fun bodyTabShowsStatisticsAndOpensASeparateEditor() {
        compose.onNodeWithText("身体").performClick()
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        compose.onNodeWithText("身体变化").assertIsDisplayed()
        compose.onNodeWithTag("body-editor").assertDoesNotExist()
        compose.onNodeWithText("添加记录").performClick()
        compose.onNodeWithText(LocalDate.now().toString()).assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("保存身体数据").performClick()
        awaitWrites()
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        compose.onNodeWithText("身体").assertIsSelected()
        compose.onNodeWithText("最新 65.5 kg · ${LocalDate.now()}").assertIsDisplayed()
        val restored = runBlocking { WorkoutRepository(fixtureApp).load() }
        Assert.assertEquals(LocalDate.now().toString(), restored.bodyData!!.records.single().date)
        Assert.assertEquals(65.5, restored.bodyData!!.records.single().values["weight"]!!, 0.0)
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("编辑身体数据").assertIsDisplayed()
    }

    @Test fun dashboardChartSwitchesMetricAndRangeUsingActualMeasurements() {
        val today = LocalDate.now()
        compose.runOnIdle {
            listOf(
                today.minusDays(100) to mapOf("weight" to 70.0),
                today.minusDays(40) to mapOf("weight" to 68.0),
                today.minusDays(2) to mapOf("weight" to 66.0, "waist" to 80.0),
                today to mapOf("weight" to 65.5, "waist" to 79.0),
                today.plusDays(1) to mapOf("weight" to 63.0),
            ).forEach { (date, values) -> model.saveBodyRecord(date.toString(), values) {} }
        }
        awaitWrites()
        compose.onNodeWithText("身体").performClick()
        compose.onNodeWithContentDescription("2个测量日，最低65.5，最高66 kg").assertExists()
        compose.onNodeWithText("近90天").performClick()
        compose.onNodeWithContentDescription("3个测量日，最低65.5，最高68 kg").assertExists()
        compose.onNodeWithText("全部").performClick()
        compose.onNodeWithContentDescription("4个测量日，最低65.5，最高70 kg").assertExists()
        compose.onNodeWithText("体重 · kg").performClick()
        compose.onNodeWithText("腰围 · cm").performClick()
        compose.onNodeWithText("最新 79 cm · $today").assertIsDisplayed()
        compose.onNodeWithContentDescription("2个测量日，最低79，最高80 cm").assertExists()
    }

    @Test fun historyOpensItsOwnDayAndSavingRefreshesTheChart() {
        val today = LocalDate.now().toString()
        compose.runOnIdle { model.saveBodyRecord(today, mapOf("weight" to 66.0, "waist" to 80.0)) {} }
        awaitWrites()
        compose.onNodeWithText("身体").performClick()
        val historyCard = hasText(today) and hasClickAction()
        compose.onNodeWithTag("body-dashboard").performScrollToNode(historyCard)
        compose.onNode(historyCard).performClick()
        compose.onNodeWithText("体重 (kg)").assertTextContains("66")
        compose.onNodeWithText("腰围 (cm)").assertTextContains("80")
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("保存身体数据").performClick()
        awaitWrites()
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        compose.onNodeWithText("最新 65 kg · $today").assertIsDisplayed()
        compose.onNodeWithContentDescription("1个测量日，最低65，最高65 kg").assertExists()
        Assert.assertEquals(mapOf("weight" to 65.0, "waist" to 80.0), runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData!!.records.single().values)
    }

    @Test fun unsavedEditorCanContinueOrDiscardWithoutWriting() {
        compose.onNodeWithText("身体").performClick()
        compose.onNodeWithText("添加记录").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("返回身体数据").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("体重 (kg)").assertTextContains("65.5")
        Espresso.pressBack()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        Assert.assertNull(runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData)
    }

    @Test fun changingEditorDateRequiresDiscardingUnsavedInput() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("前一天").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText(LocalDate.now().toString()).assertIsDisplayed()
        compose.onNodeWithText("前一天").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText(LocalDate.now().minusDays(1).toString()).assertIsDisplayed()
        compose.onNodeWithText("体重 (kg)").assert(hasText(""))
    }

    @Test fun homeShortcutUsesTheSelectedTrainingDate() {
        val today = LocalDate.now()
        val target = if (today.dayOfWeek == java.time.DayOfWeek.MONDAY) today.plusDays(1) else today.minusDays(1)
        compose.onNodeWithText(target.dayOfMonth.toString()).performClick()
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText(target.toString()).assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回身体数据").performClick()
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("${target.year}年${target.monthValue}月${target.dayOfMonth}日").assertIsDisplayed()
        compose.onNodeWithText("身体").performClick()
        compose.onNodeWithText("添加记录").performClick()
        compose.onNodeWithText(today.toString()).assertIsDisplayed()
    }

    @Test fun openingBodyTabDuringInitialLoadKeepsExistingMeasurements() {
        val today = LocalDate.now().toString()
        val saved = WorkoutData(bodyData = saveBodyDay(BodyData(), today, mapOf("weight" to 65.5, "waist" to 80.0)))
        File(directory, "workouts.json").writeText(AppJson.json.encodeToString(WorkoutData.serializer(), saved), Charsets.UTF_8)
        val releaseLoad = CountDownLatch(1)
        val context = instrumentation.targetContext.applicationContext
        fixtureApp = object : Application() {
            init { attachBaseContext(context) }
            override fun getFilesDir(): File {
                if (Looper.myLooper() != Looper.getMainLooper()) releaseLoad.await()
                return directory
            }
        }
        try {
            instrumentation.runOnMainSync {
                store.clear()
                store = ViewModelStore()
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(fixtureApp))[GymViewModel::class.java]
                activity.setContent { GymApp(model) }
            }
            compose.onNodeWithText("身体").performClick()
            compose.waitForIdle()
            Assert.assertFalse(model.uiState.loaded)
        } finally { releaseLoad.countDown() }
        compose.waitUntil(10_000) { model.uiState.loaded }
        compose.onNodeWithText("添加记录").performClick()
        compose.onNodeWithText("腰围 (cm)").assertTextContains("80")
        compose.onNodeWithText("体重 (kg)").performTextReplacement("66")
        save()
        val restored = runBlocking { WorkoutRepository(fixtureApp).load() }
        Assert.assertEquals(mapOf("weight" to 66.0, "waist" to 80.0), restored.bodyData!!.records.single().values)
    }

    @Test fun saveEditAndDeleteUseSelectedDayAndSurviveRepositoryReload() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        compose.onNodeWithText("腰围 (cm)").performTextReplacement("80")
        save()
        val reloaded = runBlocking { WorkoutRepository(fixtureApp).load() }
        Assert.assertEquals(LocalDate.now().toString(), reloaded.bodyData!!.records.single().date)
        Assert.assertEquals(mapOf("weight" to 65.5, "waist" to 80.0), reloaded.bodyData!!.records.single().values)
        Assert.assertTrue(reloaded.sessions.isEmpty())
        compose.onNodeWithText("编辑身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("64.8")
        save()
        Assert.assertEquals(64.8, runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData!!.records.single().values["weight"]!!, 0.0)
        compose.onNodeWithText("编辑身体数据").performClick()
        compose.onNodeWithTag("body-editor").performScrollToNode(hasText("删除当天记录"))
        compose.onNodeWithText("删除当天记录").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10_000) { model.uiState.data.bodyData?.records?.isEmpty() == true }
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("记录身体数据").assertIsDisplayed()
    }

    @Test fun customMetricRenameAndDisableKeepRecordedValues() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithContentDescription("管理身体指标").performClick()
        compose.onNodeWithText("添加指标").performClick()
        compose.onNodeWithText("指标名称").performTextReplacement("颈围")
        compose.onNodeWithText("单位").performTextReplacement("cm")
        compose.onNodeWithText("保存指标").performClick()
        compose.waitUntil(10_000) { model.uiState.data.bodyData?.metrics?.any { it.name == "颈围" } == true }
        Espresso.pressBack()
        compose.onNodeWithTag("body-editor").performScrollToNode(hasText("颈围 (cm)"))
        compose.onNodeWithText("颈围 (cm)").performTextReplacement("35")
        save()
        compose.onNodeWithText("编辑身体数据").performClick()
        compose.onNodeWithContentDescription("管理身体指标").performClick()
        compose.onNodeWithTag("body-metrics").performScrollToNode(hasContentDescription("编辑指标颈围"))
        compose.onNodeWithContentDescription("编辑指标颈围").performClick()
        compose.onNodeWithText("指标名称").performTextReplacement("晨起颈围")
        compose.onNodeWithText("保存指标").performClick()
        compose.waitUntil(10_000) { model.uiState.data.bodyData?.metrics?.any { it.name == "晨起颈围" } == true }
        compose.onNodeWithTag("body-metrics").performScrollToNode(hasContentDescription("停用晨起颈围"))
        compose.onNodeWithContentDescription("停用晨起颈围").performClick()
        compose.waitUntil(10_000) { model.uiState.data.bodyData?.metrics?.any { it.name == "晨起颈围" && !it.enabled } == true }
        val data = runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData!!
        val metric = data.metrics.single { it.name == "晨起颈围" }
        Assert.assertEquals(35.0, data.records.single().values[metric.id]!!, 0.0)
        Espresso.pressBack()
        compose.onNodeWithText("颈围 (cm)").assertDoesNotExist()
    }

    @Test fun invalidInputStaysInEditorWithoutSaving() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("0")
        compose.onNodeWithText("保存身体数据").performClick()
        compose.onNodeWithText("请为体重填写大于 0 的数值").assertIsDisplayed()
        compose.onNodeWithText("体重 (kg)").assertTextContains("0")
        Assert.assertNull(runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData)
    }

    @Test fun failedDiskSaveKeepsEditorInputAndRetryWorks() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        val blocked = File(directory, "workouts.json.new")
        Assert.assertTrue(blocked.mkdir())
        compose.onNodeWithText("保存身体数据").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("保存失败，输入已保留，请重试").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("体重 (kg)").assertTextContains("65.5")
        Assert.assertFalse(File(directory, "workouts.json").exists())
        Assert.assertTrue(blocked.delete())
        save()
        Assert.assertEquals(65.5, runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData!!.records.single().values["weight"]!!, 0.0)
    }

    @Test fun failedBodyDeletionCannotLeakIntoAnUnrelatedWorkoutSave() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        save()
        compose.onNodeWithText("编辑身体数据").performClick()
        compose.onNodeWithTag("body-editor").performScrollToNode(hasText("删除当天记录"))
        val blocked = File(directory, "workouts.json.new")
        Assert.assertTrue(blocked.mkdir())
        compose.onNodeWithText("删除当天记录").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("删除失败，请重试").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertTrue(blocked.delete())
        Espresso.pressBack()
        compose.runOnIdle { model.addSession(LocalDate.now().toString(), "", listOf(ExerciseRecord("失败后记录训练", id = "after-failure", count = 10))) }
        runBlocking { model.viewModelScope.coroutineContext[Job]?.children?.toList().orEmpty().joinAll() }
        val restored = runBlocking { WorkoutRepository(fixtureApp).load() }
        Assert.assertEquals(65.5, restored.bodyData!!.records.single().values["weight"]!!, 0.0)
        Assert.assertEquals("失败后记录训练", restored.sessions.single().exercises.single().name)
    }

    @Test fun backWhileSaveIsWaitingDoesNotLeaveTheEditorAndFailureKeepsInput() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        Espresso.closeSoftKeyboard()
        // Hold the actual repository lock to delay disk IO without replacing the writer.
        val repoField = GymViewModel::class.java.getDeclaredField("repo").apply { isAccessible = true }
        val repository = repoField.get(model) as WorkoutRepository
        val mutexField = WorkoutRepository::class.java.getDeclaredField("mutex").apply { isAccessible = true }
        val mutex = mutexField.get(repository) as Mutex
        val blocked = File(directory, "workouts.json.new")
        Assert.assertTrue(blocked.mkdir())
        runBlocking { mutex.lock() }
        try {
            compose.onNodeWithText("保存身体数据").performClick()
            compose.onNodeWithText("保存中…").assertIsDisplayed()
            compose.onNodeWithText("首页").assertDoesNotExist()
            compose.onNodeWithContentDescription("返回身体数据").assertIsNotEnabled()
            Espresso.pressBack()
            compose.onNodeWithText("保存中…").assertIsDisplayed()
            Assert.assertFalse(activity.isFinishing)
        } finally { mutex.unlock() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("保存失败，输入已保留，请重试").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("体重 (kg)").assertTextContains("65.5")
        Assert.assertNull(model.uiState.data.bodyData)
        Assert.assertTrue(blocked.delete())
        save()
    }

    @Test fun queuedBodyChangeAndWorkoutChangeBothSurviveReload() {
        compose.runOnIdle {
            model.saveBodyRecord(LocalDate.now().toString(), mapOf("weight" to 65.5)) {}
            model.addExercise(LocalDate.now().toString(), "", ExerciseRecord("并发训练", count = 10))
        }
        runBlocking { model.viewModelScope.coroutineContext[Job]?.children?.toList().orEmpty().joinAll() }
        val reloaded = runBlocking { WorkoutRepository(fixtureApp).load() }
        Assert.assertEquals(65.5, reloaded.bodyData!!.records.single().values["weight"]!!, 0.0)
        Assert.assertEquals("并发训练", reloaded.sessions.single().exercises.single().name)
    }

    @Test fun oldMissingAndDuplicateIdsAreNormalizedWithoutChangingRecordedValues() {
        val old = WorkoutData(sessions = listOf(SessionRecord("", "2026-10-24", note = "旧备注", exercises = listOf(
            ExerciseRecord("旧动作A", data = 10.0, count = 8),
            ExerciseRecord("旧动作B", id = "duplicate", data = 20.0, unit = "kg"),
            ExerciseRecord("旧动作C", id = "duplicate", data = 30.0, sets = 3),
        ))))
        File(directory, "workouts.json").writeText(AppJson.json.encodeToString(WorkoutData.serializer(), old), Charsets.UTF_8)
        val restored = runBlocking { WorkoutRepository(fixtureApp).load() }
        Assert.assertTrue(restored.sessions.single().id.isNotBlank())
        val exercises = restored.sessions.single().exercises
        Assert.assertTrue(exercises.all { it.id.isNotBlank() })
        Assert.assertEquals(3, exercises.map { it.id }.distinct().size)
        Assert.assertEquals(listOf(10.0, 20.0, 30.0), exercises.map { it.data })
        Assert.assertEquals("旧备注", restored.sessions.single().note)
        Assert.assertEquals("2026-10-24", restored.sessions.single().date)
        Assert.assertEquals("duplicate", exercises[1].id)
    }

    @Test fun backfilledDayDoesNotChangeTrainingDateAndStatisticsUsesItsActualValue() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("前一天").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("66.0")
        compose.onNodeWithText("保存身体数据").performClick()
        awaitWrites()
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        compose.onNodeWithText("首页").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录身体数据").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("这一天尚未记录").assertIsDisplayed()
        val record = runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData!!.records.single()
        Assert.assertEquals(LocalDate.now().minusDays(1).toString(), record.date)
        compose.onNodeWithText("统计").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("身体变化"))
        compose.onNodeWithText("最新 66 kg · ${record.date}").assertIsDisplayed()
        compose.onNodeWithContentDescription("1个测量日，最低66，最高66 kg").assertExists()
    }

    @Test fun actualHttpBackupRoundTripsBodyDataAndRejectsUnitConflictAtomically() {
        compose.onNodeWithText("记录身体数据").performClick()
        compose.onNodeWithText("体重 (kg)").performTextReplacement("65.5")
        save()
        val server = LanSyncServer(fixtureApp, 0, { model.uiState.data }) { sessions, mode, overwrite, plan, body ->
            runBlocking { model.importBackup(sessions, mode, overwrite, plan, body) }
        }
        server.start(5000, false)
        try {
            fun request(path: String, json: String? = null): Pair<Int, String> {
                val connection = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                try {
                    if (json != null) {
                        connection.requestMethod = "POST"; connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    val text = (if (status >= 400) connection.errorStream else connection.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    return status to text
                } finally { connection.disconnect() }
            }
            val export = request("/api/export.json")
            Assert.assertEquals(200, export.first)
            val payload = AppJson.json.decodeFromString(SyncPayload.serializer(), export.second)
            Assert.assertEquals(65.5, payload.bodyData!!.records.single().values["weight"]!!, 0.0)
            Assert.assertEquals(200, request("/api/import", """{"sessions":[],"overwriteDuplicates":true}""").first)
            Assert.assertEquals(payload.bodyData, runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData)
            val conflicting = """{"sessions":[{"id":"bad","date":"2026-10-01","exercises":[{"name":"不应导入"}]}],"bodyData":{"metrics":[{"id":"weight","name":"体重","unit":"lbs"}],"records":[]}}"""
            val error = request("/api/import", conflicting)
            Assert.assertEquals(400, error.first)
            Assert.assertTrue(error.second.contains("单位"))
            Assert.assertTrue(model.uiState.data.sessions.isEmpty())
            val date = LocalDate.now().toString()
            val body = """{"metrics":[{"id":"weight","name":"体重","unit":"kg"}],"records":[{"date":"$date","values":{"weight":64.0}}]}"""
            val skip = request("/api/import", """{"sessions":[],"mode":"date","date":"$date","bodyData":$body}""")
            Assert.assertEquals(200, skip.first)
            Assert.assertEquals(1, AppJson.json.decodeFromString(ImportResponse.serializer(), skip.second).skipped)
            val replace = request("/api/import", """{"sessions":[],"mode":"date","date":"$date","overwriteDuplicates":true,"bodyData":$body}""")
            Assert.assertEquals(200, replace.first)
            Assert.assertEquals(1, AppJson.json.decodeFromString(ImportResponse.serializer(), replace.second).overwritten)
            Assert.assertEquals(64.0, runBlocking { WorkoutRepository(fixtureApp).load() }.bodyData!!.records.single().values["weight"]!!, 0.0)
            Assert.assertEquals("date,exercise,data,unit,count,sets", request("/api/export.csv").second.removePrefix("\uFEFF").trim())
        } finally { server.stop() }
    }

    private fun save() {
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("保存身体数据").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("保存身体数据").performClick()
        awaitWrites()
        compose.onNodeWithTag("body-dashboard").assertIsDisplayed()
        compose.onNodeWithText("身体").assertIsSelected()
        compose.onNodeWithText("首页").performClick()
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("编辑身体数据").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("保存后页面未返回：body=${model.uiState.data.bodyData}; message=${model.uiState.message}; jobs=${model.viewModelScope.coroutineContext[Job]?.children?.map { it.isActive }?.toList()}\n" + compose.onRoot(useUnmergedTree = true).printToString(), error)
        }
        compose.waitForIdle()
    }

    private fun awaitWrites() {
        runBlocking { model.viewModelScope.coroutineContext[Job]?.children?.toList().orEmpty().joinAll() }
        compose.waitForIdle()
    }
}
