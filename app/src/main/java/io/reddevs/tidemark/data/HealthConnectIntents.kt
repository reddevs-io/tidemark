// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient

/**
 * Intents into Health Connect's own screens, so UI packages can open them without referencing
 * HealthConnectClient (which stays inside the data package).
 */
object HealthConnectIntents {
    /** Health Connect's data and access screen for Tidemark, where the user grants or revokes permissions. */
    fun manageData(context: Context): Intent =
        HealthConnectClient.getHealthConnectManageDataIntent(context, HealthConnectHealthRepository.PROVIDER_PACKAGE)

    /** Health Connect's main settings screen. */
    fun settings(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
}
