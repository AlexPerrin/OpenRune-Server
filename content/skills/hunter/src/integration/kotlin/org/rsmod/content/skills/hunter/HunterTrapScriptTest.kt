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
import org.rsmod.api.table.hunter.HunterTrapCreaturesRow
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
    fun `each creature's catch curve matches its wiki chart`() {
        fun at(key: String, level: Int): Int {
            val row = HunterTrapCreaturesRow.all().single { it.name == key }
            return catchChance256(row.low, row.high, level)
        }
        // Mod Ash: crimson swift 39% at 1, 164% at 99.
        assertEquals(101, at("crimson swift", 1))
        assertEquals(421, at("crimson swift", 99))
        assertEquals(106, at("golden warbler", 5))
        assertEquals(130, at("tropical wagtail", 19))
        // Chinchompas (the chart's 256 formula; Mod Ash's approximations: grey 145/255 at 53,
        // red 115/255 at 63, black 148/255 at 73, 228/255 at 99).
        assertEquals(146, at("chinchompa", 53))
        assertEquals(117, at("carnivorous chinchompa", 63))
        assertEquals(148, at("black chinchompa", 73))
        assertEquals(229, at("black chinchompa", 99))
    }

    @Test
    fun `chinchompas respawn after 7 ticks`() {
        val rows = HunterTrapCreaturesRow.all().filter { "chinchompa" in it.name }
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.respawnTicks == 7 })
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
    fun GameTestState.`check needs no free slot when the trap and the chinchompa stack`() =
        runInjectedGameTest(HunterTrapScript::class, null, HunterTrapScript::class) { script ->
            val center = prepare(level = 99)
            player.inv[0] = InvObj(BOX_TRAP.asRSCM(RSCMType.OBJ), 2)
            val chin = spawnNpc(center.translate(2, 0), npcType("npc.hunting_chinchompa"))
            with(script) { player.withProtectedAccess { lay(kind("box trap")) } }
            advance(ticks = 5)
            stepOff(center)
            val trap = script.trapAt(center)!!
            advanceUntil({ trap.state == TrapState.Full }, timeoutTicks = 30)
            assertFalse(chin.isSlotAssigned && chin.isVisible)
            player.inv[1] = InvObj(CHINCHOMPA.asRSCM(RSCMType.OBJ), 5)
            for (slot in 2 until player.inv.size) {
                player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
            }
            var checked = false
            with(script) { player.withProtectedAccess { checked = check(center) } }
            advance(ticks = 2)
            assertTrue(checked)
            assertNull(script.trapAt(center))
            assertEquals(2, player.inv.count(BOX_TRAP))
            assertEquals(6, player.inv.count(CHINCHOMPA))
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
        const val BOX_TRAP = "obj.hunting_box_trap"
        const val CHINCHOMPA = "obj.chinchompa_captured"
    }
}
