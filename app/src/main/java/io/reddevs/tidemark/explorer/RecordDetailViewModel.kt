// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.health.connect.client.records.Record
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reddevs.tidemark.data.HealthRepository
import io.reddevs.tidemark.data.model.RecordType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecordDetailState {
    data object Loading : RecordDetailState

    data class Loaded(val record: Record) : RecordDetailState

    /** The record was deleted or never existed. */
    data object NotFound : RecordDetailState

    data class Failed(val error: LoadError) : RecordDetailState
}

class RecordDetailViewModel(
    private val repository: HealthRepository,
    val type: RecordType,
    private val recordId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<RecordDetailState>(RecordDetailState.Loading)
    val state: StateFlow<RecordDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = RecordDetailState.Loading
        viewModelScope.launch {
            _state.value = loadCatching { repository.readRecord(type, recordId) }.fold(
                onSuccess = { record -> record?.let(RecordDetailState::Loaded) ?: RecordDetailState.NotFound },
                onFailure = { RecordDetailState.Failed(LoadError.from(it)) },
            )
        }
    }

    /** Retry only when a permission was missing; the user may have granted it meanwhile. */
    fun onResume() {
        if ((_state.value as? RecordDetailState.Failed)?.error == LoadError.PERMISSION_MISSING) load()
    }
}
