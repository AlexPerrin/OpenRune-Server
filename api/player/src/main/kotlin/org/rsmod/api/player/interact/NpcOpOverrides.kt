package org.rsmod.api.player.interact

import jakarta.inject.Singleton
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp

/**
 * Lets content take an npc op ahead of that npc's own handler, but only for the players it
 * claims: a treasure trail step on the Cook, for example, while everyone else still gets the
 * Cook's usual dialogue. A claimed op is published as an [org.rsmod.api.player.events.interact.
 * NpcOverrideEvents.Op] keyed by the npc type.
 */
@Singleton
public class NpcOpOverrides {
    private val claims = HashMap<Long, MutableList<(Player, Npc) -> Boolean>>()

    public fun register(npcType: Int, op: InteractionOp, claim: (Player, Npc) -> Boolean) {
        claims.getOrPut(key(npcType, op)) { mutableListOf() } += claim
    }

    public fun claims(player: Player, npc: Npc, npcType: Int, op: InteractionOp): Boolean {
        val list = claims[key(npcType, op)] ?: return false
        return list.any { it(player, npc) }
    }

    private fun key(npcType: Int, op: InteractionOp): Long =
        (npcType.toLong() shl 8) or op.ordinal.toLong()
}
