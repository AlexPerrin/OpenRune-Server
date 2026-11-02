package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import org.rsmod.api.net.clan.Clans
import org.rsmod.game.entity.Player

private const val CLAN_CHAT_TYPE = 3

class ClanMessagePublicHandler
@Inject
constructor(private val clans: Clans, private val publicMessage: MessagePublicHandler) :
    MessageHandler<MessagePublic> {
    override fun handle(player: Player, message: MessagePublic) {
        if (message.type != CLAN_CHAT_TYPE) {
            publicMessage.handle(player, message)
        } else {
            clans.message(player, message.message, guest = message.clanType != 0)
        }
    }
}
