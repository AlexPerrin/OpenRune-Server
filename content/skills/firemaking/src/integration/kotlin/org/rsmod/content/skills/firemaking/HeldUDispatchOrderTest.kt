package org.rsmod.content.skills.firemaking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/**
 * Logs carry a "used on anything" script (lighting them with a bow), and the tinderbox has a
 * script for the pair (tinderbox, logs). Using the logs on the tinderbox must reach the pair's
 * script, the same as using the tinderbox on the logs.
 */
@Execution(ExecutionMode.SAME_THREAD)
class HeldUDispatchOrderTest {
    @Test
    fun GameTestState.`logs used on a tinderbox light a fire`() =
        runInjectedGameTest(HeldUInteractions::class, null, BurnLogEvents::class) { interactions ->
            val logs = ServerCacheManager.getItem("obj.logs".asRSCM())!!
            val tinderbox = ServerCacheManager.getItem("obj.tinderbox".asRSCM())!!
            allocZoneCollision(FIRE_TILE)
            player.teleport(FIRE_TILE)
            player.inv[0] = InvObj(tinderbox)
            player.inv[1] = InvObj(logs)
            player.withProtectedAccess { interactions.interact(this, player.inv, logs, 1, tinderbox, 0) }
            assertEquals(0, player.inv.count("obj.logs"))
            assertEquals(1, player.inv.count("obj.tinderbox"))
        }

    @Test
    fun GameTestState.`a tinderbox used on logs lights a fire`() =
        runInjectedGameTest(HeldUInteractions::class, null, BurnLogEvents::class) { interactions ->
            val logs = ServerCacheManager.getItem("obj.logs".asRSCM())!!
            val tinderbox = ServerCacheManager.getItem("obj.tinderbox".asRSCM())!!
            allocZoneCollision(FIRE_TILE)
            player.teleport(FIRE_TILE)
            player.inv[0] = InvObj(tinderbox)
            player.inv[1] = InvObj(logs)
            player.withProtectedAccess { interactions.interact(this, player.inv, tinderbox, 0, logs, 1) }
            assertEquals(0, player.inv.count("obj.logs"))
        }

    private companion object {
        val FIRE_TILE = CoordGrid(3222, 3218)
    }
}
