// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient

/**
 * Intents that leave Tidemark for Play or Health Connect, so UI packages never reference
 * HealthConnectClient. Each action is a list of candidates tried in order, because which one
 * resolves depends on the Android version, whether Play is installed, and the Health Connect build.
 */
object HealthConnectIntents {
    private const val PROVIDER_PACKAGE = HealthConnectHealthRepository.PROVIDER_PACKAGE
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /** Play's onboarding deep link for Health Connect, as given in the Health Connect docs. */
    private const val MARKET_URI = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
    private const val PLAY_WEB_URI = "https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE"

    /** Install or update Health Connect: Play app first, then Play in a browser. */
    fun installOrUpdate(context: Context): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW, MARKET_URI.toUri())
            .setPackage(PLAY_STORE_PACKAGE)
            .putExtra("overlay", true)
            .putExtra("callerId", context.packageName),
        Intent(Intent.ACTION_VIEW, MARKET_URI.toUri()),
        Intent(Intent.ACTION_VIEW, PLAY_WEB_URI.toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
    )

    /**
     * Where the user grants or revokes Tidemark's access: Health Connect's data and access screen
     * for Tidemark first, then Health Connect's general settings.
     */
    fun access(context: Context): List<Intent> = listOf(
        HealthConnectClient.getHealthConnectManageDataIntent(context, PROVIDER_PACKAGE),
        // The library picks the platform action on Android 14+ and the app's action below.
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
    )

    /** Opens [access]. Returns false when nothing on the device handles it. */
    fun openAccess(context: Context): Boolean = startFirst(context, access(context))

    /** Starts the first candidate that resolves. Returns false when none does. */
    fun startFirst(context: Context, candidates: List<Intent>): Boolean {
        for (intent in candidates) {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next candidate.
            } catch (_: SecurityException) {
                // A candidate exists but is protected; try the next one.
            }
        }
        return false
    }
}
