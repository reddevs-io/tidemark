// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.dashboard.detail.MetricDetailRoute
import io.reddevs.tidemark.dashboard.home.DashboardRoute
import io.reddevs.tidemark.explorer.ExplorerRoute
import io.reddevs.tidemark.explorer.RecordDetailRoute
import io.reddevs.tidemark.onboarding.OnboardingRoute
import io.reddevs.tidemark.onboarding.PrivacyPolicyScreen
import io.reddevs.tidemark.settings.SettingsRoute
import java.time.LocalDate

@Composable
fun TidemarkNavHost(container: AppContainer, startDestination: Any) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startDestination) {
        composable<Onboarding> {
            OnboardingRoute(
                container = container,
                onDone = { nav.navigate(Dashboard) { popUpTo<Onboarding> { inclusive = true } } },
            )
        }
        composable<Dashboard> {
            DashboardRoute(
                container = container,
                onOpenMetric = { metric ->
                    nav.navigate(MetricDetail(metric = metric, anchorEpochDay = LocalDate.now(container.clock).toEpochDay()))
                },
                onOpenExplorer = { nav.navigate(Explorer()) },
                onOpenSettings = { nav.navigate(Settings) },
            )
        }
        composable<MetricDetail> { entry ->
            MetricDetailRoute(
                container = container,
                args = entry.toRoute(),
                onOpenRecords = { type, day -> nav.navigate(Explorer.of(type, day)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<Explorer> { entry ->
            ExplorerRoute(
                container = container,
                args = entry.toRoute(),
                onOpenRecord = { type, id -> nav.navigate(RecordDetail(type, id)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<RecordDetail> { entry ->
            RecordDetailRoute(container = container, args = entry.toRoute(), onBack = { nav.popBackStack() })
        }
        composable<Settings> {
            SettingsRoute(
                container = container,
                onOpenPrivacy = { nav.navigate(PrivacyPolicy) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<PrivacyPolicy> {
            PrivacyPolicyScreen(onBack = { nav.popBackStack() })
        }
    }
}
