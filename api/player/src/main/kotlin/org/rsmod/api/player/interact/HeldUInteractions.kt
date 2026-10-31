package org.rsmod.api.player.interact

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.interact.HeldUContentEvents
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType

public class HeldUInteractions @Inject constructor(private val eventBus: EventBus) {
    private val logger = InlineLogger()

    public suspend fun interact(
        access: ProtectedAccess,
        inventory: Inventory,
        selectedItemServerType: ItemServerType,
        selectedSlot: Int,
        targetItemServerType: ItemServerType,
        targetSlot: Int,
    ) {
        if (selectedSlot == targetSlot) {
            resendSlot(inventory, 0)
            return
        }

        val selectedObj = inventory[selectedSlot]
        if (!objectVerify(inventory, selectedObj, selectedItemServerType)) {
            return
        }

        val targetObj = inventory[targetSlot]
        if (!objectVerify(inventory, targetObj, targetItemServerType)) {
            return
        }

        access.opHeldU(selectedItemServerType, selectedSlot, targetItemServerType, targetSlot)
    }

    /**
     * Picks the script for [selectedItemServerType] used on [targetItemServerType]. Scripts for the
     * pair (type or content group, in either order) win over "used on anything" defaults, so a
     * default can never shadow a pair registered the other way round. Of the defaults, the
     * target's comes first (like RuneScript's `[opheldu,<target obj>]` trigger, which gets the
     * used obj as `last_useitem`), then the selected obj's.
     */
    private suspend fun ProtectedAccess.opHeldU(
        selectedItemServerType: ItemServerType,
        selectedSlot: Int,
        targetItemServerType: ItemServerType,
        targetSlot: Int,
    ) {
        val trigger =
            pairTrigger(selectedItemServerType, selectedSlot, targetItemServerType, targetSlot)
                ?: pairTrigger(targetItemServerType, targetSlot, selectedItemServerType, selectedSlot)
                ?: defaultTrigger(
                    targetItemServerType,
                    targetSlot,
                    selectedItemServerType,
                    selectedSlot,
                )
                ?: defaultTrigger(
                    selectedItemServerType,
                    selectedSlot,
                    targetItemServerType,
                    targetSlot,
                )
        if (trigger != null) {
            eventBus.publish(this, trigger)
            return
        }

        mes(constants.dm_default, ChatType.GameMessage)
        logger.debug {
            "opHeldU for `${selectedItemServerType.name}` on `${targetItemServerType.name}` is not " +
                "implemented: selected=$selectedItemServerType, target=$targetItemServerType"
        }
    }

    private fun pairTrigger(
        first: ItemServerType,
        firstSlot: Int,
        second: ItemServerType,
        secondSlot: Int,
    ): SuspendEvent<ProtectedAccess>? {
        val typeScript = HeldUEvents.Type(first, firstSlot, second, secondSlot)
        if (eventBus.contains(typeScript::class.java, typeScript.id)) {
            return typeScript
        }

        val contentTypeScript = HeldUContentEvents.Type(first, firstSlot, second, secondSlot)
        if (eventBus.contains(contentTypeScript::class.java, contentTypeScript.id)) {
            return contentTypeScript
        }

        val contentScript = HeldUContentEvents.Content(first, firstSlot, second, secondSlot)
        if (eventBus.contains(contentScript::class.java, contentScript.id)) {
            return contentScript
        }

        return null
    }

    private fun defaultTrigger(
        first: ItemServerType,
        firstSlot: Int,
        second: ItemServerType,
        secondSlot: Int,
    ): SuspendEvent<ProtectedAccess>? {
        val defaultTypeScript = HeldUDefaultEvents.Type(first, firstSlot, second, secondSlot)
        if (eventBus.contains(defaultTypeScript::class.java, defaultTypeScript.id)) {
            return defaultTypeScript
        }

        val defaultContentScript = HeldUDefaultEvents.Content(first, firstSlot, second, secondSlot)
        if (eventBus.contains(defaultContentScript::class.java, defaultContentScript.id)) {
            return defaultContentScript
        }

        return null
    }

    private fun objectVerify(inv: Inventory, obj: InvObj?, type: ItemServerType): Boolean {
        if (obj == null || !obj.isType(type)) {
            resendSlot(inv, 0)
            return false
        }
        return true
    }
}
