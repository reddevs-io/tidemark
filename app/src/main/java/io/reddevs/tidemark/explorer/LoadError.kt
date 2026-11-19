// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.annotation.StringRes
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.HealthAccessException
import kotlin.coroutines.cancellation.CancellationException

/** Why an explorer screen could not load. Permission problems get their own UI with a settings link. */
enum class LoadError(@StringRes val message: Int) {
    PERMISSION_MISSING(R.string.error_generic),
    RATE_LIMITED(R.string.error_rate_limited),
    UNAVAILABLE(R.string.error_unavailable),
    GENERIC(R.string.error_generic),
    ;

    companion object {
        fun from(error: Throwable): LoadError = when (error) {
            is HealthAccessException.PermissionMissing -> PERMISSION_MISSING
            is HealthAccessException.RateLimited -> RATE_LIMITED
            is HealthAccessException.Unavailable -> UNAVAILABLE
            else -> GENERIC
        }
    }
}

/**
 * Runs [block] and maps any failure to a [LoadError]. Cancellation still propagates, so a
 * superseded load never reports an error.
 */
internal inline fun <T> loadCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
