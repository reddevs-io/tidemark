// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.R
import io.reddevs.tidemark.onboarding.markdown.MarkdownBlock
import io.reddevs.tidemark.onboarding.markdown.MarkdownDocument
import io.reddevs.tidemark.ui.components.ErrorState
import io.reddevs.tidemark.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Renders the bundled privacy policy (assets/privacy/PRIVACY.md or PRIVACY.fr.md, copied from the
 * repo root at build time). Used in the app's navigation and by [PrivacyPolicyActivity].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val policy by produceState<PolicyState>(PolicyState.Loading, locale) {
        value = withContext(Dispatchers.IO) {
            try {
                PolicyState.Loaded(PrivacyPolicySource.load(context.assets, locale))
            } catch (_: IOException) {
                PolicyState.Failed
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val state = policy) {
            PolicyState.Loading -> LoadingState(Modifier.padding(padding))
            PolicyState.Failed -> ErrorState(
                modifier = Modifier.padding(padding),
                message = stringResource(R.string.privacy_policy_load_error, stringResource(R.string.privacy_policy_url)),
            )
            is PolicyState.Loaded -> MarkdownDocument(
                blocks = state.blocks,
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

private sealed interface PolicyState {
    data object Loading : PolicyState

    data object Failed : PolicyState

    data class Loaded(val blocks: List<MarkdownBlock>) : PolicyState
}
