package org.rsmod.content.quest.area.lumbridge

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
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SheepShearerInteractionTest {
    private fun fixture() = QuestTalkFixture(CoordGrid(3190, 3272, 0)) { SheepShearer(it) }

    private fun QuestTalkFixture.thing() = Npc(THE_THING, player.coords.translateZ(1))

    private fun QuestTalkFixture.talkToTheThing() =
        interact { assertTrue(events.publish(it, NpcEvents.Op3(thing()))) }

    private fun QuestTalkFixture.shearTheThing() =
        interact { assertTrue(events.publish(it, NpcEvents.Op1(thing()))) }

    private fun QuestTalkFixture.useShearsOnTheThing() {
        val npc = thing()
        val shears = checkNotNull(ServerCacheManager.getItem("obj.shears".asRSCM()))
        val slot = (0 until player.inv.size).first { player.inv[it]?.id == shears.id }
        interact { assertTrue(events.publish(it, NpcUEvents.Op(npc, slot, shears, npc.visType))) }
    }

    @Test fun `accepting starts the quest at 1 on varp 179 and hands over shears`() {
        val f = fixture()
        f.talk("npc.fred_the_farmer", 1, 1)
        assertEquals(1, f.player.vars["varp.sheep"])
        assertEquals(1, f.count("obj.shears"))
        assertTrue(f.output().contains("This icon denotes a Spinning Wheel on the world map."))
    }

    @Test fun `declining leaves the quest unstarted`() {
        val f = fixture()
        f.talk("npc.fred_the_farmer", 1, 2)
        assertEquals(0, f.player.vars["varp.sheep"])
        assertEquals(0, f.count("obj.shears"))
    }

    @Test fun `partial hand-ins add the balls to the stage and the last ball completes`() {
        val f = fixture()
        f.talk("npc.fred_the_farmer", 1, 1)
        repeat(5) { f.give("obj.ball_of_wool") }
        f.talk("npc.fred_the_farmer")
        assertEquals(6, f.player.vars["varp.sheep"])
        assertEquals(0, f.count("obj.ball_of_wool"))
        assertTrue(f.output().contains("You give Fred 5 balls of wool."), f.output())
        assertTrue(f.output().contains("I need 15 more before I can pay you."), f.output())

        repeat(20) { f.give("obj.ball_of_wool") }
        f.talk("npc.fred_the_farmer")
        assertEquals(21, f.player.vars["varp.sheep"])
        assertEquals(5, f.count("obj.ball_of_wool"))
        assertEquals(60, f.count("obj.coins"))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(150, f.player.statMap.getXP("stat.crafting"))
        assertFalse(f.output().contains("You give Fred"), f.output())
        assertTrue(f.output().contains("That's the last of them."), f.output())

        f.talk("npc.fred_the_farmer", 2)
        assertEquals(60, f.count("obj.coins"))
        assertEquals(5, f.count("obj.ball_of_wool"))
    }

    @Test fun `twenty balls on the first talk complete the quest before The Thing and the prompt`() {
        val f = fixture()
        repeat(21) { f.give("obj.ball_of_wool") }
        f.talk("npc.fred_the_farmer", 1)
        assertEquals(21, f.player.vars["varp.sheep"])
        assertEquals(1, f.count("obj.ball_of_wool"))
        assertEquals(60, f.count("obj.coins"))
        assertEquals(1, f.player.vars["varp.qp"])
        assertTrue(f.output().contains("I actually have 20 balls of wool already on me."))
        assertFalse(f.output().contains("What do you mean, The Thing?"), f.output())
        assertFalse(f.output().contains("You give Fred"), f.output())
        assertFalse(f.output().contains("That's the last of them."), f.output())
    }

    @Test fun `a full coin stack keeps the last wool and leaves the quest open`() {
        val f = fixture()
        f.talk("npc.fred_the_farmer", 1, 1)
        f.give("obj.coins", Int.MAX_VALUE)
        repeat(20) { f.give("obj.ball_of_wool") }
        f.talk("npc.fred_the_farmer")
        assertEquals(1, f.player.vars["varp.sheep"])
        assertEquals(20, f.count("obj.ball_of_wool"))
        assertEquals(Int.MAX_VALUE, f.count("obj.coins"))
        assertEquals(0, f.player.vars["varp.qp"])
        assertTrue(f.output().contains("You don't have enough room for the reward."), f.output())
    }

    @Test fun `a full coin stack keeps the wool on the first talk too`() {
        val f = fixture()
        f.give("obj.coins", Int.MAX_VALUE)
        repeat(20) { f.give("obj.ball_of_wool") }
        f.talk("npc.fred_the_farmer", 1)
        assertEquals(0, f.player.vars["varp.sheep"])
        assertEquals(20, f.count("obj.ball_of_wool"))
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test fun `talking to The Thing is the player's line`() {
        val f = fixture()
        f.talkToTheThing()
        assertTrue(f.output().contains("That's a sheep...I think. I can't talk to sheep."), f.output())
    }

    @Test fun `the Thing option after the quest needs a shearing attempt`() {
        val f = fixture()
        repeat(20) { f.give("obj.ball_of_wool") }
        f.talk("npc.fred_the_farmer", 1)
        assertEquals(21, f.player.vars["varp.sheep"])

        f.talk("npc.fred_the_farmer", 2)
        assertTrue(f.output().contains("How can you be lost?"), f.output())

        f.give("obj.shears")
        f.useShearsOnTheThing()
        assertTrue(f.output().contains("The... whatever it is... manages to get away from you!"))
        assertEquals(1, f.count("obj.shears"))

        f.talk("npc.fred_the_farmer", 3)
        assertTrue(f.output().contains("Have you been out in the sun too long?"), f.output())
    }

    @Test fun `the Thing option during the quest after a shear attempt`() {
        val f = fixture()
        f.talk("npc.fred_the_farmer", 1, 1)
        f.shearTheThing()
        f.talk("npc.fred_the_farmer", 2)
        assertTrue(f.output().contains("Penguins disguised as a sheep."), f.output())
        assertEquals(1, f.player.vars["varp.sheep"])
    }

    companion object {
        private const val THE_THING = "npc.sheep_shearer_the_thing"

        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
