package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.table.ImplingTypesRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.skills.hunter.ImplingScript.Companion.IMPLING_JAR
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class ImplingLootScriptTest {
    @Test
    fun `every caught jar has a loot table whose main table sums to one`() {
        for (row in ImplingTypesRow.all()) {
            if (row.jar.internalName == "obj.ii_captured_impling_11") continue // lucky: clue tables
            val table = ImplingLootTables.tables[row.jar.id]
            checkNotNull(table) { "no loot for ${row.jar.internalName}" }
            val sum = table.main.sumOf { it.num.toDouble() / it.den }
            assertEquals(1.0, sum, 1e-9, row.jar.internalName)
        }
    }

    @Test
    fun `loot is the jars' third inventory op, which the client sends as op 3`() {
        val type = checkNotNull(ServerCacheManager.getItem(11256))
        assertEquals(2, type.interfaceOptions.indexOfFirst { it == "Loot" })
    }

    @Test
    fun `the first main-table line comes out when every roll is zero`() {
        val baby = checkNotNull(ImplingLootTables.tables[11238])
        // Zero hits every tertiary drop too (clue scrolls).
        val drops = ImplingLootScript.roll(baby) { 0 }
        assertEquals(1755, drops.first().obj) // Chisel
        assertEquals(1 + baby.tertiary.size, drops.size)
    }

    @Test
    fun GameTestState.`looting swaps the jar for loot`() =
        runInjectedGameTest(ImplingLootScript::class, null, ImplingLootScript::class) { script ->
            val jar = "obj.ii_captured_impling_10" // dragon: no "nothing" line
            player.clearInv()
            player.inv[0] = InvObj(jar.asRSCM(RSCMType.OBJ), 1)
            with(script) { player.withProtectedAccess { loot(0, jar) } }
            advance(ticks = 1)
            assertEquals(0, player.inv.count("obj.ii_captured_impling_10"))
            val others = player.inv.objs.filterNotNull().filter {
                it.id != IMPLING_JAR.asRSCM(RSCMType.OBJ)
            }
            assertTrue(others.isNotEmpty(), player.inv.objs.toList().toString())
            assertTrue(player.inv.count(IMPLING_JAR) <= 1)
        }
}
