package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatSetRank
import org.rsmod.api.net.friendchat.FriendChats
import org.rsmod.game.entity.Player

class FriendChatSetRankHandler @Inject constructor(private val friendChats: FriendChats) :
    MessageHandler<FriendChatSetRank> {
    override fun handle(player: Player, message: FriendChatSetRank) {
        friendChats.setRank(player, message.name, message.rank)
    }
}
