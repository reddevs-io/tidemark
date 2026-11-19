// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.health.connect.client.permission.HealthPermission
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class OnboardingContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clicks = mutableListOf<String>()
    private var requested: Set<String>? = null

    private fun show(state: OnboardingState) {
        compose.setContent {
            TidemarkTheme {
                OnboardingContent(
                    state = state,
                    onGetStarted = { clicks += "start" },
                    onInstallOrUpdate = { clicks += "install" },
                    onRequestPermissions = { requested = it },
                    onOpenHealthConnectSettings = { clicks += "settings" },
                    onOpenPrivacyPolicy = { clicks += "privacy" },
                )
            }
        }
    }

    private fun text(id: Int) = context.getString(id)

    private fun button(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun welcomeOffersGetStartedAndThePolicy() {
        show(OnboardingState.Welcome)
        compose.onNodeWithText(text(R.string.onboarding_welcome_privacy)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_action_get_started)).performClick()
        compose.onNodeWithText(text(R.string.onboarding_action_privacy_policy)).performScrollTo().performClick()
        assertEquals(listOf("start", "privacy"), clicks)
    }

    @Test
    fun notInstalledOffersInstall() {
        show(OnboardingState.HealthConnectMissing(HcAvailability.NOT_INSTALLED))
        button(text(R.string.onboarding_action_install)).performClick()
        assertEquals(listOf("install"), clicks)
    }

    @Test
    fun updateRequiredOffersUpdate() {
        show(OnboardingState.HealthConnectMissing(HcAvailability.UPDATE_REQUIRED))
        compose.onNodeWithText(text(R.string.onboarding_update_body)).assertExists()
        button(text(R.string.onboarding_action_update)).performClick()
        assertEquals(listOf("install"), clicks)
    }

    @Test
    fun unsupportedHasNoActions() {
        show(OnboardingState.HealthConnectMissing(HcAvailability.UNSUPPORTED))
        compose.onNodeWithText(text(R.string.onboarding_unsupported_body)).assertExists()
        button(text(R.string.onboarding_action_install)).assertDoesNotExist()
        button(text(R.string.onboarding_action_update)).assertDoesNotExist()
    }

    @Test
    fun permissionStepListsHistoryOnlyWhenRequested() {
        val withHistory = RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        show(OnboardingState.Permissions(withHistory, includesHistory = true))
        compose.onNodeWithText(text(R.string.onboarding_permission_history)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.onboarding_action_continue)).performScrollTo().performClick()
        assertEquals(withHistory, requested)
    }

    @Test
    fun permissionStepWithoutHistory() {
        show(OnboardingState.Permissions(RecordType.readPermissions, includesHistory = false))
        compose.onNodeWithText(text(R.string.onboarding_permission_weight)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.onboarding_permission_history)).assertDoesNotExist()
    }

    @Test
    fun deniedOffersRetryAndHealthConnectSettings() {
        show(OnboardingState.Permissions(RecordType.readPermissions, includesHistory = false, denied = true))
        compose.onNodeWithText(text(R.string.onboarding_denied_body)).assertExists()
        compose.onNodeWithText(text(R.string.action_retry)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.action_open_health_connect_settings)).performScrollTo().performClick()
        assertEquals(RecordType.readPermissions, requested)
        assertEquals(listOf("settings"), clicks)
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun frenchNamesHealthConnectConnexionSante() {
        show(OnboardingState.HealthConnectMissing(HcAvailability.NOT_INSTALLED))
        button("Installer Connexion Santé").assertExists()
    }
}
