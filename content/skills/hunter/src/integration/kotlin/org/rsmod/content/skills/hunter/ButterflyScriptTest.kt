package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.table.hunter.HunterButterfliesRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.ButterflyScript.Companion.BUTTERFLY_JAR
import org.rsmod.content.skills.hunter.ButterflyScript.Companion.HUNTER
import org.rsmod.content.skills.hunter.ButterflyScript.Companion.MAGIC_NET
import org.rsmod.content.skills.hunter.ButterflyScript.Companion.NET
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class ButterflyScriptTest {
    @Test
    fun GameTestState.`a catch with a jar swaps exactly one jar for the butterfly`() =
        runInjectedGameTest(ButterflyScript::class, null, ButterflyScript::class) { script ->
            val warlock = row("dbrow.hunter_butterfly_black_warlock")
            val npc = prepare(warlock, net = NET, jars = 3, level = 99)
            val xp = player.statMap.getXP(HUNTER)
            catch(script, npc, warlock)
            assertEquals(2, player.inv.count(BUTTERFLY_JAR))
            assertEquals(1, player.inv.count(warlock.jar.internalName))
            assertEquals(xp.toDouble() + 54, player.statMap.getXP(HUNTER).toDouble(), 0.5)
            assertFalse(npc.isSlotAssigned && npc.isVisible)
        }

    @Test
    fun GameTestState.`without a jar the butterfly is released and its boost applies`() =
        runInjectedGameTest(ButterflyScript::class, null, ButterflyScript::class) { script ->
            val warlock = row("dbrow.hunter_butterfly_black_warlock")
            val npc = prepare(warlock, net = NET, jars = 0, level = 99)
            player.setBaseLevel(STRENGTH, 60)
            player.setCurrentLevel(STRENGTH, 60)
            catch(script, npc, warlock)
            assertEquals(0, player.inv.count(warlock.jar.internalName))
            assertEquals(60 + 13, player.stat(STRENGTH))
        }

    @Test
    fun GameTestState.`a sunlight moth caught without a jar restores 5% run energy`() =
        runInjectedGameTest(ButterflyScript::class, null, ButterflyScript::class) { script ->
            val sunlight = row("dbrow.hunter_butterfly_sunlight_moth")
            val npc = prepare(sunlight, net = NET, jars = 0, level = 99)
            player.runEnergy = 1000
            catch(script, npc, sunlight)
            // +500, plus a few ticks of natural recovery.
            assertTrue(player.runEnergy in 1500 until 1600, "${player.runEnergy}")
        }

    @Test
    fun GameTestState.`a jarred sunlight moth restores drained stats but not Sailing or run energy`() =
        runInjectedGameTest(ButterflyScript::class, null, ButterflyScript::class) { script ->
            val sunlight = row("dbrow.hunter_butterfly_sunlight_moth")
            prepare(sunlight, net = null, jars = 0, level = 99)
            player.inv[0] = InvObj(sunlight.jar.id, 1)
            player.runEnergy = 1000
            for (stat in listOf(STRENGTH, SAILING)) {
                player.setBaseLevel(stat, 50)
                player.setCurrentLevel(stat, 10)
            }
            with(script) { player.withProtectedAccess { release(sunlight) } }
            advance(ticks = 2)
            // 6 + 20% of 50.
            assertEquals(26, player.stat(STRENGTH))
            assertEquals(10, player.stat(SAILING))
            assertTrue(player.runEnergy < 1100, "${player.runEnergy}")
        }

    @Test
    fun GameTestState.`releasing a jarred moth gives the empty jar back once`() =
        runInjectedGameTest(ButterflyScript::class, null, ButterflyScript::class) { script ->
            val moon = row("dbrow.hunter_butterfly_moonlight_moth")
            prepare(moon, net = null, jars = 0, level = 99)
            player.inv[0] = InvObj(moon.jar.id, 1)
            player.setBaseLevel(PRAYER, 70)
            player.setCurrentLevel(PRAYER, 10)
            with(script) { player.withProtectedAccess { release(moon) } }
            with(script) { player.withProtectedAccess { release(moon) } }
            advance(ticks = 2)
            assertEquals(1, player.inv.count(BUTTERFLY_JAR))
            assertEquals(0, player.inv.count(moon.jar.internalName))
            assertEquals(32, player.stat(PRAYER))
            assertEquals(70, player.statBase(PRAYER))
        }

    @Test
    fun GameTestState.`barehanded below ten extra levels always fails`() =
        runInjectedGameTest(ButterflyScript::class, null, ButterflyScript::class) { script ->
            val sunlight = row("dbrow.hunter_butterfly_sunlight_moth")
            val npc = prepare(sunlight, net = null, jars = 1, level = sunlight.level + 9)
            val xp = player.statMap.getXP(HUNTER)
            repeat(10) { catch(script, npc, sunlight) }
            assertEquals(1, player.inv.count(BUTTERFLY_JAR))
            assertEquals(xp.toDouble(), player.statMap.getXP(HUNTER).toDouble(), 0.01)
            assertTrue(npc.isSlotAssigned && npc.isVisible)
        }

    @Test
    fun GameTestState.`a magic net catch counts for the rumour`() =
        runInjectedGameTest(ButterflyScript::class, null, ButterflyScript::class) { script ->
            val snowy = row("dbrow.hunter_butterfly_snowy_knight")
            val npc = prepare(snowy, net = MAGIC_NET, jars = 0, level = 99)
            val rumours = script.rumours
            val ornus = rumours.hunters.first { it.name == "Ornus" }
            val rumour = rumours.rumours.first { it.name == "snowy knight" }
            rumours.assign(player, ornus, rumour)
            catch(script, npc, snowy)
            assertEquals(1, player.vars[HunterRumours.PITY])
        }

    private fun GameTestScope.catch(script: ButterflyScript, npc: Npc, row: HunterButterfliesRow) {
        with(script) { player.withProtectedAccess { catch(npc, row) } }
        advance(ticks = 3)
    }

    private fun row(name: String) = HunterButterfliesRow.getRow(name)

    private fun GameTestScope.prepare(row: HunterButterfliesRow, net: String?, jars: Int, level: Int): Npc {
        val center = CoordGrid(0, 50, 50, 20, 20)
        allocZoneCollision(center)
        player.teleport(center)
        player.clearInv()
        player.righthand = net?.let { InvObj(it.asRSCM(RSCMType.OBJ), 1) }
        repeat(jars) { player.inv[it] = InvObj(BUTTERFLY_JAR.asRSCM(RSCMType.OBJ), 1) }
        player.setBaseLevel(HUNTER, level)
        player.setCurrentLevel(HUNTER, level)
        return spawnNpc(center.translate(1, 0), row.npc)
    }

    private companion object {
        const val STRENGTH = "stat.strength"
        const val PRAYER = "stat.prayer"
        const val SAILING = "stat.sailing"
    }
}
