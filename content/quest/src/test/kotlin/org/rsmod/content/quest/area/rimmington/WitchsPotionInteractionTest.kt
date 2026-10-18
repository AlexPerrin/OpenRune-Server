package org.rsmod.content.quest.area.rimmington

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class WitchsPotionInteractionTest {
    private fun fixture() = QuestTalkFixture(CoordGrid(2967, 3206, 0)) { WitchsPotion() }

    private val ingredients =
        listOf("obj.eye_of_newt", "obj.rats_tail", "obj.onion", "obj.burnt_meat")

    @Test fun `accepting sets varp 67 to 1`() {
        val f = fixture()
        f.talk("npc.hetty", 1, 1)
        assertEquals(1, f.player.vars["varp.hetty"])
    }

    @Test fun `declining and the witch topic leave the quest unstarted`() {
        val f = fixture()
        f.talk("npc.hetty", 1, 2)
        f.talk("npc.hetty", 2, 2)
        assertEquals(0, f.player.vars["varp.hetty"])
    }

    @Test fun `some ingredients get the matching line and nothing is taken`() {
        val f = fixture()
        f.talk("npc.hetty", 1, 1)
        f.give("obj.rats_tail")
        f.give("obj.eye_of_newt")
        f.talk("npc.hetty")
        assertTrue(
            f.output().contains(
                "I have the rat's tail (ewww), I don't have any burnt meat, I don't have an " +
                    "onion, and I have the eye of newt, yum!"
            ),
            f.output(),
        )
        assertEquals(1, f.player.vars["varp.hetty"])
        assertEquals(1, f.count("obj.rats_tail"))
        assertEquals(1, f.count("obj.eye_of_newt"))
    }

    @Test fun `the hand-in takes one of each and sets 2`() {
        val f = fixture()
        f.talk("npc.hetty", 1, 1)
        ingredients.forEach { f.give(it) }
        f.give("obj.onion")
        f.talk("npc.hetty")
        assertEquals(2, f.player.vars["varp.hetty"])
        assertEquals(listOf(0, 0, 1, 0), ingredients.map { f.count(it) })
        f.talk("npc.hetty")
        assertEquals(2, f.player.vars["varp.hetty"])
        assertEquals(1, f.count("obj.onion"))
    }

    @Test fun `the hand-in is one message box with the whole transcript text`() {
        val f = fixture()
        f.talk("npc.hetty", 1, 1)
        ingredients.forEach { f.give(it) }
        f.talk("npc.hetty")
        assertEquals(1, mesboxes(f, "You pass the ingredients"), f.output())
        assertEquals(1, mesboxes(f, "The cauldron bubbles mysteriously."), f.output())
        assertTrue(
            mesboxOpens(f).any {
                it.contains("You pass the ingredients") && it.contains("bubbles mysteriously")
            },
            f.output(),
        )
    }

    @Test fun `drinking from the cauldron shows a message box and completes the quest`() {
        val f = fixture()
        f.talk("npc.hetty", 1, 1)
        ingredients.forEach { f.give(it) }
        f.talk("npc.hetty")
        drink(f)
        assertEquals(1, mesboxes(f, "You drink from the cauldron."), f.output())
        assertEquals(3, f.player.vars["varp.hetty"])
    }

    @Test fun `the cauldron before the hand-in is a miss`() {
        val f = fixture()
        f.talk("npc.hetty", 1, 1)
        drink(f)
        assertTrue(f.output().contains("I think I'll give it a miss for now."), f.output())
        assertEquals(1, f.player.vars["varp.hetty"])
    }

    @Test fun `every wiki rat drops a tail for its killer once the quest has started`() {
        val f = fixture()
        f.player.observerUUID = 791L
        val hook = WitchsPotionRatTail(f.objRepo)
        assertEquals(0, kill(f, hook, "npc.rat"), "not started")
        f.talk("npc.hetty", 1, 1)
        for (rat in WitchsPotionRatTail.RATS) {
            assertTrue(ServerCacheManager.getNpc(rat.asRSCM(RSCMType.NPC)) != null, rat)
            assertEquals(1, kill(f, hook, rat), rat)
        }
        assertEquals(0, kill(f, hook, "npc.giantrat"), "regular giant rat")
        ingredients.forEach { f.give(it) }
        f.talk("npc.hetty")
        assertEquals(1, kill(f, hook, "npc.dungeon_rat"), "ingredients given, not drunk")
        drink(f)
        assertEquals(0, kill(f, hook, "npc.rat"), "complete")
    }

    @Test fun `the ingredient line follows the transcript wording`() {
        assertEquals(
            "I don't have a rat's tail, I have the burnt meat, I have an onion, and I don't " +
                "have an eye of newt.",
            WitchsPotion.ingredientsLine(tail = false, meat = true, onion = true, newt = false),
        )
    }

    private var killTile = CoordGrid(2970, 3210, 0)

    /** Kills [npc] on a fresh tile and returns how many rat's tails landed for the player. */
    private fun kill(f: QuestTalkFixture, hook: WitchsPotionRatTail, npc: String): Int {
        killTile = killTile.translateX(1)
        hook.onKill(NpcDeathKillContext(f.player, Npc(npc, killTile), lootTrackerEventId = 0))
        val tail = "obj.rats_tail".asRSCM(RSCMType.OBJ)
        return f.objRepo.findAll(killTile).count { it.type == tail && it.isPrivate }
    }

    private fun drink(f: QuestTalkFixture) {
        val type = checkNotNull(ServerCacheManager.getObject("loc.hettycauldron".asRSCM(RSCMType.LOC)))
        val loc = BoundLocInfo(LocInfo(0, CoordGrid(2967, 3205, 0), LocEntity(type.id, 10, 0)), type)
        f.interact { assertTrue(f.events.publish(it, LocEvents.Op1(loc, loc, type))) }
    }

    /** The text of every message box shown in the last interaction (its unwrapped chat echo). */
    private fun mesboxOpens(f: QuestTalkFixture): List<String> =
        f.output().lines().filter { it.startsWith("MessageGame(type=115,") }

    private fun mesboxes(f: QuestTalkFixture, text: String): Int =
        mesboxOpens(f).count { it.contains(text) }

    companion object {
        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
