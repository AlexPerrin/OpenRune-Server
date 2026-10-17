package org.rsmod.content.interfaces.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.util.UncheckedType
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.game.outgoing.varp.VarpLong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.uncert

/**
 * A minimal Grand Exchange. The server is the only counterparty and trades at the guide price
 * (`MarketPrices`, the obj's store cost):
 * - a buy offer priced at or above the guide fills at once at the guide price; the overpayment
 *   comes back as coins in the slot's collection inv;
 * - a sell offer priced at or below the guide fills at once at the guide price, less the 2% tax
 *   (floored, capped at 5M per item, nothing below 50 gp);
 * - any other offer stays open (BUYING/SELLING) until aborted. No partial fills, no buy limits.
 *
 * Offer state lives in server varps (`ge_slotN_*`, perm) and goes to the client only through
 * UPDATE_STOCKMARKET_SLOT; the collected items sit in the slot's inv from enum 150
 * (`tradingpost_sell_0..5`, `ge_collect_6/7`): slot 0 the obj, slot 1 coins.
 */
@OptIn(UncheckedType::class)
object GrandExchange {
    const val SLOTS = 8
    const val MAX_TOTAL = Int.MAX_VALUE

    /** Desktop status values (`UpdateStockMarketSlot`): low 3 bits the state, bit 3 = sell. */
    const val STATE_EMPTY = 0
    const val STATE_OPEN = 2
    const val STATE_FINISHED = 5
    const val SELL_BIT = 8

    /** Tax rate sent in `varbit.ge_transmit_taxrate` (per 1000, as scripts 779/821 scale it). */
    const val TAX_RATE_PER_MILLE = 20
    const val TAX_CAP = 5_000_000
    const val TAX_MIN_PRICE = 50

    const val FILL_TIMER = "timer.ge_offer_fill"
    private val newOfferPriceVarp: Int by lazy {
        "varp.ge_newoffer_price".asRSCM(RSCMType.VARP)
    }

    val collectionInvs: List<String> =
        (0..5).map { "inv.tradingpost_sell_$it" } + listOf("inv.ge_collect_6", "inv.ge_collect_7")

    private val coinsId: Int by lazy { "obj.coins".asRSCM(RSCMType.OBJ) }

    data class Offer(
        val slot: Int,
        val item: Int,
        val price: Int,
        val count: Int,
        val done: Int,
        val gold: Int,
        val status: Int,
    ) {
        val sell: Boolean
            get() = status and SELL_BIT != 0

        val state: Int
            get() = status and 7

        val empty: Boolean
            get() = state == STATE_EMPTY
    }

    private fun varp(slot: Int, field: String) = "varp.ge_slot${slot}_$field"

    fun offer(player: Player, slot: Int): Offer =
        Offer(
            slot = slot,
            item = player.vars[varp(slot, "item")] - 1,
            price = player.vars[varp(slot, "price")],
            count = player.vars[varp(slot, "count")],
            done = player.vars[varp(slot, "done")],
            gold = player.vars[varp(slot, "gold")],
            status = player.vars[varp(slot, "state")],
        )

    fun store(player: Player, offer: Offer) {
        set(player, varp(offer.slot, "item"), offer.item + 1)
        set(player, varp(offer.slot, "price"), offer.price)
        set(player, varp(offer.slot, "count"), offer.count)
        set(player, varp(offer.slot, "done"), offer.done)
        set(player, varp(offer.slot, "gold"), offer.gold)
        set(player, varp(offer.slot, "state"), offer.status)
    }

    fun clear(player: Player, slot: Int) {
        store(player, Offer(slot, -1, 0, 0, 0, 0, STATE_EMPTY))
    }

    fun set(player: Player, name: String, value: Int) {
        VarPlayerIntMapSetter.set(player, name, value)
    }

    /** Sets a var the client may already have changed itself, sending it even if unchanged. */
    fun setClientVar(player: Player, name: String, value: Int) {
        val before = player.vars[name]
        set(player, name, value)
        if (before == value) player.resyncVar(name)
    }

    fun collectionInv(player: Player, slot: Int): Inventory =
        player.invMap.getOrPut(collectionInvs[slot])

