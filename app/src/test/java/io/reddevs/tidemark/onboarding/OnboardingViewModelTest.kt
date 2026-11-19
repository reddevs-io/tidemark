// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import androidx.health.connect.client.permission.HealthPermission
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeUserPreferences
import io.reddevs.tidemark.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = Instant.parse("2026-11-19T08:30:00Z")
    private val history = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
    private val allPermissions = RecordType.readPermissions + history
    private val repository = FakeHealthRepository().apply { granted = emptySet() }
    private val preferences = FakeUserPreferences()
    private val viewModel = OnboardingViewModel(repository, preferences, Clock.fixed(now, ZoneOffset.UTC))

    private val state get() = viewModel.state.value

    private fun permissionsState(): OnboardingState.Permissions {
        val current = state
        assertTrue("expected the permission step, got $current", current is OnboardingState.Permissions)
        return current as OnboardingState.Permissions
    }

    @Test
    fun startsOnWelcomeWithoutTouchingHealthConnect() {
        assertEquals(OnboardingState.Welcome, state)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun notInstalledShowsInstallStepAndMovesOnAfterInstallOnResume() {
        repository.availability = HcAvailability.NOT_INSTALLED
        viewModel.onGetStarted()
        assertEquals(OnboardingState.HealthConnectMissing(HcAvailability.NOT_INSTALLED), state)

        // Still missing when the user comes back without installing.
        viewModel.onResume()
        assertEquals(OnboardingState.HealthConnectMissing(HcAvailability.NOT_INSTALLED), state)

        repository.availability = HcAvailability.AVAILABLE
        viewModel.onResume()
        assertEquals(allPermissions, permissionsState().toRequest)
    }

    @Test
    fun updateRequiredShowsUpdateStep() {
        repository.availability = HcAvailability.UPDATE_REQUIRED
        viewModel.onGetStarted()
        assertEquals(OnboardingState.HealthConnectMissing(HcAvailability.UPDATE_REQUIRED), state)

        repository.availability = HcAvailability.AVAILABLE
        viewModel.onResume()
        permissionsState()
    }

    @Test
    fun unsupportedShowsExplanationOnly() {
        repository.availability = HcAvailability.UNSUPPORTED
        viewModel.onGetStarted()
        assertEquals(OnboardingState.HealthConnectMissing(HcAvailability.UNSUPPORTED), state)
    }

    @Test
    fun allGrantedFinishesAndRecordsFirstGrant() {
        viewModel.onGetStarted()
        val step = permissionsState()
        assertTrue(step.includesHistory)
        assertFalse(step.denied)

        repository.granted = allPermissions
        viewModel.onPermissionResult(allPermissions)

        assertEquals(OnboardingState.Done, state)
        assertEquals(now, preferences.firstGrantAt.value)
    }

    @Test
    fun partialGrantFinishes() {
        viewModel.onGetStarted()
        val partial = setOf(RecordType.STEPS.readPermission, RecordType.SLEEP_SESSION.readPermission)
        repository.granted = partial
        viewModel.onPermissionResult(partial)

        assertEquals(OnboardingState.Done, state)
        assertEquals(now, preferences.firstGrantAt.value)
    }

    @Test
    fun noneGrantedExplainsAndAllowsRetry() {
        viewModel.onGetStarted()
        viewModel.onPermissionResult(emptySet())

        val denied = permissionsState()
        assertTrue(denied.denied)
        assertEquals(allPermissions, denied.toRequest)
        assertNull(preferences.firstGrantAt.value)

        // A second refusal keeps the explanation; a later grant finishes.
        viewModel.onPermissionResult(emptySet())
        assertTrue(permissionsState().denied)
        repository.granted = setOf(RecordType.WEIGHT.readPermission)
        viewModel.onPermissionResult(setOf(RecordType.WEIGHT.readPermission))
        assertEquals(OnboardingState.Done, state)
    }

    @Test
    fun historyAloneDoesNotCountAsAGrant() {
        viewModel.onGetStarted()
        repository.granted = setOf(history)
        viewModel.onPermissionResult(setOf(history))

        assertTrue(permissionsState().denied)
        assertNull(preferences.firstGrantAt.value)
    }

    @Test
    fun historyUnavailableIsNotRequested() {
        repository.historyFeatureAvailable = false
        viewModel.onGetStarted()

        val step = permissionsState()
        assertFalse(step.includesHistory)
        assertEquals(RecordType.readPermissions, step.toRequest)
        assertFalse(history in step.toRequest)
    }

    @Test
    fun historyFeatureCheckFailingDropsHistory() {
        repository.failures[FakeHealthRepository.Call.HISTORY_FEATURE] = HealthAccessException.Unavailable()
        viewModel.onGetStarted()
        assertEquals(RecordType.readPermissions, permissionsState().toRequest)
    }

    @Test
    fun grantInHealthConnectSettingsIsPickedUpOnResume() {
        viewModel.onGetStarted()
        viewModel.onPermissionResult(emptySet())
        assertTrue(permissionsState().denied)

        repository.granted = setOf(RecordType.HEART_RATE.readPermission)
        viewModel.onResume()
        assertEquals(OnboardingState.Done, state)
    }

    @Test
    fun resumeWithoutGrantsKeepsThePermissionStepUnchanged() {
        viewModel.onGetStarted()
        viewModel.onResume()
        assertFalse(permissionsState().denied)
    }

    @Test
    fun contractResultMissingEarlierGrantsStillFinishes() {
        // Health Connect already holds a grant the contract result leaves out.
        viewModel.onGetStarted()
        repository.granted = setOf(RecordType.STEPS.readPermission)
        viewModel.onPermissionResult(emptySet())
        assertEquals(OnboardingState.Done, state)
    }

    @Test
    fun alreadyGrantedFinishesWithoutAsking() {
        repository.granted = setOf(RecordType.DISTANCE.readPermission)
        viewModel.onGetStarted()
        assertEquals(OnboardingState.Done, state)
        assertEquals(now, preferences.firstGrantAt.value)
    }

    @Test
    fun firstGrantKeepsTheEarlierValue() {
        val earlier = Instant.parse("2026-01-01T00:00:00Z")
        val prefs = FakeUserPreferences(firstGrant = earlier)
        val vm = OnboardingViewModel(repository, prefs, Clock.fixed(now, ZoneOffset.UTC))
        vm.onGetStarted()
        repository.granted = allPermissions
        vm.onPermissionResult(allPermissions)

        assertEquals(OnboardingState.Done, vm.state.value)
        assertEquals(earlier, prefs.firstGrantAt.value)
    }

    @Test
    fun failingGrantedPermissionsCallIsTreatedAsNoGrant() {
        repository.failures[FakeHealthRepository.Call.GRANTED] = HealthAccessException.RateLimited()
        viewModel.onGetStarted()
        permissionsState()
        viewModel.onPermissionResult(emptySet())
        assertTrue(permissionsState().denied)
        // The contract result alone is enough when the grants query fails.
        viewModel.onPermissionResult(setOf(RecordType.STEPS.readPermission))
        assertEquals(OnboardingState.Done, state)
    }

    @Test
    fun permissionDialogFailingRechecksStatus() {
        viewModel.onGetStarted()
        repository.availability = HcAvailability.NOT_INSTALLED
        viewModel.onPermissionRequestFailed()
        assertEquals(OnboardingState.HealthConnectMissing(HcAvailability.NOT_INSTALLED), state)
    }
}
