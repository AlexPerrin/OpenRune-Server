package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `dragon javelin tips only drop after Monkey Madness II`() {
        val dragon = ImplingLootTables.tables.values.single { t -> t.main.any { it.obj == 19582 } }
        fun objs(mm2: Boolean) =
            (0 until 20_000).flatMap { r -> ImplingLootScript.roll(dragon, mm2Complete = mm2) { r % it } }
                .map { it.obj }
                .toSet()
        assertTrue(19582 in objs(mm2 = true))
        val without = objs(mm2 = false)
        assertFalse(19582 in without)
        // The roll goes to the rest of the table instead.
        assertTrue(without.size > 5, without.toString())
    }

    @Test
    fun `a held tier's clue scroll is skipped`() {
        val baby = checkNotNull(ImplingLootTables.tables[11238])
        val drops = ImplingLootScript.roll(baby, skipTertiary = { it == 2677 }) { 0 }
        assertEquals(listOf(1755, 23182), drops.map { it.obj })
    }

    @Test
    fun GameTestState.`a clue scroll in the bank counts as held`() =
        runInjectedGameTest(ImplingLootScript::class, null, ImplingLootScript::class) { script ->
            player.clearInv()
            var easy = true
            var medium = true
            with(script) {
                player.withProtectedAccess {
                    inv(ImplingLootScript.BANK)[0] = InvObj(2677, 1)
                    easy = holdsClue("easy")
                    medium = holdsClue("medium")
                }
            }
            assertTrue(easy)
            assertFalse(medium)
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
