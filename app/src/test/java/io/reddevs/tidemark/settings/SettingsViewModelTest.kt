// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import app.cash.turbine.test
import io.reddevs.tidemark.testing.FakeUserPreferences
import io.reddevs.tidemark.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun exposesTheStoredPreference() = runTest {
        val viewModel = SettingsViewModel(FakeUserPreferences(UnitPreference.METRIC))
        viewModel.unitPreference.test {
            assertEquals(UnitPreference.METRIC, awaitItem())
        }
    }

    @Test
    fun startsUnknownUntilCollected() {
        val viewModel = SettingsViewModel(FakeUserPreferences(UnitPreference.IMPERIAL))
        assertNull(viewModel.unitPreference.value)
    }

    @Test
    fun selectingAUnitStoresItAndUpdatesTheState() = runTest {
        val preferences = FakeUserPreferences()
        val viewModel = SettingsViewModel(preferences)
        viewModel.unitPreference.test {
            assertEquals(UnitPreference.SYSTEM, awaitItem())
            viewModel.selectUnit(UnitPreference.IMPERIAL)
            assertEquals(UnitPreference.IMPERIAL, awaitItem())
            assertEquals(UnitPreference.IMPERIAL, preferences.unitPreference.value)
            viewModel.selectUnit(UnitPreference.SYSTEM)
            assertEquals(UnitPreference.SYSTEM, awaitItem())
        }
    }
}
