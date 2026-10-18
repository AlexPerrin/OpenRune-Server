package org.rsmod.content.quest.area.falador

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.interact.LocOpOverrides
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DoricsQuestInteractionTest {
    private fun fixture() =
        QuestTalkFixture(CoordGrid(2951, 3450, 0)) { DoricsQuest(it, LocOpOverrides()) }

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

        f.talk("npc.doric", 2, 2)
        assertEquals(180, f.count("obj.coins"))
        assertEquals(listOf(1, 0, 1), f.materials())
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
