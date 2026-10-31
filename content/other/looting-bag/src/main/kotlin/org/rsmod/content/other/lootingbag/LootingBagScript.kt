package org.rsmod.content.other.lootingbag

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The looting bag (OSRS Wiki): Open/Close swaps the bag's variant, Check shows the bag's inventory
 * (`inv.looting_bag`, which RuneLite's looting bag plugins read), Deposit and using an item on
 * the bag store tradeable items while in the Wilderness, Settings picks whether using an item on
 * the bag asks how many. Picking items up into an open bag is [LootingBagTakeHook]; emptying it
 * into the bank is the bank's `bankside:lootingbag_items`.
 */
class LootingBagScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(BAG) { swap(it.slot, BAG_OPEN) }
        onOpHeld1(BAG_OPEN) { swap(it.slot, BAG) }
        for (bag in listOf(BAG, BAG_OPEN)) {
            onOpHeld2(bag) { check() }
            onOpHeld3(bag) { openDeposit() }
            onOpHeld4(bag) { settings() }
            onOpHeldU(bag) { useOnBag(it) }
        }
        onIfModalButton(ITEMS) { depositOp(it.comsub, it.op) }
    }

    private fun ProtectedAccess.swap(slot: Int, into: String) {
        val type = ServerCacheManager.getItem(into.asRSCM(RSCMType.OBJ)) ?: return
        invReplaceSlot(inv, slot, 1, type)
    }

    private fun ProtectedAccess.check() {
        openBagInterface(MODE_BAG, "Looting bag")
        ifSetEvents(ITEMS, bag().indices, IfEvent.Op10)
    }

    private suspend fun ProtectedAccess.settings() {
        startDialogue {
            player.storeAll =
                choice2(
                    "... ask how many to store.",
                    false,
                    "... always store as many as possible.",
                    true,
                    title = "When using items on the bag...",
                )
        }
    }

    private fun ProtectedAccess.openDeposit() {
        if (!player.coords.isInWildernessBasic()) {
            mes(OUTSIDE_WILDERNESS)
            return
        }
        openBagInterface(MODE_DEPOSIT, "Add to bag")
        ifSetEvents(ITEMS, inv.indices, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op9)
    }

    private fun ProtectedAccess.openBagInterface(mode: Int, title: String) {
        player.startInvTransmit(bag())
        ifOpenSide(INTERFACE)
        runClientScript(SETUP_SCRIPT, mode, title)
    }

    private suspend fun ProtectedAccess.useOnBag(event: HeldUDefaultEvents.Type) {
        val other = if (event.first.internalName in BAGS) event.second else event.first
        if (!player.coords.isInWildernessBasic()) {
            mes(OUTSIDE_WILDERNESS)
            return
        }
        if (other.internalName in BAGS) {
            mes("You may be surprised to learn that bagception is not permitted.")
            return
        }
        val count = if (player.storeAll) Int.MAX_VALUE else startDialogueChoice() ?: return
        store(other.internalName, count)
    }

    private suspend fun ProtectedAccess.startDialogueChoice(): Int? {
        var count: Int? = null
        startDialogue {
            count =
                choice3(
                    "One",
                    1,
                    "Five",
                    FIVE,
                    "All",
                    Int.MAX_VALUE,
                    title = "How many do you want to deposit?",
                )
        }
        return count
    }

    private suspend fun ProtectedAccess.depositOp(slot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(bag(), slot)
            return
        }
        val obj = inv[slot] ?: return
        if (op == IfButtonOp.Op9) {
            objExamine(inv, slot)
            return
        }
        val internal = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
        val count =
            when (op) {
                IfButtonOp.Op1 -> 1
                IfButtonOp.Op2 -> FIVE
                IfButtonOp.Op3 -> Int.MAX_VALUE
                IfButtonOp.Op4 -> countDialog()
                else -> return
            }
        if (count > 0) store(internal, count)
    }

    /** Moves up to [requested] of [obj] from the inventory into the bag in one transaction. */
    fun ProtectedAccess.store(obj: String, requested: Int): Boolean {
        if (obj in BAGS) {
            mes("You may be surprised to learn that bagception is not permitted.")
            return false
        }
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: return false
        if (!type.tradeable) {
            mes("Only tradeable items can be put in the bag.")
            return false
        }
        val count = minOf(requested, inv.count(obj))
        if (count <= 0) return false
        val bag = bag()
        val moved =
            player.invTransaction(inv, bag) {
                val from = select(inv)
                val into = select(bag)
                delete {
                    this.from = from
                    this.obj = type.id
                    this.strictCount = count
                }
                insert {
                    this.into = into
                    this.obj = type.id
                    this.strictCount = count
                }
            }
        if (moved.failure) {
            mes("The bag's too full.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.bag() = inv(BAG_INV)

    companion object {
        const val BAG = "obj.looting_bag"
        const val BAG_OPEN = "obj.looting_bag_open"
        const val BAG_INV = "inv.looting_bag"
        const val INTERFACE = "interface.wilderness_lootingbag"
        const val ITEMS = "component.wilderness_lootingbag:items"
        const val SETUP_SCRIPT = 495
        const val MODE_BAG = 0
        const val MODE_DEPOSIT = 1
        const val FIVE = 5
        const val OUTSIDE_WILDERNESS = "You can't put items in the bag unless you're in the Wilderness."

        val BAGS = setOf(BAG, BAG_OPEN)

        private var Player.storeAll by boolVarBit("varbit.lootingbag_useallitems")
    }
}
