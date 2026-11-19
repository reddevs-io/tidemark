// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** SYSTEM follows the language and region; METRIC and IMPERIAL override it as a whole. */
enum class UnitPreference { SYSTEM, METRIC, IMPERIAL }

/**
 * The only data Tidemark stores on the device. Frozen contract: changes go through the primary
 * session (plan section 7).
 */
interface UserPreferences {
    val unitPreference: Flow<UnitPreference>

    suspend fun setUnitPreference(preference: UnitPreference)

    /** When Tidemark first saw a granted read permission; dates the 30-day history banner. */
    val firstGrantAt: Flow<Instant?>

    /** Stores [time] only if no first-grant time is stored yet. */
    suspend fun recordFirstGrantIfAbsent(time: Instant)
}
