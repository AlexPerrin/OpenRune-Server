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

            assertEquals(listOf(2), GrandExchange.fillOpenOffers(player) { it.cost })
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
            GrandExchange.fillOpenOffers(player) { it.cost }
            val net = 5 * guide - 5 * GrandExchange.taxPerItem(guide)
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
            assertTrue(GrandExchange.fillOpenOffers(player) { it.cost }.isEmpty())
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
    fun GameTestState.`a buy takes inventory coins first and the rest from the bank`() =
        runInjectedGameTest(GrandExchangeScript::class, null, GrandExchangeScript::class) { script ->
            resetGe(player)
            val bank = player.invMap.getOrPut("inv.bank")
            player.inv[0] = InvObj(id("obj.coins"), 7)
            bank[0] = InvObj(id("obj.coins"), 10 * guide)
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 0, sell = false, item = lobster.id, count = 10, price = guide)
                    assertTrue(confirm())
                }
            }
            assertEquals(0, player.inv.count("obj.coins"))
            assertEquals(7, bank.count("obj.coins"))
            assertEquals(GrandExchange.STATE_OPEN, GrandExchange.offer(player, 0).state)

            player.inv[0] = InvObj(id("obj.coins"), 3)
            with(script) {
                player.withProtectedAccess {
                    setUp(slot = 1, sell = false, item = lobster.id, count = 1, price = 11)
                    assertFalse(confirm())
                }
            }
            assertEquals(3, player.inv.count("obj.coins"))
            assertEquals(7, bank.count("obj.coins"))
            assertTrue(GrandExchange.offer(player, 1).empty)
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
            GrandExchange.fillOpenOffers(player) { it.cost }
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
    }
}
