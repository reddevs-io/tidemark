// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.testing.AggregationResult
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

// Robolectric: connect-testing's AggregationResult factory touches Android classes.
@RunWith(AndroidJUnit4::class)
class MetricAggregatesTest {

    @Test
    fun readsEachMetricInSiUnits() {
        val result = aggregation(
            StepsRecord.COUNT_TOTAL to 12345L,
            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL to Energy.kilocalories(420.5),
            DistanceRecord.DISTANCE_TOTAL to Length.kilometers(8.2),
            SleepSessionRecord.SLEEP_DURATION_TOTAL to Duration.ofMinutes(452),
            RestingHeartRateRecord.BPM_AVG to 56L,
            RestingHeartRateRecord.BPM_MIN to 51L,
            RestingHeartRateRecord.BPM_MAX to 60L,
            WeightRecord.WEIGHT_AVG to Mass.kilograms(72.4),
            WeightRecord.WEIGHT_MIN to Mass.kilograms(72.0),
            WeightRecord.WEIGHT_MAX to Mass.kilograms(73.1),
        )

        assertEquals(Sample(12345.0), MetricAggregates.read(Metric.STEPS, result))
        assertEquals(420.5, MetricAggregates.read(Metric.ACTIVE_CALORIES, result)!!.value, 1e-6)
        assertEquals(8200.0, MetricAggregates.read(Metric.DISTANCE, result)!!.value, 1e-6)
        assertEquals(452 * 60.0, MetricAggregates.read(Metric.SLEEP, result)!!.value, 1e-6)
        assertEquals(Sample(56.0, 51.0, 60.0), MetricAggregates.read(Metric.RESTING_HEART_RATE, result))
        val weight = MetricAggregates.read(Metric.WEIGHT, result)!!
        assertEquals(72.4, weight.value, 1e-6)
        assertEquals(72.0, weight.min!!, 1e-6)
        assertEquals(73.1, weight.max!!, 1e-6)
    }

    @Test
    fun missingMetricMeansNoData() {
        val empty = AggregationResult()
        Metric.entries.forEach { assertNull(it.name, MetricAggregates.read(it, empty)) }
    }

    @Test
    fun everyMetricAndTypeHasAnAggregate() {
        Metric.entries.forEach { assertTrue(MetricAggregates.metricsFor(it).isNotEmpty()) }
        RecordType.entries.forEach { MetricAggregates.originMetric(it) }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun aggregation(vararg values: Pair<AggregateMetric<*>, Any>) =
            AggregationResult(metrics = values.associate { (metric, value) -> (metric as AggregateMetric<Any>) to value })
    }
}
