package org.rsmod.content.travel.fairyrings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.table.FairyringRow
import org.rsmod.content.travel.fairyrings.FairyRingScript.Companion.isDestination

class FairyRingScriptTest {
    private val left = listOf('A', 'D', 'C', 'B')
    private val middle = listOf('I', 'L', 'K', 'J')
    private val right = listOf('P', 'S', 'R', 'Q')

    @Test
    fun `row ids are the dial positions of their code`() {
        val rows = FairyringRow.all()
        assertEquals(64, rows.size)
        for (row in rows) {
            val code = row.code.replace(" ", "")
            val id = left.indexOf(code[0]) * 100 + middle.indexOf(code[1]) * 10 + right.indexOf(code[2])
            assertEquals(id, row.id, code)
        }
        assertEquals((0 until 64).toSet(), rows.map { it.multilocState }.toSet())
    }

    @Test
    fun `the nine unused codes and the house ring (DIQ) have no fixed destination`() {
        val unused = FairyringRow.all().filterNot(::isDestination).map { it.code.replace(" ", "") }.toSet()
        assertEquals(setOf("AIP", "BIR", "BJQ", "CJP", "CJS", "CLQ", "DJQ", "DJS", "DKQ", "DIQ"), unused)
        assertTrue(isDestination(FairyringRow.all().single { it.code == FairyRingScript.ZANARIS_CODE }))
        assertFalse(FairyringRow.all().filter(::isDestination).any { it.destCoord.x < 1000 })
    }
}
