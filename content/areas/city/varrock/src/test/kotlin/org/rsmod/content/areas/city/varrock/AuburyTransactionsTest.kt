package org.rsmod.content.areas.city.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
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
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.shops.Shops
import org.rsmod.content.areas.city.varrock.npcs.Aubury
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.rmNotes
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
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
class AuburyTransactionsTest {
    @Test fun `delivering the package takes exactly one and gives exactly one set of notes`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_AGREED)
        f.player.inv[0] = InvObj(Package, 1)
        f.talk(listOf(2, 2))
        assertEquals(0, f.count(Package))
        assertEquals(1, f.count(Notes))
        assertTrue(f.player.rmNotes)
        assertEquals(RuneMysteriesQuest.STAGE_PACKAGE_READ, f.stage())
        f.talk(listOf(2, 2))
        assertEquals(1, f.count(Notes))
    }

    @Test fun `without the package nothing advances`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_AGREED)
        f.talk(listOf(2))
        assertEquals(RuneMysteriesQuest.STAGE_AGREED, f.stage())
        assertEquals(0, f.count(Notes))
    }

    @Test fun `a full inventory keeps the package swap and the notes wait for space`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_AGREED)
        f.fillInventory()
        f.player.inv[3] = InvObj(Package, 1)
        f.talk(listOf(2, 2))
        assertEquals(0, f.count(Package))
        assertEquals(1, f.count(Notes))
        val g = Fixture(RuneMysteriesQuest.STAGE_PACKAGE_READ)
        g.fillInventory()
        g.talk(listOf(2))
        assertEquals(0, g.count(Notes))
        assertFalse(g.player.rmNotes)
        g.player.inv[27] = null
        g.talk(listOf(2))
        assertEquals(1, g.count(Notes))
        assertTrue(g.player.rmNotes)
        g.talk(listOf(2))
        assertEquals(1, g.count(Notes))
    }

    @Test fun `replacement notes remove the banked copy`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_PACKAGE_READ)
        f.player.rmNotes = true
        f.access().bank[0] = InvObj(Notes, 1)
        f.talk()
        assertEquals(1, f.player.inv.count(Notes))
        assertEquals(0, f.access().bank.count(Notes))
        f.talk(listOf(2))
        assertEquals(1, f.count(Notes))
    }
    private class Fixture(stage: Int) {
        private val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("aubury-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 794L
                slotId = 1
                assignUid()
                coords = CoordGrid(3253, 3401, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
            }

        private val quest = RuneMysteriesQuest()

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
            with(Aubury(Shops(events), quest, WorldRepository(ZoneUpdateMap()))) { scripts.startup() }
            quest.quest.setQuestStage(access(), stage)
        }

        fun stage() = quest.quest.getQuestStage(player)

        fun access() = ProtectedAccess(player, coroutine, context)

        fun count(obj: String) = player.inv.count(obj) + access().bank.count(obj)

        fun fillInventory() {
            for (slot in 0 until 28) player.inv[slot] = InvObj("obj.bronze_dagger", 1)
        }

        fun talk(options: List<Int> = emptyList()) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val npc = Npc("npc.aubury", player.coords.translateZ(1))
            val body: suspend () -> Unit = {
                assertTrue(events.publish(access(), NpcEvents.Op1(npc)))
            }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            result?.getOrThrow()
            val selections = options.iterator()
            repeat(300) {
                if (coroutine.isIdle) return
                advance(selections)
            }
            fail<Unit>("Interaction did not finish: ${client.messages}")
        }

        private fun advance(options: Iterator<Int>) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val input =
                    when {
                        player.ui.containsModal("interface.chatmenu") ->
                            ResumePauseButtonInput(
                                "component.chatmenu:options",
                                if (options.hasNext()) options.next() else 1,
                            )
                        player.ui.containsModal("interface.objectbox") ->
                            ResumePauseButtonInput("component.objectbox:universe", -1)
                        else -> {
                            val parent =
                                listOf("chat_left", "chat_right", "messagebox").firstOrNull {
                                    player.ui.containsModal("interface.$it")
                                } ?: error("Unknown dialogue: ${client.messages}")
                            ResumePauseButtonInput("component.$parent:continue", -1)
                        }
                    }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            result?.getOrThrow()
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()

        override fun write(message: Any) {
            messages += message
        }

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val Package = RuneMysteriesQuest.RESEARCH_PACKAGE
        private const val Notes = RuneMysteriesQuest.RESEARCH_NOTES
        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in
                listOf(
                    "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                    "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
                )) {
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
