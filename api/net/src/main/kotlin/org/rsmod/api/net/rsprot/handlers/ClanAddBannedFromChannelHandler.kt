package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.AffinedClanSettingsAddBannedFromChannel
import org.rsmod.api.net.clan.Clans
import org.rsmod.game.entity.Player

class ClanAddBannedFromChannelHandler @Inject constructor(private val clans: Clans) :
    MessageHandler<AffinedClanSettingsAddBannedFromChannel> {
    override fun handle(player: Player, message: AffinedClanSettingsAddBannedFromChannel) {
        if (message.clanId == 0) {
            clans.banGuest(player, message.memberIndex, message.name)
        }
    }
}
