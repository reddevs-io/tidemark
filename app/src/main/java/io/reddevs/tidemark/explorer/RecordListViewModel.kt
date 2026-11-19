// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.health.connect.client.records.Record
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reddevs.tidemark.data.HealthRepository
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.RecordType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class RecordListState(
    val type: RecordType,
    val range: ExplorerRange,
    val selectedSource: DataSource?,
    /** The day passed by a chart drill-down, offered as its own range chip. */
    val drillDownDay: LocalDate? = null,
    /** Union of the aggregate's data origins and every origin seen in loaded pages. */
    val sources: Set<DataSource> = emptySet(),
    val records: List<Record> = emptyList(),
    val loadingFirstPage: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: LoadError? = null,
) {
    val canLoadMore: Boolean get() = !loadingFirstPage && !loadingMore && !endReached && error == null
}

/**
 * Pages through the raw records of one type, [pageSize] at a time. The query bounds are fixed
 * when a listing starts, so "today" doesn't move while the user scrolls. Manual paging: the screen reports the last visible row and the next
 * page loads when that row is within [PREFETCH_DISTANCE] of the end.
 */
class RecordListViewModel(
    private val repository: HealthRepository,
    private val clock: Clock,
    type: RecordType,
    initialRange: ExplorerRange = ExplorerRange.Last7Days,
    initialSource: DataSource? = null,
    private val pageSize: Int = HealthRepository.DEFAULT_PAGE_SIZE,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecordListState(
            type = type,
            range = initialRange,
            selectedSource = initialSource,
            drillDownDay = (initialRange as? ExplorerRange.Day)?.date,
        ),
    )
    val state: StateFlow<RecordListState> = _state.asStateFlow()

    private var bounds: TimeBounds = initialRange.bounds(today(), clock.zone)
    private var nextPageToken: String? = null
    private var pageJob: Job? = null
    private var originsJob: Job? = null

    init {
        reload(refreshSources = true)
    }

    fun selectRange(range: ExplorerRange) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range) }
        reload(refreshSources = true)
    }

    /** null shows every app. Changing the source starts paging again from the first page. */
    fun selectSource(source: DataSource?) {
        if (source == _state.value.selectedSource) return
        _state.update { it.copy(selectedSource = source) }
        reload(refreshSources = false)
    }

    /** Called with the index of the last visible row whenever the list scrolls or grows. */
    fun onLastVisibleItem(index: Int) {
        val current = _state.value
        if (current.canLoadMore && index >= current.records.size - PREFETCH_DISTANCE) loadNextPage()
    }

    fun retry() {
        val current = _state.value
        if (current.records.isEmpty() || nextPageToken == null) {
            reload(refreshSources = true)
        } else {
            _state.update { it.copy(error = null) }
            loadNextPage()
        }
    }

    /** After a trip to Health Connect settings, a missing permission may have been granted. */
    fun onResume() {
        if (_state.value.error == LoadError.PERMISSION_MISSING) reload(refreshSources = true)
    }

    private fun reload(refreshSources: Boolean) {
        pageJob?.cancel()
        nextPageToken = null
        val range = _state.value.range
        bounds = range.bounds(today(), clock.zone)
        _state.update {
            it.copy(
                records = emptyList(),
                loadingFirstPage = true,
                loadingMore = false,
                endReached = false,
                error = null,
                sources = if (refreshSources) setOfNotNull(it.selectedSource) else it.sources,
            )
        }
        if (refreshSources) refreshOrigins()
        pageJob = viewModelScope.launch { loadPages(token = null) }
    }

    private fun refreshOrigins() {
        originsJob?.cancel()
        val type = _state.value.type
        val query = bounds
        originsJob = viewModelScope.launch {
            // A failure here is not fatal: the chips still fill from the loaded pages, and the
            // page load reports the error that matters (for example a missing permission).
            loadCatching { repository.dataOrigins(type, query.start, query.end) }
                .onSuccess { origins -> _state.update { it.copy(sources = it.sources + origins) } }
        }
    }

    private fun loadNextPage() {
        val token = nextPageToken ?: return
        _state.update { it.copy(loadingMore = true) }
        pageJob = viewModelScope.launch { loadPages(token) }
    }

    private suspend fun loadPages(token: String?) {
        var pageToken = token
        var current = _state.value
        // An empty page with a token would leave the list with nothing to scroll, so keep going.
        repeat(MAX_EMPTY_PAGES) {
            val result = loadCatching {
                repository.readPage(current.type, bounds.start, bounds.end, current.selectedSource, pageToken, pageSize)
            }
            val page = result.getOrElse { error ->
                _state.update { it.copy(loadingFirstPage = false, loadingMore = false, error = LoadError.from(error)) }
                return
            }
            nextPageToken = page.nextPageToken
            val seen = page.records.mapTo(linkedSetOf()) { DataSource(it.metadata.dataOrigin.packageName) }
            _state.update {
                it.copy(
                    records = it.records + page.records,
                    sources = it.sources + seen,
                    loadingFirstPage = false,
                    loadingMore = false,
                    endReached = page.nextPageToken == null,
                )
            }
            if (page.records.isNotEmpty() || page.nextPageToken == null) return
            pageToken = page.nextPageToken
            current = _state.value
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    companion object {
        /** Rows from the end at which the next page starts loading. */
        const val PREFETCH_DISTANCE = 20
        private const val MAX_EMPTY_PAGES = 10

        /** The range a route opens with: the drill-down day when given, else the last 7 days. */
        fun initialRange(day: LocalDate?): ExplorerRange = day?.let(ExplorerRange::Day) ?: ExplorerRange.Last7Days
    }
}
