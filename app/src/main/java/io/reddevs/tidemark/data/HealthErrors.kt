// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.health.connect.HealthConnectException
import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import io.reddevs.tidemark.data.model.RecordType
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs one Health Connect call and maps its failures to [HealthAccessException]. [type] is the
 * record type the call reads, so a SecurityException becomes [HealthAccessException.PermissionMissing]
 * for that type only and other metrics keep loading.
 */
internal inline fun <T> healthCall(type: RecordType?, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HealthAccessException) {
        throw e
    } catch (e: Exception) {
        throw e.toHealthAccessException(type)
    }

/**
 * connect-client turns provider errors into SecurityException (missing permission),
 * RemoteException or IOException (service or storage trouble), IllegalArgumentException and
 * IllegalStateException. Rate limiting arrives as a platform HealthConnectException with
 * ERROR_RATE_LIMIT_EXCEEDED wrapped in an IllegalStateException on Android 14+, and as a
 * RemoteException whose message names the quota through the Play provider on older versions.
 */
internal fun Throwable.toHealthAccessException(type: RecordType?): HealthAccessException = when {
    this is HealthAccessException -> this
    isRateLimit() -> HealthAccessException.RateLimited(this)
    this is SecurityException -> type?.let { HealthAccessException.PermissionMissing(it) } ?: HealthAccessException.Unexpected(this)
    this is RemoteException || this is IOException -> HealthAccessException.Unavailable(this)
    else -> HealthAccessException.Unexpected(this)
}

private val rateLimitMessage = Regex("rate.?limit|quota", RegexOption.IGNORE_CASE)

private fun Throwable.isRateLimit(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { error ->
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isPlatformRateLimit(error)) ||
            error.message?.let { rateLimitMessage.containsMatchIn(it) } == true
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun isPlatformRateLimit(error: Throwable): Boolean =
    error is HealthConnectException && error.errorCode == HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED

private const val MAX_CAUSE_DEPTH = 8
