package org.rsmod.content.other.treasuretrails

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.other.treasuretrails.StashUnitScript.Companion.UNITS
import org.rsmod.content.other.treasuretrails.StashUnitScript.Companion.isFilled
import org.rsmod.content.other.treasuretrails.TreasureTrailScript.Advance
import org.rsmod.content.other.treasuretrails.TreasureTrailScript.Companion.CASKETS
import org.rsmod.content.other.treasuretrails.TreasureTrailScript.Companion.REWARD_INV
import org.rsmod.content.other.treasuretrails.TreasureTrailScript.Companion.currentStep
import org.rsmod.content.other.treasuretrails.TreasureTrailScript.Companion.rollRewards
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class TreasureTrailScriptTest {
    private val beginner = TrailTier.Beginner
    private val easy = TrailTier.Easy
    private val start = CoordGrid(3092, 3226, 0)

    @Test
    fun `beginner casket rolls follow the flattened wiki table`() {
        assertEquals(44280, CASKETS.getValue(beginner).sumOf { it.weight })
        assertEquals("obj.mole_slippers", rollRewards(beginner, 1) { 0 }.single().obj.internalName)
        val last = rollRewards(beginner, 1) { it - 1 }.single()
        assertEquals("obj.herring", last.obj.internalName)
        assertTrue(last.noted)
        assertEquals(2, rollRewards(beginner, 2) { 0 }.single().count)
    }

    @Test
    fun `easy casket rolls follow the wiki table`() {
        val table = CASKETS.getValue(easy)
        assertEquals(617760, table.sumOf { it.weight })
        assertEquals(211, table.size)
        val coins = table.filter { it.obj.internalName == "obj.coins" }
        assertEquals(4, coins.size)
        assertEquals(1716, table.first { it.obj.internalName == "obj.trail_composite_bow_willow" }.weight)
        assertTrue(table.single { it.obj.internalName == "obj.salmon" }.noted)
    }

    @Test
    fun `beginner steps come from jagex's clue tables`() {
        val steps = TrailSteps.ALL.getValue(beginner)
        assertEquals(26, steps.size)
        val charlie = steps.filterIsInstance<TrailStep.Charlie>().single()
        assertEquals(8, charlie.tasks.size)
        assertTrue(charlie.tasks.all { it.request.startsWith("I really need ") })
        assertEquals(5, steps.filterIsInstance<TrailStep.HotCold>().single().spots.size)
        val maps = steps.filterIsInstance<TrailStep.Dig>()
        assertEquals(5, maps.size)
        assertTrue(maps.all { it.map != null })
        assertEquals(6, steps.filterIsInstance<TrailStep.Emote>().size)
        val ranael = steps.filterIsInstance<TrailStep.Talk>().first { "AN EARL" in it.text!! }
        assertTrue("npc.ranael".asRSCM(RSCMType.NPC) in ranael.npcs)
        assertEquals(3, UNITS.count { it.tier == beginner })
    }

    @Test
    fun `easy steps each have their own scroll`() {
        val steps = TrailSteps.ALL.getValue(easy)
        assertTrue(steps.size > 100, "easy steps: ${steps.size}")
        val objs = steps.map { TrailSteps.clueObj(it) }
        assertEquals(objs.size, objs.toSet().size)
        val counts = steps.groupingBy { it::class.simpleName }.eachCount()
        println("easy steps by kind: $counts")
        assertEquals(71, counts["Search"])
        assertEquals(15, counts["Dig"])
        val drawers = steps.filterIsInstance<TrailStep.Search>().first { "Falador's chain mail shop" in it.text!! }
        assertEquals(listOf("loc.drawers1open".asRSCM(RSCMType.LOC)), drawers.locs.map { it.first })
        val music = steps.filterIsInstance<TrailStep.Music>()
        assertEquals(6, music.size)
        assertTrue(music.all { Regex("<col=ffffff>[A-Za-z !&',.]+</col>").containsMatchIn(it.text!!) })
        assertTrue(steps.any { it is TrailStep.Emote && it.stashLoc != null })
    }

    @Test
    fun `medium casket rolls follow the wiki table`() {
        val table = CASKETS.getValue(TrailTier.Medium)
        assertEquals(23181180, table.sumOf { it.weight })
        assertEquals(192, table.size)
    }

    @Test
    fun `medium steps come from jagex's clue tables`() {
        val steps = TrailSteps.ALL.getValue(TrailTier.Medium)
        val counts = steps.groupingBy { it::class.simpleName }.eachCount()
        println("medium steps by kind: $counts")
        val talks = steps.filterIsInstance<TrailStep.Talk>()
        assertEquals(44, talks.count { it.challenge != null })
        val baraek = talks.single { "A BAKER" in it.text!! }
        assertEquals(5, baraek.challenge?.answer)
        assertTrue(TreasureTrailScript.objName(baraek.challenge!!.obj).endsWith("_challenge"))
        assertEquals(44, steps.count { it is TrailStep.Dig && it.map == null })
        assertEquals(12, steps.count { (it as? TrailStep.Dig)?.map != null || (it as? TrailStep.Search)?.map != null })
        assertEquals(25, steps.count { it is TrailStep.Emote && it.seq2 != null })
        assertEquals(11, steps.count { it is TrailStep.Search && it.key != null })
        assertEquals(6, steps.count { it is TrailStep.Music })
        val objs = steps.map { TrailSteps.clueObj(it) }
        assertEquals(objs.size, objs.toSet().size)
        assertTrue(UNITS.count { it.tier == TrailTier.Medium } > 20)
    }

    @Test
    fun `hard casket rolls follow the wiki table`() {
        val table = CASKETS.getValue(TrailTier.Hard)
        assertEquals(6971393, table.sumOf { it.weight })
        assertEquals(234, table.size)
    }

    @Test
    fun `hard steps come from jagex's clue tables`() {
        val steps = TrailSteps.ALL.getValue(TrailTier.Hard)
        println("hard steps by kind: ${steps.groupingBy { it::class.simpleName }.eachCount()}")
        val talks = steps.filterIsInstance<TrailStep.Talk>()
        assertEquals(20, talks.count { it.challenge != null })
        assertEquals(54, steps.count { it is TrailStep.Dig && it.map == null && it.text?.contains("degrees") == true })
        val maps = steps.mapNotNull { (it as? TrailStep.Dig)?.map ?: (it as? TrailStep.Search)?.map }
        assertEquals(7, maps.size)
        assertTrue("interface.trail_map05" in maps && "interface.trail_clue_hard_map007" in maps)
        assertEquals(17, steps.count { it is TrailStep.Emote })
        assertEquals(10, steps.count { it is TrailStep.Music })
        val objs = steps.map { TrailSteps.clueObj(it) }
        assertEquals(objs.size, objs.toSet().size)
        assertEquals(16, UNITS.count { it.tier == TrailTier.Hard })
    }

    @Test
    fun GameTestState.`a key step's npc drops its key once, and only on that step`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            val medium = TrailTier.Medium
            val step = TrailSteps.ALL.getValue(medium).filterIsInstance<TrailStep.Search>().first { it.key != null }
            val npc = step.keyNpcs.first()
            assertNull(script.keyDrop(player, npc))
            script.giveClue(Cheat(player, "clue", listOf("medium", "${step.row}", "2")))
            val key = TreasureTrailScript.objName(checkNotNull(step.key))
            assertEquals(key, script.keyDrop(player, npc))
            assertNull(script.keyDrop(player, -1))
            player.inv[5] = InvObj(checkNotNull(step.key), 1)
            assertNull(script.keyDrop(player, npc))
        }

    @Test
    fun `coordinate clue text is what the plugin parses`() {
        assertEquals(
            "00 degrees 05 minutes south<br>01 degrees 13 minutes east",
            TrailSteps.coordinateText("00.05S 01.13E"),
        )
    }

    @Test
    fun GameTestState.`a three step beginner trail ends in one casket`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            script.giveClue(Cheat(player, "clue", listOf("beginner", "", "3")))
            val clue = "obj.trail_clue_beginner"
            assertEquals(1, player.inv.count(clue))
            with(script) {
                var result = Advance.NONE
                player.withProtectedAccess { result = advanceTrail(checkNotNull(currentStep(player, beginner))) }
                assertEquals(Advance.NEXT_CLUE, result)
                assertEquals(1, player.vars[beginner.stepsDoneVarp])
                player.withProtectedAccess { result = advanceTrail(checkNotNull(currentStep(player, beginner))) }
                assertEquals(Advance.NEXT_CLUE, result)
                player.withProtectedAccess { result = advanceTrail(checkNotNull(currentStep(player, beginner))) }
                assertEquals(Advance.CASKET, result)
                assertEquals(0, player.inv.count(clue))
                assertEquals(1, player.inv.count(beginner.casket))
                assertNull(currentStep(player, beginner))
                assertEquals(0, player.vars[beginner.stepsDoneVarp])
            }
        }

    @Test
    fun GameTestState.`an easy step swaps the scroll in its slot`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            player.inv[0] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
            script.giveClue(Cheat(player, "clue", listOf("easy", "", "2")))
            val first = checkNotNull(currentStep(player, easy))
            assertEquals(TrailSteps.clueObj(first), player.inv[1]?.id)
            with(script) {
                var result = Advance.NONE
                player.withProtectedAccess { result = advanceTrail(first) }
                assertEquals(Advance.NEXT_CLUE, result)
                val second = checkNotNull(currentStep(player, easy))
                assertTrue(second.row != first.row)
                assertEquals(TrailSteps.clueObj(second), player.inv[1]?.id)
                assertEquals(2, player.inv.objs.count { it != null })
                player.withProtectedAccess { result = advanceTrail(second) }
                assertEquals(Advance.CASKET, result)
                assertEquals(1, player.inv.count(easy.casket))
                assertEquals(2, player.inv.objs.count { it != null })
            }
        }

    @Test
    fun GameTestState.`a fresh dropped easy scroll starts a trail on read`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            val drop = checkNotNull(easy.dropObj).asRSCM(RSCMType.OBJ)
            player.inv[3] = InvObj(drop, 1)
            val step = script.stepForRead(player, easy, drop)
            assertEquals(TrailSteps.clueObj(step), player.inv[3]?.id)
            assertTrue(player.vars[easy.stepsLeftVarp] in 2..4)
            assertEquals(step.row, script.stepForRead(player, easy, TrailSteps.clueObj(step)).row)
        }

    @Test
    fun GameTestState.`charlie takes exactly his item once he has asked`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            val charlie = TrailSteps.ALL.getValue(beginner).filterIsInstance<TrailStep.Charlie>().single()
            script.giveClue(Cheat(player, "clue", listOf("beginner", "${charlie.row}", "2")))
            val herring = charlie.tasks.first { "herring" in it.request }
            val herringObj = "obj.raw_herring"
            player.inv[1] = InvObj(herringObj.asRSCM(RSCMType.OBJ), 1)
            player.inv[2] = InvObj(herringObj.asRSCM(RSCMType.OBJ), 1)
            with(script) {
                var result = Advance.CASKET
                player.withProtectedAccess { result = handInToCharlie(charlie) }
                assertEquals(Advance.NONE, result)
                assertEquals(2, player.inv.count(herringObj))
                VarPlayerIntMapSetter.set(player, beginner.auxVarp, herring.row)
                assertEquals(herring.text, readText(player, charlie))
                player.withProtectedAccess { result = handInToCharlie(charlie) }
                assertEquals(Advance.NEXT_CLUE, result)
                assertEquals(1, player.inv.count(herringObj))
                assertEquals(0, player.vars[beginner.auxVarp])
                player.withProtectedAccess { result = handInToCharlie(charlie) }
                assertEquals(Advance.NONE, result)
                assertEquals(1, player.inv.count(herringObj))
            }
        }

    @Test
    fun GameTestState.`the strange device reads the distance to the spot`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            val hotCold = TrailSteps.ALL.getValue(beginner).filterIsInstance<TrailStep.HotCold>().single()
            script.giveClue(Cheat(player, "clue", listOf("beginner", "${hotCold.row}")))
            val spot = checkNotNull(script.hotColdSpot(player, hotCold)).coord
            fun feelAt(dx: Int): String {
                val tile = spot.translate(dx, 0)
                allocZoneCollision(tile)
                player.teleport(tile)
                return script.deviceMessage(player, spot)
            }
            assertEquals("The device is warm.", feelAt(120))
            assertEquals("The device is very hot, and warmer than last time.", feelAt(40))
            assertEquals("The device is very hot, and the same temperature as last time.", feelAt(40))
            assertEquals("The device is ice cold, but colder than last time.", feelAt(600))
            assertEquals("The device is incredibly hot, and warmer than last time.", feelAt(4))
            assertEquals("The device is visibly shaking, and warmer than last time.", feelAt(3))
            assertNotNull(script.digStep(player))
            feelAt(5)
            assertNull(script.digStep(player))
        }

    @Test
    fun GameTestState.`the casket needs space and pays out exactly the reward inv`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            for (slot in 0 until player.inv.size) {
                player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
            }
            player.inv[0] = InvObj(beginner.casket.asRSCM(RSCMType.OBJ), 2)
            with(script) {
                var rewards: List<TreasureTrailScript.Reward>? = null
                player.withProtectedAccess { rewards = openCasket(beginner) }
                assertNull(rewards)
                assertEquals(2, player.inv.count(beginner.casket))
                player.inv[1] = null
                player.inv[2] = null
                player.inv[3] = null
                val logs = player.inv.count("obj.logs")
                player.withProtectedAccess { rewards = openCasket(beginner) }
                assertEquals(1, player.inv.count(beginner.casket))
                assertEquals(1, player.vars[beginner.completedVarp])
                assertEquals(logs, player.inv.count("obj.logs"))
                val rolled = checkNotNull(rewards)
                assertTrue(player.invMap.getOrPut(REWARD_INV).objs.all { it == null })
                val received =
                    player.inv.objs.filterNotNull().filter {
                        it.id != beginner.casket.asRSCM(RSCMType.OBJ) && it.id != "obj.logs".asRSCM(RSCMType.OBJ)
                    }
                assertEquals(rolled.sumOf { it.count }, received.sumOf { it.count })
            }
        }

    @Test
    fun GameTestState.`a scroll box only opens without a clue of its tier`() =
        runInjectedGameTest(TreasureTrailScript::class, null, TreasureTrailScript::class) { script ->
            prepare()
            player.inv[0] = InvObj(beginner.scrollBox.asRSCM(RSCMType.OBJ), 1)
            player.inv[1] = InvObj("obj.trail_clue_beginner".asRSCM(RSCMType.OBJ), 1)
            with(script) {
                player.withProtectedAccess { openScrollBox(beginner) }
                assertEquals(1, player.inv.count(beginner.scrollBox))
                player.inv[1] = null
                player.withProtectedAccess { openScrollBox(beginner) }
                assertEquals(0, player.inv.count(beginner.scrollBox))
                assertEquals(1, player.inv.count("obj.trail_clue_beginner"))
            }
        }

    @Test
    fun GameTestState.`stash deposit and withdraw move exactly the unit's items`() =
        runInjectedGameTest(StashUnitScript::class, null, StashUnitScript::class) { script ->
            prepare()
            val unit = UNITS.first { it.tier == beginner && "obj.gold_ring" in it.items }
            assertEquals(listOf("obj.gold_ring", "obj.gold_necklace").sorted(), unit.items.sorted())
            player.inv[0] = InvObj("obj.gold_ring".asRSCM(RSCMType.OBJ), 1)
            with(script) {
                player.withProtectedAccess { search(unit) }
                assertFalse(isFilled(player, unit))
                VarPlayerIntMapSetter.set(player, unit.varbit, 1)
                player.withProtectedAccess { search(unit) }
                assertFalse(isFilled(player, unit))
                player.inv[1] = InvObj("obj.gold_necklace".asRSCM(RSCMType.OBJ), 1)
                player.inv[2] = InvObj("obj.gold_necklace".asRSCM(RSCMType.OBJ), 1)
                player.withProtectedAccess { search(unit) }
                assertTrue(isFilled(player, unit))
                assertEquals(0, player.inv.count("obj.gold_ring"))
                assertEquals(1, player.inv.count("obj.gold_necklace"))
                assertTrue(UNITS.filter { it !== unit }.none { isFilled(player, it) })
                for (slot in 0 until player.inv.size) {
                    if (player.inv[slot] == null) player.inv[slot] = InvObj("obj.logs".asRSCM(RSCMType.OBJ), 1)
                }
                player.inv[0] = null
                player.withProtectedAccess { search(unit) }
                assertTrue(isFilled(player, unit))
                player.inv[3] = null
                player.withProtectedAccess { search(unit) }
                assertFalse(isFilled(player, unit))
                assertEquals(1, player.inv.count("obj.gold_ring"))
                assertEquals(2, player.inv.count("obj.gold_necklace"))
            }
        }

    private fun GameTestScope.prepare() {
        allocZoneCollision(start)
        player.teleport(start)
        player.clearInv()
    }
}
