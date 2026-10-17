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
 * - a buy offer priced at or above the guide fills at the guide price; the overpayment comes back
 *   as coins in the slot's collection inv;
 * - a sell offer priced at or below the guide fills at the guide price, less the 2% tax (floored,
 *   capped at 5M per item, nothing below 50 gp, none on `enum.ge_tax_exempt` items);
 * - fills come in up to [FILL_STEPS] parts ([FILL_DELAY] ticks after Confirm, then every
 *   [FILL_INTERVAL] ticks), each one sent as a partial update;
 * - buys count against the item's 4-hour buy limit (`enum.ge_buy_limits`, from the wiki); an offer
 *   over the limit stays BUYING until the window that started with the first buy runs out;
 * - any other offer stays open (BUYING/SELLING) until aborted.
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
    const val FILL_DELAY = 2
    const val FILL_INTERVAL = 5
    const val FILL_STEPS = 4
    /** How often a limit-blocked offer looks again (100 ticks = 1 minute). */
    const val LIMIT_RECHECK = 100

    const val HISTORY_SIZE = 40
    /** Bit set in `ge_historyN_obj` for a sale (the low bits hold obj + 1). */
    private const val HISTORY_SOLD_BIT = 1 shl 30

    const val LIMIT_WINDOW_MINUTES = 240
    const val LIMIT_TRACKERS = 16

    /** Epoch minutes; tests replace it. */
    var clock: () -> Int = { (System.currentTimeMillis() / 60_000L).toInt() }

    private val buyLimitsEnum: Int by lazy { "enum.ge_buy_limits".asRSCM(RSCMType.ENUM) }
    private val taxExemptEnum: Int by lazy { "enum.ge_tax_exempt".asRSCM(RSCMType.ENUM) }
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

    /** Items the wiki lists as exempt from the tax (`enum.ge_tax_exempt`). */
    fun taxExempt(item: Int): Boolean =
        ServerCacheManager.getEnum(taxExemptEnum)?.values?.containsKey(item) == true

    fun taxPerItem(item: Int, price: Int): Int = if (taxExempt(item)) 0 else taxPerItem(price)

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

    /** The item's 4-hour buy limit, or null when it has none. */
    fun buyLimit(item: Int): Int? =
        (ServerCacheManager.getEnum(buyLimitsEnum)?.values?.get(item) as? Number)?.toInt()

    private fun limitVarp(index: Int, field: String) = "varp.ge_limit${index}_$field"

    private fun activeTracker(player: Player, item: Int, now: Int): Int? =
        (0 until LIMIT_TRACKERS).firstOrNull { i ->
            player.vars[limitVarp(i, "item")] == item + 1 &&
                now - player.vars[limitVarp(i, "start")] < LIMIT_WINDOW_MINUTES
        }

    /** How many more of [item] may be bought now (Int.MAX_VALUE without a limit). */
    fun limitRemaining(player: Player, item: Int, now: Int = clock()): Int {
        val limit = buyLimit(item) ?: return Int.MAX_VALUE
        val tracker = activeTracker(player, item, now) ?: return limit
        return (limit - player.vars[limitVarp(tracker, "bought")]).coerceAtLeast(0)
    }

    /** Minutes until [item]'s limit window ends, or 0 when no window is running. */
    fun limitResetMinutes(player: Player, item: Int, now: Int = clock()): Int {
        val tracker = activeTracker(player, item, now) ?: return 0
        return LIMIT_WINDOW_MINUTES - (now - player.vars[limitVarp(tracker, "start")])
    }

    fun recordBought(player: Player, item: Int, count: Int, now: Int = clock()) {
        if (buyLimit(item) == null || count <= 0) return
        val active = activeTracker(player, item, now)
        if (active != null) {
            val bought = player.vars[limitVarp(active, "bought")].toLong() + count
            set(player, limitVarp(active, "bought"), bought.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            return
        }
        val index =
            (0 until LIMIT_TRACKERS).firstOrNull { i ->
                player.vars[limitVarp(i, "item")] == 0 ||
                    now - player.vars[limitVarp(i, "start")] >= LIMIT_WINDOW_MINUTES
            } ?: (0 until LIMIT_TRACKERS).minBy { player.vars[limitVarp(it, "start")] }
        set(player, limitVarp(index, "item"), item + 1)
        set(player, limitVarp(index, "start"), now)
        set(player, limitVarp(index, "bought"), count)
    }

    data class Trade(val item: Int, val sold: Boolean, val count: Int, val gold: Int, val tax: Int)

    private fun historyVarp(index: Int, field: String) = "varp.ge_history${index}_$field"

    /** The trade history, newest first. */
    fun history(player: Player): List<Trade> =
        (0 until HISTORY_SIZE).mapNotNull { i ->
            val obj = player.vars[historyVarp(i, "obj")]
            if (obj == 0) return@mapNotNull null
            Trade(
                item = (obj and HISTORY_SOLD_BIT.inv()) - 1,
                sold = obj and HISTORY_SOLD_BIT != 0,
                count = player.vars[historyVarp(i, "count")],
                gold = player.vars[historyVarp(i, "gold")],
                tax = player.vars[historyVarp(i, "tax")],
            )
        }

    /** Adds a finished offer with at least one item traded to the front of the history. */
    fun recordHistory(player: Player, offer: Offer) {
        if (offer.done <= 0) return
        val tax = if (offer.sell) offer.done * taxPerItem(offer.item, offer.gold / offer.done) else 0
        val entries =
            listOf(Trade(offer.item, offer.sell, offer.done, offer.gold, tax)) +
                history(player).take(HISTORY_SIZE - 1)
        for (i in 0 until HISTORY_SIZE) {
            val trade = entries.getOrNull(i)
            val obj = trade?.let { (it.item + 1) or (if (it.sold) HISTORY_SOLD_BIT else 0) } ?: 0
            set(player, historyVarp(i, "obj"), obj)
            set(player, historyVarp(i, "count"), trade?.count ?: 0)
            set(player, historyVarp(i, "gold"), trade?.gold ?: 0)
            set(player, historyVarp(i, "tax"), trade?.tax ?: 0)
        }
    }

    fun stepSize(count: Int): Int = ((count + FILL_STEPS - 1) / FILL_STEPS).coerceAtLeast(1)

    sealed interface StepResult {
        /** Something filled; the offer may have more to fill. */
        data object Filled : StepResult

        /** Nothing filled because of the buy limit; try again later. */
        data object LimitBlocked : StepResult

        /** Nothing to do for this offer (not open, or priced away from the guide). */
        data object Idle : StepResult
    }

    /**
     * Fills the next part of every open offer the market takes (see the class comment). Returns
     * what happened per slot.
     */
    fun fillOpenOffers(
        player: Player,
        now: Int = clock(),
        guidePrice: (ItemServerType) -> Int,
    ): Map<Int, StepResult> {
        val results = mutableMapOf<Int, StepResult>()
        for (slot in 0 until SLOTS) {
            val offer = offer(player, slot)
            val type = type(offer.item) ?: continue
            val guide = guidePrice(type)
            if (!canFill(offer, guide)) continue
            results[slot] = fillStep(player, offer, type, guide, now)
        }
        return results
    }

    private fun fillStep(player: Player, offer: Offer, type: ItemServerType, guide: Int, now: Int): StepResult {
        val remaining = offer.count - offer.done
        var amount = minOf(stepSize(offer.count), remaining)
        if (!offer.sell) amount = minOf(amount, limitRemaining(player, offer.item, now))
        if (amount <= 0) return StepResult.LimitBlocked
        val inv = collectionInv(player, offer.slot)
        val gold = amount * guide
        if (offer.sell) {
            addTo(inv, 1, coinsId, gold - amount * taxPerItem(offer.item, guide))
        } else {
            addTo(inv, 0, offer.item, amount)
            val refund = amount * (offer.price - guide)
            if (refund > 0) addTo(inv, 1, coinsId, refund)
            recordBought(player, offer.item, amount, now)
        }
        val done = offer.done + amount
        val finished = done >= offer.count
        val status = if (finished) finishedStatus(offer.sell) else offer.status
        val updated = offer.copy(done = done, gold = offer.gold + gold, status = status)
        store(player, updated)
        sendSlot(player, offer.slot)
        if (finished) recordHistory(player, updated)
        val verb = if (offer.sell) "selling" else "buying"
        if (finished) {
            player.mes("Grand Exchange: Finished $verb ${offer.count.commas()} x ${type.name}.")
        } else {
            val past = if (offer.sell) "Sold" else "Bought"
            player.mes("Grand Exchange: $past ${done.commas()} / ${offer.count.commas()} x ${type.name}.")
        }
        return StepResult.Filled
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
        val aborted = offer.copy(status = finishedStatus(offer.sell))
        store(player, aborted)
        sendSlot(player, slot)
        recordHistory(player, aborted)
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
