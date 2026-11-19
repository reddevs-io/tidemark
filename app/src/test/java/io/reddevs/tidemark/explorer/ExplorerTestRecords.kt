// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.testing.populatedWithTestValues
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import io.reddevs.tidemark.data.model.RecordType
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** One fully populated record of each of the eight types, built from the real record classes. */
object ExplorerTestRecords {
    const val PACKAGE = "com.example.watchapp"
    val start: Instant = Instant.parse("2026-11-18T06:00:00Z")
    val modified: Instant = Instant.parse("2026-11-18T09:30:00Z")
    private val offset = ZoneOffset.ofHours(1)

    /** Metadata with every field set: id, origin, modified time, client id and version, method and device. */
    fun fullMetadata(id: String, packageName: String = PACKAGE): Metadata =
        Metadata.activelyRecorded(Device(Device.TYPE_WATCH, "Acme", "Pulse 2"), "client-$id", 3)
            .populatedWithTestValues(id, DataOrigin(packageName), modified)

    fun steps(id: String = "steps-1") = StepsRecord(
        startTime = start, startZoneOffset = offset, endTime = start + Duration.ofMinutes(15), endZoneOffset = offset,
        count = 1234, metadata = fullMetadata(id),
    )

    fun distance(id: String = "distance-1") = DistanceRecord(
        startTime = start, startZoneOffset = offset, endTime = start + Duration.ofMinutes(15), endZoneOffset = offset,
        distance = Length.meters(1234.5), metadata = fullMetadata(id),
    )

    fun activeCalories(id: String = "calories-1") = ActiveCaloriesBurnedRecord(
        startTime = start, startZoneOffset = offset, endTime = start + Duration.ofMinutes(15), endZoneOffset = offset,
        energy = Energy.kilocalories(98.25), metadata = fullMetadata(id),
    )

    /** A route Tidemark would never show (it doesn't request the route permission). */
    val sampleRoute = ExerciseRoute(listOf(ExerciseRoute.Location(start + Duration.ofMinutes(1), 48.8566, 2.3522)))

    fun exercise(id: String = "exercise-1", route: ExerciseRoute? = sampleRoute) =
        ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start + Duration.ofMinutes(40),
            endZoneOffset = offset,
            metadata = fullMetadata(id),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Morning run",
            notes = "Easy pace",
            segments = listOf(
                ExerciseSegment(start, start + Duration.ofMinutes(30), ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING, 0),
                ExerciseSegment(start + Duration.ofMinutes(30), start + Duration.ofMinutes(40), ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE, 0),
            ),
            laps = listOf(
                ExerciseLap(start, start + Duration.ofMinutes(20), Length.meters(4000.0)),
                ExerciseLap(start + Duration.ofMinutes(20), start + Duration.ofMinutes(40), Length.meters(3500.0)),
            ),
            exerciseRoute = route,
            plannedExerciseSessionId = "plan-1",
        )

    fun sleep(id: String = "sleep-1"): SleepSessionRecord {
        val begin = start - Duration.ofHours(8)
        return SleepSessionRecord(
            startTime = begin, startZoneOffset = offset, endTime = start, endZoneOffset = offset,
            metadata = fullMetadata(id),
            title = "Night",
            notes = "Slept well",
            stages = listOf(
                SleepSessionRecord.Stage(begin, begin + Duration.ofHours(2), SleepSessionRecord.STAGE_TYPE_LIGHT),
                SleepSessionRecord.Stage(begin + Duration.ofHours(2), begin + Duration.ofHours(4), SleepSessionRecord.STAGE_TYPE_DEEP),
                SleepSessionRecord.Stage(begin + Duration.ofHours(4), begin + Duration.ofHours(6), SleepSessionRecord.STAGE_TYPE_REM),
                SleepSessionRecord.Stage(begin + Duration.ofHours(6), start, SleepSessionRecord.STAGE_TYPE_AWAKE),
            ),
        )
    }

    fun heartRate(id: String = "hr-1") = HeartRateRecord(
        startTime = start, startZoneOffset = offset, endTime = start + Duration.ofMinutes(3), endZoneOffset = offset,
        samples = listOf(
            HeartRateRecord.Sample(start, 61),
            HeartRateRecord.Sample(start + Duration.ofMinutes(1), 75),
            HeartRateRecord.Sample(start + Duration.ofMinutes(2), 92),
        ),
        metadata = fullMetadata(id),
    )

    fun restingHeartRate(id: String = "rhr-1") =
        RestingHeartRateRecord(time = start, zoneOffset = offset, beatsPerMinute = 54, metadata = fullMetadata(id))

    fun weight(id: String = "weight-1") =
        WeightRecord(time = start, zoneOffset = offset, weight = Mass.kilograms(72.4), metadata = fullMetadata(id))

    fun all(): Map<RecordType, Record> = mapOf(
        RecordType.STEPS to steps(),
        RecordType.DISTANCE to distance(),
        RecordType.ACTIVE_CALORIES to activeCalories(),
        RecordType.EXERCISE_SESSION to exercise(),
        RecordType.SLEEP_SESSION to sleep(),
        RecordType.HEART_RATE to heartRate(),
        RecordType.RESTING_HEART_RATE to restingHeartRate(),
        RecordType.WEIGHT to weight(),
    )
}
