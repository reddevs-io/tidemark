// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.format

import android.icu.util.LocaleData
import android.icu.util.ULocale
import io.reddevs.tidemark.settings.UnitPreference
import java.util.Locale

/** Display units for distance and body mass. Energy is always kilocalories, whatever the system. */
enum class UnitSystem(val distance: DistanceUnit, val mass: MassUnit) {
    /** Kilometers and kilograms. */
    METRIC(DistanceUnit.KILOMETER, MassUnit.KILOGRAM),

    /** Miles and kilograms, as people in the United Kingdom expect. */
    UK(DistanceUnit.MILE, MassUnit.KILOGRAM),

    /** Miles and pounds. */
    US(DistanceUnit.MILE, MassUnit.POUND),
}

enum class DistanceUnit(val metersPerUnit: Double) {
    KILOMETER(1_000.0),
    MILE(1_609.344),
}

enum class MassUnit(val kilogramsPerUnit: Double) {
    KILOGRAM(1.0),
    POUND(0.453_592_37),
}

/**
 * Plan section 3: [UnitPreference.SYSTEM] follows the locale's measurement system (US gets miles and
 * pounds, UK gets miles and kilograms, everyone else is metric). METRIC and IMPERIAL override it as
 * a whole, and IMPERIAL means miles and pounds. Stones are not supported in v1.
 */
fun resolveUnitSystem(locale: Locale, preference: UnitPreference): UnitSystem = when (preference) {
    UnitPreference.METRIC -> UnitSystem.METRIC
    UnitPreference.IMPERIAL -> UnitSystem.US
    UnitPreference.SYSTEM -> when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
        LocaleData.MeasurementSystem.US -> UnitSystem.US
        LocaleData.MeasurementSystem.UK -> UnitSystem.UK
        else -> UnitSystem.METRIC
    }
}
