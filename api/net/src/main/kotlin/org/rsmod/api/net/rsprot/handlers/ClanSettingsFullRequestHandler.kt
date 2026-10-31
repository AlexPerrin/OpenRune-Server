package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.ClanSettingsFullRequest
import org.rsmod.api.net.clan.Clans
import org.rsmod.game.entity.Player

class ClanSettingsFullRequestHandler @Inject constructor(private val clans: Clans) : MessageHandler<ClanSettingsFullRequest> {
    override fun handle(player: Player, message: ClanSettingsFullRequest) {
        if (message.clanId == 0) {
            clans.resendSettings(player)
        }
    }
}
