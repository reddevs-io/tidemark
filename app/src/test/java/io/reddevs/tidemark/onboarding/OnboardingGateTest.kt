// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.navigation.Dashboard
import io.reddevs.tidemark.navigation.Onboarding
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeUserPreferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingGateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeHealthRepository()

    private suspend fun TestScope.start(): Any =
        OnboardingGate.startDestination(AppContainer(context, repository, FakeUserPreferences(), scope = backgroundScope))

    @Test
    fun oneReadPermissionIsEnoughForTheDashboard() = runTest {
        repository.granted = setOf(RecordType.WEIGHT.readPermission)
        assertEquals(Dashboard, start())
    }

    @Test
    fun noGrantGoesToOnboarding() = runTest {
        repository.granted = emptySet()
        assertEquals(Onboarding, start())
    }

    @Test
    fun historyAloneGoesToOnboarding() = runTest {
        repository.granted = setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        assertEquals(Onboarding, start())
    }

    @Test
    fun healthConnectNotReadyGoesToOnboardingEvenWithGrants() = runTest {
        HcAvailability.entries.filter { it != HcAvailability.AVAILABLE }.forEach { availability ->
            repository.availability = availability
            assertEquals(availability.name, Onboarding, start())
        }
    }

    @Test
    fun failingPermissionQueryGoesToOnboarding() = runTest {
        repository.failures[FakeHealthRepository.Call.GRANTED] = HealthAccessException.Unavailable()
        assertEquals(Onboarding, start())
    }
}
