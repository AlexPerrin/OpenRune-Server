package org.rsmod.content.quest.area.falador

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.LocOverrideEvents
import org.rsmod.api.player.interact.LocOpOverrides
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DoricsQuestInteractionTest {
    private val overrides = LocOpOverrides()

    private fun fixture() =
        QuestTalkFixture(CoordGrid(2951, 3450, 0)) { DoricsQuest(it, overrides) }

    private fun bound(loc: String) =
        BoundLocInfo(
            coords = CoordGrid(2950, 3451, 0),
            entity =
                LocEntity(
                    id = loc.asRSCM(RSCMType.LOC),
                    shape = LocShape.CentrepieceStraight.id,
                    angle = LocAngle.West.id,
                ),
            layer = 2,
            width = 1,
            length = 1,
            forceApproachFlags = 0,
        )

    private fun QuestTalkFixture.claimed(loc: String) =
        overrides.claims(player, bound(loc), loc.asRSCM(RSCMType.LOC), InteractionOp.Op1)

    /** Op1 on [loc] as `LocInteractions` publishes it once the quest claims the op. */
    private fun QuestTalkFixture.opLoc(loc: String, vararg options: Int) {
        assertTrue(claimed(loc), "$loc op1 is not claimed")
        val type = checkNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)))
        interact(*options) {
            assertTrue(events.publish(it, LocOverrideEvents.Op(bound(loc), type, InteractionOp.Op1)))
        }
    }

    private fun QuestTalkFixture.giveMaterials(clay: Int = 6, copper: Int = 4, iron: Int = 2) {
        repeat(clay) { give("obj.clay") }
        repeat(copper) { give("obj.copper_ore") }
        repeat(iron) { give("obj.iron_ore") }
    }

    private fun QuestTalkFixture.materials() =
        listOf(count("obj.clay"), count("obj.copper_ore"), count("obj.iron_ore"))

    @Test fun `accepting sets varp 31 to 10 and hands over a bronze pickaxe`() {
        val f = fixture()
        f.talk("npc.doric", 1, 1, 2)
        assertEquals(10, f.player.vars["varp.doricquest"])
        assertEquals(1, f.count("obj.bronze_pickaxe"))
    }

    @Test fun `declining leaves the quest unstarted`() {
        val f = fixture()
        f.talk("npc.doric", 1, 2)
        assertEquals(0, f.player.vars["varp.doricquest"])
        assertEquals(0, f.count("obj.bronze_pickaxe"))
    }

    @Test fun `too few materials change nothing`() {
        val f = fixture()
        f.talk("npc.doric", 1, 1, 2)
        f.giveMaterials(iron = 1)
        f.talk("npc.doric", 2)
        assertEquals(10, f.player.vars["varp.doricquest"])
        assertEquals(listOf(6, 4, 1), f.materials())
    }

    @Test fun `the hand-in takes exactly the materials and completes at 100`() {
        val f = fixture()
        f.talk("npc.doric", 1, 1, 2)
        f.giveMaterials(clay = 7, copper = 4, iron = 3)
        f.talk("npc.doric")
        assertEquals(100, f.player.vars["varp.doricquest"])
        assertEquals(listOf(1, 0, 1), f.materials())
        assertEquals(180, f.count("obj.coins"))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(1300, f.player.statMap.getXP("stat.mining"))

        f.talk("npc.doric")
        assertEquals(180, f.count("obj.coins"))
        assertEquals(listOf(1, 0, 1), f.materials())
    }

    @Test fun `a full coin stack keeps the materials and leaves the quest open`() {
        val f = fixture()
        f.talk("npc.doric", 1, 1, 2)
        f.give("obj.coins", Int.MAX_VALUE)
        f.giveMaterials()
        f.talk("npc.doric")
        assertEquals(10, f.player.vars["varp.doricquest"])
        assertEquals(listOf(6, 4, 2), f.materials())
        assertEquals(Int.MAX_VALUE, f.count("obj.coins"))
        assertEquals(0, f.player.vars["varp.qp"])
        assertTrue(f.output().contains("You don't have enough room for the reward."), f.output())
    }

    @Test fun `after the quest Doric asks about metalworking and the talk ends`() {
        val f = fixture()
        f.giveMaterials()
        f.talk("npc.doric", 1, 1)
        f.talk("npc.doric")
        val out = f.output()
        assertTrue(out.contains("how is your metalworking coming along?"), out)
        assertTrue(out.contains("Not too bad, Doric."), out)
        assertTrue(out.contains("the love of metal is a thing close to my heart."), out)
        assertFalse(out.contains("humble smithy"), out)
    }

    @Test fun `the anvils and whetstone are claimed until the quest is complete`() {
        val f = fixture()
        assertTrue(f.claimed("loc.dorics_anvil"))
        assertTrue(f.claimed("loc.devious_whetstone"))
        f.giveMaterials()
        f.talk("npc.doric", 1, 1)
        assertFalse(f.claimed("loc.dorics_anvil"))
        assertFalse(f.claimed("loc.devious_whetstone"))
    }

    @Test fun `the anvil menu has two options and the second ends the talk`() {
        val f = fixture()
        f.opLoc("loc.dorics_anvil", 2)
        val out = f.output()
        assertTrue(out.contains("Hey, who said you could use that?"), out)
        assertTrue(out.contains("I didn't want to use your anvils anyway."), out)
        assertTrue(out.contains("That is your choice."), out)
        assertFalse(out.contains("hitting rocks"), out)
        assertEquals(0, f.player.vars["varp.doricquest"])
    }

    @Test fun `declining on the anvil path leaves the quest unstarted`() {
        val f = fixture()
        f.opLoc("loc.dorics_anvil", 1, 2)
        assertTrue(f.output().contains("hitting rocks is for the boring people"), f.output())
        assertEquals(0, f.player.vars["varp.doricquest"])
        assertEquals(0, f.count("obj.bronze_pickaxe"))
    }

    @Test fun `accepting on the anvil path continues to where to find the materials`() {
        val f = fixture()
        f.opLoc("loc.dorics_anvil", 1, 1, 1)
        assertEquals(10, f.player.vars["varp.doricquest"])
        assertEquals(1, f.count("obj.bronze_pickaxe"))
        assertTrue(f.output().contains("just inside the Dwarven"), f.output())
    }

    @Test fun `accepting on the anvil path with the materials completes the quest at once`() {
        val f = fixture()
        f.giveMaterials()
        f.opLoc("loc.dorics_anvil", 1, 1)
        assertEquals(100, f.player.vars["varp.doricquest"])
        assertEquals(listOf(0, 0, 0), f.materials())
        assertEquals(180, f.count("obj.coins"))
        assertTrue(f.output().contains("exact quantities"), f.output())
    }

    @Test fun `using the whetstone before the quest says to ask first`() {
        val f = fixture()
        f.opLoc("loc.devious_whetstone")
        assertTrue(f.output().contains("You should probably ask before using that."), f.output())
        assertEquals(0, f.player.vars["varp.doricquest"])
    }

    @Test fun `carrying the materials when starting completes the quest at once`() {
        val f = fixture()
        f.giveMaterials()
        f.talk("npc.doric", 1, 1)
        assertEquals(100, f.player.vars["varp.doricquest"])
        assertEquals(listOf(0, 0, 0), f.materials())
        assertTrue(f.output().contains("exact quantities"), f.output())
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
