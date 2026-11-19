// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.testing

import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.settings.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** In-memory [UserPreferences]. Shared fixture owned by T-001. */
class FakeUserPreferences(
    unit: UnitPreference = UnitPreference.SYSTEM,
    firstGrant: Instant? = null,
) : UserPreferences {
    override val unitPreference = MutableStateFlow(unit)
    override val firstGrantAt = MutableStateFlow(firstGrant)

    override suspend fun setUnitPreference(preference: UnitPreference) {
        unitPreference.value = preference
    }

    override suspend fun recordFirstGrantIfAbsent(time: Instant) {
        if (firstGrantAt.value == null) firstGrantAt.value = time
    }
}
