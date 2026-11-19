// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.RecordPage
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.testing.FakeHealthRepository
import java.time.Instant

/** [FakeHealthRepository] that also records the arguments of each page query. */
class RecordingHealthRepository : FakeHealthRepository() {
    data class PageQuery(
        val type: RecordType,
        val start: Instant,
        val end: Instant,
        val source: DataSource?,
        val pageToken: String?,
        val pageSize: Int,
    )

    val pageQueries = mutableListOf<PageQuery>()

    /** When set, dataOrigins returns this instead of the origins in [records], like an aggregate that drops sources. */
    var aggregateOrigins: Set<DataSource>? = null

    override suspend fun readPage(
        type: RecordType,
        start: Instant,
        end: Instant,
        source: DataSource?,
        pageToken: String?,
        pageSize: Int,
    ): RecordPage {
        pageQueries += PageQuery(type, start, end, source, pageToken, pageSize)
        return super.readPage(type, start, end, source, pageToken, pageSize)
    }

    override suspend fun dataOrigins(type: RecordType, start: Instant, end: Instant): Set<DataSource> {
        val all = super.dataOrigins(type, start, end)
        return aggregateOrigins ?: all
    }
}
