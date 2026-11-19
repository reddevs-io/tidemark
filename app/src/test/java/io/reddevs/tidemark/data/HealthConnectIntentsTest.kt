// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.health.connect.client.HealthConnectClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class HealthConnectIntentsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    // Make startActivity throw ActivityNotFoundException for unresolved intents, as a device does.
    @Before
    fun failUnresolvedActivities() {
        shadowOf(app).checkActivities(true)
    }

    @Test
    fun accessTriesTidemarksDataScreenBeforeGeneralSettings() {
        val candidates = HealthConnectIntents.access(app)
        assertEquals(
            HealthConnectClient.getHealthConnectManageDataIntent(app, HealthConnectHealthRepository.PROVIDER_PACKAGE).action,
            candidates.first().action,
        )
        assertEquals(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS, candidates.last().action)
    }

    @Test
    fun startFirstReturnsFalseWhenNothingResolves() {
        assertFalse(HealthConnectIntents.startFirst(app, listOf(Intent("io.reddevs.tidemark.test.NOTHING"))))
    }

    @Test
    fun startFirstSkipsCandidatesThatDoNotResolve() {
        val handled = Intent("io.reddevs.tidemark.test.HANDLED")
        val component = ComponentName("io.reddevs.tidemark.test", "Handler")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(component, IntentFilter(handled.action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }

        val opened = HealthConnectIntents.startFirst(app, listOf(Intent("io.reddevs.tidemark.test.NOTHING"), handled))

        assertTrue(opened)
        assertEquals(handled.action, shadowOf(app).nextStartedActivity.action)
    }
}
