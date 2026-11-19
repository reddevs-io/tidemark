// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

/**
 * [UserPreferences] persisted with DataStore Preferences. It holds the two values the privacy
 * policy names and nothing else: the unit override and the first-grant time.
 *
 * Tests pass their own [DataStore]. The app uses the [Context] constructor, which shares one
 * DataStore per file across the process, because DataStore fails when two instances open the same
 * file.
 */
class DataStoreUserPreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferences {

    constructor(context: Context) : this(sharedDataStore(context.applicationContext ?: context))

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        // A corrupt or unreadable file falls back to defaults rather than crashing the app.
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val unitPreference: Flow<UnitPreference> = preferences
        .map { prefs -> prefs[UNIT_KEY].toUnitPreference() }
        .distinctUntilChanged()

    override suspend fun setUnitPreference(preference: UnitPreference) {
        dataStore.edit { prefs ->
            if (preference == UnitPreference.SYSTEM) prefs.remove(UNIT_KEY) else prefs[UNIT_KEY] = preference.name
        }
    }

    override val firstGrantAt: Flow<Instant?> = preferences
        .map { prefs -> prefs[FIRST_GRANT_KEY]?.let(Instant::ofEpochMilli) }
        .distinctUntilChanged()

    /** Atomic: the check and the write happen in one DataStore transaction. Stored to the millisecond. */
    override suspend fun recordFirstGrantIfAbsent(time: Instant) {
        dataStore.edit { prefs ->
            if (prefs[FIRST_GRANT_KEY] == null) prefs[FIRST_GRANT_KEY] = time.toEpochMilli()
        }
    }

    internal companion object {
        const val FILE_NAME = "tidemark_preferences"
        val UNIT_KEY = stringPreferencesKey("unit_preference")
        val FIRST_GRANT_KEY = longPreferencesKey("first_grant_at_epoch_ms")

        private val stores = mutableMapOf<String, DataStore<Preferences>>()

        fun sharedDataStore(context: Context): DataStore<Preferences> {
            val file = context.preferencesDataStoreFile(FILE_NAME)
            return synchronized(stores) {
                stores.getOrPut(file.absolutePath) { PreferenceDataStoreFactory.create(produceFile = { file }) }
            }
        }

        /** Unknown or missing values (for example after a downgrade) mean "follow the system". */
        fun String?.toUnitPreference(): UnitPreference =
            UnitPreference.entries.firstOrNull { it.name == this } ?: UnitPreference.SYSTEM
    }
}
