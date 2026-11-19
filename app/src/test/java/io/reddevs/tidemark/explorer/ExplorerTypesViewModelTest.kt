// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeHealthRepository.Call
import io.reddevs.tidemark.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExplorerTypesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeHealthRepository()

    @Test
    fun listsAllEightTypesAsReadable() {
        val state = ExplorerTypesViewModel(repo).state.value

        assertFalse(state.loading)
        assertEquals(RecordType.entries, state.types.map { it.type })
        assertTrue(state.types.all { it.readable })
    }

    @Test
    fun marksTypesWithoutPermissionAsLocked() {
        repo.granted = setOf(RecordType.STEPS.readPermission, RecordType.SLEEP_SESSION.readPermission)

        val readable = ExplorerTypesViewModel(repo).state.value.types.filter { it.readable }.map { it.type }

        assertEquals(listOf(RecordType.STEPS, RecordType.SLEEP_SESSION), readable)
    }

    @Test
    fun refreshPicksUpNewGrants() {
        repo.granted = emptySet()
        val vm = ExplorerTypesViewModel(repo)
        assertTrue(vm.state.value.types.none { it.readable })

        repo.granted = setOf(RecordType.WEIGHT.readPermission)
        vm.refresh()

        assertEquals(listOf(RecordType.WEIGHT), vm.state.value.types.filter { it.readable }.map { it.type })
    }

    @Test
    fun unavailableHealthConnectIsAnError() {
        repo.availability = HcAvailability.NOT_INSTALLED

        val state = ExplorerTypesViewModel(repo).state.value

        assertEquals(LoadError.UNAVAILABLE, state.error)
        assertTrue(state.types.isEmpty())
        assertEquals(null, repo.calls[Call.GRANTED])
    }

    @Test
    fun failureIsAnErrorThatCanBeRetried() {
        repo.failures[Call.GRANTED] = HealthAccessException.RateLimited()
        val vm = ExplorerTypesViewModel(repo)
        assertEquals(LoadError.RATE_LIMITED, vm.state.value.error)

        repo.failures.clear()
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(8, vm.state.value.types.size)
    }
}
