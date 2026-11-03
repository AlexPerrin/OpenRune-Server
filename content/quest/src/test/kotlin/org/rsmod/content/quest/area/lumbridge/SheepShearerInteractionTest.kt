package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SheepShearerInteractionTest {
    @Test fun `twenty balls held when accepting complete the quest once`() {
        val f = Fixture()
        f.give(BALL, 20)
        f.talk()
        f.finish(listOf(1, 1))
        f.assertComplete(ballsLeft = 0)
        f.talk()
        f.finish(listOf(1))
        f.assertComplete(ballsLeft = 0)
    }

    @Test fun `a partial hand-in takes the balls held and the last takes only the remaining`() {
        val f = Fixture(stage = 1)
        f.give(BALL, 5)
        f.talk()
        f.finish()
        assertEquals(0, f.player.inv.count(BALL))
        assertEquals(6, f.player.vars["varp.sheep"])
        assertEquals(0, f.player.inv.count(COINS))

        f.give(BALL, 20)
        f.talk()
        f.finish()
        f.assertComplete(ballsLeft = 5)
    }

    @Test fun `coins at the stack limit keep the balls and the stage`() {
        val f = Fixture(stage = 20)
        f.player.inv[0] = InvObj(COINS, Int.MAX_VALUE)
        f.give(BALL, 1)
        f.talk()
        f.finish()
        assertEquals(1, f.player.inv.count(BALL))
        assertEquals(Int.MAX_VALUE, f.player.inv.count(COINS))
        assertEquals(20, f.player.vars["varp.sheep"])
    }

    @Test fun `a full inventory still pays for the last ball`() {
        val f = Fixture(stage = 20)
        f.give(BALL, 1)
        f.give(WOOL, 27)
        f.talk()
        f.finish()
        f.assertComplete(ballsLeft = 0)
        assertEquals(27, f.player.inv.count(WOOL))
    }

    @Test fun `balls gone before the hand-over box continues take nothing`() {
        val f = Fixture()
        f.give(BALL, 20)
        f.onBox = { f.player.inv[0] = null }
        f.talk()
        f.finish(listOf(1, 1))
        assertEquals(19, f.player.inv.count(BALL))
        assertEquals(0, f.player.inv.count(COINS))
        assertEquals(1, f.player.vars["varp.sheep"])
    }

    @Test fun `fred gives one set of shears and drops it when the inventory is full`() {
        val f = Fixture()
        f.give(WOOL, 28)
        f.talk()
        f.finish(listOf(1, 1))
        assertEquals(0, f.player.inv.count(SHEARS))
        assertEquals(1, f.objsOnFloor(SHEARS))
        assertEquals(28, f.player.inv.count(WOOL))

        val g = Fixture()
        g.talk()
        g.finish(listOf(1, 1))
        assertEquals(1, g.player.inv.count(SHEARS))
        g.talk()
        g.finish(listOf(1))
        assertEquals(1, g.player.inv.count(SHEARS))
    }

    private class Fixture(stage: Int = 0) {
        private val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("sheep-shearer-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getNpcInteractions = { NpcInteractions(events) },
        )
        var onBox: () -> Unit = {}

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 791L
            observerUUID = 791L
            slotId = 1
            assignUid()
            coords = CoordGrid(3190, 3273, 0)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
        }

        private val objRepo = ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap()))
        val script = SheepShearer(objRepo)

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            VarPlayerIntMapSetter.set(player, "varbit.sheep_shearer_progress", stage)
        }

        private fun access() = ProtectedAccess(player, coroutine, context)

        fun give(obj: String, count: Int) {
            repeat(count) {
                val slot = (0 until 28).first { player.inv[it] == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun objsOnFloor(obj: String): Int =
            objRepo.findAll(player.coords).filter { it.type == obj.asRSCM(RSCMType.OBJ) }.sumOf { it.count }

        fun talk() {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val npc = Npc("npc.fred_the_farmer", player.coords.translateZ(1))
            val block: suspend () -> Unit = { assertTrue(events.publish(access(), NpcEvents.Op1(npc))) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        fun finish(options: List<Int> = emptyList()) {
            val selections = options.iterator()
            repeat(150) {
                if (coroutine.isIdle) return
                advance(selections)
            }
            fail<Unit>("Interaction did not finish: ${client.messages.joinToString("\n")}")
        }

        private fun advance(options: Iterator<Int>) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent = listOf("chat_left", "chat_right", "messagebox", "objectbox", "chatmenu")
                    .firstOrNull { player.ui.containsModal("interface.$it") }
                    ?: error("Unknown dialogue: ${client.messages.joinToString("\n")}")
                val input = when (parent) {
                    "chatmenu" ->
                        ResumePauseButtonInput("component.chatmenu:options", if (options.hasNext()) options.next() else 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    else -> ResumePauseButtonInput("component.$parent:continue", -1)
                }
                if (parent == "messagebox") onBox()
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            result?.getOrThrow()
        }

        fun assertComplete(ballsLeft: Int) {
            assertEquals(21, player.vars["varp.sheep"])
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(150, player.statMap.getXP("stat.crafting"))
            assertEquals(60, player.inv.count(COINS))
            assertEquals(ballsLeft, player.inv.count(BALL))
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val BALL = "obj.ball_of_wool"
        private const val WOOL = "obj.wool"
        private const val COINS = "obj.coins"
        private const val SHEARS = "obj.shears"
        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
