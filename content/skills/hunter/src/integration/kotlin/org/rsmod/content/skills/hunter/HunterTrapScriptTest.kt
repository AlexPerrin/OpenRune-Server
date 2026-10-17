package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.HunterTrapScript.Companion.HUNTER
import org.rsmod.content.skills.hunter.HunterTrapScript.Companion.catchChance256
import org.rsmod.content.skills.hunter.HunterTrapScript.Companion.maxTraps
import org.rsmod.content.skills.hunter.HunterTrapScript.TrapState
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class HunterTrapScriptTest {
    @Test
    fun `catch curve matches the crimson swift on the wiki`() {
        assertEquals(101, catchChance256(1))
        assertEquals(420, catchChance256(99))
    }

    @Test
    fun `trap limit grows at 20, 40, 60 and 80`() {
        assertEquals(1, maxTraps(19))
        assertEquals(2, maxTraps(20))
        assertEquals(5, maxTraps(99))
    }

    @Test
    fun GameTestState.`lay a snare, catch a crimson swift, check it`() =
        runInjectedGameTest(HunterTrapScript::class, null, HunterTrapScript::class) { script ->
            val center = prepare(level = 99)
            player.inv[0] = InvObj(SNARE.asRSCM(RSCMType.OBJ), 1)
            val bird = spawnNpc(center.translate(2, 0), npcType("npc.hunting_bird_jungle"))
            with(script) { player.withProtectedAccess { lay(kind("bird snare")) } }
            advance(ticks = 5)
            val trap = checkNotNull(script.trapAt(center))
            assertEquals(0, player.inv.count(SNARE))
            assertEquals(center.translate(-1, 0), player.routeDestination.peekFirst())
            stepOff(center)
            advanceUntil({ trap.state == TrapState.Full }, timeoutTicks = 30)
            assertFalse(bird.isSlotAssigned && bird.isVisible)

            val xpBefore = player.statMap.getXP(HUNTER)
            var checked = false
            with(script) { player.withProtectedAccess { checked = check(center) } }
            val sent = client.mapOf(MessageGame::message).toMutableList()
            repeat(2) {
                advance(ticks = 1)
                sent += client.mapOf(MessageGame::message)
            }
            assertTrue(checked)
            assertNull(script.trapAt(center))
            assertEquals(1, player.inv.count(SNARE))
            assertEquals(1, player.inv.count("obj.bones"))
            assertEquals(1, player.inv.count("obj.spit_raw_bird_meat"))
            assertTrue(player.inv.count("obj.hunting_jungle_feather") in 5..10)
            assertEquals(xpBefore + 34.0, player.statMap.getXP(HUNTER).toDouble(), 1.0)
            assertTrue("You've caught a crimson swift." in sent, sent.toString())
        }

    @Test
    fun GameTestState.`a full inventory leaves the full trap and moves nothing`() =
        runInjectedGameTest(HunterTrapScript::class, null, HunterTrapScript::class) { script ->
            val center = prepare(level = 99)
            player.inv[0] = InvObj(SNARE.asRSCM(RSCMType.OBJ), 1)
            spawnNpc(center.translate(2, 0), npcType("npc.hunting_bird_jungle"))
            with(script) { player.withProtectedAccess { lay(kind("bird snare")) } }
            advance(ticks = 5)
            stepOff(center)
            val trap = script.trapAt(center)!!
            advanceUntil({ trap.state == TrapState.Full }, timeoutTicks = 30)
            for (slot in 0 until player.inv.size) {
                player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
            }
            var checked = true
            with(script) { player.withProtectedAccess { checked = check(center) } }
            advance(ticks = 2)
            assertFalse(checked)
            assertEquals(TrapState.Full, script.trapAt(center)?.state)
            assertEquals(player.inv.size, player.inv.count("obj.logs"))
        }

    @Test
    fun GameTestState.`level 1 lays one trap only`() =
        runInjectedGameTest(HunterTrapScript::class, null, HunterTrapScript::class) { script ->
            val center = prepare(level = 1)
            player.inv[0] = InvObj(SNARE.asRSCM(RSCMType.OBJ), 2)
            with(script) { player.withProtectedAccess { lay(kind("bird snare")) } }
            advance(ticks = 5)
            assertEquals(1, script.trapsOf(player).size)
            with(script) { player.withProtectedAccess { lay(kind("bird snare")) } }
            val sent = client.mapOf(MessageGame::message).toMutableList()
            repeat(5) {
                advance(ticks = 1)
                sent += client.mapOf(MessageGame::message)
            }
            assertEquals(1, script.trapsOf(player).size)
            assertEquals(1, player.inv.count(SNARE))
            val expected = "You don't have a high enough Hunter level to set up more than 1 trap."
            assertTrue(expected in sent, sent.toString())
        }

    /** The test world does not process walking; the lay queued the step west. */
    private fun GameTestScope.stepOff(center: CoordGrid) {
        player.teleport(center.translate(-1, 0))
        advance(ticks = 1)
    }

    private fun HunterTrapScript.kind(name: String) = kinds().first { it.name == name }

    private fun npcType(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))

    private fun GameTestScope.prepare(level: Int): CoordGrid {
        val center = CoordGrid(0, 50, 50, 20, 20)
        allocZoneCollision(center)
        player.teleport(center)
        player.clearInv()
        player.setBaseLevel(HUNTER, level)
        player.setCurrentLevel(HUNTER, level)
        return center
    }

    private companion object {
        const val SNARE = "obj.hunting_ojibway_bird_snare"
    }
}
