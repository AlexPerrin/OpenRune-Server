package org.rsmod.content.quest.area.varrock

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ApothecaryPotionsTest {
    private fun fixture() = QuestTalkFixture(CoordGrid(3195, 3404, 0)) { ApothecaryPotions() }

    private fun QuestTalkFixture.brew(potion: ApothecaryPotion, requested: Int): Int {
        var brewed = -1
        interact { brewed = it.brewApothecaryPotions(potion, requested) }
        return brewed
    }

    @Test fun `the potions op opens the interface`() {
        val f = fixture()
        val npc = Npc("npc.apothecary", f.player.coords.translateZ(1))
        f.interact { assertTrue(f.events.publish(it, NpcEvents.Op3(npc))) }
        assertTrue(f.player.ui.containsModal("interface.apothecary_potions"))
    }

    @Test fun `strength potions stop at the scarcest ingredient`() {
        val f = fixture()
        f.give("obj.limpwurt_root")
        f.give("obj.limpwurt_root")
        f.give("obj.limpwurt_root")
        f.give("obj.red_spiders_eggs")
        f.give("obj.red_spiders_eggs")
        f.give("obj.coins", 100)
        assertEquals(2, f.brew(ApothecaryPotion.Strength, 5))
        assertEquals(2, f.count("obj.strength4"))
        assertEquals(1, f.count("obj.limpwurt_root"))
        assertEquals(0, f.count("obj.red_spiders_eggs"))
        assertEquals(90, f.count("obj.coins"))
    }

    @Test fun `antipoison stops when the coins run out`() {
        val f = fixture()
        f.give("obj.limpwurt_root")
        f.give("obj.limpwurt_root")
        f.give("obj.cadavaberries")
        f.give("obj.cadavaberries")
        f.give("obj.coins", 7)
        assertEquals(1, f.brew(ApothecaryPotion.Antipoison, Int.MAX_VALUE))
        assertEquals(1, f.count("obj.4doseantipoison"))
        assertEquals(1, f.count("obj.limpwurt_root"))
        assertEquals(1, f.count("obj.cadavaberries"))
        assertEquals(2, f.count("obj.coins"))
    }

    @Test fun `energy potions take two roots and are free`() {
        val f = fixture()
        repeat(5) { f.give("obj.limpwurt_root") }
        repeat(3) { f.give("obj.chocolate_dust") }
        f.give("obj.coins", 10)
        assertEquals(2, f.brew(ApothecaryPotion.Energy, Int.MAX_VALUE))
        assertEquals(2, f.count("obj.4dose1energy"))
        assertEquals(1, f.count("obj.limpwurt_root"))
        assertEquals(1, f.count("obj.chocolate_dust"))
        assertEquals(10, f.count("obj.coins"))
    }

    @Test fun `nothing is taken without every ingredient`() {
        val f = fixture()
        f.give("obj.limpwurt_root")
        f.give("obj.coins", 5)
        assertEquals(0, f.brew(ApothecaryPotion.Strength, 1))
        assertEquals(1, f.count("obj.limpwurt_root"))
        assertEquals(5, f.count("obj.coins"))
        assertEquals(0, f.count("obj.strength4"))
        assertTrue(f.output().contains("You don't have the ingredients"), f.output())
    }

    @Test fun `a full inventory still brews because the ingredients free their slots`() {
        val f = fixture()
        f.give("obj.coins", 5)
        f.give("obj.limpwurt_root")
        f.give("obj.red_spiders_eggs")
        while (f.player.inv.indices.any { f.player.inv[it] == null }) f.give("obj.bones")
        assertEquals(1, f.brew(ApothecaryPotion.Strength, 1))
        assertEquals(1, f.count("obj.strength4"))
        assertEquals(0, f.count("obj.coins"))
        assertEquals(25, f.count("obj.bones"))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
