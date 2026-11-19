// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data.model

import androidx.health.connect.client.records.Record
import java.time.Instant

/** The app that wrote a record. The UI resolves the label and icon. */
data class DataSource(val packageName: String)

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, AWAKE_IN_BED, UNKNOWN }

data class SleepStageSpan(val start: Instant, val end: Instant, val stage: SleepStage)

data class SleepNight(
    val sessionId: String,
    val start: Instant,
    val end: Instant,
    val stages: List<SleepStageSpan>,
    val source: DataSource,
)

/** The most recent value of a point-in-time metric (weight in kilograms, resting heart rate in bpm). */
data class LatestValue(val metric: Metric, val time: Instant, val value: Double, val source: DataSource)

data class HeartRateSample(val time: Instant, val bpm: Long)

/** One page of raw records. [nextPageToken] is null on the last page. */
data class RecordPage(val records: List<Record>, val nextPageToken: String?)

enum class HcAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, UNSUPPORTED }

/** Whether Tidemark can read data older than 30 days before the first grant. */
enum class HistoryAccess { GRANTED, NOT_GRANTED, UNAVAILABLE }
