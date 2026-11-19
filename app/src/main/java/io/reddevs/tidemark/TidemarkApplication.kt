// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark

import android.app.Application
import android.content.Context
import android.content.res.Configuration

class TidemarkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.onConfigurationChanged()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as TidemarkApplication).container
