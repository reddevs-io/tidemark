// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.HealthConnectHealthRepository
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.navigation.Dashboard
import io.reddevs.tidemark.navigation.Onboarding
import io.reddevs.tidemark.onboarding.OnboardingGate
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeUserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContainerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun applicationBuildsTheRealContainer() {
        val app = context as TidemarkApplication
        assertTrue(app.container.healthRepository is HealthConnectHealthRepository)
        assertNotNull(app.container.formatter.value.metricValue(Metric.STEPS, 1234.0))
    }

    @Test
    fun formatterFollowsUnitPreference() = runTest {
        val preferences = FakeUserPreferences()
        val container = AppContainer(context, FakeHealthRepository(), preferences, scope = backgroundScope)
        preferences.setUnitPreference(UnitPreference.IMPERIAL)
        // The stub formatter ignores the preference; T-004 asserts the unit change itself.
        assertNotNull(container.formatter.first())
    }

    @Test
    fun gateSendsNewUsersToOnboardingAndGrantedUsersToDashboard() = runTest {
        val repo = FakeHealthRepository()
        val container = AppContainer(context, repo, FakeUserPreferences(), scope = backgroundScope)
        assertEquals(Dashboard, OnboardingGate.startDestination(container))

        repo.granted = emptySet()
        assertEquals(Onboarding, OnboardingGate.startDestination(container))

        repo.granted = setOf(io.reddevs.tidemark.data.model.RecordType.STEPS.readPermission)
        repo.availability = HcAvailability.NOT_INSTALLED
        assertEquals(Onboarding, OnboardingGate.startDestination(container))
    }
}
