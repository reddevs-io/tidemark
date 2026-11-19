// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Gives a purely visual element (spinner, chart) a spoken label. */
fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }
