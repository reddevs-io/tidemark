// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType

/** Which Health Connect aggregate metrics back each dashboard metric, and how to read them in SI units. */
internal object MetricAggregates {

    fun metricsFor(metric: Metric): Set<AggregateMetric<*>> = when (metric) {
        Metric.STEPS -> setOf(StepsRecord.COUNT_TOTAL)
        Metric.ACTIVE_CALORIES -> setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        Metric.DISTANCE -> setOf(DistanceRecord.DISTANCE_TOTAL)
        Metric.SLEEP -> setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL)
        Metric.RESTING_HEART_RATE ->
            setOf(RestingHeartRateRecord.BPM_AVG, RestingHeartRateRecord.BPM_MIN, RestingHeartRateRecord.BPM_MAX)
        Metric.WEIGHT -> setOf(WeightRecord.WEIGHT_AVG, WeightRecord.WEIGHT_MIN, WeightRecord.WEIGHT_MAX)
    }

    /**
     * The value of [metric] in [result]: step count, kilocalories, meters, seconds of sleep, bpm or
     * kilograms. Null when the bucket has no data.
     */
    fun read(metric: Metric, result: AggregationResult): Sample? = when (metric) {
        Metric.STEPS -> result[StepsRecord.COUNT_TOTAL]?.let { Sample(it.toDouble()) }
        Metric.ACTIVE_CALORIES ->
            result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { Sample(it.inKilocalories) }
        Metric.DISTANCE -> result[DistanceRecord.DISTANCE_TOTAL]?.let { Sample(it.inMeters) }
        Metric.SLEEP -> result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let { Sample(it.toMillis() / 1000.0) }
        Metric.RESTING_HEART_RATE -> result[RestingHeartRateRecord.BPM_AVG]?.let { avg ->
            Sample(
                avg.toDouble(),
                result[RestingHeartRateRecord.BPM_MIN]?.toDouble(),
                result[RestingHeartRateRecord.BPM_MAX]?.toDouble(),
            )
        }
        Metric.WEIGHT -> result[WeightRecord.WEIGHT_AVG]?.let { avg ->
            Sample(avg.inKilograms, result[WeightRecord.WEIGHT_MIN]?.inKilograms, result[WeightRecord.WEIGHT_MAX]?.inKilograms)
        }
    }

    /** One aggregate metric per record type, used only to learn which apps wrote data in a range. */
    fun originMetric(type: RecordType): AggregateMetric<*> = when (type) {
        RecordType.STEPS -> StepsRecord.COUNT_TOTAL
        RecordType.DISTANCE -> DistanceRecord.DISTANCE_TOTAL
        RecordType.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
        RecordType.EXERCISE_SESSION -> ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
        RecordType.SLEEP_SESSION -> SleepSessionRecord.SLEEP_DURATION_TOTAL
        RecordType.HEART_RATE -> HeartRateRecord.MEASUREMENTS_COUNT
        RecordType.RESTING_HEART_RATE -> RestingHeartRateRecord.BPM_AVG
        RecordType.WEIGHT -> WeightRecord.WEIGHT_AVG
    }
}
