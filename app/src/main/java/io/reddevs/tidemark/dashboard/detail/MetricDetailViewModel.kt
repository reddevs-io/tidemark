// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.HealthRepository
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.HeartRateSample
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * State for the metric detail screen: the window being shown, the chart content, and drill-down
 * events. Reads go through [HealthRepository] only.
 *
 * @param today the current local date, from AppContainer.clock.
 * @param locale the formatter's locale, which decides the first day of the week.
 */
class MetricDetailViewModel(
    private val repository: HealthRepository,
    val metric: Metric,
    initialWindow: ChartWindow,
    private val today: () -> LocalDate,
    private val locale: () -> Locale,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state: MutableStateFlow<MetricDetailUiState>
    val state: StateFlow<MetricDetailUiState>

    private val openRecords = Channel<DrillTarget.Records>(Channel.BUFFERED)

    /** One event per tap that should open the explorer. */
    val recordsRequests: Flow<DrillTarget.Records> = openRecords.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        val restored = savedState.get<String>(KEY_RANGE)?.let { range ->
            savedState.get<Long>(KEY_ANCHOR)?.let { ChartWindow(ChartRange.valueOf(range), LocalDate.ofEpochDay(it)) }
        }
        val window = (restored ?: initialWindow).let { it.copy(anchor = minOf(it.anchor, today())) }
        _state = MutableStateFlow(stateFor(window))
        state = _state.asStateFlow()
        load()
    }

    fun selectRange(range: ChartRange) {
        val window = state.value.window
        if (range != window.range) show(DetailWindows.switchRange(window, range, today()))
    }

    fun previous() = show(DetailWindows.shift(state.value.window, -1, today()))

    fun next() {
        if (state.value.canGoNext) show(DetailWindows.shift(state.value.window, 1, today()))
    }

    fun retry() = load()

    /** A tap on bucket [index] of the current series. Ignored when the chart shows no buckets. */
    fun onBucketSelected(index: Int) {
        val content = state.value.content as? DetailContent.Buckets ?: return
        val point = content.series.points.getOrNull(index) ?: return
        when (val target = DetailWindows.drillTarget(metric, state.value.window, point, today())) {
            is DrillTarget.Window -> show(target.window)
            is DrillTarget.Records -> openRecords.trySend(target)
        }
    }

    /** "Show records" in the day view, or a tap on the hypnogram or heart rate line. */
    fun onDaySelected() {
        val window = state.value.window
        openRecords.trySend(DrillTarget.Records(DetailWindows.dayViewRecordType(metric), window.anchor))
    }

    private fun show(window: ChartWindow) {
        if (window == state.value.window) return
        savedState[KEY_RANGE] = window.range.name
        savedState[KEY_ANCHOR] = window.anchor.toEpochDay()
        _state.value = stateFor(window)
        load()
    }

    private fun stateFor(window: ChartWindow) = MetricDetailUiState(
        metric = metric,
        window = window,
        canGoNext = DetailWindows.canGoNext(window, today(), locale()),
        content = DetailContent.Loading,
    )

    private fun load() {
        val window = state.value.window
        loadJob?.cancel()
        _state.update { it.copy(content = DetailContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = try {
                loadContent(window)
            } catch (e: HealthAccessException) {
                e.toContent()
            }
            _state.update { if (it.window == window) it.copy(content = content) else it }
        }
    }

    private suspend fun loadContent(window: ChartWindow): DetailContent = when {
        window.range == ChartRange.DAY && metric == Metric.SLEEP ->
            repository.sleepNight(window.anchor)?.let { DetailContent.SleepDay(it) } ?: DetailContent.Empty

        window.range == ChartRange.DAY && metric == Metric.RESTING_HEART_RATE -> loadHeartRateDay(window)

        else -> {
            val series = repository.series(metric, window)
            if (series.points.none { it.value != null }) {
                DetailContent.Empty
            } else {
                DetailContent.Buckets(series, chartStyle(metric))
            }
        }
    }

    private suspend fun loadHeartRateDay(window: ChartWindow): DetailContent {
        val resting = repository.series(Metric.RESTING_HEART_RATE, window).average
        var permissionMissing = false
        val samples: List<HeartRateSample> = try {
            repository.heartRateSamples(window.anchor)
        } catch (e: HealthAccessException.PermissionMissing) {
            if (e.type != RecordType.HEART_RATE) throw e
            permissionMissing = true
            emptyList()
        }
        return if (samples.isEmpty() && resting == null && !permissionMissing) {
            DetailContent.Empty
        } else {
            DetailContent.HeartRateDay(samples.sortedBy { it.time }, resting, permissionMissing)
        }
    }

    private fun HealthAccessException.toContent(): DetailContent.Error = when (this) {
        is HealthAccessException.PermissionMissing -> DetailContent.Error(DetailError.PERMISSION_MISSING, type)
        is HealthAccessException.RateLimited -> DetailContent.Error(DetailError.RATE_LIMITED)
        is HealthAccessException.Unavailable -> DetailContent.Error(DetailError.UNAVAILABLE)
        is HealthAccessException.Unexpected -> DetailContent.Error(DetailError.GENERIC)
    }

    private companion object {
        const val KEY_RANGE = "detail.range"
        const val KEY_ANCHOR = "detail.anchor"
    }
}
