// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import androidx.health.connect.client.records.SleepSessionRecord
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.SleepNight
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.data.model.SleepStageSpan
import java.time.Duration
import java.time.Instant

/**
 * Picks one night out of possibly overlapping sleep sessions (plan section 3). readRecords does not
 * apply the user's Health Connect priority order, so the rule must be deterministic: the longest
 * session wins, then the one ending later, then package name and id.
 */
internal object SleepNights {
    private val order = compareBy<SleepSessionRecord> { Duration.between(it.startTime, it.endTime) }
        .thenBy { it.endTime }
        .thenByDescending { it.metadata.dataOrigin.packageName }
        .thenByDescending { it.metadata.id }

    /**
     * The longest session ending at or after [from] and before [until] (or at [until] when
     * [untilInclusive]).
     */
    fun pick(
        sessions: List<SleepSessionRecord>,
        from: Instant,
        until: Instant,
        untilInclusive: Boolean = false,
    ): SleepSessionRecord? =
        sessions
            .filter { !it.endTime.isBefore(from) && (it.endTime.isBefore(until) || (untilInclusive && it.endTime == until)) }
            .maxWithOrNull(order)

    fun toNight(record: SleepSessionRecord) = SleepNight(
        sessionId = record.metadata.id,
        start = record.startTime,
        end = record.endTime,
        stages = record.stages.sortedBy { it.startTime }.map { SleepStageSpan(it.startTime, it.endTime, stage(it.stage)) },
        source = DataSource(record.metadata.dataOrigin.packageName),
    )

    fun stage(type: Int): SleepStage = when (type) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStage.AWAKE
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.SLEEPING
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.OUT_OF_BED
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.AWAKE_IN_BED
        else -> SleepStage.UNKNOWN
    }
}
