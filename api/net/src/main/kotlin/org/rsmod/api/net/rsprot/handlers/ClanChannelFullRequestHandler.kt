package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.ClanChannelFullRequest
import org.rsmod.api.net.clan.Clans
import org.rsmod.game.entity.Player

class ClanChannelFullRequestHandler @Inject constructor(private val clans: Clans) : MessageHandler<ClanChannelFullRequest> {
    override fun handle(player: Player, message: ClanChannelFullRequest) {
        if (message.clanId == 0) {
            clans.resendChannel(player)
        }
    }
}
