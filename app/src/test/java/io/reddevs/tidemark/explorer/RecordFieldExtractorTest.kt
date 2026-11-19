// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import android.content.Context
import android.content.res.Configuration
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.settings.UnitPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RecordFieldExtractorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun extractor(context: Context = this.context, locale: Locale = Locale.US) =
        RecordFieldExtractor(context, LocaleMeasurementFormatter(context, locale, UnitPreference.METRIC)) { pkg ->
            if (pkg == ExplorerTestRecords.PACKAGE) "Watch App" else pkg
        }

    @Test
    fun everyPublicFieldOfEachTypeAndItsMetadataAppears() {
        ExplorerTestRecords.all().forEach { (type, record) ->
            val keys = keysOf(extractor().extract(record))
            val missing = expectedKeys(record) - keys
            assertTrue("$type is missing $missing (has $keys)", missing.isEmpty())
        }
    }

    @Test
    fun sectionsRunFromTimeToMetadata() {
        ExplorerTestRecords.all().forEach { (type, record) ->
            val kinds = extractor().extract(record).map { it.kind }
            assertEquals("$type", SectionKind.TIME, kinds.first())
            assertEquals("$type", SectionKind.METADATA, kinds.last())
            assertEquals("$type sections out of order", kinds.sortedBy { it.ordinal }, kinds)
        }
    }

    @Test
    fun metadataShowsAppLabelPackageAndReadableConstants() {
        val metadata = extractor().extract(ExplorerTestRecords.weight()).single { it.kind == SectionKind.METADATA }
        val byKey = metadata.fields.associateBy { it.key }

        assertEquals("weight-1", byKey.getValue("metadata.id").value)
        assertEquals("Watch App", byKey.getValue("metadata.dataOrigin").value)
        assertEquals(ExplorerTestRecords.PACKAGE, byKey.getValue("metadata.dataOrigin").raw)
        assertEquals("client-weight-1", byKey.getValue("metadata.clientRecordId").value)
        assertEquals("3", byKey.getValue("metadata.clientRecordVersion").value)
        assertEquals("Actively recorded", byKey.getValue("metadata.recordingMethod").value)
        assertEquals("RECORDING_METHOD_ACTIVELY_RECORDED = 1", byKey.getValue("metadata.recordingMethod").raw)
        assertEquals("Acme", byKey.getValue("metadata.device.manufacturer").value)
        assertEquals("Pulse 2", byKey.getValue("metadata.device.model").value)
        assertEquals("Watch", byKey.getValue("metadata.device.type").value)
        assertEquals(ExplorerTestRecords.modified.toString(), byKey.getValue("metadata.lastModifiedTime").raw)
    }

    @Test
    fun valuesCarryTheRawStoredValueAndUnit() {
        fun value(record: Record, key: String) =
            extractor().extract(record).single { it.kind == SectionKind.VALUES }.fields.single { it.key == key }

        assertEquals("1234.5 m", value(ExplorerTestRecords.distance(), "distance").raw)
        assertEquals("98.25 kcal", value(ExplorerTestRecords.activeCalories(), "energy").raw)
        assertEquals("72.4 kg", value(ExplorerTestRecords.weight(), "weight").raw)
        assertEquals("54 bpm", value(ExplorerTestRecords.restingHeartRate(), "beatsPerMinute").raw)
        assertEquals("1234 steps", value(ExplorerTestRecords.steps(), "count").raw)
        val exerciseType = value(ExplorerTestRecords.exercise(), "exerciseType")
        assertEquals("Running", exerciseType.value)
        assertEquals("EXERCISE_TYPE_RUNNING = 56", exerciseType.raw)
    }

    @Test
    fun exerciseRoutesAreNeverShown() {
        fun route(route: ExerciseRoute?) = extractor().extract(ExplorerTestRecords.exercise(route = route))
            .single { it.kind == SectionKind.VALUES }.fields.single { it.key == "exerciseRouteResult" }.value

        assertEquals("Not requested by Tidemark", route(ExplorerTestRecords.sampleRoute))
        assertEquals("No route recorded", route(null))
    }

    @Test
    fun listSectionsAreCollapsibleWithReadableNames() {
        val stages = extractor().extract(ExplorerTestRecords.sleep()).single { it.kind == SectionKind.STAGES }
        assertTrue(stages.collapsible)
        assertEquals("Sleep stages (4)", stages.title)
        assertEquals(
            listOf("Light sleep", "Deep sleep", "REM sleep", "Awake"),
            stages.entries.map { entry -> entry.fields.single { it.key == "stage" }.value },
        )
        val segments = extractor().extract(ExplorerTestRecords.exercise()).single { it.kind == SectionKind.SEGMENTS }
        assertEquals(listOf("Running", "Pause"), segments.entries.map { e -> e.fields.single { it.key == "segmentType" }.value })
    }

    @Test
    fun unsetFieldsAreShownAsNotSet() {
        val record = ExplorerTestRecords.weight().let {
            androidx.health.connect.client.records.WeightRecord(
                time = it.time, zoneOffset = null, weight = it.weight, metadata = Metadata.manualEntry(),
            )
        }
        val fields = extractor().extract(record).flatMap { it.fields }.associateBy { it.key }
        assertEquals("Not set", fields.getValue("zoneOffset").value)
        assertEquals("Not set", fields.getValue("metadata.clientRecordId").value)
        assertEquals("No device information", fields.getValue("metadata.device").value)
        assertEquals("Entered manually", fields.getValue("metadata.recordingMethod").value)
    }

    @Test
    fun labelsFollowTheFrenchLocale() {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.FRANCE) }
        val french = context.createConfigurationContext(config)
        val sections = extractor(french, Locale.FRANCE).extract(ExplorerTestRecords.exercise())

        assertEquals("Métadonnées", sections.last().title)
        val values = sections.single { it.kind == SectionKind.VALUES }.fields.associateBy { it.key }
        assertEquals("Course à pied", values.getValue("exerciseType").value)
        assertEquals("Non demandé par Tidemark", values.getValue("exerciseRouteResult").value)
    }

    private fun keysOf(sections: List<FieldSection>): Set<String> = buildSet {
        sections.forEach { section ->
            section.fields.forEach { add(it.key) }
            listName(section.kind)?.let { list ->
                add(list)
                section.entries.forEach { entry -> entry.fields.forEach { add("$list.${it.key}") } }
            }
        }
    }

    private fun listName(kind: SectionKind) = when (kind) {
        SectionKind.SAMPLES -> "samples"
        SectionKind.STAGES -> "stages"
        SectionKind.SEGMENTS -> "segments"
        SectionKind.LAPS -> "laps"
        else -> null
    }

    /** Every public property of the record, its list elements, its metadata and the metadata's device. */
    private fun expectedKeys(record: Record): Set<String> = buildSet {
        properties(record.javaClass).filter { it != "metadata" }.forEach { add(it) }
        elementClasses(record).forEach { (list, cls) -> properties(cls).forEach { add("$list.$it") } }
        properties(Metadata::class.java).filter { it != "device" }.forEach { add("metadata.$it") }
        properties(Device::class.java).forEach { add("metadata.device.$it") }
    }

    private fun elementClasses(record: Record): Map<String, Class<*>> = when (RecordType.fromRecord(record)) {
        RecordType.HEART_RATE -> mapOf("samples" to HeartRateRecord.Sample::class.java)
        RecordType.SLEEP_SESSION -> mapOf("stages" to SleepSessionRecord.Stage::class.java)
        RecordType.EXERCISE_SESSION -> mapOf("segments" to ExerciseSegment::class.java, "laps" to ExerciseLap::class.java)
        else -> emptyMap()
    }

    private fun properties(cls: Class<*>): Set<String> = cls.methods
        .filter { it.declaringClass == cls && !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 }
        .map { it.name }
        .filter { '$' !in it && (it.startsWith("get") || it.startsWith("is")) && it != "getClass" }
        .map { it.removePrefix("get").removePrefix("is").replaceFirstChar(Char::lowercaseChar) }
        .toSet()
}
