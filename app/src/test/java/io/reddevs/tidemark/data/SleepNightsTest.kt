// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import androidx.health.connect.client.records.SleepSessionRecord
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.testing.TestRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SleepNightsTest {

    @Test
    fun mapsEveryStageConstant() {
        val expected = mapOf(
            SleepSessionRecord.STAGE_TYPE_UNKNOWN to SleepStage.UNKNOWN,
            SleepSessionRecord.STAGE_TYPE_AWAKE to SleepStage.AWAKE,
            SleepSessionRecord.STAGE_TYPE_SLEEPING to SleepStage.SLEEPING,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to SleepStage.OUT_OF_BED,
            SleepSessionRecord.STAGE_TYPE_LIGHT to SleepStage.LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP to SleepStage.DEEP,
            SleepSessionRecord.STAGE_TYPE_REM to SleepStage.REM,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to SleepStage.AWAKE_IN_BED,
            99 to SleepStage.UNKNOWN,
        )
        expected.forEach { (constant, stage) -> assertEquals(stage, SleepNights.stage(constant)) }
    }

    @Test
    fun picksTheLongestSessionEndingInTheWindow() {
        val from = Instant.parse("2026-11-18T11:00:00Z")
        val until = Instant.parse("2026-11-19T11:00:00Z")
        val sessions = listOf(
            session("a", "com.a", "2026-11-18T22:00:00Z", "2026-11-19T05:00:00Z"), // 7 h
            session("b", "com.b", "2026-11-18T21:30:00Z", "2026-11-19T05:30:00Z"), // 8 h, overlaps a
            session("old", "com.a", "2026-11-17T23:00:00Z", "2026-11-18T10:59:00Z"), // ends before the window
            session("late", "com.a", "2026-11-18T23:30:00Z", "2026-11-19T11:00:00Z"), // 11.5 h, ends at the window's end
        )
        assertEquals("b", SleepNights.pick(sessions, from, until)?.metadata?.id)
        assertEquals("late", SleepNights.pick(sessions, from, until, untilInclusive = true)?.metadata?.id)
        assertNull(SleepNights.pick(sessions, until.plusSeconds(1), until.plusSeconds(60)))
    }

    @Test
    fun tiesBreakDeterministically() {
        val from = Instant.parse("2026-11-18T11:00:00Z")
        val until = Instant.parse("2026-11-19T11:00:00Z")
        val sessions = listOf(
            session("z", "com.z", "2026-11-18T22:00:00Z", "2026-11-19T06:00:00Z"),
            session("a", "com.a", "2026-11-18T22:00:00Z", "2026-11-19T06:00:00Z"),
        )
        assertEquals("com.a", SleepNights.pick(sessions, from, until)?.metadata?.dataOrigin?.packageName)
        assertEquals("com.a", SleepNights.pick(sessions.reversed(), from, until)?.metadata?.dataOrigin?.packageName)
    }

    private fun session(id: String, packageName: String, start: String, end: String) = SleepSessionRecord(
        startTime = Instant.parse(start),
        startZoneOffset = ZoneOffset.UTC,
        endTime = Instant.parse(end),
        endZoneOffset = ZoneOffset.UTC,
        metadata = TestRecords.metadata(id, packageName),
    )
}
