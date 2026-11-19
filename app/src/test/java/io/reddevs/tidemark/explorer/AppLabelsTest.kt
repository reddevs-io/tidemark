// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AppLabelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun unknownPackageFallsBackToItsName() {
        val labels = AppLabels(context.packageManager)

        assertEquals("com.example.missing", labels.label("com.example.missing"))
        assertNull(labels.icon("com.example.missing", 48))
    }

    @Test
    fun visiblePackageShowsItsLabelAndIcon() {
        val info = PackageInfo().apply {
            packageName = "com.example.fit"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.example.fit"
                nonLocalizedLabel = "Example Fit"
            }
        }
        shadowOf(context.packageManager).installPackage(info)
        val labels = AppLabels(context.packageManager)

        assertEquals("Example Fit", labels.label("com.example.fit"))
        assertNotNull(labels.icon("com.example.fit", 48))
    }
}
