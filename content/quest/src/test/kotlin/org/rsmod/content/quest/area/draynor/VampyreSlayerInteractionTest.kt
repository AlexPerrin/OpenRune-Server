package org.rsmod.content.quest.area.draynor

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
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
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

    @Test fun `players under combat level 20 are warned before the start prompt`() {
        val low = fixture()
        low.player.appearance.combatLevel = 19
        low.talk(MORGAN, 2)
        assertTrue(low.output().contains(LOW_COMBAT), low.output())

        val high = fixture()
        high.player.appearance.combatLevel = 20
        high.talk(MORGAN, 2)
        assertFalse(high.output().contains(LOW_COMBAT), high.output())
    }

    @Test fun `Morgan asks about this vampyre at the start and about Count Draynor later`() {
        val f = fixture()
        f.talk(MORGAN, 1, 2)
        assertTrue(f.output().contains("tell me about this vampyre?"), f.output())
        assertFalse(f.output().contains("tell me about Count Draynor?"), f.output())

        f.talk(MORGAN, 2)
        assertTrue(f.output().contains("tell me about Count Draynor?"), f.output())
    }

    @Test fun `the Count recovers twenty times as fast as ordinary monsters`() {
        val type = checkNotNull(ServerCacheManager.getNpc(COUNT.asRSCM(RSCMType.NPC)))
        assertEquals(5, type.regenRate)
    }

    @Test fun `garlic drains the Count while the player attacks him`() {
        val f = fixture()
        val (count, fight) = fight(f)
        f.give("obj.garlic")
        assertEquals(0, fight.garlicCheck(), "no attack queued yet")
        assertEquals(30, count.attackLvl)

        attack(f, count)
        assertEquals(CountDraynorFight.GARLIC_DAMAGE, fight.garlicCheck())
        assertEquals(20, count.attackLvl)
        assertEquals(15, count.strengthLvl)
        assertEquals(0, count.defenceLvl)
        assertTrue(f.output().contains(CountDraynorFight.WEAKEN_MESSAGE), f.output())

        // The 10 damage is still in flight (hitpoints still full): no second hit.
        assertEquals(0, fight.garlicCheck())
        assertEquals(20, count.attackLvl)
    }

    @Test fun `garlic re-applies as the Count's stats restore`() {
        val f = fixture()
        val (count, fight) = fight(f)
        f.give("obj.garlic")
        attack(f, count)
        fight.garlicCheck()
        count.hitpoints = count.baseHitpointsLvl - 10
        repeat(3) { fight.garlicCheck() }

        count.attackLvl = count.baseAttackLvl
        assertEquals(0, fight.garlicCheck())
        assertEquals(20, count.attackLvl)
        assertEquals(15, count.strengthLvl, "Strength is not full yet")
        assertTrue(f.output().contains(CountDraynorFight.REWEAKEN_MESSAGE), f.output())

        count.defenceLvl = count.baseDefenceLvl
        count.hitpoints = count.baseHitpointsLvl
        assertEquals(CountDraynorFight.GARLIC_DAMAGE, fight.garlicCheck())
        assertEquals(0, count.defenceLvl)
    }

    @Test fun `without garlic or with a manual cast the Count is not drained`() {
        val f = fixture()
        val (count, fight) = fight(f)
        attack(f, count)
        assertEquals(0, fight.garlicCheck())
        assertEquals(30, count.attackLvl)

        f.give("obj.garlic")
        f.player.interaction = null
        assertEquals(0, fight.garlicCheck())
        assertEquals(30, count.defenceLvl)
    }

    @Test fun `garlic adds 1 to the player's damage on the Count`() {
        val f = fixture()
        val (_, fight) = fight(f)
        val plain = hit(f, 3)
        fight.garlicBonus(plain)
        assertEquals(3, plain.damage)

        f.give("obj.garlic")
        val boosted = hit(f, 3)
        fight.garlicBonus(boosted)
        assertEquals(4, boosted.damage)

        val miss = hit(f, 0)
        fight.garlicBonus(miss)
        assertEquals(0, miss.damage)

        val other = hit(f, 3, source = f.player.uid.packed + 1)
        fight.garlicBonus(other)
        assertEquals(3, other.damage)
    }

    @OptIn(InternalApi::class)
    private fun fight(f: QuestTalkFixture): Pair<Npc, CountDraynorFight> {
        val count = Npc(COUNT, f.player.coords.translateZ(1))
        count.slotId = 7
        count.assignUid()
        return count to CountDraynorFight(f.player, count)
    }

    private fun attack(f: QuestTalkFixture, count: Npc) {
        f.player.interaction =
            InteractionNpcOp(InteractionOp.Op2, count, hasOpTrigger = true, hasApTrigger = true)
    }

    private fun hit(f: QuestTalkFixture, damage: Int, source: Int = f.player.uid.packed) =
        HitBuilder(
            type = HitType.Melee,
            damage = damage,
            sourceUid = source,
            sourceSlot = f.player.slotId,
            isFromNpc = false,
            isFromPlayer = true,
            clientDelay = 0,
            righthandType = null,
            secondaryType = null,
            targetHitmark = 0,
            sourceHitmark = 0,
            publicHitmark = null,
            zeroDamageHitmarkLit = null,
            zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = 0,
            sourceMaxDamageThreshold = 0,
        )

    companion object {
        const val MORGAN = "npc.morgan"
        const val HARLOW = "npc.dr_harlow"
        const val COUNT = "npc.count_draynor"
        const val LOW_COMBAT =
            "Before starting this quest, be aware that your combat level is lower than the " +
                "recommended level of 20."

        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
