// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.navigation

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import kotlinx.serialization.Serializable
import java.time.LocalDate

// Type-safe Navigation-Compose routes. Frozen contract: changes go through the primary session.

@Serializable
data object Onboarding

@Serializable
data object Dashboard

@Serializable
data class MetricDetail(
    val metric: Metric,
    val range: ChartRange = ChartRange.WEEK,
    val anchorEpochDay: Long,
) {
    val anchor: LocalDate get() = LocalDate.ofEpochDay(anchorEpochDay)
}

/** Every argument is optional; drill-down from a chart passes the type and day. */
@Serializable
data class Explorer(
    val typeName: String? = null,
    val dayEpochDay: Long? = null,
    val sourcePackage: String? = null,
) {
    val type: RecordType? get() = typeName?.let(RecordType::valueOf)
    val day: LocalDate? get() = dayEpochDay?.let(LocalDate::ofEpochDay)

    companion object {
        fun of(type: RecordType?, day: LocalDate? = null, sourcePackage: String? = null) =
            Explorer(type?.name, day?.toEpochDay(), sourcePackage)
    }
}

@Serializable
data class RecordDetail(val type: RecordType, val recordId: String)

@Serializable
data object Settings

@Serializable
data object PrivacyPolicy
