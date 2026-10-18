package org.rsmod.content.quest.area.goblinvillage

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
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GoblinDiplomacyInteractionTest {
    private fun fixture() =
        QuestTalkFixture(CoordGrid(2957, 3511, 0)) { GoblinDiplomacy() }.apply { randomValue = 1 }

    private val QuestTalkFixture.stage get() = player.vars["varbit.gobdip_main"]

    private fun QuestTalkFixture.start() = talk(GENERAL, 3, 3, 1, 3)

    private fun QuestTalkFixture.searchCrate(loc: String) {
        val type = checkNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)))
        val bound =
            BoundLocInfo(LocInfo(0, player.coords.translateX(1), LocEntity(type.id, 10, 0)), type)
        interact { assertTrue(events.publish(it, LocEvents.Op1(bound, bound, type))) }
    }

    private fun QuestTalkFixture.slot(obj: String): Int =
        (0 until 28).first { player.inv[it]?.id == obj.asRSCM(RSCMType.OBJ) }

    private fun QuestTalkFixture.use(first: String, second: String) {
        val a = checkNotNull(ServerCacheManager.getItem(first.asRSCM(RSCMType.OBJ)))
        val b = checkNotNull(ServerCacheManager.getItem(second.asRSCM(RSCMType.OBJ)))
        val event = HeldUEvents.Type(a, slot(first), b, slot(second))
        interact { assertTrue(events.publish(it, event)) }
    }

    private fun QuestTalkFixture.useOnGeneral(obj: String) {
        val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
        val npcType = checkNotNull(ServerCacheManager.getNpc(GENERAL.asRSCM(RSCMType.NPC)))
        val npc = Npc(GENERAL, player.coords.translateZ(1))
        interact { assertTrue(events.publish(it, NpcUDefaultEvents.OpType(npc, slot(obj), type, npcType))) }
    }

    private fun QuestTalkFixture.complete() {
        start()
        give(ORANGE_MAIL)
        useOnGeneral(ORANGE_MAIL)
        give(BLUE_MAIL)
        useOnGeneral(BLUE_MAIL)
        give(MAIL)
        useOnGeneral(MAIL)
        assertEquals(6, stage)
    }

    @Test fun `accepting the new colour starts the quest at 3`() {
        val f = fixture()
        f.start()
        assertEquals(3, f.stage)
        assertEquals(3, f.player.vars["varp.goblinquest"] and 0x3f)
    }

    @Test fun `each crate gives one goblin mail once`() {
        val f = fixture()
        for (crate in CRATES) f.searchCrate(crate)
        assertEquals(3, f.count(MAIL))
        for (crate in CRATES) f.searchCrate(crate)
        assertEquals(3, f.count(MAIL))
        assertTrue(f.output().contains("but but find nothing of interest"), f.output())
    }

    @Test fun `dyeing and mixing take one of each and give one result`() {
        val f = fixture()
        f.give("obj.reddye")
        f.give("obj.yellowdye")
        f.use("obj.reddye", "obj.yellowdye")
        assertEquals(listOf(0, 0, 1), listOf("obj.reddye", "obj.yellowdye", ORANGE_DYE).map { f.count(it) })
        f.give(MAIL)
        f.give(MAIL)
        f.use(ORANGE_DYE, MAIL)
        assertEquals(listOf(0, 1, 1), listOf(ORANGE_DYE, MAIL, ORANGE_MAIL).map { f.count(it) })
    }

    @Test fun `orange then blue then brown complete the quest once`() {
        val f = fixture()
        f.start()
        f.give(ORANGE_MAIL)
        f.give(BLUE_MAIL)
        f.give(MAIL)
        f.talk(GENERAL, 4)
        assertEquals(4, f.stage)
        assertEquals(0, f.count(ORANGE_MAIL))
        assertEquals(1, f.player.vars["varbit.gobdip_grubfoot_vis"])

        f.useOnGeneral(MAIL)
        assertEquals(4, f.stage)
        assertEquals(1, f.count(MAIL))
        assertTrue(f.output().contains("We tell you get blue armour."), f.output())

        f.useOnGeneral(BLUE_MAIL)
        assertEquals(5, f.stage)
        assertEquals(2, f.player.vars["varbit.gobdip_grubfoot_vis"])

        f.useOnGeneral(MAIL)
        assertEquals(6, f.stage)
        assertEquals(0, f.count(MAIL))
        assertEquals(1, f.count("obj.gold_bar"))
        assertEquals(200, f.player.statMap.getXP("stat.crafting"))
    }

    @Test fun `the fat opener can end the conversation`() {
        val f = fixture().apply { randomValue = 0 }
        f.talk(GENERAL, 3)
        assertTrue(f.output().contains("I'll leave you to it."), f.output())
        assertEquals(0, f.stage)
    }

    @Test fun `asking why swaps in the new century question until leaving`() {
        val f = fixture()
        f.talk(GENERAL, 1, 1, 1, 4)
        val output = f.output()
        val answers = Regex("\\|Shut up! Big High War God would hate you!").findAll(output)
        assertEquals(2, answers.count(), output)
        assertTrue(output.contains("I'll leave you to it."), output)
        f.talk(GENERAL, 1, 4)
        assertFalse(f.output().contains("Big High War God would hate you!"), f.output())
    }

    @Test fun `generals after the quest have the fifth dialogue`() {
        val f = fixture()
        f.complete()
        f.randomValue = 4
        f.talk(GENERAL)
        assertTrue(f.output().contains("Yep, we bored now."), f.output())
    }

    @Test fun `grubfoot argues about colours before the quest and chats after it`() {
        val f = fixture()
        f.talk(GRUBFOOT)
        assertTrue(f.output().contains("Grubfoot wear red armour!"), f.output())
        f.complete()
        f.talk(GRUBFOOT)
        assertTrue(f.output().contains("Me not allowed to talk."), f.output())
        assertFalse(f.output().contains("Grubfoot wear red armour!"), f.output())
    }

    @Test fun `every dye turns one goblin mail into its colour`() {
        for ((dye, mail) in DYED_MAIL) {
            val f = fixture()
            f.give(dye)
            f.give(MAIL)
            f.give(MAIL)
            f.use(dye, MAIL)
            assertEquals(listOf(0, 1, 1), listOf(dye, MAIL, mail).map { f.count(it) }, dye)
        }
    }

    companion object {
        const val GRUBFOOT = "npc.catwalk_goblin"
        val DYED_MAIL =
            listOf(
                "obj.reddye" to "obj.goblin_armour_red",
                "obj.yellowdye" to "obj.goblin_armour_yellow",
                "obj.bluedye" to BLUE_MAIL,
                ORANGE_DYE to ORANGE_MAIL,
                "obj.greendye" to "obj.goblin_armour_green",
                "obj.purpledye" to "obj.goblin_armour_purple",
                "obj.handsand_pink_dye" to "obj.goblin_armour_pink",
                "obj.golem_ink" to "obj.goblin_armour_black",
            )
        const val GENERAL = "npc.general_bentnoze_red"
        const val MAIL = "obj.goblin_armour"
        const val ORANGE_MAIL = "obj.goblin_armour_orange"
        const val BLUE_MAIL = "obj.goblin_armour_darkblue"
        const val ORANGE_DYE = "obj.orangedye"
        val CRATES =
            listOf(
                "loc.goblin_outpost_large_crate_armour1",
                "loc.goblin_outpost_large_crate_armour2",
                "loc.goblin_outpost_large_crate_armour3",
            )

        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
