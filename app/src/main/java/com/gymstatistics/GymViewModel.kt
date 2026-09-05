package com.gymstatistics

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymstatistics.data.ExerciseRecord
import com.gymstatistics.data.SessionRecord
import com.gymstatistics.data.WorkoutData
import com.gymstatistics.data.WorkoutRepository
import com.gymstatistics.server.LanSyncServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.util.UUID

data class UiState(
    val data: WorkoutData = WorkoutData(),
    val loaded: Boolean = false,
    val syncEnabled: Boolean = false,
    val syncUrl: String? = null,
    val message: String? = null,
)

class GymViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val SYNC_PORT = 8931
    }

    private val repo = WorkoutRepository(app)
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
            uiState = uiState.copy(data = data, loaded = true)
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
        val withId = exercise.copy(id = if (exercise.id.isEmpty()) UUID.randomUUID().toString() else exercise.id)
        val current = uiState.data
        val existing = current.sessions.find { it.date == date }
        val newSessions = if (existing != null) {
            val merged = existing.copy(
                exercises = existing.exercises + withId,
                note = if (note.isBlank()) existing.note else note,
            )
            current.sessions.map { if (it.id == existing.id) merged else it }
        } else {
            current.sessions + SessionRecord(
                id = UUID.randomUUID().toString(),
                date = date,
                note = note,
                exercises = listOf(withId),
            )
        }
        persist(current.copy(sessions = newSessions))
        uiState = uiState.copy(message = "已保存 ${exercise.name}")
    }

    /** Update a single action (by id) within the day's session. */
    fun updateExercise(date: String, exerciseId: String, updated: ExerciseRecord, note: String) {
        val current = uiState.data
        val session = current.sessions.find { it.date == date } ?: return
        val newExercises = session.exercises.map { if (it.id == exerciseId) updated.copy(id = exerciseId) else it }
        val newSession = session.copy(
            exercises = newExercises,
            note = if (note.isBlank()) session.note else note,
        )
        persist(current.copy(sessions = current.sessions.map { if (it.id == session.id) newSession else it }))
        uiState = uiState.copy(message = "已更新 ${updated.name}")
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

    fun setSync(enabled: Boolean) {
        if (enabled) {
            if (server == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val s = LanSyncServer(getApplication(), SYNC_PORT) { uiState.data }
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
        fun sess(date: String, note: String, vararg ex: ExerciseRecord) =
            SessionRecord(id = uid(), date = date, note = note, exercises = ex.toList())

        return WorkoutData(
            sessions = listOf(
                sess(d(0), "", ExerciseRecord("卧推", uid(), 70.0, "kg", 8, 4), ExerciseRecord("引体向上", uid(), null, "", 10, 2)),
                sess(d(1), "上肢日", ExerciseRecord("卧推", uid(), 72.5, "kg", 8, 3), ExerciseRecord("肩推", uid(), 40.0, "kg", 10, 3)),
                sess(d(2), "", ExerciseRecord("深蹲", uid(), 85.0, "kg", 8, 4), ExerciseRecord("硬拉", uid(), 110.0, "kg", 5, 3)),
                sess(d(3), "演示数据", ExerciseRecord("卧推", uid(), 65.0, "kg", 10, 3), ExerciseRecord("卧推", uid(), 135.0, "lbs", 10, 3), ExerciseRecord("深蹲", uid(), 80.0, "kg", 8, 3)),
                sess(d(4), "", ExerciseRecord("引体向上", uid(), null, "", 8, 3)),
                sess(d(5), "推日", ExerciseRecord("卧推", uid(), 60.0, "kg", 10, 3), ExerciseRecord("肩推", uid(), 35.0, "kg", 12, 3)),
                sess(d(6), "", ExerciseRecord("深蹲", uid(), 75.0, "kg", 10, 3)),
                sess(d(8), "", ExerciseRecord("卧推", uid(), 160.0, "lbs", 8, 3), ExerciseRecord("硬拉", uid(), 105.0, "kg", 6, 3)),
                sess(d(10), "", ExerciseRecord("引体向上", uid(), null, "", 12, 2), ExerciseRecord("卧推", uid(), 55.0, "kg", 12, 3)),
            ),
        )
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
