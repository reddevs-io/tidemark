// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.HealthRepository
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.settings.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/** One step of the first-run flow. */
sealed interface OnboardingState {
    /** What Tidemark does, and that data stays on the device. */
    data object Welcome : OnboardingState

    /** Checking Health Connect's status after "Get started". */
    data object Checking : OnboardingState

    /** Health Connect is [HcAvailability.NOT_INSTALLED], [HcAvailability.UPDATE_REQUIRED] or [HcAvailability.UNSUPPORTED]. */
    data class HealthConnectMissing(val availability: HcAvailability) : OnboardingState

    /**
     * Ready to ask for [toRequest]. [includesHistory] says whether the history permission is part of
     * it. [denied] is true after a request came back without any read permission.
     */
    data class Permissions(
        val toRequest: Set<String>,
        val includesHistory: Boolean,
        val denied: Boolean = false,
    ) : OnboardingState

    /** At least one read permission is granted. The route leaves onboarding. */
    data object Done : OnboardingState
}

class OnboardingViewModel(
    private val repository: HealthRepository,
    private val preferences: UserPreferences,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Welcome)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onGetStarted() {
        if (_state.value != OnboardingState.Welcome) return
        _state.value = OnboardingState.Checking
        viewModelScope.launch { refresh() }
    }

    /**
     * The screen resumed: after installing Health Connect from Play, or after changing access in
     * Health Connect's settings. Re-check whatever the current step depends on.
     */
    fun onResume() {
        when (_state.value) {
            is OnboardingState.HealthConnectMissing -> viewModelScope.launch { refresh() }
            is OnboardingState.Permissions -> viewModelScope.launch { finishIfGranted(grantedPermissions()) }
            else -> Unit
        }
    }

    /** The result of PermissionController's request contract: the permissions now granted. */
    fun onPermissionResult(result: Set<String>) {
        viewModelScope.launch {
            // The contract can leave out permissions granted earlier, so merge in the current grants.
            if (finishIfGranted(result + grantedPermissions())) return@launch
            val current = _state.value
            _state.value = if (current is OnboardingState.Permissions) current.copy(denied = true) else current
        }
    }

    /** The permission dialog couldn't be started, for example because Health Connect just went away. */
    fun onPermissionRequestFailed() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val availability = repository.availability()
        if (availability != HcAvailability.AVAILABLE) {
            _state.value = OnboardingState.HealthConnectMissing(availability)
            return
        }
        if (finishIfGranted(grantedPermissions())) return
        val includeHistory = try {
            repository.historyFeatureAvailable()
        } catch (_: HealthAccessException) {
            false
        }
        val toRequest = repository.permissionsToRequest(includeHistory)
        _state.value = OnboardingState.Permissions(
            toRequest = toRequest,
            includesHistory = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in toRequest,
        )
    }

    /** Records the first grant and moves to [OnboardingState.Done] when [granted] holds a read permission. */
    private suspend fun finishIfGranted(granted: Set<String>): Boolean {
        if (_state.value == OnboardingState.Done) return true
        if (granted.none { it in RecordType.readPermissions }) return false
        preferences.recordFirstGrantIfAbsent(clock.instant())
        _state.value = OnboardingState.Done
        return true
    }

    private suspend fun grantedPermissions(): Set<String> = try {
        repository.grantedPermissions()
    } catch (_: HealthAccessException) {
        emptySet()
    }
}
