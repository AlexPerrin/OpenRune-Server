package org.rsmod.api.player.interact

import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo

/**
 * Lets content take a loc op ahead of that loc's own handler, but only for the players it claims:
 * a treasure trail step that searches a crate, for example, while everyone else gets the crate's
 * usual op. A claimed op is published as an [org.rsmod.api.player.events.interact.
 * LocOverrideEvents.Op] keyed by the loc type.
 */
@Singleton
public class LocOpOverrides {
    private val claims = HashMap<Long, MutableList<(Player, BoundLocInfo) -> Boolean>>()

    public fun register(locType: Int, op: InteractionOp, claim: (Player, BoundLocInfo) -> Boolean) {
        claims.getOrPut(key(locType, op)) { mutableListOf() } += claim
    }

    public fun claims(player: Player, loc: BoundLocInfo, locType: Int, op: InteractionOp): Boolean {
        val list = claims[key(locType, op)] ?: return false
        return list.any { it(player, loc) }
    }

    private fun key(locType: Int, op: InteractionOp): Long =
        (locType.toLong() shl 8) or op.ordinal.toLong()
}
