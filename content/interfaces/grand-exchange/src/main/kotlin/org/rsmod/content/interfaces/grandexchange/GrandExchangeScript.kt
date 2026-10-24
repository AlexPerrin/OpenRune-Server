package org.rsmod.content.interfaces.grandexchange

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.interfaces.grandexchange.GrandExchange.commas
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(UncheckedType::class)
class GrandExchangeScript @Inject constructor(private val marketPrices: MarketPrices) :
    PluginScript() {
    private val ItemServerType.guide: Int
        get() = (marketPrices[this] ?: 1).coerceAtLeast(1)

    override fun ScriptContext.startup() {
        for (clerk in CLERKS) {
            onOpNpc3(clerk) { openExchange() }
            onApNpc3(clerk) { if (isWithinApRange(it.npc, distance = 2)) openExchange() }
            onOpNpc4(clerk) { openHistory() }
            onApNpc4(clerk) { if (isWithinApRange(it.npc, distance = 2)) openHistory() }
        }

        onPlayerLogin {
            val now = GrandExchange.clock()
            val logout = player.vars[GrandExchange.LOGOUT_VARP].takeIf { it > 0 } ?: now
            GrandExchange.catchUp(player, logout, now) { it.guide }
            for (slot in 0 until GrandExchange.SLOTS) {
                player.startInvTransmit(GrandExchange.collectionInv(player, slot))
                GrandExchange.sendSlot(player, slot)
            }
            resetItemSinkVars(player)
            if (hasFillable(player)) player.softTimer(GrandExchange.FILL_TIMER, 1)
        }
        onPlayerLogout { GrandExchange.set(player, GrandExchange.LOGOUT_VARP, GrandExchange.clock()) }
        onPlayerSoftTimer(GrandExchange.FILL_TIMER) {
            player.clearSoftTimer(GrandExchange.FILL_TIMER)
            val results = GrandExchange.fillOpenOffers(player) { it.guide }
            when {
                hasFillable(player, ignoreLimit = false) ->
                    player.softTimer(GrandExchange.FILL_TIMER, GrandExchange.FILL_INTERVAL)
                GrandExchange.StepResult.LimitBlocked in results.values || hasFillable(player) ->
                    player.softTimer(GrandExchange.FILL_TIMER, GrandExchange.LIMIT_RECHECK)
            }
        }

        for (slot in 0 until GrandExchange.SLOTS) {
            onIfModalButton("component.ge_offers:index_$slot") { indexOp(slot, it.comsub) }
            onIfModalButton("component.ge_collect:collect_$slot") {
                if (it.comsub in 3..4) collect(slot, it.comsub - 3, it.op)
            }
        }
        onIfModalButton("component.ge_offers:back") { backToIndex() }
        onIfModalButton("component.ge_offers:history") { openHistory() }
        onIfModalButton("component.ge_history:exchange") { openExchange() }
        onIfModalButton("component.ge_history:list") { historyOp(it.comsub, it.op) }
        onIfModalButton("component.ge_offers:setup") { setupOp(it.comsub, it.op) }
        onIfModalButton("component.ge_offers:setup_confirm") { confirm() }
        onIfModalButton("component.ge_offers:details_status") {
            if (it.comsub == 0) abortSelected()
        }
        onIfModalButton("component.ge_offers:details_collect") {
            val slot = selectedSlot() ?: return@onIfModalButton
            if (it.comsub in 2..3) collect(slot, it.comsub - 2, it.op)
        }
        onIfModalButton("component.ge_offers:collectall") {
            if (it.comsub == 0) collectAll(toBank = it.op == IfButtonOp.Op2)
        }
        onIfModalButton("component.ge_offers_side:items") { offerFromInv(it.comsub, it.op) }
        onIfModalButton("component.ge_collect:collect_inv") { collectAll(toBank = false) }
        onIfModalButton("component.ge_collect:collect_bank") { collectAll(toBank = true) }
        onIfClose("interface.ge_offers") { resetSetup(player, slot = 0) }
        onIfOpen("interface.ge_collect") { setCollectionBoxEvents(player) }
    }

    /** Whether an open offer can fill now (with [ignoreLimit] false: and its buy limit allows it). */
    private fun hasFillable(player: Player, ignoreLimit: Boolean = true): Boolean =
        (0 until GrandExchange.SLOTS).any { slot ->
            val offer = GrandExchange.offer(player, slot)
            val type = GrandExchange.type(offer.item)
            type != null &&
                GrandExchange.canFill(offer, type.guide) &&
                (ignoreLimit || offer.sell || GrandExchange.limitRemaining(player, offer.item) > 0)
        }

    private fun resetItemSinkVars(player: Player) {
        for (slot in 0 until GrandExchange.SLOTS) {
            GrandExchange.set(player, "varp.ge_itemsink_obj_$slot", -1)
        }
    }

    private fun resetSetup(player: Player, slot: Int) {
        GrandExchange.setClientVar(player, "varp.tradingpost_search", -1)
        GrandExchange.setClientVar(player, "varbit.ge_newoffer_type", 0)
        GrandExchange.setClientVar(player, "varbit.ge_newoffer_quantity", 0)
        GrandExchange.sendSetupPrice(player, 0)
        GrandExchange.setClientVar(player, "varbit.ge_selectedslot", slot)
    }

    private fun ProtectedAccess.openExchange() {
        resetItemSinkVars(player)
        resetSetup(player, slot = 0)
        vars["varbit.ge_transmit_taxrate"] = GrandExchange.TAX_RATE_PER_MILLE
        invTransmit(inv)
        ifOpenMainSidePair(main = "interface.ge_offers", side = "interface.ge_offers_side")
        for (slot in 0 until GrandExchange.SLOTS) {
            ifSetEvents("component.ge_offers:index_$slot", 0..4, IfEvent.Op1)
        }
        ifSetEvents("component.ge_offers:setup", 0..15, IfEvent.Op1, IfEvent.Op2)
        ifSetEvents("component.ge_offers:details_status", 0..1, IfEvent.Op1)
        ifSetEvents(
            "component.ge_offers:details_collect",
            2..3,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op10,
        )
        ifSetEvents("component.ge_offers:collectall", 0..1, IfEvent.Op1, IfEvent.Op2)
        ifSetEvents("component.ge_offers_side:items", inv.indices, IfEvent.Op1, IfEvent.Op10)
    }

    /** The trade history (`ge_history` 383), drawn by scripts 1644-1646, newest first. */
    private fun ProtectedAccess.openHistory() {
        ifOpenMain("interface.ge_history")
        player.runClientScript(HISTORY_INIT_SCRIPT)
        GrandExchange.history(player).forEachIndexed { i, trade ->
            player.runClientScript(
                HISTORY_ADDLINE_SCRIPT,
                i,
                trade.item,
                if (trade.sold) 1 else 0,
                trade.count,
                trade.gold.toLong(),
                trade.tax.toLong(),
            )
        }
        player.runClientScript(HISTORY_FINISH_SCRIPT)
        ifSetEvents(
            "component.ge_history:list",
            0 until GrandExchange.HISTORY_SIZE * HISTORY_LINE_COMPONENTS,
            IfEvent.Op1,
            IfEvent.Op10,
        )
    }

    /** "Buy-offer" on a history line opens a buy offer for its item; "Examine" examines it. */
    private fun ProtectedAccess.historyOp(comsub: Int, op: IfButtonOp) {
        val trade = GrandExchange.history(player).getOrNull(comsub / HISTORY_LINE_COMPONENTS) ?: return
        val type = GrandExchange.type(trade.item) ?: return
        if (op == IfButtonOp.Op10) {
            mes(type.examine)
            return
        }
        val slot = firstFreeSlot()
        openExchange()
        if (slot == null) {
            mes("You have no free offer slots.")
            return
        }
        resetSetup(player, slot + 1)
        selectItem(type, quantity = 1)
    }

    private fun setCollectionBoxEvents(player: Player) {
        for (slot in 0 until GrandExchange.SLOTS) {
            player.ifSetEvents(
                "component.ge_collect:collect_$slot",
                3..4,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op10,
            )
        }
    }

    private fun ProtectedAccess.selectedSlot(): Int? =
        (vars["varbit.ge_selectedslot"] - 1).takeIf { it in 0 until GrandExchange.SLOTS }

    private fun ProtectedAccess.backToIndex() {
        resetSetup(player, slot = 0)
    }

    private suspend fun ProtectedAccess.indexOp(slot: Int, comsub: Int) {
        val offer = GrandExchange.offer(player, slot)
        when {
            !offer.empty || !GrandExchange.collectionInv(player, slot).isEmpty() ->
                GrandExchange.setClientVar(player, "varbit.ge_selectedslot", slot + 1)
            comsub == BUY_BUTTON -> startBuy(slot)
            comsub == SELL_BUTTON -> startSell(slot)
        }
    }

    private suspend fun ProtectedAccess.startBuy(slot: Int) {
        resetSetup(player, slot + 1)
        clearSetupText()
        val type = objDialog("What would you like to buy?", showLastSearched = true)
        if (vars["varbit.ge_selectedslot"] != slot + 1) return
        val item = GrandExchange.tradeable(type)
        if (item == null) {
            mes("You can't buy that item on the Grand Exchange.")
            return
        }
        selectItem(item, quantity = 1)
    }

    private fun ProtectedAccess.clearSetupText() {
        player.runClientScript(SETDESC_SCRIPT, "", "", SETUP_DESC, SETUP_FEE)
        ifSetText("component.ge_offers:setup_marketprice", "")
    }

    private fun ProtectedAccess.startSell(slot: Int) {
        resetSetup(player, slot + 1)
        clearSetupText()
        GrandExchange.setClientVar(player, "varbit.ge_newoffer_type", 1)
    }

    private fun ProtectedAccess.offerFromInv(invSlot: Int, op: IfButtonOp) {
        val obj = inv[invSlot] ?: return
        val type = GrandExchange.type(obj.id) ?: return
        if (op == IfButtonOp.Op10) {
            mes(type.examine)
            return
        }
        val item = GrandExchange.tradeable(type)
        if (item == null) {
            mes("You can't sell that item on the Grand Exchange.")
            return
        }
        val slot = selectedSlot() ?: firstFreeSlot()
        if (slot == null) {
            mes("You have no free offer slots.")
            return
        }
        val offer = GrandExchange.offer(player, slot)
        if (!offer.empty || !GrandExchange.collectionInv(player, slot).isEmpty()) return
        if (selectedSlot() != slot) resetSetup(player, slot + 1)
        GrandExchange.setClientVar(player, "varbit.ge_newoffer_type", 1)
        selectItem(item, quantity = GrandExchange.heldCount(inv, item.id))
    }

    private fun ProtectedAccess.firstFreeSlot(): Int? =
        (0 until GrandExchange.SLOTS).firstOrNull { slot ->
            GrandExchange.offer(player, slot).empty &&
                GrandExchange.collectionInv(player, slot).isEmpty()
        }

    private fun ProtectedAccess.selectItem(item: ItemServerType, quantity: Int) {
        GrandExchange.setClientVar(player, "varp.tradingpost_search", item.id)
        GrandExchange.setClientVar(player, "varbit.ge_newoffer_quantity", quantity)
        GrandExchange.sendSetupPrice(player, item.guide)
        player.runClientScript(SETDESC_SCRIPT, item.examine, "", SETUP_DESC, SETUP_FEE)
        ifSetText("component.ge_offers:setup_marketprice", "${item.guide.commas()} coins")
    }

    private fun ProtectedAccess.setupItem(): ItemServerType? =
        GrandExchange.type(vars["varp.tradingpost_search"])?.takeIf {
            vars["varp.tradingpost_search"] >= 0 && selectedSlot() != null
        }

    private suspend fun ProtectedAccess.setupOp(comsub: Int, op: IfButtonOp) {
        if (comsub == CHOOSE_ITEM) {
            val slot = selectedSlot() ?: return
            if (vars["varbit.ge_newoffer_type"] == 0) startBuy(slot)
            return
        }
        val item = setupItem() ?: return
        val sell = vars["varbit.ge_newoffer_type"] == 1
        val quantity = vars["varbit.ge_newoffer_quantity"]
        val price = vars["varp.ge_setup_price"]
        when (comsub) {
            in QUANTITY_DELTAS.keys -> {
                if (op != IfButtonOp.Op1) return
                val held = GrandExchange.heldCount(inv, item.id)
                setQuantity(changeQuantity(sell, quantity, QUANTITY_DELTAS.getValue(comsub), held))
            }
            ENTER_QUANTITY -> {
                if (op == IfButtonOp.Op2 && !sell) {
                    val affordable = coinsHeld() / price.coerceAtLeast(1)
                    setQuantity(affordable.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
                    return
                }
                val verb = if (sell) "sell" else "buy"
                val amount = countDialog("How many do you wish to $verb?")
                if (amount > 0) setQuantity(amount)
            }
            in PRICE_DELTAS.keys -> {
                if (op != IfButtonOp.Op1) return
                GrandExchange.sendSetupPrice(player, changePrice(price, PRICE_DELTAS.getValue(comsub)))
            }
            GUIDE_PRICE -> GrandExchange.sendSetupPrice(player, item.guide)
            ENTER_PRICE -> {
                val amount = countDialog("Set a price for each item:")
                if (amount > 0) GrandExchange.sendSetupPrice(player, amount)
            }
            else -> mes("That button is not available on this server.")
        }
    }

    private fun ProtectedAccess.setQuantity(quantity: Int) {
        GrandExchange.setClientVar(player, "varbit.ge_newoffer_quantity", quantity)
    }

    fun ProtectedAccess.confirm(): Boolean {
        val slot = selectedSlot() ?: return false
        val item = setupItem() ?: return false
        val sell = vars["varbit.ge_newoffer_type"] == 1
        val quantity = vars["varbit.ge_newoffer_quantity"]
        val price = vars["varp.ge_setup_price"]
        if (quantity <= 0 || price <= 0) return false
        if (!GrandExchange.offer(player, slot).empty) return false
        if (!GrandExchange.collectionInv(player, slot).isEmpty()) return false
        val total = quantity.toLong() * price
        if (total > GrandExchange.MAX_TOTAL) {
            mes("That offer is worth too much money.")
            return false
        }
        if (sell) {
            if (GrandExchange.heldCount(inv, item.id) < quantity) {
                mes("You don't have enough of that item.")
                return false
            }
            if (!takeItems(inv, item, quantity)) return false
        } else {
            if (coinsHeld() < total) {
                mes("You don't have enough coins.")
                return false
            }
            if (!takeCoins(total.toInt())) return false
        }
        val offer =
            GrandExchange.Offer(
                slot = slot,
                item = item.id,
                price = price,
                count = quantity,
                done = 0,
                gold = 0,
                status = GrandExchange.openStatus(sell),
            )
        GrandExchange.store(player, offer)
        GrandExchange.sendSlot(player, slot)
        resetSetup(player, slot = 0)
        softTimer(GrandExchange.FILL_TIMER, GrandExchange.FILL_DELAY)
        return true
    }

    private fun ProtectedAccess.coinsHeld(): Long =
        inv.count("obj.coins").toLong() + bank.count("obj.coins")

    /** Buy offers pay from the inventory first and take the rest from the bank. */
    private fun ProtectedAccess.takeCoins(total: Int): Boolean {
        val fromInv = minOf(inv.count("obj.coins"), total)
        val fromBank = total - fromInv
        if (bank.count("obj.coins") < fromBank) return false
        if (fromInv > 0 && invDel(inv, "obj.coins", fromInv).failure) return false
        if (fromBank > 0 && invDel(bank, "obj.coins", fromBank).failure) {
            invAdd(inv, "obj.coins", fromInv)
            return false
        }
        return true
    }

    private fun ProtectedAccess.takeItems(inv: Inventory, item: ItemServerType, count: Int): Boolean {
        val unnoted = GrandExchange.internal(item.id)
        val held = inv.count(unnoted).coerceAtMost(count)
        val noted = count - held
        if (noted > 0 && !item.canCert) return false
        val notedName = if (item.canCert) GrandExchange.internal(item.certlink) else null
        val result =
            if (noted > 0 && held > 0) {
                invDel(inv, unnoted, held, notedName!!, noted)
            } else if (noted > 0) {
                invDel(inv, notedName!!, noted)
            } else {
                invDel(inv, unnoted, held)
            }
        return result.success
    }

    private fun ProtectedAccess.abortSelected() {
        val slot = selectedSlot() ?: return
        if (GrandExchange.abort(player, slot)) {
            mes(
                "Abort request acknowledged. Please be aware that your offer may " +
                    "have already been completed."
            )
        }
    }

    fun ProtectedAccess.collect(slot: Int, invSlot: Int, op: IfButtonOp): Boolean {
        val collection = GrandExchange.collectionInv(player, slot)
        val obj = collection[invSlot] ?: return false
        if (op == IfButtonOp.Op10) {
            GrandExchange.type(obj.id)?.let { mes(it.examine) }
            return false
        }
        val type = GrandExchange.type(obj.id) ?: return false
        val toBank = op == IfButtonOp.Op3
        val note =
            when (op) {
                IfButtonOp.Op1 -> obj.count > 1
                IfButtonOp.Op2 -> obj.count == 1
                else -> false
            } && type.canCert
        if (!collectInto(collection, invSlot, toBank, note)) return false
        if (GrandExchange.clearIfCollected(player, slot) && selectedSlot() == slot) {
            resetSetup(player, slot = 0)
        }
        return true
    }

    fun ProtectedAccess.collectAll(toBank: Boolean) {
        for (slot in 0 until GrandExchange.SLOTS) {
            val collection = GrandExchange.collectionInv(player, slot)
            for (invSlot in 0 until collection.size) {
                val obj = collection[invSlot] ?: continue
                val type = GrandExchange.type(obj.id) ?: continue
                if (!collectInto(collection, invSlot, toBank, note = type.canCert && obj.count > 1)) {
                    return
                }
            }
            GrandExchange.clearIfCollected(player, slot)
        }
        if (selectedSlot()?.let { GrandExchange.offer(player, it).empty } == true) {
            resetSetup(player, slot = 0)
        }
    }

    private fun ProtectedAccess.collectInto(
        collection: Inventory,
        invSlot: Int,
        toBank: Boolean,
        note: Boolean,
    ): Boolean {
        val obj = collection[invSlot] ?: return true
        val name = GrandExchange.internal(obj.id)
        val into = if (toBank) bank else inv
        val added = invAdd(into, name, obj.count, cert = note && !toBank)
        if (added.failure) {
            mes(if (toBank) "Your bank is too full." else "You don't have enough inventory space.")
            return false
        }
        collection[invSlot] = null
        return true
    }

    companion object {
        val CLERKS = (1..4).map { "npc.ge_clerk_$it" }

        const val BUY_BUTTON = 3
        const val SELL_BUTTON = 4

        const val CHOOSE_ITEM = 0
        const val ENTER_QUANTITY = 7
        const val GUIDE_PRICE = 11
        const val ENTER_PRICE = 12
        val QUANTITY_DELTAS = mapOf(1 to -1, 2 to 1, 3 to 1, 4 to 10, 5 to 100, 6 to Int.MAX_VALUE)
        val PRICE_DELTAS = mapOf(8 to -1, 9 to 1, 10 to -5, 13 to 5)

        /** Components `ge_history_addline` creates per line (its sub ids are line * 6 + n). */
        const val HISTORY_LINE_COMPONENTS = 6
        val HISTORY_INIT_SCRIPT: Int by lazy {
            "clientscript.[clientscript,ge_history_init]".asRSCM(RSCMType.CLIENTSCRIPT)
        }
        val HISTORY_ADDLINE_SCRIPT: Int by lazy {
            "clientscript.[clientscript,ge_history_addline]".asRSCM(RSCMType.CLIENTSCRIPT)
        }
        val HISTORY_FINISH_SCRIPT: Int by lazy {
            "clientscript.[clientscript,ge_history_finish]".asRSCM(RSCMType.CLIENTSCRIPT)
        }

        val SETDESC_SCRIPT: Int by lazy {
            "clientscript.[clientscript,ge_offers_setdesc]".asRSCM(RSCMType.CLIENTSCRIPT)
        }
        val SETUP_DESC: Int by lazy { "component.ge_offers:setup_desc".asRSCM(RSCMType.COMPONENT) }
        val SETUP_FEE: Int by lazy { "component.ge_offers:setup_fee".asRSCM(RSCMType.COMPONENT) }

        /** Mirrors `ge_offers_setup_changequantity` (script 777), which the client runs too. */
        fun changeQuantity(sell: Boolean, current: Int, requested: Int, held: Int): Int {
            var delta = requested
            if (sell) {
                if (delta >= Int.MAX_VALUE) return held
                if (delta > 0) {
                    if (delta > 1 && current == 1) delta -= 1
                    return if (held - delta < current) held else current + delta
                }
            } else {
                if (delta >= Int.MAX_VALUE) delta = 1000
                if (delta > 0) {
                    if (delta > 1 && current == 1) delta -= 1
                    return if (Int.MAX_VALUE - delta < current) Int.MAX_VALUE else current + delta
                }
            }
            return if (current <= -delta) 1 else current + delta
        }

        /** Mirrors `ge_offers_setup_changeprice` (script 778), capped at [Int.MAX_VALUE]. */
        fun changePrice(current: Int, step: Int): Int {
            val long = current.toLong()
            val next =
                when (step) {
                    1 -> long + 1
                    -1 -> long - 1
                    5 -> long + (long / 20).coerceAtLeast(1)
                    -5 -> long - (long / 20).coerceAtLeast(1)
                    else -> long
                }
            return next.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
