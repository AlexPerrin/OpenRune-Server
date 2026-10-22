package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.table.HerbiboarHerbsRow
import org.rsmod.api.table.HerbiboarLegsRow
import org.rsmod.api.table.HerbiboarSpotsRow
import org.rsmod.api.table.HerbiboarTunnelsRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.ENDS
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.HARVESTED
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.HERBLORE
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.HUNTER
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.STARTED
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.START_LOCS
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.VISIBLE
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.herbChance
import org.rsmod.content.skills.hunter.HerbiboarScript.Companion.tunnelXp
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class HerbiboarScriptTest {
    @Test
    fun `tunnel XP follows the wiki table`() {
        assertEquals(1950, tunnelXp(80))
        assertEquals(2370, tunnelXp(94))
        assertEquals(2385, tunnelXp(95))
        assertEquals(2461, tunnelXp(99))
    }

    @Test
    fun `herb level ranges match the wiki table within two levels`() {
        val rows = HerbiboarHerbsRow.all().associateBy { it.herb.internalName }
        fun range(herb: String): IntRange {
            val row = rows.getValue(herb)
            val levels = (31..99).filter { herbChance(it, row.low, row.high) > 0 }
            return levels.first()..levels.last()
        }
        fun near(expected: IntRange, herb: String) {
            val actual = range(herb)
            val ok = kotlin.math.abs(actual.first - expected.first) <= 2 && kotlin.math.abs(actual.last - expected.last) <= 2
            assertTrue(ok, "$herb: $actual vs wiki $expected")
        }
        near(31..80, "obj.unidentified_marentill")
        near(31..77, "obj.unidentified_tarromin")
        near(31..76, "obj.unidentified_harralander")
        near(41..99, "obj.unidentified_lantadyme")
        near(62..99, "obj.unidentified_dwarf_weed")
        near(74..99, "obj.unidentified_snapdragon")
        near(77..99, "obj.unidentified_torstol")
    }

    @Test
    fun `every leg's trail varbit is a footprint varbit and every group can be left`() {
        val spots = HerbiboarSpotsRow.all().associate { it.loc.id to it.group }
        val legs = HerbiboarLegsRow.all()
        assertEquals(19, spots.size)
        for (leg in legs) {
            val varbit = checkNotNull(ServerCacheManager.getVarbit(leg.trail))
            assertEquals(2, varbit.endBit - varbit.startBit, "3-bit trail varbit ${leg.trail}")
            assertTrue(leg.dir in 1..2)
            assertTrue(leg.toGroup >= 0 || leg.endTunnel in 1..9)
        }
        for (group in legs.mapNotNull { it.toGroup.takeIf { g -> g >= 0 } }.toSet()) {
            assertTrue(legs.any { spots[it.fromLoc.id] == group }, "group $group has no leg out")
        }
    }

    @Test
    fun GameTestState.`track from every start, stun and harvest`() =
        runInjectedGameTest(HerbiboarScript::class, null, HerbiboarScript::class) { script ->
            prepare()
            val tunnels = HerbiboarTunnelsRow.all().associate { it.index to it.coords }
            val legs = HerbiboarLegsRow.all()
            var harvested = 0
            repeat(4) {
                for (start in START_LOCS) {
                    val sent = mutableListOf<String>()
                    with(script) { player.withProtectedAccess { inspectStart(start.asRSCM(RSCMType.LOC)) } }
                    run(sent)
                    if (player.vars[STARTED] == 0) {
                        assertTrue(sent.any { "confused you" in it }, sent.toString())
                        return@repeat
                    }
                    var steps = 0
                    while (player.vars[ENDS] == 0 && player.vars[STARTED] == 1) {
                        val hidden =
                            legs.singleOrNull { player.vars[checkNotNull(ServerCacheManager.getVarbit(it.trail))] == it.dir }
                        checkNotNull(hidden) { "no hidden leg after $steps steps: $sent" }
                        with(script) { player.withProtectedAccess { inspectSpot(hidden.fromLoc.id) } }
                        run(sent)
                        assertTrue(++steps <= 8, sent.toString())
                    }
                    if (player.vars[ENDS] == 0) continue
                    val tunnel = player.vars[ENDS]
                    with(script) { player.withProtectedAccess { attackTunnel(tunnels.getValue(tunnel)) } }
                    run(sent)
                    assertEquals(tunnel, player.vars[VISIBLE])
                    assertEquals(0, player.vars[ENDS])
                    val before = (player.inv.size - player.inv.freeSpace())
                    with(script) { player.withProtectedAccess { harvest() } }
                    run(sent)
                    harvested++
                    assertEquals(0, player.vars[VISIBLE])
                    assertTrue((player.inv.size - player.inv.freeSpace()) - before in 1..3, sent.toString())
                    assertEquals(harvested, player.vars[HARVESTED])
                    player.clearInv()
                }
            }
            assertTrue(harvested > 0)
        }

    @Test
    fun GameTestState.`the wrong clue loc changes nothing`() =
        runInjectedGameTest(HerbiboarScript::class, null, HerbiboarScript::class) { script ->
            prepare()
            val sent = mutableListOf<String>()
            with(script) { player.withProtectedAccess { inspectSpot("loc.hunting_trail_clue9_4".asRSCM(RSCMType.LOC)) } }
            run(sent)
            assertTrue("Nothing seems to be out of place here." in sent, sent.toString())
            assertEquals(0, player.vars[STARTED])
        }

    private fun GameTestScope.run(sent: MutableList<String>) {
        repeat(4) {
            advance(ticks = 1)
            sent += client.mapOf(MessageGame::message)
        }
    }

    private fun GameTestScope.prepare() {
        val center = CoordGrid(0, 57, 60, 20, 20)
        allocZoneCollision(center)
        player.teleport(center)
        player.clearInv()
        player.setBaseLevel(HUNTER, 99)
        player.setCurrentLevel(HUNTER, 99)
        player.setBaseLevel(HERBLORE, 99)
        player.setCurrentLevel(HERBLORE, 99)
    }
}
