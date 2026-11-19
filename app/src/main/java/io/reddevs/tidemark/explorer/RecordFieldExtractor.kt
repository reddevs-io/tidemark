// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.ui.components.AppLabels
import android.content.Context
import androidx.annotation.StringRes
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import io.reddevs.tidemark.R
import io.reddevs.tidemark.format.MeasurementFormatter
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** The kinds of section the record detail screen shows, in display order. */
enum class SectionKind(@StringRes val title: Int, val collapsible: Boolean = false) {
    TIME(R.string.explorer_section_time),
    VALUES(R.string.explorer_section_values),
    SAMPLES(R.string.explorer_section_samples, collapsible = true),
    STAGES(R.string.explorer_section_stages, collapsible = true),
    SEGMENTS(R.string.explorer_section_segments, collapsible = true),
    LAPS(R.string.explorer_section_laps, collapsible = true),
    METADATA(R.string.explorer_section_metadata),
}

/**
 * One displayed field. [key] is the SDK property path (for example "startTime" or
 * "metadata.device.model"), stable across locales so tests can check coverage. [value] is the
 * formatted value; [raw] is the stored value with its unit or constant name, when it differs.
 */
data class Field(val key: String, val label: String, val value: String, val raw: String? = null)

/** One item of a list section, such as a heart rate sample or a sleep stage. */
data class FieldEntry(val title: String, val fields: List<Field>)

data class FieldSection(
    val kind: SectionKind,
    val title: String,
    val fields: List<Field> = emptyList(),
    val entries: List<FieldEntry> = emptyList(),
) {
    val collapsible: Boolean get() = kind.collapsible
}

/**
 * Maps each of the eight record classes to [FieldSection]s for the generic detail renderer. Every
 * public property of the record and of its metadata appears, including unset ones.
 *
 * [context] resolves labels in the current locale; [appLabel] turns a package name into an app
 * name (see [AppLabels]).
 */
