// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data.model

import androidx.annotation.Keep
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import kotlin.reflect.KClass

/**
 * The eight Health Connect record types Tidemark reads. The set is the same in every build.
 * @Keep: used as a navigation argument; R8 must not obfuscate its serializer.
 */
@Keep
enum class RecordType(val recordClass: KClass<out Record>) {
    STEPS(StepsRecord::class),
    DISTANCE(DistanceRecord::class),
    ACTIVE_CALORIES(ActiveCaloriesBurnedRecord::class),
    EXERCISE_SESSION(ExerciseSessionRecord::class),
    SLEEP_SESSION(SleepSessionRecord::class),
    HEART_RATE(HeartRateRecord::class),
    RESTING_HEART_RATE(RestingHeartRateRecord::class),
    WEIGHT(WeightRecord::class),
    ;

    val readPermission: String
        get() = HealthPermission.getReadPermission(recordClass)

    companion object {
        val readPermissions: Set<String>
            get() = entries.mapTo(linkedSetOf()) { it.readPermission }

        fun fromRecord(record: Record): RecordType? = entries.firstOrNull { it.recordClass.isInstance(record) }
    }
}

/** A dashboard metric, backed by exactly one record type. @Keep for navigation, like [RecordType]. */
@Keep
enum class Metric(val recordType: RecordType) {
    STEPS(RecordType.STEPS),
    ACTIVE_CALORIES(RecordType.ACTIVE_CALORIES),
    DISTANCE(RecordType.DISTANCE),
    SLEEP(RecordType.SLEEP_SESSION),
    RESTING_HEART_RATE(RecordType.RESTING_HEART_RATE),
    WEIGHT(RecordType.WEIGHT),
}
