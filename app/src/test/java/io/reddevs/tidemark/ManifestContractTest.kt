// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.permission.HealthPermission
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.onboarding.PrivacyPolicyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The manifest must request exactly the eight read permissions plus history, and no network access. */
@RunWith(AndroidJUnit4::class)
class ManifestContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val requested: Set<String> by lazy {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
    }

    @Test
    fun requestsExactlyTheHealthReadPermissions() {
        val expected = RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        assertEquals(9, expected.size)
        // androidx.core adds an app-private signature permission for its non-exported receivers.
        val androidxInternal = "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        assertEquals(expected, requested - androidxInternal)
    }

    @Test
    fun requestsNoNetworkAccess() {
        assertFalse("android.permission.INTERNET" in requested)
        assertFalse("android.permission.ACCESS_NETWORK_STATE" in requested)
    }

    @Test
    fun privacyPolicyActivityIsExported() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(ComponentName(context, PrivacyPolicyActivity::class.java), 0)
        assertEquals(true, info.exported)
    }
}
