package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.BirdHouseScript.Companion.CLOCKWORK
import org.rsmod.content.skills.hunter.BirdHouseScript.Companion.FEATHER
import org.rsmod.content.skills.hunter.BirdHouseScript.Companion.RAW_BIRD_MEAT
import org.rsmod.content.skills.hunter.BirdHouseScript.Companion.SPACES
import org.rsmod.content.skills.hunter.BirdHouseScript.Companion.TIERS
import org.rsmod.content.skills.hunter.BirdHouseScript.Companion.collectMessage
import org.rsmod.content.skills.hunter.BirdHouseScript.Companion.rollLoot
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class BirdHouseScriptTest {
    /** RuneLite's LootTrackerPlugin.BIRDHOUSE_PATTERN. */
    private val lootTrackerPattern =
        Regex(
            "You dismantle and discard the trap, retrieving (?:(?:a|\\d{1,2}) nests?, )?10 dead " +
                "birds, \\d{1,3} feathers and (\\d,?\\d{1,3}) Hunter XP\\."
        )

    @Test
    fun `the collect message matches what the Loot Tracker parses`() {
        assertTrue(lootTrackerPattern.matches(collectMessage(0, 30, 112)))
        assertTrue(lootTrackerPattern.matches(collectMessage(1, 60, 280)))
        assertTrue(lootTrackerPattern.matches(collectMessage(4, 40, 1200)))
    }

    @Test
    fun `each space is the multiloc of its transmit varp`() {
        for (space in SPACES) {
            val type = checkNotNull(ServerCacheManager.getObject(space.loc.asRSCM(RSCMType.LOC)))
            assertEquals(space.transmitVarp.asRSCM(RSCMType.VARP), type.multiVarp, space.loc)
        }
        assertEquals(1626, "varp.birdhouse_transmit_a".asRSCM(RSCMType.VARP))
    }

    @Test
    fun `nine tiers in order, regular first`() {
        assertEquals(9, TIERS.size)
        assertEquals("obj.birdhouse_normal", TIERS.first().item.internalName)
        assertEquals((0..8).toList(), TIERS.map { it.tier })
    }

    @Test
    fun `nest chances follow the wiki's chart`() {
        assertEquals(300, BirdHouseScript.nestChance(99, 300))
        assertEquals(150, BirdHouseScript.nestChance(50, 300))
        assertEquals(150, BirdHouseScript.nestChance(5, 300))
        assertEquals(525, BirdHouseScript.nestChance(99, TIERS.last().nestChance))
        val loot = rollLoot(TIERS.first(), 99) { bound -> if (bound == 1000) 300 else 0 }
        assertEquals(1, loot.nests.size)
    }

    @Test
    fun `every roll at zero gives a seed nest and five ring nests`() {
        val loot = rollLoot(TIERS.last(), 99) { 0 }
        assertEquals(30, loot.feathers)
        assertEquals(6, loot.nests.size)
        assertEquals(5, loot.nests.count { it == BirdHouseScript.RING_NEST })
    }

    @Test
    fun GameTestState.`build, seed, wait and empty`() =
        runInjectedGameTest(BirdHouseScript::class, null, BirdHouseScript::class) { script ->
            val start = Instant.parse("2026-10-16T12:00:00Z")
            script.clock = Clock.fixed(start, ZoneOffset.UTC)
            val space = SPACES.first()
            prepare()
            player.inv[0] = InvObj("obj.birdhouse_normal".asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.barley_seed".asRSCM(RSCMType.OBJ), 15)
            with(script) {
                player.withProtectedAccess { build(space, TIERS.first()) }
                assertEquals(1, script.state(player, space))
                assertEquals(0, player.inv.count("obj.birdhouse_normal"))

                player.withProtectedAccess { addSeeds(space, null) }
                assertEquals(3, script.state(player, space))
                assertEquals(5, player.inv.count("obj.barley_seed"))

                var emptied = true
                player.withProtectedAccess { emptied = empty(space) }
                assertFalse(emptied)
                assertEquals(3, script.state(player, space))

                script.clock = Clock.fixed(start.plus(Duration.ofMinutes(50)), ZoneOffset.UTC)
                val xpBefore = player.statMap.getXP("stat.hunter")
                player.withProtectedAccess { emptied = empty(space) }
                assertTrue(emptied)
                assertEquals(0, script.state(player, space))
                assertEquals(1, player.inv.count(CLOCKWORK))
                // Raw bird meat always goes to the ground (wiki).
                assertEquals(0, player.inv.count(RAW_BIRD_MEAT))
                val meat = script.objRepo.findAll(player.coords).filter { it.type == RAW_BIRD_MEAT.asRSCM(RSCMType.OBJ) }
                assertEquals(10, meat.sumOf { it.count })
                assertTrue(player.inv.count(FEATHER) in 30..60)
                assertTrue(player.statMap.getXP("stat.hunter") > xpBefore)
            }
            val sent = client.mapOf(MessageGame::message).toMutableList()
            advance(ticks = 1)
            sent += client.mapOf(MessageGame::message)
            assertTrue(sent.any { lootTrackerPattern.matches(it) }, sent.toString())
        }

    @Test
    fun `the seed nest follows the wiki chart`() {
        assertEquals(1, BirdHouseScript.chance(1, 0, 200))
        assertEquals(101, BirdHouseScript.chance(50, 0, 200))
        assertEquals(201, BirdHouseScript.chance(99, 0, 200))
    }

    @Test
    fun GameTestState.`five high-level seeds fill a house, and seeds mix`() =
        runInjectedGameTest(BirdHouseScript::class, null, BirdHouseScript::class) { script ->
            script.clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
            val (first, second) = SPACES
            prepare()
            player.inv[0] = InvObj("obj.birdhouse_normal".asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.ranarr_seed".asRSCM(RSCMType.OBJ), 7)
            player.inv[3] = InvObj("obj.birdhouse_normal".asRSCM(RSCMType.OBJ), 1)
            with(script) {
                player.withProtectedAccess { build(first, TIERS.first()) }
                player.withProtectedAccess { addSeeds(first, "obj.ranarr_seed") }
                assertEquals(3, script.state(player, first))
                assertEquals(2, player.inv.count("obj.ranarr_seed"))

                player.inv[2] = InvObj("obj.barley_seed".asRSCM(RSCMType.OBJ), 9)
                player.withProtectedAccess { build(second, TIERS.first()) }
                player.withProtectedAccess { addSeeds(second, "obj.barley_seed") }
                // 9 barley and then one ranarr (worth 2) fill it.
                assertEquals(3, script.state(player, second))
                assertEquals(0, player.inv.count("obj.barley_seed"))
                assertEquals(1, player.inv.count("obj.ranarr_seed"))
            }
        }

    @Test
    fun GameTestState.`emptying early keeps only the clockwork`() =
        runInjectedGameTest(BirdHouseScript::class, null, BirdHouseScript::class) { script ->
            script.clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
            val space = SPACES.first()
            prepare()
            player.inv[0] = InvObj("obj.birdhouse_normal".asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.barley_seed".asRSCM(RSCMType.OBJ), 10)
            val xpBefore = player.statMap.getXP("stat.hunter")
            with(script) {
                player.withProtectedAccess { build(space, TIERS.first()) }
                player.withProtectedAccess { addSeeds(space, null) }
                player.withProtectedAccess { emptyEarly(space) }
            }
            assertEquals(0, script.state(player, space))
            assertEquals(1, player.inv.count(CLOCKWORK))
            assertEquals(0, player.inv.count(FEATHER))
            assertEquals(0, player.inv.count("obj.birdhouse_normal"))
            assertEquals(xpBefore, player.statMap.getXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`reset builds a new house from logs and the clockwork`() =
        runInjectedGameTest(BirdHouseScript::class, null, BirdHouseScript::class) { script ->
            val start = Instant.parse("2026-10-16T12:00:00Z")
            script.clock = Clock.fixed(start, ZoneOffset.UTC)
            val space = SPACES.first()
            prepare()
            player.setBaseLevel("stat.crafting", 20)
            player.setCurrentLevel("stat.crafting", 20)
            player.inv[0] = InvObj("obj.birdhouse_normal".asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.barley_seed".asRSCM(RSCMType.OBJ), 10)
            player.inv[2] = InvObj("obj.hammer".asRSCM(RSCMType.OBJ), 1)
            player.inv[3] = InvObj("obj.chisel".asRSCM(RSCMType.OBJ), 1)
            player.inv[4] = InvObj("obj.oak_logs".asRSCM(RSCMType.OBJ), 1)
            player.inv[5] = InvObj("obj.willow_logs".asRSCM(RSCMType.OBJ), 1)
            with(script) {
                player.withProtectedAccess { build(space, TIERS.first()) }
                player.withProtectedAccess { addSeeds(space, null) }
                script.clock = Clock.fixed(start.plus(Duration.ofMinutes(50)), ZoneOffset.UTC)
                val craftingXp = player.statMap.getXP("stat.crafting")
                player.withProtectedAccess { reset(space) }
                advance(ticks = 2)
                // Crafting 20: oak (15) but not willow (25).
                assertEquals(TIERS[1].tier * 3 + 1, script.state(player, space))
                assertEquals(0, player.inv.count("obj.oak_logs"))
                assertEquals(1, player.inv.count("obj.willow_logs"))
                assertEquals(0, player.inv.count(CLOCKWORK))
                assertTrue(player.statMap.getXP("stat.crafting") > craftingXp)
            }
        }

    @Test
    fun GameTestState.`a full inventory drops the loot instead of losing it`() =
        runInjectedGameTest(BirdHouseScript::class, null, BirdHouseScript::class) { script ->
            val start = Instant.parse("2026-10-16T12:00:00Z")
            script.clock = Clock.fixed(start, ZoneOffset.UTC)
            val space = SPACES.first()
            prepare()
            player.observerUUID = 793L
            player.inv[0] = InvObj("obj.birdhouse_normal".asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.barley_seed".asRSCM(RSCMType.OBJ), 10)
            with(script) {
                player.withProtectedAccess { build(space, TIERS.first()) }
                player.withProtectedAccess { addSeeds(space, null) }
                for (slot in 0 until player.inv.size) {
                    player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
                }
                script.clock = Clock.fixed(start.plus(Duration.ofMinutes(60)), ZoneOffset.UTC)
                player.withProtectedAccess { empty(space) }
            }
            assertEquals(0, script.state(player, space))
            assertEquals(player.inv.size, player.inv.count("obj.logs"))
        }

    private fun GameTestScope.prepare() {
        val center = CoordGrid(0, 50, 50, 20, 20)
        allocZoneCollision(center)
        player.teleport(center)
        player.observerUUID = 793L
        player.clearInv()
        for (stat in listOf("stat.hunter")) {
            player.setBaseLevel(stat, 99)
            player.setCurrentLevel(stat, 99)
        }
    }
}
