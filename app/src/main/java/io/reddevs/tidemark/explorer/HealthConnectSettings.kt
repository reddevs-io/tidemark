// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient

/** Opens Health Connect's data and access screen, where the user can grant Tidemark a missing type. */
internal fun openHealthConnectSettings(context: Context): Boolean = try {
    val intent = HealthConnectClient.getHealthConnectManageDataIntent(context)
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
