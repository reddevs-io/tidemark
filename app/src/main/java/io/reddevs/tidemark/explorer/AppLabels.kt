// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the label and icon of the app that wrote a record. Other Health Connect apps are
 * visible through the manifest's <queries> entry for the permissions rationale intent, so no
 * QUERY_ALL_PACKAGES is needed. A package that can't be resolved shows its package name.
 */
class AppLabels(private val packageManager: PackageManager) {
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Icon>()

    // ConcurrentHashMap can't hold null, so a missing icon is cached as Icon(null).
    private class Icon(val bitmap: ImageBitmap?)

    fun label(packageName: String): String = labels.getOrPut(packageName) {
        applicationInfo(packageName)?.let { packageManager.getApplicationLabel(it).toString() }
            ?.takeIf { it.isNotBlank() }
            ?: packageName
    }

    /** The app icon at [sizePx], or null when the package isn't visible. Call off the main thread. */
    fun icon(packageName: String, sizePx: Int): ImageBitmap? = icons.getOrPut("$packageName@$sizePx") {
        Icon(
            applicationInfo(packageName)?.let { info ->
                runCatching { packageManager.getApplicationIcon(info).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
            },
        )
    }.bitmap

    private fun applicationInfo(packageName: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        @Volatile
        private var instance: AppLabels? = null

        /** One cache per process, so labels survive screen changes. */
        fun get(context: Context): AppLabels =
            instance ?: synchronized(this) {
                instance ?: AppLabels(context.applicationContext.packageManager).also { instance = it }
            }
    }
}
