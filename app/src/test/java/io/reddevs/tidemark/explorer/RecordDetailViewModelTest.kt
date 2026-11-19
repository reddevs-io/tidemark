// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeHealthRepository.Call
import io.reddevs.tidemark.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeHealthRepository().apply {
        records[RecordType.SLEEP_SESSION] = listOf(ExplorerTestRecords.sleep("night"))
    }

    @Test
    fun loadsTheRecord() {
        val state = RecordDetailViewModel(repo, RecordType.SLEEP_SESSION, "night").state.value

        assertEquals("night", (state as RecordDetailState.Loaded).record.metadata.id)
    }

    @Test
    fun missingRecordIsNotFound() {
        val state = RecordDetailViewModel(repo, RecordType.SLEEP_SESSION, "gone").state.value

        assertEquals(RecordDetailState.NotFound, state)
    }

    @Test
    fun missingPermissionRetriesOnResume() {
        repo.granted = emptySet()
        val vm = RecordDetailViewModel(repo, RecordType.SLEEP_SESSION, "night")
        assertEquals(RecordDetailState.Failed(LoadError.PERMISSION_MISSING), vm.state.value)

        repo.granted = setOf(RecordType.SLEEP_SESSION.readPermission)
        vm.onResume()

        assertEquals(RecordDetailState.Loaded::class, vm.state.value::class)
    }

    @Test
    fun errorRetriesOnRequestOnly() {
        repo.failures[Call.READ_RECORD] = HealthAccessException.Unavailable()
        val vm = RecordDetailViewModel(repo, RecordType.SLEEP_SESSION, "night")
        assertEquals(RecordDetailState.Failed(LoadError.UNAVAILABLE), vm.state.value)

        repo.failures.clear()
        vm.onResume()
        assertEquals(1, repo.calls[Call.READ_RECORD])

        vm.load()
        assertEquals(RecordDetailState.Loaded::class, vm.state.value::class)
    }
}
