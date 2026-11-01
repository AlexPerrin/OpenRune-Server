package org.rsmod.content.areas.misc.wizards_tower

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
import org.rsmod.content.areas.misc.wizards_tower.npcs.ArchmageSedridor
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.rmNotes
import org.rsmod.content.quest.area.lumbridge.rmNotesGiven
import org.rsmod.content.quest.area.lumbridge.rmOwedTalisman
import org.rsmod.content.quest.area.lumbridge.rmPackage
import org.rsmod.content.quest.area.lumbridge.rmTalismanGiven
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
class ArchmageSedridorTransactionsTest {
    @Test fun `handing in the talisman takes exactly one and the package follows`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_TALISMAN)
        f.player.inv[0] = InvObj(Talisman, 1)
        f.talk(listOf(1, 1, 1))
        assertEquals(0, f.count(Talisman))
        assertTrue(f.player.rmTalismanGiven)
        assertEquals(RuneMysteriesQuest.STAGE_AGREED, f.stage())
        assertEquals(1, f.count(Package))
        assertTrue(f.player.rmPackage)
    }

    @Test fun `without the talisman nothing is taken and the quest waits`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_TALISMAN)
        f.talk(listOf(1))
        assertFalse(f.player.rmTalismanGiven)
        assertEquals(RuneMysteriesQuest.STAGE_MET_SEDRIDOR, f.stage())
        assertEquals(0, f.count(Package))
    }

    @Test fun `a full inventory gets no package and a later talk gives exactly one`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_AGREED)
        f.player.rmTalismanGiven = true
        f.fillInventory()
        f.talk()
        assertEquals(0, f.count(Package))
        assertFalse(f.player.rmPackage)
        f.player.inv[27] = null
        f.talk()
        assertEquals(1, f.count(Package))
        f.talk()
        assertEquals(1, f.count(Package))
    }

    @Test fun `a replacement package removes the banked copy`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_AGREED)
        f.player.rmTalismanGiven = true
        f.player.rmPackage = true
        f.access().bank[0] = InvObj(Package, 1)
        f.talk()
        assertEquals(1, f.player.inv.count(Package))
        assertEquals(0, f.access().bank.count(Package))
    }

    @Test fun `without the notes nothing completes`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_PACKAGE_READ)
        f.player.rmNotes = true
        f.talk()
        assertEquals(RuneMysteriesQuest.STAGE_PACKAGE_READ, f.stage())
        assertFalse(f.player.rmNotesGiven)
        assertEquals(0, f.count(Talisman))
    }

    @Test fun `the notes are swapped for one air talisman even with a full inventory`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_PACKAGE_READ)
        f.player.rmNotes = true
        f.fillInventory()
        f.player.inv[5] = InvObj(Notes, 1)
        f.talk(listOf(4))
        assertEquals(RuneMysteriesQuest.STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(Notes))
        assertEquals(1, f.count(Talisman))
        assertFalse(f.player.rmOwedTalisman)
        assertEquals(1, f.player.vars["varp.qp"])
        f.talk(listOf(4))
        assertEquals(1, f.count(Talisman))
        assertEquals(1, f.player.vars["varp.qp"])
    }

    @Test fun `an owed talisman waits for space and is given once`() {
        val f = Fixture(RuneMysteriesQuest.STAGE_COMPLETE)
        f.player.rmNotesGiven = true
        f.player.rmOwedTalisman = true
        f.fillInventory()
        f.talk(listOf(4))
        assertEquals(0, f.count(Talisman))
        assertTrue(f.player.rmOwedTalisman)
        f.player.inv[27] = null
        f.talk(listOf(4))
        assertEquals(1, f.count(Talisman))
        assertFalse(f.player.rmOwedTalisman)
        f.talk(listOf(4))
        assertEquals(1, f.count(Talisman))
    }

    private class Fixture(stage: Int) {
        private val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("sedridor-test")
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
                coords = CoordGrid(3104, 9571, 0)
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
            with(ArchmageSedridor(quest, WorldRepository(ZoneUpdateMap()))) { scripts.startup() }
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
            val npc = Npc("npc.head_wizard", player.coords.translateZ(1))
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
        private const val Talisman = RuneMysteriesQuest.AIR_TALISMAN
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
