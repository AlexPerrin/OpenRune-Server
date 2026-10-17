package org.rsmod.content.interfaces.diary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.table.AchievementDiariesRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.diary.DiaryTasks.Area
import org.rsmod.content.interfaces.diary.DiaryTasks.Tier
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class DiaryTasksTest {
    @Test
    fun GameTestState.`a task counts once and sets the client varbits`() = runGameTest {
        assertTrue(DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 5))
        assertFalse(DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 5))
        DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 4)
        assertEquals(2, player.vars["varbit.lumbridge_easy_count"])
        assertEquals(1, player.vars["varbit.started_lumbridge_diary"])
        assertEquals(0, player.vars["varbit.lumbridge_diary_easy_complete"])
        assertEquals(setOf(4, 5), DiaryTasks.doneTasks(player, Area.Lumbridge, Tier.Easy))
    }

    @Test
    fun GameTestState.`the last task of a tier completes it`() = runGameTest {
        for (task in 1..5) DiaryTasks.complete(player, Area.Varrock, Tier.Elite, task)
        assertEquals(5, player.vars["varbit.varrock_elite_count"])
        assertEquals(1, player.vars["varbit.varrock_diary_elite_complete"])
    }

    @Test
    fun GameTestState.`oak logs count only when cut and burned in Lumbridge`() = runGameTest {
        val lumbridge = CoordGrid(3200, 3240, 0)
        DiaryHooks.logsBurned(player, "obj.oak_logs", lumbridge)
        assertFalse(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 7))
        DiaryHooks.logsCut(player, "obj.oak_logs", CoordGrid(3000, 3400, 0))
        DiaryHooks.logsBurned(player, "obj.oak_logs", lumbridge)
        assertFalse(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 7))
        DiaryHooks.logsCut(player, "obj.oak_logs", lumbridge)
        DiaryHooks.logsBurned(player, "obj.oak_logs", lumbridge)
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 7))
    }

    @Test
    fun GameTestState.`done tasks are struck in the journal`() = runGameTest {
        val diary = AchievementDiariesRow.all().first { it.area == 6 }
        val lines = AchievementDiaryScript().journalLines(diary) { tier, task -> tier == 0 && task == 5 }
        assertTrue(lines.contains("<str>Learn your age from Hans in Lumbridge.</str>"))
        assertTrue(lines.contains("Have Sedridor teleport you to the Essence Mine."))
    }

    @Test
    fun GameTestState.`skilling hooks check the item and the area`() = runGameTest {
        DiaryHooks.fishCaught(player, "obj.raw_anchovies", CoordGrid(3000, 3400, 0))
        assertFalse(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 9))
        DiaryHooks.fishCaught(player, "obj.raw_anchovies", CoordGrid(3267, 3148, 0))
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 9))
        DiaryHooks.fishCaught(player, "obj.raw_trout", CoordGrid(3104, 3430, 0))
        assertTrue(DiaryTasks.isDone(player, Area.Varrock, Tier.Easy, 13))
        DiaryHooks.oreMined(player, "obj.iron_ore", CoordGrid(3285, 3368, 0))
        assertTrue(DiaryTasks.isDone(player, Area.Varrock, Tier.Easy, 3))
        assertFalse(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 11))
        DiaryHooks.logsChopped(player, "obj.willow_logs", CoordGrid(3087, 3235, 0))
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Medium, 8))
        DiaryHooks.pickpocketed(player, "npc.man", "Man", CoordGrid(3222, 3218, 0))
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 6))
        DiaryHooks.npcKilled(player, "Zombie", CoordGrid(3124, 9662, 0))
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 8))
        DiaryHooks.npcKilled(player, "Cave bug", CoordGrid(3160, 9580, 0))
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 2))
        DiaryHooks.slayerTaskAssigned(player, 3)
        assertTrue(DiaryTasks.isDone(player, Area.Varrock, Tier.Medium, 9))
        DiaryHooks.planksMade(player, "obj.mahogany_plank", 19)
        assertFalse(DiaryTasks.isDone(player, Area.Varrock, Tier.Medium, 10))
        DiaryHooks.planksMade(player, "obj.mahogany_plank", 20)
        assertTrue(DiaryTasks.isDone(player, Area.Varrock, Tier.Medium, 10))
    }

    @Test
    fun GameTestState.`a bowl counts only when spun and fired in Barbarian Village`() = runGameTest {
        val village = CoordGrid(3086, 3409, 0)
        DiaryHooks.crafted(player, "obj.bowl_empty", village)
        assertFalse(DiaryTasks.isDone(player, Area.Varrock, Tier.Easy, 10))
        DiaryHooks.crafted(player, "obj.bowl_unfired", CoordGrid(3200, 3240, 0))
        DiaryHooks.crafted(player, "obj.bowl_empty", village)
        assertFalse(DiaryTasks.isDone(player, Area.Varrock, Tier.Easy, 10))
        DiaryHooks.crafted(player, "obj.bowl_unfired", village)
        DiaryHooks.crafted(player, "obj.bowl_empty", village)
        assertTrue(DiaryTasks.isDone(player, Area.Varrock, Tier.Easy, 10))
    }

    @Test
    fun GameTestState.`the four Stronghold emotes and the Waka canoe tasks`() = runGameTest {
        for (seq in listOf("seq.emote_panic_flap", "seq.emote_slap_head_loop", "seq.emote_lightbulb")) {
            DiaryHooks.emotePerformed(player, seq)
        }
        DiaryHooks.emotePerformed(player, "seq.emote_wave")
        assertFalse(DiaryTasks.isDone(player, Area.Varrock, Tier.Medium, 5))
        DiaryHooks.emotePerformed(player, "seq.emote_stampfeet")
        assertTrue(DiaryTasks.isDone(player, Area.Varrock, Tier.Medium, 5))
        DiaryHooks.canoeTravelled(player, waka = false, from = "Lumbridge", to = "Edgeville")
        assertFalse(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Hard, 4))
        DiaryHooks.canoeTravelled(player, waka = true, from = "Lumbridge", to = "Edgeville")
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Hard, 4))
        DiaryHooks.canoeShaped(player, waka = true, station = "Edgeville")
        assertTrue(DiaryTasks.isDone(player, Area.Varrock, Tier.Hard, 3))
    }
}
