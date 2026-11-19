// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build

/**
 * Intents that leave Tidemark for Play or Health Connect. Each action is a list of candidates
 * tried in order, because which one resolves depends on the Android version, whether Play is
 * installed, and the Health Connect build. Plain action strings keep HealthConnectClient inside
 * the data package.
 */
object HealthConnectIntents {
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /** Play's onboarding deep link for Health Connect, as given in the Health Connect docs. */
    const val MARKET_URI = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
    const val PLAY_WEB_URI = "https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE"

    // Android 14+ framework actions (android.health.connect.HealthConnectManager).
    const val ACTION_MANAGE_HEALTH_PERMISSIONS = "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"
    const val ACTION_MANAGE_HEALTH_DATA = "android.health.connect.action.MANAGE_HEALTH_DATA"
    const val ACTION_HEALTH_HOME_SETTINGS = "android.health.connect.action.HEALTH_HOME_SETTINGS"

    // Health Connect app actions on Android 9 to 13.
    const val ACTION_APK_MANAGE_HEALTH_PERMISSIONS = "androidx.health.ACTION_MANAGE_HEALTH_PERMISSIONS"
    const val ACTION_APK_SETTINGS = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"

    /** Install or update Health Connect: Play app first, then Play in a browser. */
    fun installOrUpdate(context: Context): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW, MARKET_URI.toUri())
            .setPackage(PLAY_STORE_PACKAGE)
            .putExtra("overlay", true)
            .putExtra("callerId", context.packageName),
        Intent(Intent.ACTION_VIEW, MARKET_URI.toUri()),
        Intent(Intent.ACTION_VIEW, PLAY_WEB_URI.toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
    )

    /** Tidemark's own permission screen in Health Connect, then Health Connect's general settings. */
    fun appPermissions(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): List<Intent> = buildList {
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Intent(ACTION_MANAGE_HEALTH_PERMISSIONS).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName))
            add(Intent(ACTION_HEALTH_HOME_SETTINGS))
            add(Intent(ACTION_MANAGE_HEALTH_DATA))
        } else {
            add(
                Intent(ACTION_APK_MANAGE_HEALTH_PERMISSIONS)
                    .setPackage(PROVIDER_PACKAGE)
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
            )
            add(Intent(ACTION_APK_SETTINGS).setPackage(PROVIDER_PACKAGE))
        }
    }

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
