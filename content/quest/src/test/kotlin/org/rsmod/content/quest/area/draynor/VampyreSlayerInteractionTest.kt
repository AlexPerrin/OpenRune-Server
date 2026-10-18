package org.rsmod.content.quest.area.draynor

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class VampyreSlayerInteractionTest {
    private fun fixture() = QuestTalkFixture(CoordGrid(3098, 3268, 0)) { VampyreSlayer(it) }

    private val QuestTalkFixture.stage get() = player.vars["varp.vampire"]

    @Test fun `accepting Morgan's request sets varp 178 to 1`() {
        val f = fixture()
        f.talk(MORGAN, 1, 2)
        assertEquals(1, f.stage)
    }

    @Test fun `declining leaves the quest unstarted`() {
        val f = fixture()
        f.talk(MORGAN, 2)
        assertEquals(0, f.stage)
    }

    @Test fun `Harlow wants a beer before he hands over one stake`() {
        val f = fixture()
        f.talk(MORGAN, 1, 2)
        f.talk(HARLOW, 1)
        assertEquals(1, f.stage)
        f.talk(HARLOW, 1)
        assertEquals(1, f.stage)
        assertTrue(f.output().contains("Cheersh, matey..."), f.output())

        f.give("obj.beer")
        f.give("obj.beer")
        f.talk(HARLOW, 1, 4)
        assertEquals(2, f.stage)
        assertEquals(1, f.count("obj.beer"))
        assertEquals(1, f.count("obj.stake"))
        assertEquals(1, f.player.vars["varbit.harlow_given_stake"])

        f.talk(HARLOW, 4)
        assertEquals(1, f.count("obj.stake"))
    }

    @Test fun `a lost stake is replaced`() {
        val f = fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.vampyre_slayer_progress", 2)
        f.talk(HARLOW, 4)
        assertEquals(1, f.count("obj.stake"))
        assertTrue(f.output().contains("I lost that stake"), f.output())
    }

    @Test fun `Morgan points to the garlic once the stake is held`() {
        val f = fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.vampyre_slayer_progress", 2)
        f.talk(MORGAN, 2)
        assertTrue(f.output().contains("cupboard upstairs"), f.output())
    }

    companion object {
        const val MORGAN = "npc.morgan"
        const val HARLOW = "npc.dr_harlow"

        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
