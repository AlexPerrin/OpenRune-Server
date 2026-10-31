package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatKick
import org.rsmod.api.net.friendchat.FriendChats
import org.rsmod.game.entity.Player

class FriendChatKickHandler @Inject constructor(private val friendChats: FriendChats) :
    MessageHandler<FriendChatKick> {
    override fun handle(player: Player, message: FriendChatKick) {
        friendChats.kick(player, message.name)
    }
}
