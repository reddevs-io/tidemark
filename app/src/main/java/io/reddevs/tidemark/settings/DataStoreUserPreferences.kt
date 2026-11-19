// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * [UserPreferences] persisted with DataStore Preferences.
 *
 * T-001 stub: keeps values in memory only. T-004 owns this file and adds DataStore persistence.
 */
@Suppress("UNUSED_PARAMETER")
class DataStoreUserPreferences(context: Context) : UserPreferences {
    private val unit = MutableStateFlow(UnitPreference.SYSTEM)
    private val firstGrant = MutableStateFlow<Instant?>(null)

    override val unitPreference: Flow<UnitPreference> = unit

    override suspend fun setUnitPreference(preference: UnitPreference) {
        unit.value = preference
    }

    override val firstGrantAt: Flow<Instant?> = firstGrant

    override suspend fun recordFirstGrantIfAbsent(time: Instant) {
        if (firstGrant.value == null) firstGrant.value = time
    }
}
