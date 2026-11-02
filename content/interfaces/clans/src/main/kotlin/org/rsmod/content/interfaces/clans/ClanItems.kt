package org.rsmod.content.interfaces.clans

import org.rsmod.api.net.clan.Clan
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType

private const val OUTFIT_SETTING = 11

internal enum class ClanItem(val objs: List<String>) {
    Cloak(motifs("obj.clan_cloak")),
    Vexillum(motifs("obj.clan_vexillum")),
}

private fun motifs(prefix: String): List<String> =
    listOf("black", "orange", "blue", "red", "green", "yellow", "lightblue", "pink").map { "${prefix}_$it" }

internal fun Clan.motif(): Int = (((intSettings[OUTFIT_SETTING] ?: 0) shr 20 and 0x3FF) - 1).coerceIn(0, 7)

internal fun ProtectedAccess.takeClanItem(kind: ClanItem, clan: Clan): Boolean =
    invAdd(player.inv, kind.objs[clan.motif()]).success

internal fun Player.recolourClanItems(clan: Clan) {
    val motif = clan.motif()
    var wornChanged = false
    for (inv in listOfNotNull(inv, worn, invMap["inv.bank"])) {
        for (slot in inv.indices) {
            val obj = inv[slot] ?: continue
            for (kind in ClanItem.entries) {
                val colour = kind.objs.indexOfFirst { obj.isType(it) }
                if (colour != -1 && colour != motif) {
                    inv[slot] = InvObj(kind.objs[motif], obj.count, obj.vars)
                    wornChanged = wornChanged || inv === worn
                }
            }
        }
    }
    if (wornChanged) {
        rebuildAppearance()
    }
}
