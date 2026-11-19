// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.format

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.settings.DataStoreUserPreferences
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeUserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/** Acceptance criterion 3: changing the unit preference updates AppContainer.formatter live. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr-rFR")
class FormatterUpdatesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private suspend fun AppContainer.awaitFormatter(predicate: (MeasurementFormatter) -> Boolean) =
        withTimeout(10_000) { formatter.first(predicate) }

    @Test
    fun unitPreferenceChangeRebuildsTheFormatter() = runTest {
        val preferences = FakeUserPreferences()
        val container = AppContainer(context, FakeHealthRepository(), preferences, scope = backgroundScope)
        assertEquals("km", container.awaitFormatter { true }.distanceUnitLabel())

        preferences.setUnitPreference(UnitPreference.IMPERIAL)
        val imperial = container.awaitFormatter { it.distanceUnitLabel() == "mi" }
        assertEquals("lb", imperial.massUnitLabel())
        assertEquals(Locale.FRANCE, imperial.locale)

        preferences.setUnitPreference(UnitPreference.METRIC)
        assertEquals("kg", container.awaitFormatter { it.distanceUnitLabel() == "km" }.massUnitLabel())
    }

    @Test
    fun persistedPreferenceDrivesTheFormatterEndToEnd() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") }
        val preferences = DataStoreUserPreferences(store)
        val container = AppContainer(context, FakeHealthRepository(), preferences, scope = backgroundScope)

        preferences.setUnitPreference(UnitPreference.IMPERIAL)
        assertEquals("lb", container.awaitFormatter { it.distanceUnitLabel() == "mi" }.massUnitLabel())

        preferences.setUnitPreference(UnitPreference.SYSTEM)
        assertEquals("kg", container.awaitFormatter { it.distanceUnitLabel() == "km" }.massUnitLabel())
    }

    @Test
    fun localeChangeRebuildsTheFormatter() = runTest {
        val container = AppContainer(context, FakeHealthRepository(), FakeUserPreferences(), scope = backgroundScope)
        assertEquals("km", container.awaitFormatter { it.locale == Locale.FRANCE }.distanceUnitLabel())

        RuntimeEnvironment.setQualifiers("en-rUS")
        container.onConfigurationChanged()
        val us = container.awaitFormatter { it.locale == Locale.US }
        assertEquals("mi", us.distanceUnitLabel())
        assertEquals("lb", us.massUnitLabel())
    }
}
