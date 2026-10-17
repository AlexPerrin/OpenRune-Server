package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class GrandExchangeScriptTest {
    private val lobster: ItemServerType
        get() = checkNotNull(ServerCacheManager.getItem("obj.lobster".asRSCM(RSCMType.OBJ)))

    private val guide: Int
        get() = lobster.cost.coerceAtLeast(1)

    private fun id(name: String) = name.asRSCM(RSCMType.OBJ)

    private fun ProtectedAccess.setUp(slot: Int, sell: Boolean, item: Int, count: Int, price: Int) {
        vars["varbit.ge_selectedslot"] = slot + 1
        vars["varbit.ge_newoffer_type"] = if (sell) 1 else 0
        vars["varp.tradingpost_search"] = item
        vars["varbit.ge_newoffer_quantity"] = count
        GrandExchange.set(player, "varp.ge_setup_price", price)
    }

    private fun GameTestScope.resetGe(player: Player) {
        player.clearInv()
        player.clearInv(player.invMap.getOrPut("inv.bank"))
        for (slot in 0 until GrandExchange.SLOTS) {
            GrandExchange.clear(player, slot)
            player.clearInv(GrandExchange.collectionInv(player, slot))
        }
        for (i in 0 until GrandExchange.HISTORY_SIZE) {
            for (field in listOf("obj", "count", "gold", "tax")) {
                GrandExchange.set(player, "varp.ge_history${i}_$field", 0)
            }
        }
        for (i in 0 until GrandExchange.LIMIT_TRACKERS) {
            for (field in listOf("item", "start", "bought")) {
                GrandExchange.set(player, "varp.ge_limit${i}_$field", 0)
            }
        }
    }

    /** Runs fill steps until nothing fills any more. */
    private fun fillAll(player: Player, now: Int = NOW) {
        while (GrandExchange.StepResult.Filled in GrandExchange.fillOpenOffers(player, now) { it.cost }.values) {
            continue
        }
    }

    @Test
    fun `the lobster is a GE item with a guide price`() {
        assertTrue(lobster.stockmarket)
        assertTrue(lobster.canCert)
    }

    @Test
    fun GameTestState.`a buy at or above guide fills at guide and refunds the difference`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            player.inv[0] = InvObj(id("obj.coins"), 100_000)
            val price = guide + 5
            var placed = false
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 2, sell = false, item = lobster.id, count = 10, price = price)
                    placed = confirm()
                }
            }
            assertTrue(placed)
            assertEquals(100_000 - 10 * price, player.inv.count("obj.coins"))
            val open = GrandExchange.offer(player, 2)
            assertEquals(GrandExchange.STATE_OPEN, open.status)
            assertEquals(0, player.vars["varbit.ge_selectedslot"])

            val first = GrandExchange.fillOpenOffers(player, NOW) { it.cost }
            assertEquals(mapOf(2 to GrandExchange.StepResult.Filled), first)
            val partial = GrandExchange.offer(player, 2)
            assertEquals(GrandExchange.STATE_OPEN, partial.status)
            assertEquals(3, partial.done)
            assertEquals(3 * guide, partial.gold)
            assertEquals(InvObj(lobster.id, 3), GrandExchange.collectionInv(player, 2)[0])
            assertEquals(InvObj(id("obj.coins"), 15), GrandExchange.collectionInv(player, 2)[1])

            fillAll(player)
            val filled = GrandExchange.offer(player, 2)
            assertEquals(GrandExchange.STATE_FINISHED, filled.status)
            assertEquals(10, filled.done)
            assertEquals(10 * guide, filled.gold)
            val collection = GrandExchange.collectionInv(player, 2)
            assertEquals(InvObj(lobster.id, 10), collection[0])
            assertEquals(InvObj(id("obj.coins"), 50), collection[1])

            with(script) {
                player.withProtectedAccess {
                    assertTrue(collect(2, 0, IfButtonOp.Op1))
                    assertTrue(collect(2, 1, IfButtonOp.Op2))
                }
            }
            assertEquals(10, player.inv.count(GrandExchange.internal(lobster.certlink)))
            assertEquals(100_000 - 10 * guide, player.inv.count("obj.coins"))
            assertTrue(GrandExchange.offer(player, 2).empty)
            assertTrue(collection.isEmpty())
        }

    @Test
    fun GameTestState.`a sell at or below guide takes noted and unnoted items and pays less tax`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            repeat(3) { player.inv[it] = InvObj(lobster.id, 1) }
            player.inv[3] = InvObj(lobster.certlink, 2)
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 0, sell = true, item = lobster.id, count = 5, price = 1)
                    assertTrue(confirm())
                }
            }
            assertEquals(0, GrandExchange.heldCount(player.inv, lobster.id))
            assertEquals(GrandExchange.STATE_OPEN or GrandExchange.SELL_BIT, GrandExchange.offer(player, 0).status)
            fillAll(player)
            val net = 5 * guide - 5 * GrandExchange.taxPerItem(lobster.id, guide)
            assertEquals(InvObj(id("obj.coins"), net), GrandExchange.collectionInv(player, 0)[1])
            assertNull(GrandExchange.collectionInv(player, 0)[0])
            with(script) { player.withProtectedAccess { collectAll(toBank = true) } }
            assertEquals(net, player.invMap.getOrPut("inv.bank").count("obj.coins"))
            assertTrue(GrandExchange.offer(player, 0).empty)
        }

    @Test
    fun GameTestState.`a buy below guide stays open until aborted and the coins come back`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            player.inv[0] = InvObj(id("obj.coins"), 1_000)
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 1, sell = false, item = lobster.id, count = 3, price = 1)
                    assertTrue(confirm())
                }
            }
            assertEquals(997, player.inv.count("obj.coins"))
            assertTrue(GrandExchange.fillOpenOffers(player, NOW) { it.cost }.isEmpty())
            assertTrue(GrandExchange.abort(player, 1))
            assertFalse(GrandExchange.abort(player, 1))
            val aborted = GrandExchange.offer(player, 1)
            assertEquals(GrandExchange.STATE_FINISHED, aborted.state)
            assertEquals(0, aborted.done)
            with(script) { player.withProtectedAccess { collectAll(toBank = false) } }
            assertEquals(1_000, player.inv.count("obj.coins"))
            assertTrue(GrandExchange.offer(player, 1).empty)
        }

    @Test
    fun GameTestState.`offers the player cannot pay for or that overflow change nothing`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            player.inv[0] = InvObj(id("obj.coins"), 10)
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 0, sell = false, item = lobster.id, count = 100, price = guide)
                    assertFalse(confirm())
                    setUp(slot = 0, sell = false, item = lobster.id, count = Int.MAX_VALUE, price = 2)
                    assertFalse(confirm())
                    setUp(slot = 0, sell = true, item = lobster.id, count = 1, price = 1)
                    assertFalse(confirm())
                }
            }
            assertEquals(10, player.inv.count("obj.coins"))
            assertTrue(GrandExchange.offer(player, 0).empty)
        }

    @Test
    fun GameTestState.`collecting into a full inventory keeps the items in the collection`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            player.inv[0] = InvObj(id("obj.coins"), 10_000)
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 0, sell = false, item = lobster.id, count = 2, price = guide)
                    assertTrue(confirm())
                }
            }
            fillAll(player)
            player.fillInv()
            with(script) {
                player.withProtectedAccess { assertFalse(collect(0, 0, IfButtonOp.Op2)) }
            }
            assertEquals(InvObj(lobster.id, 2), GrandExchange.collectionInv(player, 0)[0])
            assertEquals(GrandExchange.STATE_FINISHED, GrandExchange.offer(player, 0).state)
        }

    @Test
    fun `quantity and price buttons follow the client scripts`() {
        val q = GrandExchangeScript::changeQuantity
        assertEquals(10, q(false, 1, 10, 0))
        assertEquals(1000, q(false, 1, Int.MAX_VALUE, 0))
        assertEquals(1, q(false, 1, -1, 0))
        assertEquals(7, q(true, 1, Int.MAX_VALUE, 7))
        assertEquals(7, q(true, 5, 100, 7))
        assertEquals(Int.MAX_VALUE, q(false, Int.MAX_VALUE - 3, 100, 0))
        assertEquals(105, GrandExchangeScript.changePrice(100, 5))
        assertEquals(1, GrandExchangeScript.changePrice(1, -5))
        assertEquals(2, GrandExchangeScript.changePrice(1, 1))
        assertEquals(0, GrandExchange.taxPerItem(49))
        assertEquals(1, GrandExchange.taxPerItem(50))
        assertEquals(GrandExchange.TAX_CAP, GrandExchange.taxPerItem(Int.MAX_VALUE))
        val lobster = "obj.lobster".asRSCM(RSCMType.OBJ)
        val coal = "obj.coal".asRSCM(RSCMType.OBJ)
        assertTrue(GrandExchange.taxExempt(lobster))
        assertEquals(0, GrandExchange.taxPerItem(lobster, 1000))
        assertFalse(GrandExchange.taxExempt(coal))
        assertEquals(20, GrandExchange.taxPerItem(coal, 1000))
    }

    @Test
    fun GameTestState.`aborting a partly filled buy keeps what was bought and returns the rest`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            player.inv[0] = InvObj(id("obj.coins"), 100_000)
            val price = guide + 5
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 0, sell = false, item = lobster.id, count = 10, price = price)
                    assertTrue(confirm())
                }
            }
            GrandExchange.fillOpenOffers(player, NOW) { it.cost }
            assertTrue(GrandExchange.abort(player, 0))
            val collection = GrandExchange.collectionInv(player, 0)
            assertEquals(InvObj(lobster.id, 3), collection[0])
            assertEquals(InvObj(id("obj.coins"), 3 * 5 + 7 * price), collection[1])
            assertTrue(GrandExchange.fillOpenOffers(player, NOW) { it.cost }.isEmpty())
            with(script) { player.withProtectedAccess { collectAll(toBank = false) } }
            assertEquals(100_000 - 3 * guide, player.inv.count("obj.coins"))
            assertEquals(3, GrandExchange.heldCount(player.inv, lobster.id))
            assertTrue(GrandExchange.offer(player, 0).empty)
        }

    @Test
    fun GameTestState.`a buy over the buy limit waits for the 4-hour window`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            val pickaxe = checkNotNull(ServerCacheManager.getItem(id("obj.rune_pickaxe")))
            val limit = checkNotNull(GrandExchange.buyLimit(pickaxe.id))
            assertEquals(40, limit)
            val price = pickaxe.cost.coerceAtLeast(1)
            player.inv[0] = InvObj(id("obj.coins"), 50 * price)
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 1, sell = false, item = pickaxe.id, count = 50, price = price)
                    assertTrue(confirm())
                }
            }
            fillAll(player)
            assertEquals(40, GrandExchange.offer(player, 1).done)
            assertEquals(GrandExchange.STATE_OPEN, GrandExchange.offer(player, 1).state)
            assertEquals(0, GrandExchange.limitRemaining(player, pickaxe.id, NOW))
            assertEquals(
                mapOf(1 to GrandExchange.StepResult.LimitBlocked),
                GrandExchange.fillOpenOffers(player, NOW + 239) { it.cost },
            )

            fillAll(player, NOW + GrandExchange.LIMIT_WINDOW_MINUTES)
            val offer = GrandExchange.offer(player, 1)
            assertEquals(GrandExchange.STATE_FINISHED, offer.state)
            assertEquals(50, offer.done)
            assertEquals(InvObj(pickaxe.id, 50), GrandExchange.collectionInv(player, 1)[0])
            assertEquals(limit - 10, GrandExchange.limitRemaining(player, pickaxe.id, NOW + 240))
            assertEquals(Int.MAX_VALUE, GrandExchange.limitRemaining(player, id("obj.coins"), NOW))
        }

    @Test
    fun GameTestState.`buy limit trackers reuse expired entries and evict the oldest`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) {
            resetGe(player)
            val limited =
                ServerCacheManager.getItems().keys.filter { GrandExchange.buyLimit(it) != null }
                    .take(GrandExchange.LIMIT_TRACKERS + 1)
            limited.dropLast(1).forEachIndexed { i, item -> GrandExchange.recordBought(player, item, 1, NOW + i) }
            limited.dropLast(1).forEach {
                assertEquals(GrandExchange.buyLimit(it)!! - 1, GrandExchange.limitRemaining(player, it, NOW + 20))
            }
            GrandExchange.recordBought(player, limited.last(), 1, NOW + 20)
            assertEquals(GrandExchange.buyLimit(limited[0]), GrandExchange.limitRemaining(player, limited[0], NOW + 20))
            assertEquals(
                GrandExchange.buyLimit(limited.last())!! - 1,
                GrandExchange.limitRemaining(player, limited.last(), NOW + 20),
            )
        }

    @Test
    fun GameTestState.`finished and partly filled aborted offers go into the history, newest first`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            player.inv[0] = InvObj(id("obj.coins"), 100_000)
            repeat(3) { player.inv[1 + it] = InvObj(lobster.id, 1) }
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 0, sell = false, item = lobster.id, count = 10, price = guide)
                    assertTrue(confirm())
                    setUp(slot = 1, sell = true, item = lobster.id, count = 3, price = 1)
                    assertTrue(confirm())
                    setUp(slot = 2, sell = false, item = lobster.id, count = 5, price = 1)
                    assertTrue(confirm())
                }
            }
            GrandExchange.fillOpenOffers(player, NOW) { it.cost }
            assertTrue(GrandExchange.abort(player, 0))
            assertTrue(GrandExchange.abort(player, 2))
            fillAll(player)
            val history = GrandExchange.history(player)
            assertEquals(2, history.size)
            assertEquals(GrandExchange.Trade(lobster.id, true, 3, 3 * guide, 3 * GrandExchange.taxPerItem(lobster.id, guide)), history[0])
            assertEquals(GrandExchange.Trade(lobster.id, false, 3, 3 * guide, 0), history[1])

            repeat(GrandExchange.HISTORY_SIZE) {
                GrandExchange.recordHistory(
                    player,
                    GrandExchange.Offer(0, id("obj.coal"), 1, 1, 1, it + 1, GrandExchange.STATE_FINISHED),
                )
            }
            val full = GrandExchange.history(player)
            assertEquals(GrandExchange.HISTORY_SIZE, full.size)
            assertEquals(GrandExchange.HISTORY_SIZE, full.first().gold)
            assertEquals(1, full.last().gold)
        }

    private companion object {
        const val NOW = 29_000_000
    }
}