class RecordFieldExtractor(
    private val context: Context,
    private val formatter: MeasurementFormatter,
    private val appLabel: (String) -> String,
) {
    private val rawNumber = NumberFormat.getNumberInstance(formatter.locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = RAW_FRACTION_DIGITS
    }

    fun extract(record: Record): List<FieldSection> = when (record) {
        is StepsRecord -> listOf(
            interval(record.startTime, record.startZoneOffset, record.endTime, record.endZoneOffset),
            values(
                Field(
                    "count",
                    s(R.string.explorer_field_steps),
                    steps(formatter.steps(record.count), record.count),
                    raw = steps(record.count.toString(), record.count),
                ),
            ),
            metadata(record.metadata),
        )
        is DistanceRecord -> listOf(
            interval(record.startTime, record.startZoneOffset, record.endTime, record.endZoneOffset),
            values(
                Field(
                    "distance",
                    s(R.string.explorer_field_distance),
                    formatter.distance(record.distance.inMeters),
                    raw = rawWithUnit(record.distance.inMeters, R.string.explorer_unit_meters),
                ),
            ),
            metadata(record.metadata),
        )
        is ActiveCaloriesBurnedRecord -> listOf(
            interval(record.startTime, record.startZoneOffset, record.endTime, record.endZoneOffset),
            values(
                Field(
                    "energy",
                    s(R.string.explorer_field_energy),
                    formatter.energy(record.energy.inKilocalories),
                    raw = rawWithUnit(record.energy.inKilocalories, R.string.explorer_unit_kilocalories),
                ),
            ),
            metadata(record.metadata),
        )
        is ExerciseSessionRecord -> listOf(
            interval(record.startTime, record.startZoneOffset, record.endTime, record.endZoneOffset),
            values(
                Field(
                    "exerciseType",
                    s(R.string.explorer_field_exercise_type),
                    ConstantNames.exerciseType.label(context, record.exerciseType),
                    raw = ConstantNames.exerciseType.raw(record.exerciseType),
                ),
                text("title", R.string.explorer_field_title, record.title),
                text("notes", R.string.explorer_field_notes, record.notes),
                text("plannedExerciseSessionId", R.string.explorer_field_planned_session, record.plannedExerciseSessionId),
                Field("exerciseRouteResult", s(R.string.explorer_field_route), route(record.exerciseRouteResult)),
            ),
            FieldSection(
                SectionKind.SEGMENTS,
                title(SectionKind.SEGMENTS, record.segments.size),
                entries = record.segments.map { segment ->
                    val name = ConstantNames.segmentType.label(context, segment.segmentType)
                    FieldEntry(
                        title = listOf(name, timeSpan(segment.startTime, segment.endTime)).joinToString(SEPARATOR),
                        fields = listOf(
                            instant("startTime", R.string.explorer_field_start, segment.startTime),
                            instant("endTime", R.string.explorer_field_end, segment.endTime),
                            Field(
                                "segmentType",
                                s(R.string.explorer_field_segment_type),
                                name,
                                raw = ConstantNames.segmentType.raw(segment.segmentType),
                            ),
                            Field("repetitions", s(R.string.explorer_field_repetitions), segment.repetitions.toString()),
                        ),
                    )
                },
            ),
            FieldSection(
                SectionKind.LAPS,
                title(SectionKind.LAPS, record.laps.size),
                entries = record.laps.mapIndexed { index, lap ->
                    FieldEntry(
                        title = listOfNotNull((index + 1).toString(), lap.length?.let { formatter.distance(it.inMeters) })
                            .joinToString(SEPARATOR),
                        fields = listOf(
                            instant("startTime", R.string.explorer_field_start, lap.startTime),
                            instant("endTime", R.string.explorer_field_end, lap.endTime),
                            Field(
                                "length",
                                s(R.string.explorer_field_lap_length),
                                lap.length?.let { formatter.distance(it.inMeters) } ?: notSet(),
                                raw = lap.length?.let { rawWithUnit(it.inMeters, R.string.explorer_unit_meters) },
                            ),
                        ),
                    )
                },
            ),
            metadata(record.metadata),
        )
        is SleepSessionRecord -> listOf(
            interval(record.startTime, record.startZoneOffset, record.endTime, record.endZoneOffset),
            values(
                text("title", R.string.explorer_field_title, record.title),
                text("notes", R.string.explorer_field_notes, record.notes),
            ),
            FieldSection(
                SectionKind.STAGES,
                title(SectionKind.STAGES, record.stages.size),
                entries = record.stages.map { stage ->
                    val name = ConstantNames.sleepStage.label(context, stage.stage)
                    FieldEntry(
                        title = listOf(name, timeSpan(stage.startTime, stage.endTime)).joinToString(SEPARATOR),
                        fields = listOf(
                            instant("startTime", R.string.explorer_field_start, stage.startTime),
                            instant("endTime", R.string.explorer_field_end, stage.endTime),
                            Field(
                                "stage",
                                s(R.string.explorer_field_stage),
                                name,
                                raw = ConstantNames.sleepStage.raw(stage.stage),
                            ),
                        ),
                    )
                },
            ),
            metadata(record.metadata),
        )
        is HeartRateRecord -> {
            val bpm = record.samples.map { it.beatsPerMinute }
            listOf(
                interval(record.startTime, record.startZoneOffset, record.endTime, record.endZoneOffset),
                values(
                    Field(
                        "derived.average",
                        s(R.string.explorer_field_average),
                        bpm.takeIf { it.isNotEmpty() }?.let { formatter.heartRate(it.average()) } ?: notSet(),
                    ),
                    Field("derived.minimum", s(R.string.explorer_field_minimum), bpm.minOrNull()?.let(::heartRate) ?: notSet()),
                    Field("derived.maximum", s(R.string.explorer_field_maximum), bpm.maxOrNull()?.let(::heartRate) ?: notSet()),
                ),
                FieldSection(
                    SectionKind.SAMPLES,
                    title(SectionKind.SAMPLES, record.samples.size),
                    entries = record.samples.map { sample ->
                        FieldEntry(
                            title = listOf(formatter.time(sample.time), heartRate(sample.beatsPerMinute)).joinToString(SEPARATOR),
                            fields = listOf(
                                instant("time", R.string.explorer_field_time, sample.time),
                                Field(
                                    "beatsPerMinute",
                                    s(R.string.explorer_field_heart_rate),
                                    heartRate(sample.beatsPerMinute),
                                    raw = rawWithUnit(sample.beatsPerMinute.toDouble(), R.string.explorer_unit_bpm),
                                ),
                            ),
                        )
                    },
                ),
                metadata(record.metadata),
            )
        }
        is RestingHeartRateRecord -> listOf(
            instantaneous(record.time, record.zoneOffset),
            values(
                Field(
                    "beatsPerMinute",
                    s(R.string.explorer_field_heart_rate),
                    heartRate(record.beatsPerMinute),
                    raw = rawWithUnit(record.beatsPerMinute.toDouble(), R.string.explorer_unit_bpm),
                ),
            ),
            metadata(record.metadata),
        )
        is WeightRecord -> listOf(
            instantaneous(record.time, record.zoneOffset),
            values(
                Field(
                    "weight",
                    s(R.string.explorer_field_weight),
                    formatter.mass(record.weight.inKilograms),
                    raw = rawWithUnit(record.weight.inKilograms, R.string.explorer_unit_kilograms),
                ),
            ),
            metadata(record.metadata),
        )
        // Tidemark only reads the eight types above; anything else still shows its metadata.
        else -> listOf(metadata(record.metadata))
    }

    private fun interval(start: Instant, startOffset: ZoneOffset?, end: Instant, endOffset: ZoneOffset?) = FieldSection(
        SectionKind.TIME,
        s(SectionKind.TIME.title),
        fields = listOf(
            instant("startTime", R.string.explorer_field_start, start),
            offset("startZoneOffset", R.string.explorer_field_start_offset, startOffset),
            instant("endTime", R.string.explorer_field_end, end),
            offset("endZoneOffset", R.string.explorer_field_end_offset, endOffset),
            Field("derived.duration", s(R.string.explorer_field_duration), formatter.duration(Duration.between(start, end))),
        ),
    )

    private fun instantaneous(time: Instant, offset: ZoneOffset?) = FieldSection(
        SectionKind.TIME,
        s(SectionKind.TIME.title),
        fields = listOf(
            instant("time", R.string.explorer_field_time, time),
            offset("zoneOffset", R.string.explorer_field_offset, offset),
        ),
    )

    private fun values(vararg fields: Field) = FieldSection(SectionKind.VALUES, s(SectionKind.VALUES.title), fields = fields.toList())

    private fun metadata(metadata: Metadata): FieldSection {
        val packageName = metadata.dataOrigin.packageName
        val device = metadata.device
        val deviceFields = if (device == null) {
            listOf(Field("metadata.device", s(R.string.explorer_field_device), s(R.string.explorer_value_no_device)))
        } else {
            listOf(
                text("metadata.device.manufacturer", R.string.explorer_field_device_manufacturer, device.manufacturer),
                text("metadata.device.model", R.string.explorer_field_device_model, device.model),
                Field(
                    "metadata.device.type",
                    s(R.string.explorer_field_device_type),
                    ConstantNames.deviceType.label(context, device.type),
                    raw = ConstantNames.deviceType.raw(device.type),
                ),
            )
        }
        return FieldSection(
            SectionKind.METADATA,
            s(SectionKind.METADATA.title),
            fields = listOf(
                text("metadata.id", R.string.explorer_field_record_id, metadata.id),
                Field("metadata.dataOrigin", s(R.string.explorer_field_data_origin), appLabel(packageName), raw = packageName),
                instant("metadata.lastModifiedTime", R.string.explorer_field_last_modified, metadata.lastModifiedTime),
                text("metadata.clientRecordId", R.string.explorer_field_client_record_id, metadata.clientRecordId),
                Field(
                    "metadata.clientRecordVersion",
                    s(R.string.explorer_field_client_record_version),
                    metadata.clientRecordVersion.toString(),
                ),
                Field(
                    "metadata.recordingMethod",
                    s(R.string.explorer_field_recording_method),
                    ConstantNames.recordingMethod.label(context, metadata.recordingMethod),
                    raw = ConstantNames.recordingMethod.raw(metadata.recordingMethod),
                ),
            ) + deviceFields,
        )
    }

    private fun route(result: ExerciseRouteResult): String = when (result) {
        is ExerciseRouteResult.NoData -> s(R.string.explorer_value_no_route)
        // Tidemark never asks for the exercise route permission, so routes are never shown.
        else -> s(R.string.explorer_value_route_not_requested)
    }

    private fun title(kind: SectionKind, count: Int) = context.getString(R.string.explorer_section_count, s(kind.title), count)

    private fun instant(key: String, @StringRes label: Int, instant: Instant) =
        Field(key, s(label), formatter.dateTime(instant), raw = instant.toString())

    private fun offset(key: String, @StringRes label: Int, offset: ZoneOffset?) =
        Field(key, s(label), offset?.let { context.getString(R.string.explorer_value_utc_offset, if (it == ZoneOffset.UTC) "" else it.id) } ?: notSet())

    private fun text(key: String, @StringRes label: Int, value: String?) =
        Field(key, s(label), value?.takeIf { it.isNotEmpty() } ?: notSet())

    private fun timeSpan(start: Instant, end: Instant) =
        context.getString(R.string.explorer_time_span, formatter.time(start), formatter.time(end))

    private fun heartRate(bpm: Long) = formatter.heartRate(bpm.toDouble())

    private fun steps(formatted: String, count: Long) =
        context.resources.getQuantityString(R.plurals.explorer_steps, count.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), formatted)

    private fun rawWithUnit(value: Double, @StringRes unit: Int) =
        context.getString(R.string.explorer_value_with_unit, rawNumber.format(value), s(unit))

    private fun notSet() = s(R.string.explorer_value_not_set)

    private fun s(@StringRes id: Int) = context.getString(id)

    private companion object {
        const val RAW_FRACTION_DIGITS = 9
        const val SEPARATOR = " · "
    }
}