    fun sendSlot(player: Player, slot: Int) {
        val offer = offer(player, slot)
        val update =
            if (offer.empty) {
                UpdateStockMarketSlotV2.ResetStockMarketSlot
            } else {
                UpdateStockMarketSlotV2.SetStockMarketSlot(
                    status = offer.status,
                    obj = offer.item,
                    price = offer.price.toLong(),
                    count = offer.count,
                    completedCount = offer.done,
                    completedGold = offer.gold.toLong(),
                )
            }
        player.client.write(UpdateStockMarketSlotV2(slot, update))
    }

    fun sendSetupPrice(player: Player, price: Int) {
        set(player, "varp.ge_setup_price", price)
        player.client.write(VarpLong(newOfferPriceVarp, price.toLong()))
    }

    fun type(id: Int): ItemServerType? = ServerCacheManager.getItem(id)

    fun name(id: Int): String = type(id)?.name ?: "null"

    fun internal(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)

    /** The tradeable (unnoted) form of [type], or null if the GE does not trade it. */
    fun tradeable(type: ItemServerType): ItemServerType? {
        val base = uncert(type)
        return base.takeIf { it.stockmarket }
    }

    fun taxPerItem(price: Int): Int =
        if (price < TAX_MIN_PRICE) 0 else minOf(price / 50, TAX_CAP) // 2%, floored

    /** Held count of [item] including its noted form. */
    fun heldCount(inv: Inventory, item: Int): Int {
        val base = type(item) ?: return 0
        var total = 0L
        for (obj in inv.objs) {
            if (obj == null) continue
            if (obj.id == base.id || (base.canCert && obj.id == base.certlink)) total += obj.count
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun canFill(offer: Offer, guide: Int): Boolean =
        offer.state == STATE_OPEN && if (offer.sell) offer.price <= guide else offer.price >= guide

    /**
     * Fills every open offer the market takes (see the class comment). Returns the slots filled.
     */
    fun fillOpenOffers(player: Player, guidePrice: (ItemServerType) -> Int): List<Int> {
        val filled = mutableListOf<Int>()
        for (slot in 0 until SLOTS) {
            val offer = offer(player, slot)
            val type = type(offer.item) ?: continue
            val guide = guidePrice(type)
            if (!canFill(offer, guide)) continue
            val inv = collectionInv(player, slot)
            val gold = offer.count * guide
            if (offer.sell) {
                val net = gold - offer.count * taxPerItem(guide)
                addTo(inv, 1, coinsId, net)
            } else {
                addTo(inv, 0, offer.item, offer.count)
                val refund = offer.count * (offer.price - guide)
                if (refund > 0) addTo(inv, 1, coinsId, refund)
            }
            store(
                player,
                offer.copy(done = offer.count, gold = gold, status = finishedStatus(offer.sell)),
            )
            sendSlot(player, slot)
            val verb = if (offer.sell) "selling" else "buying"
            player.mes("Grand Exchange: Finished $verb ${offer.count.commas()} x ${type.name}.")
            filled += slot
        }
        return filled
    }

    /** Aborts an open offer: what was put in waits in the collection inv. */
    fun abort(player: Player, slot: Int): Boolean {
        val offer = offer(player, slot)
        if (offer.state != STATE_OPEN) return false
        val inv = collectionInv(player, slot)
        if (offer.sell) {
            addTo(inv, 0, offer.item, offer.count - offer.done)
        } else {
            addTo(inv, 1, coinsId, (offer.count - offer.done) * offer.price)
        }
        store(player, offer.copy(status = finishedStatus(offer.sell)))
        sendSlot(player, slot)
        return true
    }

    fun finishedStatus(sell: Boolean): Int = STATE_FINISHED or (if (sell) SELL_BIT else 0)

    fun openStatus(sell: Boolean): Int = STATE_OPEN or (if (sell) SELL_BIT else 0)

    /** Clears a finished slot once its collection inv is empty. */
    fun clearIfCollected(player: Player, slot: Int): Boolean {
        val offer = offer(player, slot)
        if (offer.state != STATE_FINISHED) return false
        if (!collectionInv(player, slot).isEmpty()) return false
        clear(player, slot)
        sendSlot(player, slot)
        return true
    }

    private fun addTo(inv: Inventory, slot: Int, id: Int, count: Int) {
        if (count <= 0) return
        val current = inv[slot]
        val total = (current?.count ?: 0).toLong() + count
        inv[slot] = InvObj(id, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    fun Int.commas(): String = "%,d".format(this)
}
