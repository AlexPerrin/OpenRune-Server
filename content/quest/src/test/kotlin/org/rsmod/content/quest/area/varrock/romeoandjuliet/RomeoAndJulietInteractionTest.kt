package org.rsmod.content.quest.area.varrock.romeoandjuliet

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RomeoAndJulietInteractionTest {
    private fun fixture(coords: CoordGrid = CoordGrid(3211, 3421, 0)) =
        QuestTalkFixture(coords) { RomeoAndJuliet(it) }

    private fun QuestTalkFixture.stage() = player.vars["varp.rjquest"]

    private fun QuestTalkFixture.setStage(stage: Int) =
        VarPlayerIntMapSetter.set(player, "varbit.romeo_juliet_progress", stage)

    private fun QuestTalkFixture.slotOf(obj: String): Int {
        val id = obj.asRSCM()
        return (0 until player.inv.size).first { player.inv[it]?.id == id }
    }

    private fun QuestTalkFixture.useOn(npc: String, obj: String, vararg options: Int) {
        val target = Npc(npc, player.coords.translateZ(1))
        val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
        val slot = slotOf(obj)
        interact(*options) {
            assertTrue(events.publish(it, NpcUEvents.Op(target, slot, type, target.visType)))
        }
    }

    private fun QuestTalkFixture.heldOp(op: Int, obj: String, vararg options: Int) {
        val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
        val slot = slotOf(obj)
        val held = checkNotNull(player.inv[slot])
        interact(*options) {
            val event =
                when (op) {
                    1 -> HeldObjEvents.Op1(slot, held, type, player.inv)
                    else -> HeldObjEvents.Op2(slot, held, type, player.inv)
                }
            assertTrue(events.publish(it, event))
        }
    }

    @Test fun `the quest walks through the OSRS stages on varp 144`() {
        val f = fixture()
        f.talk("npc.romeo", 1, 1, 3)
        assertEquals(10, f.stage())
        assertTrue(f.output().contains("Blub! Blub...where is my Juliet? Have you seen her?"))

        f.talk("npc.juliet")
        assertEquals(20, f.stage())
        assertEquals(1, f.count("obj.julietmessage"))

        f.talk("npc.romeo", 4)
        assertEquals(30, f.stage())
        assertEquals(0, f.count("obj.julietmessage"))

        f.talk("npc.father_lawrence")
        assertEquals(40, f.stage())

        f.talk("npc.apothecary", 2, 1, 4)
        assertEquals(50, f.stage())
        assertEquals(0, f.count("obj.cadava"))

        f.give("obj.cadavaberries")
        f.talk("npc.apothecary", 2, 1)
        assertEquals(50, f.stage())
        assertEquals(0, f.count("obj.cadavaberries"))
        assertEquals(1, f.count("obj.cadava"))

        f.talk("npc.juliet_multi_visible")
        assertEquals(60, f.stage())
        assertEquals(0, f.count("obj.cadava"))
        assertTrue(f.output().contains("Right...bottoms up!"), f.output())
        assertTrue(f.output().contains("make preparations for her body to be placed in the Crypt"))

        f.talk("npc.romeo")
        assertEquals(100, f.stage())
        assertEquals(5, f.player.vars["varp.qp"])

        f.talk("npc.romeo")
        assertTrue(f.output().contains("I heard Juliet had died. Terrible business."), f.output())
        f.talk("npc.juliet")
        assertTrue(f.output().contains("Now go away before I call my father!"), f.output())
    }

    @Test fun `romeo's opener is one of the six random lines`() {
        val f = fixture()
        f.randomValue = 5
        f.talk("npc.romeo", 2, 2)
        assertTrue(f.output().contains("What is to become of me and my darling Juliet"), f.output())
        assertTrue(f.output().contains("Surely you mean that 'she is the love of your life"))
        assertEquals(0, f.stage())
    }

    @Test fun `romeo's option menus come back after each answer`() {
        val f = fixture()
        f.talk("npc.romeo", 3, 1, 1, 2, 3)
        assertEquals(10, f.stage())
        assertTrue(f.output().contains("she's always a kidder that Juliet!"))
        assertTrue(f.output().contains("Not a thing sorry...."))
    }

    @Test fun `a lost message is replaced and the apothecary hides the quest topic early`() {
        val f = fixture()
        f.talk("npc.romeo", 3, 1, 3)
        f.talk("npc.juliet")
        f.player.inv.indices.forEach { if (f.player.inv[it]?.id != null) f.player.inv[it] = null }
        f.talk("npc.juliet")
        assertEquals(1, f.count("obj.julietmessage"))
        assertEquals(20, f.stage())

        f.talk("npc.apothecary", 2, 4)
        assertEquals(20, f.stage())
    }

    @Test fun `berries in hand on the first apothecary visit make the potion at once`() {
        val f = fixture()
        f.setStage(40)
        f.give("obj.cadavaberries")
        f.talk("npc.apothecary", 2, 1)
        assertEquals(50, f.stage())
        assertEquals(1, f.count("obj.cadava"))
    }

    @Test fun `asking the apothecary for potions opens his potion interface`() {
        val f = fixture()
        f.talk("npc.apothecary", 1)
        assertTrue(f.player.ui.containsModal("interface.apothecary_potions"), f.output())
        assertFalse(f.output().contains("too busy"))
    }

    @Test fun `the apothecary's gossip and hair answers return to his menu`() {
        val f = fixture()
        f.talk("npc.apothecary", 2, 1, 2, 2, 2, 4)
        assertTrue(f.output().contains("all's quiet on the eastern front"), f.output())
        assertTrue(f.output().contains("But can I do something for you?"), f.output())

        f.setStage(100)
        f.talk("npc.apothecary", 2, 2, 2, 5)
        assertTrue(f.output().contains("Life goes on and so does business!"), f.output())
    }

    @Test fun `the free sample is a spot cream half the time and only one at a time`() {
        val f = fixture()
        f.randomValue = 0
        f.talk("npc.apothecary", 2, 3)
        assertEquals(1, f.count("obj.acne_potion"))
        f.talk("npc.apothecary", 2, 3)
        assertEquals(1, f.count("obj.acne_potion"))
        assertTrue(f.output().contains("Only that spot cream."), f.output())

        f.randomValue = 1
        f.talk("npc.apothecary", 2, 3, 2, 4)
        assertEquals(1, f.count("obj.acne_potion"))
        assertTrue(f.output().contains("Sorry, charity is not my strong point. Do you need anything else?"))
    }

    @Test fun `berries used on the apothecary after the quest`() {
        val f = fixture()
        f.setStage(100)
        f.give("obj.cadavaberries")
        f.useOn("npc.apothecary", "obj.cadavaberries")
        assertTrue(f.output().contains("I don't need those any more."), f.output())
        assertEquals(1, f.count("obj.cadavaberries"))
    }

    @Test fun `father lawrence before and after the quest`() {
        val f = fixture()
        f.talk("npc.father_lawrence", 1, 3, 4)
        assertTrue(f.output().contains("I see poor Romeo wandering around the square."))
        assertTrue(f.output().contains("But the Blue Moon in the city is cheap enough."))
        assertEquals(0, f.stage())

        f.setStage(60)
        f.randomValue = 1
        f.talk("npc.father_lawrence")
        assertTrue(f.output().contains("I sing and I drink and I wake up in gutters."), f.output())
        assertTrue(f.output().contains("I need a think I drink."), f.output())
    }

    @Test fun `draul leptoc's menu and the carried-item branches`() {
        val f = fixture()
        f.talk("npc.draul_leptoc", 1)
        assertTrue(f.output().contains("Harsh but fair I think you'll find...now get OUT!"))

        f.setStage(20)
        f.give("obj.julietmessage")
        f.talk("npc.draul_leptoc")
        assertTrue(f.output().contains("Sir Draul notices the message!"), f.output())

        f.setStage(50)
        f.give("obj.cadava")
        f.talk("npc.draul_leptoc")
        assertTrue(f.output().contains("Draul notices the potion!"), f.output())
        assertEquals(1, f.count("obj.cadava"))
    }

    @Test fun `phillipa's full lines`() {
        val f = fixture()
        f.talk("npc.phillipa")
        assertTrue(f.output().contains("in a wet blanket sort of way."))
        f.setStage(30)
        f.talk("npc.phillipa")
        assertTrue(f.output().contains("tears of happiness at his foolish antics!"))
        f.setStage(60)
        f.talk("npc.phillipa")
        assertTrue(f.output().contains("I can't wait to see the look on his face!"))
    }

    @Test fun `a spare message waved at romeo`() {
        val f = fixture()
        f.setStage(30)
        f.give("obj.julietmessage")
        f.useOn("npc.romeo", "obj.julietmessage")
        assertTrue(f.output().contains("Why are you waving that around at me"), f.output())
        assertEquals(1, f.count("obj.julietmessage"))
        assertEquals(30, f.stage())
    }

    @Test fun `the cadava potion can only be drunk at the apothecary's`() {
        val outside = fixture()
        outside.give("obj.cadava")
        outside.heldOp(1, "obj.cadava")
        assertTrue(outside.output().contains("probably best not to drink it."))
        outside.heldOp(2, "obj.cadava")
        assertTrue(outside.output().contains("You dare not drink this outside"))
        assertEquals(1, outside.count("obj.cadava"))

        val shop = fixture(CoordGrid(3195, 3404, 0))
        shop.give("obj.cadava")
        shop.heldOp(2, "obj.cadava", 2)
        assertEquals(1, shop.count("obj.cadava"))
        assertTrue(shop.output().contains("You decide against drinking the potion."), shop.output())
        shop.heldOp(2, "obj.cadava", 1)
        assertEquals(0, shop.count("obj.cadava"))
        assertTrue(shop.output().contains("Blimey... you've been out for hours!"), shop.output())
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
