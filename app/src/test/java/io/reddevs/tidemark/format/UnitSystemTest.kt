// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.settings.UnitPreference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class UnitSystemTest {
    private fun resolve(tag: String, preference: UnitPreference = UnitPreference.SYSTEM) =
        resolveUnitSystem(Locale.forLanguageTag(tag), preference)

    @Test
    fun systemFollowsTheRegionMeasurementSystem() {
        assertEquals(UnitSystem.US, resolve("en-US"))
        assertEquals(UnitSystem.US, resolve("es-US"))
        assertEquals(UnitSystem.UK, resolve("en-GB"))
        assertEquals(UnitSystem.METRIC, resolve("fr-FR"))
        assertEquals(UnitSystem.METRIC, resolve("fr-CA"))
        assertEquals(UnitSystem.METRIC, resolve("en-CA"))
        assertEquals(UnitSystem.METRIC, resolve("en-IE"))
        assertEquals(UnitSystem.METRIC, resolve("de-DE"))
    }

    @Test
    fun overridesApplyAsAWhole() {
        for (tag in listOf("en-US", "en-GB", "fr-FR", "fr-CA")) {
            assertEquals(tag, UnitSystem.METRIC, resolve(tag, UnitPreference.METRIC))
            assertEquals(tag, UnitSystem.US, resolve(tag, UnitPreference.IMPERIAL))
        }
    }

    @Test
    fun unitsPerSystem() {
        assertEquals(DistanceUnit.KILOMETER to MassUnit.KILOGRAM, UnitSystem.METRIC.distance to UnitSystem.METRIC.mass)
        assertEquals(DistanceUnit.MILE to MassUnit.KILOGRAM, UnitSystem.UK.distance to UnitSystem.UK.mass)
        assertEquals(DistanceUnit.MILE to MassUnit.POUND, UnitSystem.US.distance to UnitSystem.US.mass)
    }
}
