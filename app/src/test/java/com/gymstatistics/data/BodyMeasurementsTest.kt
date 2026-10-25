package com.gymstatistics.data

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class BodyMeasurementsTest {
    @Test
    fun workoutRoundTripRetainsBodyOnlyBackup() {
        val source = """{"sessions":[],"bodyData":{"metrics":[{"id":"weight","name":"体重","unit":"kg","enabled":true}],"records":[{"date":"2026-10-25","values":{"weight":65.5}}]}}"""
        val decoded = AppJson.json.decodeFromString(WorkoutData.serializer(), source)
        val restored = AppJson.json.parseToJsonElement(
            AppJson.json.encodeToString(WorkoutData.serializer(), decoded)
        ).jsonObject
        assertEquals(AppJson.json.parseToJsonElement(source).jsonObject["bodyData"], restored["bodyData"])
    }

    @Test fun savingADayReplacesItAndDoesNotFillMissingValues() {
        val first = saveBodyDay(BodyData(), "2026-10-24", mapOf("weight" to 66.0, "waist" to 80.0))
        val next = saveBodyDay(first, "2026-10-24", mapOf("weight" to 65.5))
        assertEquals(1, next.records.size)
        assertEquals(mapOf("weight" to 65.5), next.records.single().values)
        assertEquals(7, next.metrics.size)
    }

    @Test fun renameAndDisablePreserveHistoryAndStableIdentity() {
        val original = saveBodyDay(BodyData(), "2026-10-24", mapOf("waist" to 80.0))
        val updated = updateBodyMetric(original, original.metrics.first { it.id == "waist" }.copy(name = "晨起腰围", enabled = false))
        assertEquals(80.0, updated.records.single().values["waist"]!!, 0.0)
        assertFalse(updated.metrics.first { it.id == "waist" }.enabled)
        assertEquals("晨起腰围", updated.metrics.first { it.id == "waist" }.name)
    }

    @Test fun oldBackupNeverClearsExistingBodyData() {
        val local = saveBodyDay(BodyData(), "2026-10-24", mapOf("weight" to 65.5))
        assertEquals(local, importBodyData(local, null, ImportMode.ALL, true).data)
        assertNull(AppJson.json.decodeFromString(WorkoutData.serializer(), "{\"sessions\":[]}").bodyData)
    }

    @Test fun restoreIntoFreshInstallRestoresRenamedAndDisabledPresets() {
        val backup = BodyData(metrics = defaultBodyMetrics().map { if (it.id == "waist") it.copy(name = "晨起腰围", enabled = false) else it })
        val restored = importBodyData(BodyData(), backup, ImportMode.ALL, false).data
        assertEquals("晨起腰围", restored.metrics.single { it.id == "waist" }.name)
        assertFalse(restored.metrics.single { it.id == "waist" }.enabled)
    }

    @Test fun dateImportSkipsOrReplacesOnlyTheSelectedDay() {
        val local = saveBodyDay(BodyData(), "2026-10-24", mapOf("weight" to 65.5, "waist" to 80.0))
        val incoming = saveBodyDay(saveBodyDay(BodyData(), "2026-10-24", mapOf("weight" to 66.0)), "2026-10-25", mapOf("weight" to 67.0))
        val skipped = importBodyData(local, incoming, ImportMode.DATE("2026-10-24"), false)
        assertEquals(local, skipped.data)
        assertEquals(ImportResult(duplicates = 1, skipped = 1), skipped.counts)
        val replaced = importBodyData(local, incoming, ImportMode.DATE("2026-10-24"), true)
        assertEquals(mapOf("weight" to 66.0), replaced.data.records.single().values)
        assertEquals(1, replaced.counts.overwritten)
    }

    @Test fun customDefinitionSurvivesImportAndUnitConflictsAreRejected() {
        val custom = BodyMetric("custom-neck", "颈围", "cm")
        val imported = importBodyData(BodyData(), saveBodyDay(BodyData(metrics = listOf(custom)), "2026-10-24", mapOf(custom.id to 35.0)), ImportMode.ALL, false).data
        assertEquals(custom, imported.metrics.last())
        assertThrows(IllegalArgumentException::class.java) {
            importBodyData(imported, BodyData(metrics = listOf(custom.copy(unit = "mm"))), ImportMode.ALL, false)
        }
        assertThrows(IllegalArgumentException::class.java) { updateBodyMetric(imported, custom.copy(unit = "mm")) }
    }

    @Test fun invalidDatesUnknownMetricsAndNonPositiveValuesAreRejected() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { saveBodyDay(BodyData(), "2026-10-24", mapOf("weight" to value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { saveBodyDay(BodyData(), "2026-02-30", mapOf("weight" to 65.0)) }
        assertThrows(IllegalArgumentException::class.java) { saveBodyDay(BodyData(), "2026-10-24", mapOf("unknown" to 65.0)) }
        assertThrows(IllegalArgumentException::class.java) { saveBodyDay(BodyData(), "2026-10-24", emptyMap()) }
    }

    @Test fun trendUsesActualDatesWithoutZeroFillingOrFutureValues() {
        val data = BodyData(records = listOf(
            BodyDayRecord("2026-09-01", mapOf("weight" to 68.0)),
            BodyDayRecord("2026-10-20", mapOf("waist" to 80.0)),
            BodyDayRecord("2026-10-23", mapOf("weight" to 66.0)),
            BodyDayRecord("2026-10-25", mapOf("weight" to 65.5)),
            BodyDayRecord("2026-10-26", mapOf("weight" to 65.0)),
        ))
        assertEquals(listOf(66.0, 65.5), bodyPoints(data, "weight", 30, java.time.LocalDate.parse("2026-10-25")).map { it.value })
        assertEquals(3, bodyPoints(data, "weight", null, java.time.LocalDate.parse("2026-10-25")).size)
        assertEquals(0, WorkoutStatistics(emptyList(), java.time.LocalDate.parse("2026-10-25")).days.size)
    }
}
