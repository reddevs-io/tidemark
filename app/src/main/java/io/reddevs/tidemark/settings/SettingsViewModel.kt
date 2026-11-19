// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the unit choice for the settings screen. Everything else on the screen is static. */
class SettingsViewModel(private val preferences: UserPreferences) : ViewModel() {

    /** Null until the stored value has loaded, so no radio button flashes the wrong choice. */
    val unitPreference: StateFlow<UnitPreference?> = preferences.unitPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun selectUnit(preference: UnitPreference) {
        viewModelScope.launch { preferences.setUnitPreference(preference) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
