// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import android.content.Context
import androidx.annotation.StringRes
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.format.MeasurementFormatter
import java.time.Duration
import java.time.Instant

/** Readable plural name of each record type, for titles and the type list. */
@get:StringRes
val RecordType.label: Int
    get() = when (this) {
        RecordType.STEPS -> R.string.explorer_type_steps
        RecordType.DISTANCE -> R.string.explorer_type_distance
        RecordType.ACTIVE_CALORIES -> R.string.explorer_type_active_calories
        RecordType.EXERCISE_SESSION -> R.string.explorer_type_exercise
        RecordType.SLEEP_SESSION -> R.string.explorer_type_sleep
        RecordType.HEART_RATE -> R.string.explorer_type_heart_rate
        RecordType.RESTING_HEART_RATE -> R.string.explorer_type_resting_heart_rate
        RecordType.WEIGHT -> R.string.explorer_type_weight
    }

/** One-line text for a record list row: its time and a value summary. */
class RecordSummaries(private val context: Context, private val formatter: MeasurementFormatter) {

    /** "19 Nov 2026, 10:00 to 10:15" for interval records, the date and time for instant records. */
    fun time(record: Record): String {
        val (start, end) = span(record)
        return if (end == null) {
            formatter.dateTime(start)
        } else {
            context.getString(R.string.explorer_time_span, formatter.dateTime(start), formatter.time(end))
        }
    }

    fun value(record: Record): String = when (record) {
        is StepsRecord -> context.resources.getQuantityString(
            R.plurals.explorer_steps,
            record.count.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            formatter.steps(record.count),
        )
        is DistanceRecord -> formatter.distance(record.distance.inMeters)
        is ActiveCaloriesBurnedRecord -> formatter.energy(record.energy.inKilocalories)
        is ExerciseSessionRecord -> join(
            ConstantNames.exerciseType.label(context, record.exerciseType),
            formatter.duration(Duration.between(record.startTime, record.endTime)),
            record.title?.takeIf { it.isNotBlank() },
        )
        is SleepSessionRecord -> join(
            formatter.duration(Duration.between(record.startTime, record.endTime)),
            record.stages.size.takeIf { it > 0 }?.let {
                context.resources.getQuantityString(R.plurals.explorer_stage_count, it, it)
            },
        )
        is HeartRateRecord -> {
            val bpm = record.samples.map { it.beatsPerMinute }
            join(
                bpm.takeIf { it.isNotEmpty() }?.let {
                    context.getString(
                        R.string.explorer_heart_rate_range,
                        formatter.heartRate(it.min().toDouble()),
                        formatter.heartRate(it.max().toDouble()),
                    )
                },
                context.resources.getQuantityString(R.plurals.explorer_sample_count, bpm.size, bpm.size),
            )
        }
        is RestingHeartRateRecord -> formatter.heartRate(record.beatsPerMinute.toDouble())
        is WeightRecord -> formatter.mass(record.weight.inKilograms)
        else -> ""
    }

    private fun join(vararg parts: String?) = parts.filterNotNull().joinToString(" · ")

    companion object {
        /** Start and end for interval records; time and null for instant records. */
        fun span(record: Record): Pair<Instant, Instant?> = when (record) {
            is StepsRecord -> record.startTime to record.endTime
            is DistanceRecord -> record.startTime to record.endTime
            is ActiveCaloriesBurnedRecord -> record.startTime to record.endTime
            is ExerciseSessionRecord -> record.startTime to record.endTime
            is SleepSessionRecord -> record.startTime to record.endTime
            is HeartRateRecord -> record.startTime to record.endTime
            is RestingHeartRateRecord -> record.time to null
            is WeightRecord -> record.time to null
            else -> Instant.EPOCH to null
        }
    }
}
