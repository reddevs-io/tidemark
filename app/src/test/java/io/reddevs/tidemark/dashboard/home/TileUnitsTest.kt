// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Test

class TileUnitsTest {
    private val unit = SpanStyle(fontSize = 0.5.em)

    private fun styledRuns(value: String): List<String> =
        withSmallUnits(value, unit).spanStyles.map { value.substring(it.start, it.end) }

    @Test
    fun keepsTheTextAndShrinksOnlyTheUnitWords() {
        assertEquals("7 h 32 min", withSmallUnits("7 h 32 min", unit).text)
        assertEquals(listOf("h", "min"), styledRuns("7 h 32 min"))
        assertEquals(listOf("steps"), styledRuns("8,432 steps"))
        assertEquals(listOf("pas"), styledRuns("8 432 pas"))
        assertEquals(listOf("kg"), styledRuns("72,5 kg"))
        assertEquals(emptyList<String>(), styledRuns("412"))
    }
}
