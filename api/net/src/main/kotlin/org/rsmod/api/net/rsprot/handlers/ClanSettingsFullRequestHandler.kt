package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.ClanSettingsFullRequest
import org.rsmod.api.net.clan.Clans
import org.rsmod.game.entity.Player

class ClanSettingsFullRequestHandler @Inject constructor(private val clans: Clans) : MessageHandler<ClanSettingsFullRequest> {
    override fun handle(player: Player, message: ClanSettingsFullRequest) {
        when {
            message.clanId == 0 -> clans.resendSettings(player)
            message.clanId < 0 -> clans.resendSettings(player, guest = true)
        }
    }
}
