package org.rsmod.content.events.randomevents

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.player.SessionStateEvent
import jakarta.inject.Inject
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.CALL_INTERVAL
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.LAMP
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.LIFETIME_TICKS
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GenieRandomEventTest {
    @Test
    fun GameTestState.`the genie spawns beside its owner and faces them`() =
        runInjectedGameTest(GenieRandomEvent::class, null, GenieRandomEvent::class) { script ->
            val center = CoordGrid(0, 50, 50, 20, 20)
            allocZoneCollision(center)
            player.teleport(center)
            val genie = script.spawn(player)
            assertEquals(1, genie.coords.chebyshevDistance(player.coords))
            assertTrue(genie.faceEntity.isPlayer)
            assertEquals(player.slotId, genie.faceEntity.playerSlot)
            assertEquals(genie, script.genieOf(player))
        }

    @Test
    fun GameTestState.`an ignored genie leaves after a minute`() =
        runInjectedGameTest(GenieRandomEvent::class, null, GenieRandomEvent::class) { script ->
            val center = CoordGrid(0, 50, 50, 20, 20)
            allocZoneCollision(center)
            player.teleport(center)
            val genie = script.spawn(player)
            advance(ticks = LIFETIME_TICKS + CALL_INTERVAL)
            assertEquals(null, script.genieOf(player))
            assertFalse(genie.isSlotAssigned)
        }

    class Deps @Inject constructor(val script: GenieRandomEvent, val objRepo: ObjRepository)

    @Test
    fun GameTestState.`cancelling the open dialogue keeps the lamp and he still leaves`() =
        runInjectedGameTest(Deps::class, null, GenieRandomEvent::class) { deps ->
            val center = CoordGrid(0, 50, 50, 20, 20)
            allocZoneCollision(center)
            player.teleport(center)
            val genie = deps.script.spawn(player)
            player.opNpc1(genie)
            advance(ticks = 3)
            assertEquals(1, player.inv.count(LAMP))
            player.ifClose(eventBus)
            advance(ticks = CALL_INTERVAL)
            assertEquals(1, player.inv.count(LAMP))
            assertEquals(null, deps.script.genieOf(player))
            assertFalse(genie.isSlotAssigned)
        }

    @Test
    fun GameTestState.`logging out at the open dialogue leaves no genie and keeps the lamp`() =
        runInjectedGameTest(Deps::class, null, GenieRandomEvent::class) { deps ->
            val center = CoordGrid(0, 50, 50, 20, 20)
            allocZoneCollision(center)
            player.teleport(center)
            val genie = deps.script.spawn(player)
            player.opNpc1(genie)
            advance(ticks = 3)
            eventBus.publish(SessionStateEvent.Logout(player))
            assertEquals(null, deps.script.genieOf(player))
            assertFalse(genie.isSlotAssigned)
            assertEquals(1, player.inv.count(LAMP))
        }

    @Test
    fun GameTestState.`a full inventory drops the lamp instead of discarding it`() =
        runInjectedGameTest(Deps::class, null, GenieRandomEvent::class) { deps ->
            val center = CoordGrid(0, 50, 50, 20, 20)
            allocZoneCollision(center)
            player.teleport(center)
            player.invAdd(player.inv, "obj.logs", 28)
            val genie = deps.script.spawn(player)
            player.opNpc1(genie)
            advance(ticks = 3)
            assertEquals(0, player.inv.count(LAMP))
            assertEquals(1, deps.objRepo.findAll(player.coords).count())
        }

    @Test
    fun GameTestState.`he leaves on the next call tick after Talk-to without calling`() =
        runInjectedGameTest(Deps::class, null, GenieRandomEvent::class) { deps ->
            val center = CoordGrid(0, 50, 50, 20, 20)
            allocZoneCollision(center)
            player.teleport(center)
            val genie = deps.script.spawn(player)
            advance(ticks = 5)
            player.opNpc1(genie)
            advance(ticks = 3)
            assertTrue(genie.isSlotAssigned)
            advance(ticks = CALL_INTERVAL - 8)
            assertFalse(genie.isSlotAssigned)
            assertEquals(null, deps.script.genieOf(player))
            assertEquals(1, player.inv.count(LAMP))
        }
}
