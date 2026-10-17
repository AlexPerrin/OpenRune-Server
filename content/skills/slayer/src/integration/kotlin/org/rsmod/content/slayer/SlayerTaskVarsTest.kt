package org.rsmod.content.slayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.dialogue.SlayerContact.checkSlayerTask
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * The task state RuneLite's Slayer plugin reads lives in the OSRS vars (varp
 * slayer_count 394, slayer_target 395, varbit slayer_tasks_completed 4069 for the streak), and the
 * gem check and the completion message use the OSRS wording.
 */
@Execution(ExecutionMode.SAME_THREAD)
class SlayerTaskVarsTest {
    @Test
    fun GameTestState.`the gem check uses the OSRS wording`() = runGameTest {
        val cow = setUp(count = 5)
        player.withProtectedAccess { checkSlayerTask() }
        val sent = messages()
        assertTrue("You're assigned to kill Cows; only 5 more to go." in sent, sent.toString())
        assertTrue(cow.isSlotAssigned)
    }

    @Test
    fun GameTestState.`the last kill completes the task and raises the client-visible streak`() =
        runGameTest {
            val cow = setUp(count = 1)
            assertTrue(SlayerTaskManager.decreaseTask(player, cow))
            val sent = messages()
            assertEquals(0, player.vars["varp.slayer_count"])
            assertEquals(0, player.vars["varp.slayer_target"])
            assertEquals(1, player.vars["varbit.slayer_tasks_completed"])
            assertEquals(4069, "varbit.slayer_tasks_completed".asRSCM(RSCMType.VARBIT))
            val expected =
                "You've completed 1 task and received 0 points, giving you a total of 0; " +
                    "return to a Slayer master."
            assertTrue(expected in sent, sent.toString())
        }

    private fun GameTestScope.setUp(count: Int): Npc {
        val task = SlayerTaskManager.slayerTargets.first { it.nameUppercase == "Cows" }
        val cowType = checkNotNull(ServerCacheManager.getNpc("npc.cow".asRSCM(RSCMType.NPC)))
        assertTrue(SlayerTaskManager.isTaskNpcType(cowType, task.id))
        val center = CoordGrid(0, 50, 50, 20, 20)
        allocZoneCollision(center)
        player.teleport(center)
        VarPlayerIntMapSetter.set(player, "varbit.slayer_master", 1)
        VarPlayerIntMapSetter.set(player, "varp.slayer_target", task.id)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count", count)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count_original", count)
        return spawnNpc(center.translate(1, 0), cowType)
    }

    private fun GameTestScope.messages(): List<String> {
        val sent = client.mapOf(MessageGame::message).toMutableList()
        repeat(2) {
            advance(ticks = 1)
            sent += client.mapOf(MessageGame::message)
        }
        return sent
    }
}
