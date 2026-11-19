// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reddevs.tidemark.data.HealthRepository
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TypeAccess(val type: RecordType, val readable: Boolean)

data class TypeListState(
    val loading: Boolean = true,
    val types: List<TypeAccess> = emptyList(),
    val error: LoadError? = null,
)

/** The explorer's first screen: the eight record types and whether Tidemark may read each one. */
class ExplorerTypesViewModel(private val repository: HealthRepository) : ViewModel() {
    private val _state = MutableStateFlow(TypeListState())
    val state: StateFlow<TypeListState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /** Re-reads granted permissions, for example when the user returns from Health Connect settings. */
    fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = it.types.isEmpty(), error = null) }
        job = viewModelScope.launch {
            if (repository.availability() != HcAvailability.AVAILABLE) {
                _state.value = TypeListState(loading = false, error = LoadError.UNAVAILABLE)
                return@launch
            }
            loadCatching { repository.grantedPermissions() }
                .onSuccess { granted ->
                    _state.value = TypeListState(
                        loading = false,
                        types = RecordType.entries.map { TypeAccess(it, it.readPermission in granted) },
                    )
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = LoadError.from(e)) } }
        }
    }
}
