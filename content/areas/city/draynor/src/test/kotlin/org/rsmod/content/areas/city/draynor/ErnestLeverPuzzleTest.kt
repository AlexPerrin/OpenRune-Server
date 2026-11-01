package org.rsmod.content.areas.city.draynor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.city.draynor.ErnestLeverPuzzle.Doors
import org.rsmod.content.areas.city.draynor.ErnestLeverPuzzle.LeverState

class ErnestLeverPuzzleTest {
    @Test
    fun `every door follows the wiki lever table in all lever states`() {
        for (bits in 0 until 64) {
            val down = BooleanArray(6) { bits shr it and 1 == 1 }
            val state = LeverState(down[0], down[1], down[2], down[3], down[4], down[5])
            for ((door, rule) in Rules) {
                val (table, actual) = WikiTable.getValue(door) to rule(state)
                val expected = table.indices.all { table[it] == 'x' || (table[it] == 'D') == down[it] }
                assertEquals(expected, actual, "door $door with levers down ${down.toList()}")
            }
        }
    }

    private companion object {
        private val WikiTable =
            mapOf(
                "4to7" to "DDUxUU",
                "4to5" to "DxUxUU",
                "5to8" to "xxxDUx",
                "5to6" to "xxxDUx",
                "3to6" to "xUxDxU",
                "2to3" to "xUxDxD",
                "1to2" to "UUxxDD",
                "2to5" to "xxxDUD",
                "8to9" to "xxDDxD",
            )

        private val Rules: Map<String, (LeverState) -> Boolean> =
            mapOf(
                "1to2" to Doors::oneToTwo,
                "2to3" to Doors::twoToThree,
                "2to5" to Doors::twoToFive,
                "3to6" to Doors::threeToSix,
                "4to5" to Doors::fourToFive,
                "4to7" to Doors::fourToSeven,
                "5to6" to Doors::fiveToSix,
                "5to8" to Doors::fiveToEight,
                "8to9" to Doors::eightToNine,
            )
    }
}
