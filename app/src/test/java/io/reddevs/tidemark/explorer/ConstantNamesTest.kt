// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import android.content.Context
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier

@RunWith(AndroidJUnit4::class)
class ConstantNamesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Every public static int named [prefix]* on [cls], by name. */
    private fun sdkConstants(cls: Class<*>, prefix: String): Map<String, Int> = cls.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.name.startsWith(prefix) }
        .associate { it.name to it.getInt(null) }

    private fun assertCovers(table: List<ConstantName>, cls: Class<*>, prefix: String) {
        val sdk = sdkConstants(cls, prefix)
        assertTrue("no $prefix constants found", sdk.isNotEmpty())
        assertEquals("$prefix table differs from the SDK", sdk, table.associate { it.constant to it.value })
        table.forEach { assertTrue(it.constant, context.getString(checkNotNull(it.label)).isNotBlank()) }
    }

    @Test
    fun tablesCoverEverySdkConstant() {
        assertCovers(ConstantNames.exerciseTypes, ExerciseSessionRecord::class.java, "EXERCISE_TYPE_")
        assertCovers(ConstantNames.segmentTypes, ExerciseSegment::class.java, "EXERCISE_SEGMENT_TYPE_")
        assertCovers(ConstantNames.sleepStages, SleepSessionRecord::class.java, "STAGE_TYPE_")
        assertCovers(ConstantNames.recordingMethods, Metadata::class.java, "RECORDING_METHOD_")
        assertCovers(ConstantNames.deviceTypes, Device::class.java, "TYPE_")
    }

    @Test
    fun knownValuesGetReadableNamesAndRawConstants() {
        assertEquals("Treadmill running", ConstantNames.exerciseType.label(context, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL))
        assertEquals("REM sleep", ConstantNames.sleepStage.label(context, SleepSessionRecord.STAGE_TYPE_REM))
        assertEquals("Chest strap", ConstantNames.deviceType.label(context, Device.TYPE_CHEST_STRAP))
        assertEquals("STAGE_TYPE_REM = 6", ConstantNames.sleepStage.raw(SleepSessionRecord.STAGE_TYPE_REM))
    }

    @Test
    fun untranslatedConstantsShowTheirHumanizedNameAndUnknownValuesTheirNumber() {
        val table = ConstantTable(listOf(ConstantName(99, "EXERCISE_TYPE_UNDERWATER_HOCKEY", label = null)), "EXERCISE_TYPE_")
        assertEquals("Underwater hockey", table.label(context, 99))
        assertEquals("EXERCISE_TYPE_UNDERWATER_HOCKEY = 99", table.raw(99))

        assertEquals("Unknown value (999)", ConstantNames.deviceType.label(context, 999))
        assertEquals("999", ConstantNames.deviceType.raw(999))
    }

    @Test
    fun humanizeStripsThePrefix() {
        assertEquals("Out of bed", ConstantTable.humanize("STAGE_TYPE_OUT_OF_BED", "STAGE_TYPE_"))
    }
}
