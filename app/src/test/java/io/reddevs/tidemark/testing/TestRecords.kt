// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.testing

import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.testing.populatedWithTestValues
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Builders for Health Connect records with a chosen id and data origin. Shared fixture owned by T-001. */
object TestRecords {
    /**
     * Metadata with an id and data origin. The public Metadata constructor can't set either, so
     * this uses connect-testing's helper.
     */
    fun metadata(id: String, packageName: String = "com.example.writer", modified: Instant = Instant.EPOCH): Metadata =
        Metadata.unknownRecordingMethod().populatedWithTestValues(id, DataOrigin(packageName), modified)

    fun steps(
        id: String,
        start: Instant,
        count: Long,
        packageName: String = "com.example.writer",
        length: Duration = Duration.ofMinutes(15),
    ) = StepsRecord(
        startTime = start,
        startZoneOffset = ZoneOffset.UTC,
        endTime = start + length,
        endZoneOffset = ZoneOffset.UTC,
        count = count,
        metadata = metadata(id, packageName),
    )
}
