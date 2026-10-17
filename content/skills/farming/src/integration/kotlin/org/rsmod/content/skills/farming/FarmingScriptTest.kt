package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.farming.FarmingScript.Companion.AREAS
import org.rsmod.content.skills.farming.FarmingScript.Companion.CROPS
import org.rsmod.content.skills.farming.FarmingScript.Companion.DIBBER
import org.rsmod.content.skills.farming.FarmingScript.Companion.RAKE
import org.rsmod.content.skills.farming.FarmingScript.Companion.SPADE
import org.rsmod.content.skills.farming.FarmingScript.Companion.WEEDS
import org.rsmod.content.skills.farming.FarmingScript.Companion.cropAt
import org.rsmod.content.skills.farming.FarmingScript.Companion.flowerYield
import org.rsmod.content.skills.farming.FarmingScript.Companion.growth
import org.rsmod.content.skills.farming.FarmingScript.Companion.saveChance
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class FarmingScriptTest {
    private val start = Instant.parse("2026-10-16T12:03:00Z")
    private val catherby = AREAS[0].patches
    private val catherbyTile = CoordGrid(2809, 3463, 0)

    private fun crop(name: String) = CROPS.first { it.seed.internalName == "obj.${name}_seed" }

    @Test
    fun `chance to save matches the wiki's percentages`() {
        assertEquals(102, saveChance(crop("potato"), 1))
        assertEquals(181, saveChance(crop("potato"), 99))
        assertEquals(26, saveChance(crop("guam"), 1))
        assertEquals(81, saveChance(crop("guam"), 99))
        assertEquals(81, saveChance(crop("torstol"), 99))
    }

    @Test
    fun `growth stops at the harvest value`() {
        val potato = crop("potato")
        assertEquals(7, growth(potato, 6, 1))
        assertEquals(9, growth(potato, 6, 3))
        assertEquals(10, growth(potato, 6, 4))
        assertEquals(10, growth(potato, 8, Int.MAX_VALUE))
        assertEquals(138, growth(crop("snape_grass"), 69, 1))
    }

    @Test
    fun `limpwurt gets the level bonus and woad three leaves`() {
        assertEquals(3, flowerYield(crop("woad"), 99) { it - 1 })
        assertEquals(12, flowerYield(crop("limpwurt"), 99) { it - 1 })
        assertEquals(3, flowerYield(crop("limpwurt"), 99) { 0 })
        assertEquals(1, flowerYield(crop("marigold"), 99) { it - 1 })
    }

    @Test
    fun `each patch is the multiloc of its varbit, ripe at the harvest value`() {
        for (patch in AREAS.flatMap { it.patches }) {
            val type = checkNotNull(ServerCacheManager.getObject(patch.loc.asRSCM(RSCMType.LOC)))
            assertEquals(patch.transmitVarbit.asRSCM(RSCMType.VARBIT), type.multiVarBit, patch.loc)
            for (crop in CROPS.filter { it.patch == patch.type.id }) {
                val ripe = ServerCacheManager.getObject(type.multiLoc[crop.harvestValue])
                val op = ripe?.actions?.getOpOrNull(0)
                assertTrue(op == "Harvest" || op == "Pick", "${patch.loc} ${crop.seed.internalName}: $op")
                for (value in crop.plantValue until crop.plantValue + crop.stages) {
                    assertEquals(crop, cropAt(patch, value))
                }
            }
        }
    }

    @Test
    fun GameTestState.`rake, plant, grow and harvest an allotment`() =
        runInjectedGameTest(FarmingScript::class, null, FarmingScript::class) { script ->
            script.clock = Clock.fixed(start, ZoneOffset.UTC)
            prepare()
            val patch = catherby[0]
            player.inv[0] = InvObj(RAKE.asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj(DIBBER.asRSCM(RSCMType.OBJ), 1)
            player.inv[2] = InvObj(SPADE.asRSCM(RSCMType.OBJ), 1)
            player.inv[3] = InvObj("obj.potato_seed".asRSCM(RSCMType.OBJ), 5)
            with(script) {
                player.withProtectedAccess { repeat(4) { rakeOnce(patch) } }
                assertEquals(3, script.value(player, patch))
                assertEquals(3, player.inv.count(WEEDS))

                var planted = false
                player.withProtectedAccess { planted = plant(patch, "obj.potato_seed") }
                assertTrue(planted)
                assertEquals(6, script.value(player, patch))
                assertEquals(2, player.inv.count("obj.potato_seed"))
                script.transmit(player)
                assertEquals(6, player.vars[patch.transmitVarbit])

                script.clock = Clock.fixed(start.plus(Duration.ofMinutes(7)), ZoneOffset.UTC)
                assertEquals(7, script.value(player, patch))
                script.clock = Clock.fixed(start.plus(Duration.ofMinutes(37)), ZoneOffset.UTC)
                assertEquals(10, script.value(player, patch))

                val xpBefore = player.statMap.getFineXP("stat.farming")
                var harvests = 0
                var result = FarmingScript.Harvest.MORE
                while (result == FarmingScript.Harvest.MORE) {
                    player.withProtectedAccess { result = harvestOnce(patch, crop("potato")) }
                    harvests++
                }
                assertEquals(FarmingScript.Harvest.EMPTY, result)
                assertTrue(harvests >= 3)
                assertEquals(harvests, player.inv.count("obj.potato"))
                assertEquals(3, script.value(player, patch))
                val xp = player.statMap.getFineXP("stat.farming") - xpBefore
                assertEquals(harvests * 90 + 80, xp)
            }
        }

    @Test
    fun GameTestState.`a full inventory stops the harvest without losing a life`() =
        runInjectedGameTest(FarmingScript::class, null, FarmingScript::class) { script ->
            script.clock = Clock.fixed(start, ZoneOffset.UTC)
            prepare()
            val patch = catherby[3]
            player.inv[0] = InvObj(DIBBER.asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.guam_seed".asRSCM(RSCMType.OBJ), 1)
            with(script) {
                player.withProtectedAccess { repeat(3) { rakeOnce(patch) } }
                player.withProtectedAccess { plant(patch, "obj.guam_seed") }
                assertEquals(0, player.inv.count("obj.guam_seed"))
                script.clock = Clock.fixed(start.plus(Duration.ofMinutes(80)), ZoneOffset.UTC)
                assertEquals(8, script.value(player, patch))
                for (slot in 0 until player.inv.size) {
                    player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
                }
                var result = FarmingScript.Harvest.MORE
                player.withProtectedAccess { result = harvestOnce(patch, crop("guam")) }
                assertEquals(FarmingScript.Harvest.NONE, result)
                assertEquals(8, script.value(player, patch))
                assertEquals(player.inv.size, player.inv.count("obj.logs"))
                assertEquals(3, player.vars[patch.stateVarp] shr FarmingScript.LIVES_SHIFT)
            }
        }

    @Test
    fun GameTestState.`planting needs a clear patch, a dibber and the level`() =
        runInjectedGameTest(FarmingScript::class, null, FarmingScript::class) { script ->
            script.clock = Clock.fixed(start, ZoneOffset.UTC)
            prepare(level = 1)
            val patch = catherby[2]
            player.inv[0] = InvObj("obj.white_lily_seed".asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.marigold_seed".asRSCM(RSCMType.OBJ), 1)
            with(script) {
                var planted = true
                player.withProtectedAccess { planted = plant(patch, "obj.marigold_seed") }
                assertEquals(false, planted)
                player.withProtectedAccess { repeat(3) { rakeOnce(patch) } }
                player.withProtectedAccess { planted = plant(patch, "obj.marigold_seed") }
                assertEquals(false, planted)
                player.inv[2] = InvObj(DIBBER.asRSCM(RSCMType.OBJ), 1)
                player.withProtectedAccess { planted = plant(patch, "obj.white_lily_seed") }
                assertEquals(false, planted)
                assertEquals(1, player.inv.count("obj.white_lily_seed"))
                player.setBaseLevel("stat.farming", 2)
                player.setCurrentLevel("stat.farming", 2)
                player.withProtectedAccess { planted = plant(patch, "obj.marigold_seed") }
                assertTrue(planted)
                assertEquals(8, script.value(player, patch))
            }
        }

    @Test
    fun GameTestState.`patches transmit only inside their area`() =
        runInjectedGameTest(FarmingScript::class, null, FarmingScript::class) { script ->
            script.clock = Clock.fixed(start, ZoneOffset.UTC)
            prepare()
            val patch = catherby[1]
            with(script) { player.withProtectedAccess { rakeOnce(patch) } }
            script.transmit(player)
            assertEquals(1, player.vars[patch.transmitVarbit])
            val lumbridge = CoordGrid(3222, 3218, 0)
            allocZoneCollision(lumbridge)
            player.teleport(lumbridge)
            script.transmit(player)
            assertEquals(0, player.vars[patch.transmitVarbit])
            assertNull(AREAS.firstOrNull { it.contains(player.coords) })
            assertNotNull(AREAS.firstOrNull { it.contains(catherbyTile) })
            val farms =
                listOf(
                    CoordGrid(3055, 3307, 0),
                    CoordGrid(2666, 3374, 0),
                    CoordGrid(3601, 3525, 0),
                    CoordGrid(1738, 3550, 0),
                    CoordGrid(1585, 3100, 0),
                )
            for ((index, tile) in farms.withIndex()) {
                assertEquals(AREAS[index + 1], AREAS.firstOrNull { it.contains(tile) }, tile.toString())
            }
        }

    private fun GameTestScope.prepare(level: Int = 99) {
        allocZoneCollision(catherbyTile)
        player.teleport(catherbyTile)
        player.clearInv()
        player.setBaseLevel("stat.farming", level)
        player.setCurrentLevel("stat.farming", level)
    }
}
