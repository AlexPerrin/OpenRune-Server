package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.ClanChannelKickUser
import org.rsmod.api.net.clan.Clans
import org.rsmod.game.entity.Player

class ClanChannelKickUserHandler @Inject constructor(private val clans: Clans) : MessageHandler<ClanChannelKickUser> {
    override fun handle(player: Player, message: ClanChannelKickUser) {
        if (message.clanId == 0) {
            clans.kickGuest(player, message.memberIndex, message.name)
        }
    }
}
