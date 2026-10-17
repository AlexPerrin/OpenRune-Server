package org.rsmod.content.skills.hunter

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
import org.rsmod.content.skills.hunter.HunterRumours.Companion.HUNTER
import org.rsmod.content.skills.hunter.HunterRumours.Companion.PITY
import org.rsmod.content.skills.hunter.HunterRumours.Companion.turnInXp
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class HunterRumoursTest {
    @Test
    fun `turn-in XP matches the wiki ranges`() {
        assertEquals(2550, turnInXp(46, 50))
        assertEquals(5200, turnInXp(99, 50))
        assertEquals(4235, turnInXp(72, 55))
        assertEquals(6240, turnInXp(99, 60))
    }

    @Test
    fun GameTestState.`assignments follow each hunter's list, level and other hunters`() =
        runInjectedGameTest(HunterRumours::class) { rumours ->
            prepare(level = 53)
            val gilman = rumours.hunters.first { it.name == "Gilman" }
            val ornus = rumours.hunters.first { it.name == "Ornus" }
            val cervus = rumours.hunters.first { it.name == "Cervus" }
            assertNull(rumours.assign(player, ornus))
            val first = checkNotNull(rumours.assign(player, gilman))
            val novice53 = setOf("tropical wagtail", "sapphire glacialis", "snowy knight", "black warlock", "grey chinchompa")
            assertTrue(first.name in novice53, first.name)
            val grey = rumours.rumours.first { it.name == "grey chinchompa" }
            rumours.assign(player, gilman, grey)
            assertNull(rumours.assign(player, cervus), "grey chinchompa is Gilman's already")
        }

    @Test
    fun GameTestState.`catches reach the rare part by the pity threshold and the whistle names it`() =
        runInjectedGameTest(HunterRumours::class) { rumours ->
            prepare(level = 99)
            val cervus = rumours.hunters.first { it.name == "Cervus" }
            val grey = rumours.rumours.first { it.name == "grey chinchompa" }
            assertEquals(grey, rumours.assign(player, cervus, grey))
            val whistle = rumours.whistleMessage(player)
            assertTrue("grey chinchompa" in whistle && "cervus" in whistle.lowercase(), whistle)

            rumours.onCatch(player, "npc.hunting_chinchompa_big")
            assertEquals(0, player.vars[PITY])
            var catches = 0
            while (player.inv.count(PART) == 0) {
                rumours.onCatch(player, "npc.hunting_chinchompa")
                assertTrue(++catches <= grey.pity)
            }
            assertEquals(0, player.vars[PITY])
            val sent = client.mapOf(MessageGame::message).toMutableList()
            advance(ticks = 1)
            sent += client.mapOf(MessageGame::message)
            assertTrue(sent.any { "You find a rare piece of the creature!" in it }, sent.toString())

            repeat(5) { rumours.onCatch(player, "npc.hunting_chinchompa") }
            assertEquals(1, player.inv.count(PART), "a held rare part blocks another")
        }

    @Test
    fun GameTestState.`a pity part is destroyed when the inventory is full and comes on the next catch`() =
        runInjectedGameTest(HunterRumours::class) { rumours ->
            prepare(level = 99)
            val gilman = rumours.hunters.first { it.name == "Gilman" }
            val grey = rumours.rumours.first { it.name == "grey chinchompa" }
            rumours.assign(player, gilman, grey)
            fillInventory()
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, PITY, grey.pity - 1)
            rumours.onCatch(player, "npc.hunting_chinchompa")
            assertEquals(0, player.inv.count(PART))
            player.inv[0] = null
            rumours.onCatch(player, "npc.hunting_chinchompa")
            assertEquals(1, player.inv.count(PART))
        }

    @Test
    fun GameTestState.`turn-in swaps the part for the loot sack once`() =
        runInjectedGameTest(HunterRumours::class) { rumours ->
            prepare(level = 99)
            val wolf = rumours.hunters.first { it.name == "Wolf" }
            val red = rumours.rumours.first { it.name == "carnivorous chinchompa" }
            rumours.assign(player, wolf, red)
            assertFalse(rumours.turnIn(player, wolf), "no part yet")
            fillInventory()
            player.inv[0] = InvObj(RED_PART.asRSCM(RSCMType.OBJ), 1)
            val xp = player.statMap.getXP(HUNTER)
            assertTrue(rumours.turnIn(player, wolf))
            assertEquals(0, player.inv.count(RED_PART))
            assertEquals(1, player.inv.count("obj.hg_lootsack_t3"))
            assertEquals((xp + turnInXp(99, 60)).toDouble(), player.statMap.getXP(HUNTER).toDouble(), 1.0)
            assertFalse(rumours.turnIn(player, wolf))
            assertEquals(1, player.inv.count("obj.hg_lootsack_t3"))
            assertNull(rumours.rumourOf(player, wolf))
        }

    private fun GameTestScope.fillInventory() {
        for (slot in 0 until player.inv.size) {
            player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
        }
    }

    private fun GameTestScope.prepare(level: Int) {
        val center = CoordGrid(0, 24, 147, 20, 50)
        allocZoneCollision(center)
        player.teleport(center)
        player.clearInv()
        player.setBaseLevel(HUNTER, level)
        player.setCurrentLevel(HUNTER, level)
    }

    private companion object {
        const val PART = "obj.hg_chinchompatuft"
        const val RED_PART = "obj.hg_chinchompatuft_red"
    }
}
