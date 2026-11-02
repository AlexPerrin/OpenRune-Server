package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import org.rsmod.api.net.clan.Clans
import org.rsmod.game.entity.Player

private const val CLAN_CHAT_TYPE = 3
private const val GUEST_CLAN_CHAT_TYPE = 4

class ClanMessagePublicHandler
@Inject
constructor(private val clans: Clans, private val publicMessage: MessagePublicHandler) :
    MessageHandler<MessagePublic> {
    override fun handle(player: Player, message: MessagePublic) {
        when (message.type) {
            CLAN_CHAT_TYPE -> if (message.clanType == 0) clans.message(player, message.message, guest = false)
            GUEST_CLAN_CHAT_TYPE -> clans.message(player, message.message, guest = true)
            else -> publicMessage.handle(player, message)
        }
    }
}
