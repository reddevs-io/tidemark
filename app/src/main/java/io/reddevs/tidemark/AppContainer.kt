// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark

import android.content.Context
import io.reddevs.tidemark.data.HealthConnectHealthRepository
import io.reddevs.tidemark.data.HealthRepository
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.format.MeasurementFormatter
import io.reddevs.tidemark.settings.DataStoreUserPreferences
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.settings.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.util.Locale

/**
 * Manual dependency container, created once by [TidemarkApplication]. Tests pass fakes through the
 * constructor.
 *
 * Frozen contract: changes go through the primary session (plan section 7).
 */
class AppContainer(
    private val context: Context,
    val healthRepository: HealthRepository = HealthConnectHealthRepository(context),
    val userPreferences: UserPreferences = DataStoreUserPreferences(context),
    val clock: Clock = Clock.systemDefaultZone(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val locale = MutableStateFlow(currentLocale())

    /** The formatter for the current locale and unit preference. */
    val formatter: StateFlow<MeasurementFormatter> =
        combine(locale, userPreferences.unitPreference) { locale, unit -> createFormatter(locale, unit) }
            .stateIn(scope, SharingStarted.Eagerly, createFormatter(locale.value, UnitPreference.SYSTEM))

    /** Called by [TidemarkApplication] when the configuration (and possibly the locale) changes. */
    fun onConfigurationChanged() {
        locale.value = currentLocale()
    }

    private fun createFormatter(locale: Locale, unit: UnitPreference): MeasurementFormatter =
        LocaleMeasurementFormatter(context, locale, unit)

    private fun currentLocale(): Locale = context.resources.configuration.locales[0]
}
