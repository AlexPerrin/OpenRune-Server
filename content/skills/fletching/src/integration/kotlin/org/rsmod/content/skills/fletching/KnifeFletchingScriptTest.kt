package org.rsmod.content.skills.fletching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.table.fletching.FletchingKnifeRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.skills.fletching.KnifeFletchingScript.Companion.FLETCHING
import org.rsmod.content.skills.fletching.KnifeFletchingScript.Companion.KNIFE
import org.rsmod.game.inv.InvObj

@Execution(ExecutionMode.SAME_THREAD)
class KnifeFletchingScriptTest {
    private fun row(key: String) = FletchingKnifeRow.getRow("dbrow.fletching_knife_$key")

    @Test
    fun GameTestState.`one log becomes fifteen arrow shafts and the knife stays`() =
        runInjectedGameTest(KnifeFletchingScript::class, null, KnifeFletchingScript::class) { script ->
            player.inv[0] = InvObj(KNIFE, 1)
            player.inv[1] = InvObj("obj.logs", 1)
            player.inv[2] = InvObj("obj.logs", 1)
            player.setBaseLevel(FLETCHING, 1)
            val xp = player.statMap.getXP(FLETCHING)
            var cut = false
            with(script) { player.withProtectedAccess { cut = cutOnce(row("shafts_logs")) } }
            assertTrue(cut)
            assertEquals(1, player.inv.count("obj.logs"))
            assertEquals(15, player.inv.count("obj.arrow_shaft"))
            assertEquals(1, player.inv.count(KNIFE))
            assertEquals(xp.toDouble() + 5, player.statMap.getXP(FLETCHING).toDouble(), 0.5)
        }

    @Test
    fun GameTestState.`a bow below the level takes nothing`() =
        runInjectedGameTest(KnifeFletchingScript::class, null, KnifeFletchingScript::class) { script ->
            player.inv[0] = InvObj(KNIFE, 1)
            player.inv[1] = InvObj("obj.yew_logs", 1)
            player.setBaseLevel(FLETCHING, 64)
            var cut = false
            with(script) { player.withProtectedAccess { cut = cutOnce(row("yew_shortbow")) } }
            assertFalse(cut)
            assertEquals(1, player.inv.count("obj.yew_logs"))
            assertEquals(0, player.inv.count("obj.unstrung_yew_shortbow"))
        }

    @Test
    fun GameTestState.`without a knife or with no room nothing is lost`() =
        runInjectedGameTest(KnifeFletchingScript::class, null, KnifeFletchingScript::class) { script ->
            player.inv[0] = InvObj("obj.logs", 1)
            player.setBaseLevel(FLETCHING, 99)
            var noKnife = false
            with(script) { player.withProtectedAccess { noKnife = cutOnce(row("longbow")) } }
            assertFalse(noKnife)
            assertEquals(1, player.inv.count("obj.logs"))

            player.inv[1] = InvObj(KNIFE, 1)
            for (slot in 2 until player.inv.size) player.inv[slot] = InvObj("obj.bronze_dagger", 1)
            var bow = false
            with(script) { player.withProtectedAccess { bow = cutOnce(row("longbow")) } }
            assertTrue(bow)
            assertEquals(0, player.inv.count("obj.logs"))
            assertEquals(1, player.inv.count("obj.unstrung_longbow"))
        }
}
