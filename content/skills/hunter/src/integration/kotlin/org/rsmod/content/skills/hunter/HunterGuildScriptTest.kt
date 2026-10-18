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
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.HunterGuildScript.Companion.COINS
import org.rsmod.content.skills.hunter.HunterGuildScript.Companion.STEW
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class HunterGuildScriptTest {
    @Test
    fun `assignment and remember lines say one of, some of or its as the transcripts do`() {
        val lines = HunterGuildScript.ASSIGN_LINES.map { it.format("grey chinchompa", "fur") }
        assertEquals(listOf(true, true, true, false, true), lines.map { "bring back one of its fur." in it })
        assertTrue("bring back some of its fur." in lines[3])
        val remember = HunterGuildScript.LINES.mapValues { it.value.remember.format("a grey chinchompa", "fur") }
        assertTrue("bring back one of its fur." in remember.getValue("Gilman"))
        assertTrue("bring back one of its fur." in remember.getValue("Aco"))
        assertTrue("bring back its fur." in remember.getValue("Ornus"))
        assertTrue("bring back some of its fur." in remember.getValue("Wolf"))
    }

    @Test
    fun `Wolf's leave option is Never mind and the player says See you later`() {
        val wolf = HunterGuildScript.LINES.getValue("Wolf")
        assertEquals("Never mind.", wolf.leaveOption)
        assertEquals("See you later.", wolf.leaveLine)
    }

    @Test
    fun GameTestState.`buying a stew swaps exactly 20 coins for one stew`() =
        runInjectedGameTest(HunterGuildScript::class, null, HunterGuildScript::class) { script ->
            prepare(coins = 25)
            assertTrue(buy(script))
            assertEquals(5, player.inv.count(COINS))
            assertEquals(1, player.inv.count(STEW))
        }

    @Test
    fun GameTestState.`too few coins moves nothing`() =
        runInjectedGameTest(HunterGuildScript::class, null, HunterGuildScript::class) { script ->
            prepare(coins = 19)
            assertFalse(buy(script))
            assertEquals(19, player.inv.count(COINS))
            assertEquals(0, player.inv.count(STEW))
        }

    @Test
    fun GameTestState.`a full inventory with coins left over moves nothing, exact coins free their slot`() =
        runInjectedGameTest(HunterGuildScript::class, null, HunterGuildScript::class) { script ->
            prepare(coins = 30)
            for (slot in 1 until player.inv.size) player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
            assertFalse(buy(script))
            assertEquals(30, player.inv.count(COINS))
            player.inv[0] = InvObj(COINS.asRSCM(RSCMType.OBJ), 20)
            assertTrue(buy(script))
            assertEquals(0, player.inv.count(COINS))
            assertEquals(1, player.inv.count(STEW))
        }

    private fun GameTestScope.buy(script: HunterGuildScript): Boolean {
        var bought = false
        with(script) { player.withProtectedAccess { bought = buy(STEW, 20) } }
        return bought
    }

    private fun GameTestScope.prepare(coins: Int) {
        val center = CoordGrid(0, 24, 147, 20, 50)
        allocZoneCollision(center)
        player.teleport(center)
        player.clearInv()
        player.inv[0] = InvObj(COINS.asRSCM(RSCMType.OBJ), coins)
    }
}
