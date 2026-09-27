package com.gymstatistics

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymstatistics.ai.DeepSeekFoodAnalyzer
import com.gymstatistics.data.ExerciseRecord
import com.gymstatistics.data.AiSettingsRepository
import com.gymstatistics.data.FoodAnalysisRecord
import com.gymstatistics.data.FoodAnalysisImageStore
import com.gymstatistics.data.FoodAnalysisRepository
import com.gymstatistics.data.ImportMode
import com.gymstatistics.data.ImportResult
import com.gymstatistics.data.MuscleSelection
import com.gymstatistics.data.SessionRecord
import com.gymstatistics.data.PlannedExercise
import com.gymstatistics.data.TrainingPlan
import com.gymstatistics.data.TrainingPlanDay
import com.gymstatistics.data.WorkoutData
import com.gymstatistics.data.WorkoutRepository
import com.gymstatistics.data.completedPlanExerciseIds
import com.gymstatistics.data.exerciseRecordForPlan
import com.gymstatistics.data.planDayIndex
import com.gymstatistics.data.reorderManualExercises
import com.gymstatistics.server.LanSyncServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.util.UUID

data class UiState(
    val data: WorkoutData = WorkoutData(),
    val loaded: Boolean = false,
    val syncEnabled: Boolean = false,
    val syncUrl: String? = null,
    val message: String? = null,
    val foodAnalyses: List<FoodAnalysisRecord> = emptyList(),
    val deepSeekKeyConfigured: Boolean = false,
    val foodAnalysisLoading: Boolean = false,
    val foodAnalysisResult: FoodAnalysisRecord? = null,
    val foodAnalysisError: String? = null,
)

class GymViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val SYNC_PORT = 8931
    }

    private val repo = WorkoutRepository(app)
    private val foodRepo = FoodAnalysisRepository(app)
    private val imageStore = FoodAnalysisImageStore(app)
    private val aiSettings = AiSettingsRepository(app)
    private val foodAnalyzer = DeepSeekFoodAnalyzer(app)
    private var server: LanSyncServer? = null
    private var seedDemo = false
    private var autoSync = false

    var uiState by mutableStateOf(UiState())
        private set

    init {
        viewModelScope.launch {
            var data = repo.load()
            if (seedDemo && data.sessions.isEmpty()) {
                data = demoData()
                repo.save(data)
            }
            uiState = uiState.copy(
                data = data,
                loaded = true,
                foodAnalyses = foodRepo.load().sortedByDescending { it.createdAt },
                deepSeekKeyConfigured = aiSettings.getApiKey() != null,
            )
            if (autoSync) setSync(true)
        }
    }

    /** Handles optional launch extras used by the automated test harness. */
    fun onLaunch(seedDemo: Boolean, autoSync: Boolean) {
        // Called right after construction; the load coroutine in init() reads these
        // flags once its IO work resumes, so seeding/sync run after load.
        this.seedDemo = seedDemo
        this.autoSync = autoSync
    }

    /** Add a session by date, merging exercises into an existing same-date session. */
    fun addSession(date: String, note: String, exercises: List<ExerciseRecord>) {
        if (date.isBlank() || exercises.isEmpty()) {
            uiState = uiState.copy(message = "请填写日期并至少添加一个动作")
            return
        }
        val clean = exercises.filter { it.name.isNotBlank() }
        if (clean.isEmpty()) {
            uiState = uiState.copy(message = "请填写动作名称")
            return
        }
        val current = uiState.data
        val existing = current.sessions.find { it.date == date }
        val newSessions = if (existing != null) {
            val merged = existing.copy(
                exercises = existing.exercises + clean,
                note = if (note.isBlank()) existing.note else note,
            )
            current.sessions.map { if (it.id == existing.id) merged else it }
        } else {
            current.sessions + SessionRecord(
                id = UUID.randomUUID().toString(),
                date = date,
                note = note,
                exercises = clean,
            )
        }
        persist(current.copy(sessions = newSessions))
        uiState = uiState.copy(message = "已保存 ${clean.size} 个动作")
    }

    fun deleteSession(sessionId: String) {
        val current = uiState.data
        persist(current.copy(sessions = current.sessions.filter { it.id != sessionId }))
    }

    /** Add a single action to the day (merge into same-date session). */
    fun addExercise(date: String, note: String, exercise: ExerciseRecord) {
        if (date.isBlank() || exercise.name.isBlank()) {
            uiState = uiState.copy(message = "请填写动作名称")
            return
        }
        val withId = exercise.copy(
            id = if (exercise.id.isEmpty()) UUID.randomUUID().toString() else exercise.id,
            note = note.trim(),
        )
        val current = uiState.data
        val existing = current.sessions.find { it.date == date }
        val newSessions = if (existing != null) {
            val merged = existing.copy(
                exercises = existing.exercises + withId,
            )
            current.sessions.map { if (it.id == existing.id) merged else it }
        } else {
            current.sessions + SessionRecord(
                id = UUID.randomUUID().toString(),
                date = date,
                exercises = listOf(withId),
            )
        }
        persist(current.copy(sessions = newSessions))
        uiState = uiState.copy(message = "已保存 ${exercise.name}")
    }

    fun reorderExercises(date: String, exerciseId: String, direction: Int) {
        val current = uiState.data
        val session = current.sessions.firstOrNull { it.date == date } ?: return
        val reordered = reorderManualExercises(session.exercises, exerciseId, direction)
        if (reordered == session.exercises) return
        val sessions = current.sessions.map { if (it.id == session.id) session.copy(exercises = reordered) else it }
        persist(current.copy(sessions = sessions))
    }

    fun saveTrainingPlan(plan: TrainingPlan) {
        val normalizedDays = (1..7).map { weekday ->
            plan.days.firstOrNull { it.weekday == weekday } ?: TrainingPlanDay(weekday)
        }
        persist(uiState.data.copy(trainingPlan = plan.copy(days = normalizedDays)))
        uiState = uiState.copy(message = "训练计划已保存")
    }

    fun addPlannedExercise(weekday: Int, exercise: PlannedExercise) {
        if (weekday !in 1..7 || exercise.name.isBlank()) {
            uiState = uiState.copy(message = "请填写计划动作名称")
            return
        }
        val plan = uiState.data.trainingPlan ?: TrainingPlan()
        val day = plan.days.firstOrNull { it.weekday == weekday } ?: TrainingPlanDay(weekday)
        val idAlreadyUsed = plan.days.any { candidate -> candidate.exercises.any { it.id == exercise.id } }
        val withId = exercise.copy(
            id = if (exercise.id.isBlank() || idAlreadyUsed) UUID.randomUUID().toString() else exercise.id,
            name = exercise.name.trim(),
        )
        saveTrainingPlan(plan.copy(days = plan.days.map { if (it.weekday == weekday) day.copy(exercises = day.exercises + withId) else it }))
    }

    fun updatePlannedExercise(weekday: Int, exercise: PlannedExercise) {
        if (weekday !in 1..7 || exercise.name.isBlank()) {
            uiState = uiState.copy(message = "请填写计划动作名称")
            return
        }
        val plan = uiState.data.trainingPlan ?: TrainingPlan()
        if (plan.days.any { day -> day.weekday != weekday && day.exercises.any { it.id == exercise.id } }) {
            uiState = uiState.copy(message = "计划动作 ID 重复，无法保存")
            return
        }
        saveTrainingPlan(plan.copy(days = plan.days.map { day ->
            if (day.weekday != weekday) day else day.copy(
                exercises = day.exercises.map { if (it.id == exercise.id) exercise.copy(name = exercise.name.trim()) else it },
            )
        }))
    }

    fun deletePlannedExercise(weekday: Int, plannedExerciseId: String) {
        val plan = uiState.data.trainingPlan ?: return
        saveTrainingPlan(plan.copy(days = plan.days.map { day ->
            if (day.weekday == weekday) day.copy(exercises = day.exercises.filterNot { it.id == plannedExerciseId }) else day
        }))
    }

    fun reorderPlannedExercises(weekday: Int, plannedExerciseId: String, direction: Int) {
        val plan = uiState.data.trainingPlan ?: return
        val updatedDays = plan.days.map { day ->
            if (day.weekday != weekday) return@map day
            val index = day.exercises.indexOfFirst { it.id == plannedExerciseId }
            val target = index + direction
            if (index < 0 || target !in day.exercises.indices) return@map day
            val exercises = day.exercises.toMutableList()
            val moved = exercises.removeAt(index)
            exercises.add(target, moved)
            day.copy(exercises = exercises)
        }
        saveTrainingPlan(plan.copy(days = updatedDays))
    }

    fun copyTrainingPlanDay(sourceWeekday: Int, targetWeekday: Int) {
        if (sourceWeekday !in 1..7 || targetWeekday !in 1..7 || sourceWeekday == targetWeekday) return
        val plan = uiState.data.trainingPlan ?: return
        val source = plan.days.firstOrNull { it.weekday == sourceWeekday } ?: return
        val copied = com.gymstatistics.data.copyPlanDay(source, targetWeekday)
        saveTrainingPlan(plan.copy(days = plan.days.map { if (it.weekday == targetWeekday) copied else it }))
    }

    fun setPlanRestDay(weekday: Int, restDay: Boolean) {
        val plan = uiState.data.trainingPlan ?: TrainingPlan()
        saveTrainingPlan(plan.copy(days = plan.days.map { if (it.weekday == weekday) it.copy(restDay = restDay) else it }))
    }

    fun completePlannedExercise(date: String, plannedExerciseId: String) {
        val parsedDate = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return
        val plan = uiState.data.trainingPlan ?: return
        val day = plan.days.firstOrNull { it.weekday == planDayIndex(parsedDate) } ?: return
        val planned = day.exercises.firstOrNull { it.id == plannedExerciseId } ?: return
        val existing = uiState.data.sessions.firstOrNull { it.date == date }
        if (plannedExerciseId in completedPlanExerciseIds(existing)) {
            uiState = uiState.copy(message = "该计划动作已记录")
            return
        }
        val record = exerciseRecordForPlan(planned, UUID.randomUUID().toString())
        val sessions = if (existing == null) {
            uiState.data.sessions + SessionRecord(UUID.randomUUID().toString(), date, exercises = listOf(record))
        } else {
            uiState.data.sessions.map { if (it.id == existing.id) it.copy(exercises = it.exercises + record) else it }
        }
        persist(uiState.data.copy(sessions = sessions))
        uiState = uiState.copy(message = "已记录计划动作 ${planned.name}")
    }

    fun cancelPlannedExercise(date: String, plannedExerciseId: String) {
        val current = uiState.data
        val session = current.sessions.firstOrNull { it.date == date } ?: return
        val remaining = session.exercises.filterNot { it.plannedExerciseId == plannedExerciseId }
        if (remaining.size == session.exercises.size) return
        val sessions = if (remaining.isEmpty()) {
            current.sessions.filterNot { it.id == session.id }
        } else {
            current.sessions.map { if (it.id == session.id) session.copy(exercises = remaining) else it }
        }
        persist(current.copy(sessions = sessions))
        uiState = uiState.copy(message = "已取消计划动作")
    }

    fun associateExerciseWithPlan(date: String, exerciseId: String, plannedExerciseId: String) {
        val current = uiState.data
        val session = current.sessions.firstOrNull { it.date == date } ?: return
        if (plannedExerciseId in completedPlanExerciseIds(session)) {
            uiState = uiState.copy(message = "该计划动作已关联其他记录")
            return
        }
        val exercise = session.exercises.firstOrNull { it.id == exerciseId } ?: return
        val changed = session.copy(exercises = session.exercises.map {
            if (it.id == exercise.id) it.copy(plannedExerciseId = plannedExerciseId) else it
        })
        persist(current.copy(sessions = current.sessions.map { if (it.id == session.id) changed else it }))
        uiState = uiState.copy(message = "已关联计划动作")
    }

    /** Update a single action; its muscle selection applies to every same-name action. */
    fun updateExercise(date: String, exerciseId: String, updated: ExerciseRecord, note: String) {
        val current = uiState.data
        val session = current.sessions.find { it.date == date } ?: return
        val newExercises = session.exercises.map {
            if (it.id == exerciseId) {
                val edited = updated.copy(id = exerciseId, note = note.trim())
                edited.copy(plannedExerciseId = updated.plannedExerciseId ?: it.plannedExerciseId)
            } else it
        }
        val newSession = session.copy(exercises = newExercises)
        val changedSessions = current.sessions.map { if (it.id == session.id) newSession else it }
        val newSessions = changedSessions.map { day ->
            day.copy(exercises = day.exercises.map { exercise ->
                if (exercise.name == updated.name) exercise.copy(muscles = updated.muscles) else exercise
            })
        }
        persist(current.copy(sessions = newSessions))
        uiState = uiState.copy(message = "已更新 ${updated.name}")
    }

    /** Update muscle groups for every occurrence of an action with the same name. */
    fun updateMusclesForAction(name: String, muscles: List<MuscleSelection>) {
        if (name.isBlank()) return
        val clean = muscles.distinctBy { it.id }
        val current = uiState.data
        val newSessions = current.sessions.map { day ->
            day.copy(exercises = day.exercises.map { exercise ->
                if (exercise.name == name) exercise.copy(muscles = clean) else exercise
            })
        }
        persist(current.copy(sessions = newSessions))
        uiState = uiState.copy(message = "已更新 $name 的锻炼肌群")
    }

    /** Delete a single action (by id) from the day's session; drop the session if empty. */
    fun deleteExercise(date: String, exerciseId: String) {
        val current = uiState.data
        val session = current.sessions.find { it.date == date } ?: return
        val newExercises = session.exercises.filter { it.id != exerciseId }
        val newSessions = if (newExercises.isEmpty()) {
            current.sessions.filter { it.id != session.id }
        } else {
            current.sessions.map { if (it.id == session.id) session.copy(exercises = newExercises) else it }
        }
        persist(current.copy(sessions = newSessions))
    }

    fun clearMessage() {
        uiState = uiState.copy(message = null)
    }

    fun setDeepSeekApiKey(key: String) {
        if (key.isBlank()) {
            uiState = uiState.copy(message = "API Key 不能为空")
            return
        }
        runCatching { aiSettings.setApiKey(key) }
            .onSuccess { uiState = uiState.copy(deepSeekKeyConfigured = true, message = "API Key 已保存") }
            .onFailure { uiState = uiState.copy(message = "API Key 保存失败") }
    }

    fun clearDeepSeekApiKey() {
        aiSettings.clearApiKey()
        uiState = uiState.copy(deepSeekKeyConfigured = false, message = "API Key 已删除")
    }

    fun analyzeFoodImage(uri: Uri) {
        val key = aiSettings.getApiKey()
        if (key.isNullOrBlank()) {
            uiState = uiState.copy(message = "请先配置 DeepSeek API Key")
            return
        }
        uiState = uiState.copy(foodAnalysisLoading = true, foodAnalysisError = null, foodAnalysisResult = null)
        viewModelScope.launch {
            runCatching { foodAnalyzer.analyze(uri, key) }
                .onSuccess { result ->
                    uiState = uiState.copy(foodAnalysisLoading = false, foodAnalysisResult = result)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        foodAnalysisLoading = false,
                        foodAnalysisError = error.message ?: "食物分析失败",
                        message = error.message ?: "食物分析失败",
                    )
                }
        }
    }

    fun clearFoodAnalysisResult() {
        uiState = uiState.copy(foodAnalysisResult = null, foodAnalysisError = null)
    }

    fun saveFoodAnalysis(
        record: FoodAnalysisRecord,
        sourceImage: Uri?,
        onImageSaveFailed: () -> Unit,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            val imageName = sourceImage?.let { withContext(Dispatchers.IO) { imageStore.save(it, record.id) } }
            if (sourceImage != null && imageName == null) {
                onImageSaveFailed()
                return@launch
            }
            persistFoodAnalysis(record.copy(imageFileName = imageName ?: record.imageFileName))
            onSaved()
        }
    }

    fun saveFoodAnalysis(record: FoodAnalysisRecord) = saveFoodAnalysisWithoutImage(record)

    fun saveFoodAnalysisWithoutImage(record: FoodAnalysisRecord) {
        viewModelScope.launch { persistFoodAnalysis(record) }
    }

    private suspend fun persistFoodAnalysis(record: FoodAnalysisRecord) {
        val records = (uiState.foodAnalyses.filterNot { it.id == record.id } + record)
            .sortedByDescending { it.createdAt }
        uiState = uiState.copy(foodAnalyses = records, foodAnalysisResult = null, message = "已保存食物分析")
        foodRepo.saveAll(records)
    }

    fun deleteFoodAnalysis(id: String) {
        val removed = uiState.foodAnalyses.firstOrNull { it.id == id }
        val records = uiState.foodAnalyses.filterNot { it.id == id }
        uiState = uiState.copy(foodAnalyses = records, message = "已删除食物分析")
        viewModelScope.launch {
            imageStore.delete(removed?.imageFileName.orEmpty())
            foodRepo.saveAll(records)
        }
    }

    /** Turn the fatigue warning for this action on/off (persisted). */
    fun setFatigueSuppressed(name: String, suppressed: Boolean) {
        val current = uiState.data
        val newSet = if (suppressed) current.suppressedFatigue + name else current.suppressedFatigue - name
        persist(current.copy(suppressedFatigue = newSet))
    }

    /** Import sessions from an external source (a file or a PC POST), handling duplicates. */
    fun importSessions(
        incoming: List<SessionRecord>,
        mode: ImportMode,
        overwriteDuplicates: Boolean,
        trainingPlan: TrainingPlan? = null,
    ): ImportResult {
        val current = uiState.data
        val sessions = current.sessions.toMutableList()
        val usedExerciseIds = sessions
            .flatMap { it.exercises }
            .mapNotNull { it.id.takeIf(String::isNotBlank) }
            .toMutableSet()
        var added = 0; var duplicates = 0; var skipped = 0; var overwritten = 0
        val incomingByDate = incoming.filter { sessionMatchesMode(it, mode) }.groupBy { it.date }
        for ((date, inc) in incomingByDate) {
            val normalizedIncoming = inc.map { session ->
                session.copy(exercises = session.exercises.map { exercise ->
                    var id = exercise.id
                    if (id.isBlank() || !usedExerciseIds.add(id)) {
                        do {
                            id = UUID.randomUUID().toString()
                        } while (!usedExerciseIds.add(id))
                    }
                    exercise.copy(id = id)
                })
            }
            val idx = sessions.indexOfFirst { it.date == date }
            if (idx >= 0) {
                duplicates += inc.size
                if (overwriteDuplicates) {
                    val existing = sessions[idx]
                    sessions[idx] = existing.copy(exercises = existing.exercises + normalizedIncoming.flatMap { it.exercises })
                    overwritten += inc.size
                } else {
                    skipped += inc.size
                }
            } else {
                sessions.addAll(normalizedIncoming)
                added += inc.size
            }
        }
        val newData = current.copy(
            sessions = sessions,
            trainingPlan = if (mode is ImportMode.ALL && trainingPlan != null) trainingPlan else current.trainingPlan,
        )
        persist(newData)
        uiState = uiState.copy(message = "已导入:新增 $added 条,重复 $duplicates 条(跳过 $skipped/合并 $overwritten)")
        return ImportResult(added, duplicates, skipped, overwritten)
    }

    /** Number of incoming dates that already exist on the phone (drives the duplicate prompt). */
    fun importPreview(incoming: List<SessionRecord>, mode: ImportMode): Int {
        val incomingDates = incoming.filter { sessionMatchesMode(it, mode) }.map { it.date }.toSet()
        val existing = uiState.data.sessions.map { it.date }.toSet()
        return incomingDates.count { it in existing }
    }

    private fun sessionMatchesMode(s: SessionRecord, mode: ImportMode): Boolean =
        when (mode) {
            is ImportMode.ALL -> true
            is ImportMode.DATE -> s.date == mode.date
        }

    fun setSync(enabled: Boolean) {
        if (enabled) {
            if (server == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val s = LanSyncServer(getApplication(), SYNC_PORT, { uiState.data }) { incoming, mode, overwrite, trainingPlan ->
                            importSessions(incoming, mode, overwrite, trainingPlan)
                        }
                        s.start(500, false)
                        server = s
                        uiState = uiState.copy(
                            syncEnabled = true,
                            syncUrl = "http://${lanIp(getApplication())}:$SYNC_PORT",
                        )
                    } catch (e: Exception) {
                        uiState = uiState.copy(message = "同步开启失败: ${e.message}")
                    }
                }
            } else {
                uiState = uiState.copy(
                    syncEnabled = true,
                    syncUrl = "http://${lanIp(getApplication())}:$SYNC_PORT",
                )
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                try { server?.stop() } catch (_: Exception) {}
                server = null
            }
            uiState = uiState.copy(syncEnabled = false, syncUrl = null)
        }
    }

    private fun persist(newData: WorkoutData) {
        uiState = uiState.copy(data = newData)
        viewModelScope.launch { repo.save(newData) }
    }

    private fun demoData(): WorkoutData {
        val d = { offset: Long -> java.time.LocalDate.now().minusDays(offset).toString() }
        fun uid() = UUID.randomUUID().toString()

        // 6 trend actions spanning ~30 days, two groups of 3.
        val offsets = listOf(0L, 2L, 4L, 6L, 8L, 10L, 12L, 14L, 16L, 18L, 20L, 22L, 24L, 26L, 28L)
        // Group 1: data varies, count stays 8.
        val dataWave = listOf(60.0, 65.0, 62.0, 68.0, 63.0, 72.0, 66.0, 70.0, 64.0, 74.0, 68.0, 71.0, 63.0, 69.0, 67.0) // 上下起伏
        val dataDesc = listOf(85.0, 84.0, 82.0, 80.0, 78.0, 76.0, 74.0, 72.0, 70.0, 68.0, 66.0, 64.0, 62.0, 60.0, 58.0) // 逐渐下降
        val dataAsc = listOf(50.0, 51.0, 53.0, 54.0, 56.0, 58.0, 60.0, 62.0, 63.0, 65.0, 67.0, 69.0, 70.0, 72.0, 73.0) // 逐渐上升
        // Group 2: data stays 70, count varies.
        val cntWave = listOf(8, 10, 9, 12, 10, 11, 13, 11, 12, 14, 12, 13, 11, 12, 10) // 上下起伏
        val cntDesc = listOf(14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 2, 1)        // 逐渐下降
        val cntAsc = listOf(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)     // 逐渐上升

        val byDate = LinkedHashMap<String, MutableList<ExerciseRecord>>()
        fun put(date: String, vararg ex: ExerciseRecord) {
            byDate.getOrPut(date) { mutableListOf() }.addAll(ex)
        }

        offsets.forEachIndexed { i, off ->
            put(
                d(off),
                ExerciseRecord("起伏数据", uid(), dataWave[i], "kg", 8, 3),
                ExerciseRecord("渐降数据", uid(), dataDesc[i], "kg", 8, 3),
                ExerciseRecord("渐升数据", uid(), dataAsc[i], "kg", 8, 3),
                ExerciseRecord("恒定起伏", uid(), 70.0, "kg", cntWave[i], 3),
                ExerciseRecord("恒定渐降", uid(), 70.0, "kg", cntDesc[i], 3),
                ExerciseRecord("恒定渐升", uid(), 70.0, "kg", cntAsc[i], 3),
            )
        }
        // 卧推 with two units (kg + lbs) on recent days, to show unit-split charts.
        put(d(0), ExerciseRecord("卧推", uid(), 70.0, "kg", 8, 4), ExerciseRecord("卧推", uid(), 155.0, "lbs", 8, 3))
        put(d(2), ExerciseRecord("卧推", uid(), 65.0, "kg", 10, 3), ExerciseRecord("卧推", uid(), 145.0, "lbs", 10, 3))
        put(d(6), ExerciseRecord("卧推", uid(), 60.0, "kg", 12, 3), ExerciseRecord("卧推", uid(), 135.0, "lbs", 12, 2))

        val sessions = byDate.map { (date, exs) -> SessionRecord(id = uid(), date = date, note = "", exercises = exs) }
        return WorkoutData(sessions = sessions)
    }
}

private fun lanIp(context: Context): String {
    try {
        // 1) Prefer a site-local IPv4 (e.g. 192.168.x.x) — directly usable by a LAN PC.
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
            for (addr in nif.inetAddresses) {
                val h = addr.hostAddress ?: continue
                if (h.contains(".") && addr.isSiteLocalAddress) return h
            }
        }
        // 2) Fallback: any non-loopback IPv4.
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in nif.inetAddresses) {
                val h = addr.hostAddress ?: continue
                if (h.contains(".")) return h
            }
        }
        // 3) Last resort: any site-local address (could be IPv6 ULA).
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in nif.inetAddresses) {
                if (addr.isSiteLocalAddress) return addr.hostAddress ?: continue
            }
        }
    } catch (_: Exception) {
    }
    return "127.0.0.1"
}
