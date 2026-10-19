package org.rsmod.content.other.lootingbag

import dev.openrune.types.ItemServerType
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.api.player.hook.PlayerObjTakeRedirectHook
import org.rsmod.content.other.lootingbag.LootingBagScript.Companion.BAGS
import org.rsmod.content.other.lootingbag.LootingBagScript.Companion.BAG_INV
import org.rsmod.content.other.lootingbag.LootingBagScript.Companion.BAG_OPEN
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.obj.Obj

/** An open bag takes every tradeable item picked up in the Wilderness (OSRS Wiki, "Usage"). */
public class LootingBagTakeHook : PlayerObjTakeRedirectHook {
    override fun takeInto(player: Player, obj: Obj, objType: ItemServerType): Inventory? =
        player.openBagFor(objType)
}

public fun Player.openBagFor(objType: ItemServerType): Inventory? {
    if (inv.count(BAG_OPEN) == 0) return null
    if (!objType.tradeable || objType.internalName in BAGS) return null
    if (!coords.isInWildernessBasic()) return null
    return invMap.getOrPut(BAG_INV)
}
