package com.gymstatistics.server

import android.content.Context
import com.gymstatistics.data.AppJson
import com.gymstatistics.data.SyncPayload
import com.gymstatistics.data.WorkoutData
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Embedded HTTP server that lets a computer on the same LAN view and export
 * the workout data in its browser. The phone remains the source of truth.
 */
class LanSyncServer(
    context: Context,
    private val port: Int,
    private val dataProvider: () -> WorkoutData,
) : NanoHTTPD("0.0.0.0", port) {

    private val dashboardHtml: String = try {
        context.assets.open("dashboard.html").readBytes().toString(Charsets.UTF_8)
    } catch (e: Exception) {
        "<html><body><h3>dashboard.html missing</h3></body></html>"
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

    private fun fileStamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun attachmentName(ext: String): String = "gymstatistics_${fileStamp()}.$ext"

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/", "/index.html", "/dashboard" ->
                respond(Status.OK, "text/html; charset=utf-8", dashboardHtml)

            "/api/health" ->
                respond(Status.OK, "application/json; charset=utf-8", """{"status":"ok"}""")

            "/api/workouts" -> {
                val payload = SyncPayload(exportedAt = nowIso(), sessions = dataProvider().sessions)
                respond(Status.OK, "application/json; charset=utf-8", AppJson.json.encodeToString(SyncPayload.serializer(), payload))
            }

            "/api/export.json" -> {
                val payload = SyncPayload(exportedAt = nowIso(), sessions = dataProvider().sessions)
                respond(Status.OK, "application/json; charset=utf-8", AppJson.json.encodeToString(SyncPayload.serializer(), payload), attachmentName("json"))
            }

            "/api/export.csv" ->
                respond(Status.OK, "text/csv; charset=utf-8", "\uFEFF" + buildCsv(dataProvider()), attachmentName("csv"))

            else ->
                respond(Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun respond(status: Status, mime: String, body: String, attachment: String? = null): Response =
        newFixedLengthResponse(status, mime, body)
            .apply {
                addHeader("Access-Control-Allow-Origin", "*")
                if (attachment != null) {
                    addHeader("Content-Disposition", "attachment; filename=\"$attachment\"")
                }
            }

    private fun buildCsv(data: WorkoutData): String {
        val sb = StringBuilder("date,exercise,data,unit,count,sets\n")
        for (s in data.sessions.sortedBy { it.date }) {
            for (e in s.exercises) {
                val d = e.data?.let { it.toString() } ?: ""
                sb.append("${csvEscape(s.date)},${csvEscape(e.name)},$d,${csvEscape(e.unit)},${e.count ?: ""},${e.sets ?: ""}\n")
            }
        }
        return sb.toString()
    }

    private fun csvEscape(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v
}
