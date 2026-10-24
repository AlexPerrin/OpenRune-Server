package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.righthand
import org.rsmod.api.table.ImplingTypesRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.ImplingScript.Companion.HUNTER
import org.rsmod.content.skills.hunter.ImplingScript.Companion.IMPLING_JAR
import org.rsmod.content.skills.hunter.ImplingScript.Companion.MAGIC_NET
import org.rsmod.content.skills.hunter.ImplingScript.Companion.NET
import org.rsmod.content.skills.hunter.ImplingScript.Companion.catchChance
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class ImplingScriptTest {
    @Test
    fun `catch chance runs from 12 to 98 percent, plus 8 with a magic net`() {
        assertEquals(12, catchChance(1, bonus = false))
        assertEquals(98, catchChance(99, bonus = false))
        assertEquals(55, catchChance(50, bonus = false))
        assertEquals(106, catchChance(99, bonus = true))
    }

    @Test
    fun GameTestState.`a catch swaps one jar for the impling jar and gives hunter xp`() =
        runInjectedGameTest(ImplingScript::class, null, ImplingScript::class) { script ->
            val baby = row("dbrow.impling_type_baby")
            val impling = prepare(baby, net = MAGIC_NET, jars = 2, level = 99)
            val xpBefore = player.statMap.getXP(HUNTER)
            with(script) { player.withProtectedAccess { catch(impling, baby, puroPuro = true) } }
            val sent = client.mapOf(MessageGame::message).toMutableList()
            repeat(4) {
                advance(ticks = 1)
                sent += client.mapOf(MessageGame::message)
            }
            assertEquals(1, player.inv.count(IMPLING_JAR))
            assertEquals(1, player.inv.count(baby.jar.internalName))
            assertTrue(player.statMap.getXP(HUNTER) > xpBefore)
            assertFalse(impling.isSlotAssigned && impling.isVisible)
            val expected = "You manage to catch the impling and squeeze it into a jar."
            assertTrue(expected in sent, sent.toString())
        }

    @Test
    fun GameTestState.`no jar, nothing moves`() =
        runInjectedGameTest(ImplingScript::class, null, ImplingScript::class) { script ->
            val baby = row("dbrow.impling_type_baby")
            val impling = prepare(baby, net = NET, jars = 0, level = 99)
            with(script) { player.withProtectedAccess { catch(impling, baby, puroPuro = false) } }
            val sent = client.mapOf(MessageGame::message).toMutableList()
            repeat(4) {
                advance(ticks = 1)
                sent += client.mapOf(MessageGame::message)
            }
            val expected = "You need an empty impling jar to keep the impling in."
            assertTrue(expected in sent, sent.toString())
            assertEquals(0, player.inv.count(baby.jar.internalName))
            assertTrue(impling.isSlotAssigned && impling.isVisible)
        }

    @Test
    fun GameTestState.`barehanded needs ten more levels`() =
        runInjectedGameTest(ImplingScript::class, null, ImplingScript::class) { script ->
            val dragon = row("dbrow.impling_type_dragon")
            val impling = prepare(dragon, net = null, jars = 1, level = dragon.level + 9)
            with(script) { player.withProtectedAccess { catch(impling, dragon, puroPuro = false) } }
            val sent = client.mapOf(MessageGame::message).toMutableList()
            repeat(4) {
                advance(ticks = 1)
                sent += client.mapOf(MessageGame::message)
            }
            val expected =
                "You need a Hunter level of ${dragon.level + 10} to catch this impling barehanded."
            assertTrue(expected in sent, sent.toString())
            assertEquals(1, player.inv.count(IMPLING_JAR))
        }

    private fun row(name: String) = ImplingTypesRow.getRow(name)

    private fun GameTestScope.prepare(
        impling: ImplingTypesRow,
        net: String?,
        jars: Int,
        level: Int,
    ): Npc {
        val center = CoordGrid(0, 50, 50, 20, 20)
        allocZoneCollision(center)
        player.teleport(center)
        player.clearInv()
        player.righthand = net?.let { InvObj(it.asRSCM(RSCMType.OBJ), 1) }
        repeat(jars) { player.inv[it] = InvObj(IMPLING_JAR.asRSCM(RSCMType.OBJ), 1) }
        player.setBaseLevel(HUNTER, level)
        player.setCurrentLevel(HUNTER, level)
        return spawnNpc(center.translate(1, 0), impling.npc)
    }
}
