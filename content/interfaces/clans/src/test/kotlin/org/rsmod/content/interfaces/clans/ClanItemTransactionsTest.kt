package org.rsmod.content.interfaces.clans

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.net.clan.Clan
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class ClanItemTransactionsTest {
    @Test
    fun `a full inventory takes no clan item and keeps every obj`() {
        val f = Fixture()
        repeat(INV_SIZE) { f.player.inv[it] = InvObj(FILLER, 1) }
        val before = f.player.inv.objs.toList()
        repeat(2) {
            ClanItem.entries.forEach { kind -> assertFalse(f.take(kind)) }
            assertEquals(before, f.player.inv.objs.toList())
        }
    }

    @Test
    fun `each take adds one item until the inventory is full`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(FILLER, 1)
        repeat(INV_SIZE - 1) { assertTrue(f.take(ClanItem.Vexillum)) }
        assertFalse(f.take(ClanItem.Vexillum))
        assertFalse(f.take(ClanItem.Cloak))
        assertEquals(INV_SIZE - 1, f.count(ClanItem.Vexillum))
        assertEquals(0, f.count(ClanItem.Cloak))
        assertEquals(1, f.player.inv.count(FILLER))
    }

    @Test
    fun `the item takes the clan motif colour`() {
        val f = Fixture()
        assertTrue(f.take(ClanItem.Cloak))
        assertEquals(1, f.player.inv.count(ClanItem.Cloak.objs[f.clan.motif()]))
        assertEquals(1, f.count(ClanItem.Cloak))
    }

    private class Fixture {
        val clan = Clan(1, "Test", 0, 0)
        val player =
            Player().apply {
                this.client = SilentClient()
                uuid = 893L
                slotId = 1
                assignUid()
                coords = CoordGrid(3164, 3466, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = inventory("inv.inv", INV_SIZE)
            }
        private val access =
            ProtectedAccess(
                player,
                GameCoroutine("clan-item-test"),
                ProtectedAccessContextFactory.empty()
                    .copy(getEventBus = { EventBus() }, getAlignment = { TextAlignment() }),
            )

        fun take(kind: ClanItem): Boolean = access.takeClanItem(kind, clan)

        fun count(kind: ClanItem): Int = kind.objs.sumOf { player.inv.count(it) }

        private fun inventory(name: String, size: Int) =
            Inventory(checkNotNull(ServerCacheManager.getInventory(name.asRSCM())), arrayOfNulls(size))
    }

    private class SilentClient : Client<Any, Any> {
        override fun write(message: Any) {}

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val INV_SIZE = 28
        private const val FILLER = "obj.bronze_dagger"
        private val restored = mutableListOf<() -> Unit>()

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in
                listOf(
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

        @JvmStatic
        @AfterAll
        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
