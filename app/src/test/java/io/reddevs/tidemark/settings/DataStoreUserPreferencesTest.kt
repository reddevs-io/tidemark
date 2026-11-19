// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DataStoreUserPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "test.preferences_pb") }

    /** A DataStore on [file] whose lifetime is its own job, so a later one can reopen the file. */
    private fun TestScope.openStore(): Pair<DataStore<Preferences>, Job> {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(coroutineContext + job)) { file }
        return store to job
    }

    @Test
    fun defaultsAreSystemAndNoFirstGrant() = runTest {
        val (store, job) = openStore()
        val preferences = DataStoreUserPreferences(store)
        assertEquals(UnitPreference.SYSTEM, preferences.unitPreference.first())
        assertNull(preferences.firstGrantAt.first())
        job.cancelAndJoin()
    }

    @Test
    fun unitPreferenceRoundTripsEveryValue() = runTest {
        val (store, job) = openStore()
        val preferences = DataStoreUserPreferences(store)
        for (value in listOf(UnitPreference.IMPERIAL, UnitPreference.METRIC, UnitPreference.SYSTEM, UnitPreference.IMPERIAL)) {
            preferences.setUnitPreference(value)
            assertEquals(value, preferences.unitPreference.first())
        }
        job.cancelAndJoin()
    }

    @Test
    fun unitPreferenceSurvivesANewInstance() = runTest {
        val (first, firstJob) = openStore()
        DataStoreUserPreferences(first).setUnitPreference(UnitPreference.IMPERIAL)
        firstJob.cancelAndJoin()

        val (second, secondJob) = openStore()
        assertEquals(UnitPreference.IMPERIAL, DataStoreUserPreferences(second).unitPreference.first())
        secondJob.cancelAndJoin()
    }

    @Test
    fun choosingSystemClearsTheStoredOverride() = runTest {
        val (store, job) = openStore()
        val preferences = DataStoreUserPreferences(store)
        preferences.setUnitPreference(UnitPreference.METRIC)
        preferences.setUnitPreference(UnitPreference.SYSTEM)
        assertNull(store.data.first()[DataStoreUserPreferences.UNIT_KEY])
        job.cancelAndJoin()
    }

    @Test
    fun unknownStoredUnitFallsBackToSystem() = runTest {
        val (store, job) = openStore()
        store.edit { it[DataStoreUserPreferences.UNIT_KEY] = "STONES" }
        assertEquals(UnitPreference.SYSTEM, DataStoreUserPreferences(store).unitPreference.first())
        job.cancelAndJoin()
    }

    @Test
    fun recordFirstGrantIfAbsentKeepsTheFirstValue() = runTest {
        val (store, job) = openStore()
        val preferences = DataStoreUserPreferences(store)
        val first = Instant.parse("2026-10-20T08:15:30.123Z")
        preferences.recordFirstGrantIfAbsent(first)
        preferences.recordFirstGrantIfAbsent(Instant.parse("2026-11-19T10:00:00Z"))
        preferences.recordFirstGrantIfAbsent(Instant.parse("2025-01-01T00:00:00Z"))
        assertEquals(first, preferences.firstGrantAt.first())
        job.cancelAndJoin()
    }

    @Test
    fun firstGrantSurvivesANewInstanceAndIsStillNotOverwritten() = runTest {
        val first = Instant.parse("2026-10-20T08:15:30Z")
        val (store, job) = openStore()
        DataStoreUserPreferences(store).recordFirstGrantIfAbsent(first)
        job.cancelAndJoin()

        val (reopened, reopenedJob) = openStore()
        val preferences = DataStoreUserPreferences(reopened)
        preferences.recordFirstGrantIfAbsent(Instant.parse("2026-11-19T10:00:00Z"))
        assertEquals(first, preferences.firstGrantAt.first())
        reopenedJob.cancelAndJoin()
    }

    @Test
    fun unitAndFirstGrantAreIndependent() = runTest {
        val (store, job) = openStore()
        val preferences = DataStoreUserPreferences(store)
        val grant = Instant.parse("2026-10-20T08:15:30Z")
        preferences.recordFirstGrantIfAbsent(grant)
        preferences.setUnitPreference(UnitPreference.IMPERIAL)
        preferences.setUnitPreference(UnitPreference.SYSTEM)
        assertEquals(grant, preferences.firstGrantAt.first())
        job.cancelAndJoin()
    }

    @Test
    fun contextInstancesShareOneStore() = runTest {
        // Two DataStores on one file would throw; the Context constructor must share a single one.
        val context: Context = ApplicationProvider.getApplicationContext()
        val a = DataStoreUserPreferences(context)
        val b = DataStoreUserPreferences(context)
        a.setUnitPreference(UnitPreference.METRIC)
        assertEquals(UnitPreference.METRIC, b.unitPreference.first())
        b.setUnitPreference(UnitPreference.SYSTEM)
        assertEquals(UnitPreference.SYSTEM, a.unitPreference.first())
    }
}
