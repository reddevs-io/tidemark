// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import io.reddevs.tidemark.data.model.RecordType

/**
 * Every failure a [HealthRepository] call can raise. ViewModels catch it and show an error state;
 * it must never crash the app.
 */
sealed class HealthAccessException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionMissing(val type: RecordType) : HealthAccessException("Read permission missing for $type")

    class RateLimited(cause: Throwable? = null) : HealthAccessException("Health Connect rate limit reached", cause)

    class Unavailable(cause: Throwable? = null) : HealthAccessException("Health Connect is not available", cause)

    class Unexpected(cause: Throwable) : HealthAccessException(cause.message ?: "Unexpected Health Connect error", cause)
}
