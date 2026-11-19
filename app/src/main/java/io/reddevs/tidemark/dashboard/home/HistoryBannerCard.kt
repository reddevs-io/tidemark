// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.format.DateStyle
import io.reddevs.tidemark.format.MeasurementFormatter
import java.time.LocalDate

internal const val BANNER_TEST_TAG = "dashboard_history_banner"

/** Explains why data older than 30 days before the first grant is missing. */
@Composable
internal fun HistoryBannerCard(
    banner: HistoryBanner,
    formatter: MeasurementFormatter,
    today: LocalDate,
    onOpenHealthConnectSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = banner.visibleFrom?.let {
        formatter.date(it, if (it.year == today.year) DateStyle.DAY_MONTH else DateStyle.LONG)
    }
    val body = when (banner.access) {
        HistoryAccess.NOT_GRANTED -> if (date != null) {
            stringResource(R.string.dashboard_banner_not_granted_since, date)
        } else {
            stringResource(R.string.dashboard_banner_not_granted)
        }
        else -> if (date != null) {
            stringResource(R.string.dashboard_banner_unavailable_since, date)
        } else {
            stringResource(R.string.dashboard_banner_unavailable)
        }
    }
    Card(
        modifier = modifier.fillMaxWidth().testTag(BANNER_TEST_TAG),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.History, contentDescription = null)
                Text(
                    stringResource(R.string.dashboard_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dashboard_banner_dismiss)) }
                if (banner.canOpenSettings) {
                    TextButton(onClick = onOpenHealthConnectSettings) {
                        Text(stringResource(R.string.action_open_health_connect_settings))
                    }
                }
            }
        }
    }
}
