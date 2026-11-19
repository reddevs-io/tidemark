// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.navigation.Dashboard
import io.reddevs.tidemark.navigation.Onboarding

/** Picks the first screen. T-003 owns this file. */
object OnboardingGate {
    /** [Dashboard] when Health Connect is available and at least one read permission is granted. */
    suspend fun startDestination(container: AppContainer): Any {
        val repository = container.healthRepository
        if (repository.availability() != HcAvailability.AVAILABLE) return Onboarding
        val granted = try {
            repository.grantedPermissions()
        } catch (e: HealthAccessException) {
            emptySet()
        }
        return if (granted.any { it in RecordType.readPermissions }) Dashboard else Onboarding
    }
}
