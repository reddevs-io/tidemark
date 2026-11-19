// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.health.connect.HealthConnectException
import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.model.RecordType
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class HealthErrorsTest {

    @Test
    fun securityExceptionBecomesPermissionMissingForTheType() {
        val mapped = SecurityException("no permission").toHealthAccessException(RecordType.WEIGHT)
        assertTrue(mapped is HealthAccessException.PermissionMissing)
        assertEquals(RecordType.WEIGHT, (mapped as HealthAccessException.PermissionMissing).type)

        assertTrue(SecurityException("x").toHealthAccessException(null) is HealthAccessException.Unexpected)
    }

    @Test
    fun rateLimitsAreRecognisedOnBothProviderPaths() {
        // Android 14+: the platform exception wrapped by connect-client.
        val platform = IllegalStateException(platformException(HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED, "busy"))
        assertTrue(platform.toHealthAccessException(RecordType.STEPS) is HealthAccessException.RateLimited)
        // Play provider (Android 9 to 13): a RemoteException naming the quota.
        val apk = RemoteException("Rate limited request quota has been exceeded")
        assertTrue(apk.toHealthAccessException(RecordType.STEPS) is HealthAccessException.RateLimited)
    }

    @Test
    fun serviceTroubleIsUnavailableAndTheRestUnexpected() {
        assertTrue(RemoteException("binder died").toHealthAccessException(RecordType.STEPS) is HealthAccessException.Unavailable)
        assertTrue(IOException("disk").toHealthAccessException(RecordType.STEPS) is HealthAccessException.Unavailable)
        val internal = IllegalStateException(platformException(HealthConnectException.ERROR_INTERNAL, "boom"))
        assertTrue(internal.toHealthAccessException(RecordType.STEPS) is HealthAccessException.Unexpected)
    }

    @Test
    fun healthCallPassesCancellationAndOwnErrorsThrough() {
        val cancel = CancellationException("cancelled")
        assertSame(cancel, runCatching { healthCall(RecordType.STEPS) { throw cancel } }.exceptionOrNull())
        val own = HealthAccessException.RateLimited()
        assertSame(own, runCatching { healthCall(RecordType.STEPS) { throw own } }.exceptionOrNull())
    }

    /** HealthConnectException's constructor is hidden in the SDK stubs; Robolectric's android-all has it. */
    private fun platformException(code: Int, message: String): HealthConnectException =
        HealthConnectException::class.java
            .getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java)
            .apply { isAccessible = true }
            .newInstance(code, message)
}
