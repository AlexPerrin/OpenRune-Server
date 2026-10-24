package org.rsmod.content.quest.area.portsarim

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
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.QuestTalkFixture
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasure
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureSmuggling
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PiratesTreasureInteractionTest {
    private fun quest() =
        QuestTalkFixture(CoordGrid(3053, 3250, 0)) {
            val (npcs, locs) = repositories()
            PiratesTreasure(npcs, locs, AiPlayerInteractions(EventBus(), PlayerList()))
        }

    private fun smuggling() =
        QuestTalkFixture(CoordGrid(2938, 3150, 0)) {
            val (npcs, locs) = repositories()
            PiratesTreasure(npcs, locs, AiPlayerInteractions(EventBus(), PlayerList()))
            PiratesTreasureSmuggling(Shops(EventBus()))
        }

    private val QuestTalkFixture.stage get() = player.vars["varbit.pirates_treasure_progress"]

    private fun QuestTalkFixture.slot(obj: String): Int =
        (0 until 28).first { player.inv[it]?.id == obj.asRSCM(RSCMType.OBJ) }

    private fun locType(loc: String) =
        checkNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)))

    private fun bound(loc: String, coords: CoordGrid): BoundLocInfo {
        val type = locType(loc)
        return BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, 0)), type)
    }

    private fun QuestTalkFixture.opLoc(loc: String, vararg options: Int) {
        val target = bound(loc, player.coords.translateX(1))
        interact(*options) { assertTrue(events.publish(it, LocEvents.Op1(target, target, locType(loc)))) }
    }

    private fun QuestTalkFixture.opLoc2(loc: String) {
        val target = bound(loc, player.coords.translateX(1))
        interact { assertTrue(events.publish(it, LocEvents.Op2(target, target, locType(loc)))) }
    }

    private fun QuestTalkFixture.useOnLoc(obj: String, loc: String) {
        val target = bound(loc, player.coords.translateX(1))
        val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
        interact {
            assertTrue(events.publish(it, LocUEvents.Op(target, target, locType(loc), objType, slot(obj))))
        }
    }

    private fun QuestTalkFixture.rumOnFrank(vararg options: Int) {
        val obj = checkNotNull(ServerCacheManager.getItem(RUM.asRSCM(RSCMType.OBJ)))
        val npcType = checkNotNull(ServerCacheManager.getNpc(FRANK.asRSCM(RSCMType.NPC)))
        val npc = Npc(FRANK, player.coords.translateZ(1))
        interact(*options) { assertTrue(events.publish(it, NpcUEvents.Op(npc, slot(RUM), obj, npcType))) }
    }

    private fun QuestTalkFixture.openCasket() {
        val slot = slot(CASKET)
        val obj = checkNotNull(player.inv[slot])
        val type = checkNotNull(ServerCacheManager.getItem(obj.id))
        interact { assertTrue(events.publish(it, HeldObjEvents.Op1(slot, obj, type, player.inv))) }
    }

    private fun QuestTalkFixture.setStage(stage: Int) {
        VarPlayerIntMapSetter.set(player, "varbit.pirates_treasure_progress", stage)
    }

    @Test fun `accepting Frank's offer sets varp 71 to 1`() {
        val f = quest()
        f.talk(FRANK, 1, 1)
        assertEquals(1, f.stage)
    }

    @Test fun `declining leaves the quest unstarted`() {
        val f = quest()
        f.talk(FRANK, 1, 2)
        assertEquals(0, f.stage)
    }

    @Test fun `Frank swaps exactly one rum for one key`() {
        val f = quest()
        f.setStage(1)
        f.give(RUM, 2)
        f.talk(FRANK, 1)
        assertEquals(2, f.stage)
        assertEquals(1, f.count(RUM))
        assertEquals(1, f.count(KEY))
    }

    @Test fun `using the rum on Frank works the same way`() {
        val f = quest()
        f.setStage(1)
        f.give(RUM)
        f.rumOnFrank(1)
        assertEquals(2, f.stage)
        assertEquals(0, f.count(RUM))
        assertEquals(1, f.count(KEY))
    }

    @Test fun `Frank replaces a lost key once`() {
        val f = quest()
        f.setStage(2)
        f.talk(FRANK, 2)
        assertEquals(1, f.count(KEY))
        f.talk(FRANK, 2)
        assertEquals(1, f.count(KEY))
    }

    @Test fun `the key opens the chest and turns into the message`() {
        val f = quest()
        f.setStage(2)
        f.give(KEY)
        f.useOnLoc(KEY, "loc.piratechest")
        assertEquals(0, f.count(KEY))
        assertEquals(1, f.count(MESSAGE))
    }

    @Test fun `the casket opens into its three rewards and is gone`() {
        val f = quest()
        f.give(CASKET)
        f.openCasket()
        assertEquals(0, f.count(CASKET))
        assertEquals(450, f.count("obj.coins"))
        assertEquals(1, f.count("obj.gold_ring"))
        assertEquals(1, f.count("obj.emerald"))
    }

    @Test fun `a full inventory keeps the casket closed`() {
        val f = quest()
        f.give(CASKET)
        repeat(27) { f.give("obj.bronze_dagger") }
        f.openCasket()
        assertEquals(1, f.count(CASKET))
        assertEquals(0, f.count("obj.coins"))
        assertTrue(f.output().contains("You don't have enough inventory space."), f.output())
    }

    @Test fun `the rum is stashed, shipped with ten bananas and found at Wydin's`() {
        val f = smuggling()
        f.setStage(1)
        f.talk(LUTHAS, 1)
        f.give(RUM)
        repeat(12) { f.give(BANANA) }
        f.useOnLoc(RUM, CRATE)
        assertEquals(0, f.count(RUM))
        f.useOnLoc(BANANA, CRATE)
        f.opLoc2(CRATE)
        assertEquals(2, f.count(BANANA), f.output())
        assertTrue(f.output().contains("You pack all your bananas into the crate."), f.output())
        f.opLoc2(CRATE)
        assertEquals(2, f.count(BANANA))
        f.talk(LUTHAS, 2)
        assertEquals(30, f.count("obj.coins"))
        f.opLoc(GROCERY_CRATE, 2)
        assertEquals(1, f.count(RUM))
        f.opLoc(GROCERY_CRATE, 2)
        assertEquals(1, f.count(RUM))
    }

    @Test fun `the rum stays out of the crate before the job`() {
        val f = smuggling()
        f.setStage(1)
        f.give(RUM)
        f.useOnLoc(RUM, CRATE)
        assertEquals(1, f.count(RUM))
    }

    private fun repositories(): Pair<NpcRepository, LocRepository> {
        val events = EventBus()
        val clock = MapClock(100)
        val collision = CollisionFlagMap()
        val npcs = NpcList()
        val updates = ZoneUpdateMap()
        val storage = LocZoneStorage()
        val normal = LocRegistryNormal(updates, collision, storage)
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val regions =
            RegionRegistry(
                RegionListSmall(),
                RegionListLarge(),
                RegionListWorldEntity(),
                normal,
                collision,
                storage,
                npcRegistry,
                ControllerRegistry(clock, ControllerList()),
                ZonePlayerActivityBitSet(),
            )
        val locs =
            LocRepository(
                clock,
                LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)),
                regions,
            )
        return NpcRepository(clock, npcRegistry, npcs) to locs
    }

    companion object {
        const val FRANK = "npc.redbeard_frank"
        const val LUTHAS = "npc.luthas"
        const val RUM = "obj.karamja_rum"
        const val KEY = "obj.chest_key"
        const val MESSAGE = "obj.piratemessage"
        const val CASKET = "obj.pirate_casket"
        const val BANANA = "obj.banana"
        const val CRATE = "loc.bananacrate"
        const val GROCERY_CRATE = "loc.grocerycrate"

        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
