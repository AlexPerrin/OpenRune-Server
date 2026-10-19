package org.rsmod.content.events.randomevents

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.events.randomevents.GenieRandomEvent.CheckResult
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.CALL_INTERVAL
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.HAND_OVER_TICKS
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.LAMP
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.MAX_EVENT_DELAY_MS
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.MAX_OFFLINE_EXTENSION_MS
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.MIN_EVENT_DELAY_MS
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.MIN_OFFLINE_EXTENSION_MS
import org.rsmod.content.events.randomevents.GenieRandomEvent.Companion.NEXT_EVENT_AT
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GenieRandomEventTest {
    @Test
    fun GameTestState.`the next event is due 1-2 hours ahead on a persisted real-time clock`() =
        genieTest { script ->
            var now = START
            script.currentTimeMillis = { now }
            script.login(player)
            val due = checkNotNull(script.nextEventAt(player))
            assertTrue(due in (START + MIN_EVENT_DELAY_MS)..(START + MAX_EVENT_DELAY_MS))
            assertTrue(player.attr.toPersistentMap().containsKey(NEXT_EVENT_AT.persistenceKey))

            now = START + 30 * MINUTE
            script.login(player)
            assertEquals(due, script.nextEventAt(player))
            assertEquals(CheckResult.NotDue, script.check(player))
        }

    @Test
    fun GameTestState.`an event that came due while offline moves forward 10-70 minutes`() =
        genieTest { script ->
            script.currentTimeMillis = { START }
            val missed = START - MINUTE
            player.attr[NEXT_EVENT_AT] = missed
            script.login(player)
            val due = checkNotNull(script.nextEventAt(player))
            assertTrue(due > START)
            val extension = due - missed
            assertTrue(extension in MIN_OFFLINE_EXTENSION_MS..MAX_OFFLINE_EXTENSION_MS)
            assertNull(script.genieOf(player))
        }

    @Test
    fun GameTestState.`a due event spawns the genie and rolls the next one`() =
        genieTest { script ->
            script.currentTimeMillis = { START }
            player.attr[NEXT_EVENT_AT] = START - 1
            assertEquals(CheckResult.Spawned, script.check(player))
            assertNotNull(script.genieOf(player))
            val next = checkNotNull(script.nextEventAt(player))
            assertTrue(next in (START + MIN_EVENT_DELAY_MS)..(START + MAX_EVENT_DELAY_MS))
        }

    @Test
    fun GameTestState.`an event due in a bank is missed and a new one rolled`() =
        genieTest { script ->
            placeMapLoc(player.coords.translate(2, 0), "loc.aide_bankbooth")
            script.currentTimeMillis = { START }
            player.attr[NEXT_EVENT_AT] = START - 1
            assertEquals(CheckResult.MissedLocation, script.check(player))
            assertNull(script.genieOf(player))
            val next = checkNotNull(script.nextEventAt(player))
            assertTrue(next >= START + MIN_EVENT_DELAY_MS)
        }

    @Test
    fun GameTestState.`no event while transformed, and a transform sends the genie away`() =
        genieTest { script ->
            val form = checkNotNull(ServerCacheManager.getNpc(GenieRandomEvent.GENIE.asRSCM(RSCMType.NPC)))
            script.currentTimeMillis = { START }
            player.attr[NEXT_EVENT_AT] = START - 1
            player.transmog = form
            assertEquals(CheckResult.MissedTransformed, script.check(player))
            assertNull(script.genieOf(player))

            player.transmog = null
            val genie = script.spawn(player)
            player.transmog = form
            advance(ticks = CALL_INTERVAL)
            assertNull(script.genieOf(player))
            assertFalse(genie.isSlotAssigned)
        }

    @Test
    fun GameTestState.`an open interface delays the event until it closes`() =
        genieTest { script ->
            script.currentTimeMillis = { START }
            player.attr[NEXT_EVENT_AT] = START - 1
            player.ifOpenMain("interface.bankmain")
            assertEquals(CheckResult.Delayed, script.check(player))
            assertEquals(START - 1, script.nextEventAt(player))
            advance(ticks = 3)
            assertNull(script.genieOf(player))

            player.ifClose()
            advance(ticks = 2)
            assertNotNull(script.genieOf(player))
        }

    @Test
    fun GameTestState.`a Make-X repeat delays the event`() =
        genieTest { script ->
            script.currentTimeMillis = { START }
            player.attr[NEXT_EVENT_AT] = START - 1
            player.weakQueue("queue.generic_queue1", 50)
            assertEquals(CheckResult.Delayed, script.check(player))
            assertNull(script.genieOf(player))
        }

    @Test
    fun GameTestState.`an interrupted hand-over still gives one lamp and the genie leaves`() =
        genieTest { script ->
            val genie = script.spawn(player)
            assertTrue(script.handOver(player))
            assertFalse(script.handOver(player))
            assertEquals(1, player.inv.count(LAMP))
            advance(ticks = HAND_OVER_TICKS + 1)
            assertNull(script.genieOf(player))
            assertFalse(genie.isSlotAssigned)
            assertEquals(1, player.inv.count(LAMP))
        }

    private fun GameTestState.genieTest(body: GameTestScope.(GenieRandomEvent) -> Unit) =
        runInjectedGameTest(GenieRandomEvent::class, null, GenieRandomEvent::class) { script ->
            val center = CoordGrid(0, 50, 50, 20, 20)
            allocZoneCollision(center)
            player.teleport(center)
            player.attr.remove(NEXT_EVENT_AT)
            body(script)
        }

    private companion object {
        const val MINUTE = 60_000L
        const val START = 1_800_000_000_000L
    }
}
