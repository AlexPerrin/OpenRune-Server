package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatJoinLeave
import org.rsmod.api.net.friendchat.FriendChats
import org.rsmod.game.entity.Player

class FriendChatJoinLeaveHandler @Inject constructor(private val friendChats: FriendChats) :
    MessageHandler<FriendChatJoinLeave> {
    override fun handle(player: Player, message: FriendChatJoinLeave) {
        friendChats.joinOrLeave(player, message.name)
    }
}
