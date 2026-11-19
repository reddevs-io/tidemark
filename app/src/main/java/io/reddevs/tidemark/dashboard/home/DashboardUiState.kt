// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.data.model.LatestValue
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.SleepNight
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the dashboard shows. Values stay in SI units; the screen formats them with the
 * current [io.reddevs.tidemark.format.MeasurementFormatter], so a unit change needs no reload.
 */
data class DashboardUiState(
    val today: LocalDate,
    /** The zone "today" was taken in; latest values are dated in it. */
    val zone: ZoneId = ZoneId.systemDefault(),
    val tiles: Map<Metric, TileState> = Metric.entries.associateWith { TileState.Loading },
    val banner: HistoryBanner? = null,
    /** True only while a pull-to-refresh the user started is running. */
    val isRefreshing: Boolean = false,
) {
    fun tile(metric: Metric): TileState = tiles[metric] ?: TileState.Loading
}

/** The state of one dashboard tile. Each tile loads and fails on its own. */
sealed interface TileState {
    data object Loading : TileState

    /**
     * A value to show. [value] is SI (steps, kcal, meters, seconds of sleep, bpm, kg). [latest] is
     * set for resting heart rate and weight, [sleep] for last night's sleep.
     */
    data class Value(
        val value: Double,
        val sparkline: SparklineData?,
        val latest: LatestValue? = null,
        val sleep: SleepNight? = null,
    ) : TileState

    /** Health Connect returned nothing for today, last night or the past year. */
    data class NoData(val sparkline: SparklineData?) : TileState

    data class PermissionMissing(val type: RecordType) : TileState

    data class Error(val kind: ErrorKind) : TileState
}

enum class ErrorKind { RATE_LIMITED, UNAVAILABLE, OTHER }

/**
 * One value per day from [from] to [from] + values.size - 1, oldest first. A null value means the
 * day has no data.
 */
data class SparklineData(val from: LocalDate, val values: List<Double?>) {
    val hasData: Boolean get() = values.any { it != null && it > 0.0 }
}

/**
 * Shown when Tidemark can't read data older than 30 days before the first grant. [visibleFrom] is
 * that date, or null when the first grant time is unknown.
 */
data class HistoryBanner(val access: HistoryAccess, val visibleFrom: LocalDate?) {
    /** Only a denied permission can be fixed in Health Connect's settings. */
    val canOpenSettings: Boolean get() = access == HistoryAccess.NOT_GRANTED
}

internal fun HealthAccessException.toTileState(): TileState = when (this) {
    is HealthAccessException.PermissionMissing -> TileState.PermissionMissing(type)
    is HealthAccessException.RateLimited -> TileState.Error(ErrorKind.RATE_LIMITED)
    is HealthAccessException.Unavailable -> TileState.Error(ErrorKind.UNAVAILABLE)
    is HealthAccessException.Unexpected -> TileState.Error(ErrorKind.OTHER)
}
