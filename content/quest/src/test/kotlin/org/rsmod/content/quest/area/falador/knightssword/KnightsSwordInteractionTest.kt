package org.rsmod.content.quest.area.falador.knightssword

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_ORE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_SWORD
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.IRON_BAR
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.REDBERRY_PIE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_GAVE_PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_LOOKING_FOR_PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_THURGO_NEEDS_PICTURE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_TOLD_BY_RELDO
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
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class KnightsSwordInteractionTest {
    @Test fun `the pie is taken once and moves the quest to the picture`() {
        val f = Fixture(STAGE_TOLD_BY_RELDO)
        f.give(REDBERRY_PIE, 2)
        f.talk(THURGO)
        f.finish(listOf(1, 1, 1))
        assertEquals(STAGE_THURGO_NEEDS_PICTURE, f.stage())
        assertEquals(1, f.player.inv.count(REDBERRY_PIE))
    }

    @Test fun `a pie gone before it is handed over changes nothing`() {
        val f = Fixture(STAGE_TOLD_BY_RELDO)
        f.give(REDBERRY_PIE, 1)
        f.talk(THURGO)
        f.until { f.output().contains("eyes light up") }
        f.player.inv.fillNulls()
        f.finish(listOf(1, 1, 1))
        assertEquals(STAGE_TOLD_BY_RELDO, f.stage())
        assertEquals(0, f.player.inv.count(REDBERRY_PIE))
    }

    @Test fun `every portrait held is taken once`() {
        val f = Fixture(STAGE_LOOKING_FOR_PORTRAIT)
        f.give(PORTRAIT, 3)
        f.talk(THURGO)
        f.finish(listOf(1))
        assertEquals(STAGE_GAVE_PORTRAIT, f.stage())
        assertEquals(0, f.player.inv.count(PORTRAIT))
        f.give(PORTRAIT, 1)
        f.talk(THURGO)
        f.finish(listOf(1))
        assertEquals(STAGE_GAVE_PORTRAIT, f.stage())
        assertEquals(1, f.player.inv.count(PORTRAIT))
    }

    @Test fun `a portrait gone before it is handed over changes nothing`() {
        val f = Fixture(STAGE_LOOKING_FOR_PORTRAIT)
        f.give(PORTRAIT, 1)
        f.talk(THURGO)
        f.until { f.output().contains("You give the Portrait to Thurgo") }
        f.player.inv.fillNulls()
        f.finish()
        assertEquals(STAGE_LOOKING_FOR_PORTRAIT, f.stage())
    }

    @Test fun `a full inventory swaps one ore and two bars for one sword`() {
        val f = Fixture(STAGE_GAVE_PORTRAIT)
        f.give(BLURITE_ORE, 2)
        f.give(IRON_BAR, 4)
        while (f.player.inv.freeSpace() > 0) f.give("obj.bronze_dagger", 1)
        f.talk(THURGO)
        f.finish(listOf(1))
        assertEquals(1, f.player.inv.count(BLURITE_ORE))
        assertEquals(2, f.player.inv.count(IRON_BAR))
        assertEquals(1, f.player.inv.count(BLURITE_SWORD))
        assertEquals(2, f.player.inv.freeSpace())
        f.talk(THURGO)
        f.finish(listOf(1))
        assertEquals(1, f.player.inv.count(BLURITE_ORE))
        assertEquals(2, f.player.inv.count(IRON_BAR))
        assertEquals(1, f.player.inv.count(BLURITE_SWORD))
    }

    @Test fun `materials gone before the swap change nothing`() {
        val f = Fixture(STAGE_GAVE_PORTRAIT)
        f.give(BLURITE_ORE, 1)
        f.give(IRON_BAR, 2)
        f.talk(THURGO)
        f.until { f.output().contains("I have them right here.") }
        f.player.inv[0] = null
        f.finish()
        assertEquals(0, f.player.inv.count(BLURITE_SWORD))
        assertEquals(2, f.player.inv.count(IRON_BAR))
        assertTrue(KnightsSwordQuest.makeSword(f.player).not())
        assertEquals(2, f.player.inv.count(IRON_BAR))
    }

    @Test fun `the sword is handed in once for one reward`() {
        val f = Fixture(STAGE_GAVE_PORTRAIT)
        f.give(BLURITE_SWORD, 2)
        f.talk(SQUIRE)
        f.finish()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.player.inv.count(BLURITE_SWORD))
        assertEquals(1, f.player.vars["varp.qp"])
        val xp = f.player.statMap.getXP("stat.smithing")
        f.talk(SQUIRE)
        f.finish()
        assertEquals(1, f.player.inv.count(BLURITE_SWORD))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(xp, f.player.statMap.getXP("stat.smithing"))
    }

    @Test fun `a sword gone before the hand-in changes nothing`() {
        val f = Fixture(STAGE_GAVE_PORTRAIT)
        f.give(BLURITE_SWORD, 1)
        f.talk(SQUIRE)
        f.until { f.output().contains("You give the sword to the squire.") }
        f.player.inv.fillNulls()
        f.finish()
        assertEquals(STAGE_GAVE_PORTRAIT, f.stage())
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test fun `a wielded sword is not taken`() {
        val f = Fixture(STAGE_GAVE_PORTRAIT)
        f.player.worn[3] = InvObj(BLURITE_SWORD, 1)
        f.talk(SQUIRE)
        f.finish()
        assertEquals(STAGE_GAVE_PORTRAIT, f.stage())
        assertEquals(1, f.player.worn.count(BLURITE_SWORD))
    }

    private class Harness(private val quest: KnightsSwordQuest) : PluginScript() {
        override fun ScriptContext.startup() {
            onOpNpc1(THURGO) {
                startDialogue(it.npc) { with(quest) { thurgoDialogue(pie = {}, cape = {}) } }
            }
            onOpNpc1(SQUIRE) { startDialogue(it.npc) { with(quest) { squireDialogue() } } }
        }
    }

    private class Fixture(stage: Int) {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("knights-sword-test")
        private var result: Result<Unit>? = null
        private val context =
            ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events },
                getAlignment = { TextAlignment() },
                getNpcInteractions = { NpcInteractions(events) },
            )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 797L
                slotId = 1
                assignUid()
                coords = CoordGrid(3000, 3144, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            }

        val script = KnightsSwordQuest()

        init {
            val ctx = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(script) { ctx.startup() }
            with(Harness(script)) { ctx.startup() }
            VarPlayerIntMapSetter.set(player, "varp.squire", stage)
        }

        fun stage() = script.stage(player)

        fun give(obj: String, count: Int) = repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }

        private fun access() = ProtectedAccess(player, coroutine, context)

        fun talk(npcType: String) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val npc = Npc(npcType, player.coords.translateZ(1))
            val block: suspend () -> Unit = { assertTrue(events.publish(access(), NpcEvents.Op1(npc))) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        fun until(predicate: () -> Boolean) {
            repeat(150) {
                if (predicate()) return
                advance()
            }
            fail<Unit>("Condition was not reached: ${output()}")
        }

        fun finish(options: List<Int> = emptyList()) {
            val selections = options.iterator()
            repeat(150) {
                if (coroutine.isIdle) return
                advance(selections)
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun advance(options: Iterator<Int> = emptyList<Int>().iterator()) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent = listOf("chat_left", "chat_right", "messagebox", "objectbox", "chatmenu")
                    .firstOrNull { player.ui.containsModal("interface.$it") }
                    ?: error("Unknown dialogue: ${output()}")
                val input = when (parent) {
                    "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", if (options.hasNext()) options.next() else 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    else -> ResumePauseButtonInput("component.$parent:continue", -1)
                }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            result?.getOrThrow()
        }

        fun output() = client.messages.joinToString("\n")
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
        private const val THURGO = "npc.thurgo"
        private const val SQUIRE = "npc.squire"
        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
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
