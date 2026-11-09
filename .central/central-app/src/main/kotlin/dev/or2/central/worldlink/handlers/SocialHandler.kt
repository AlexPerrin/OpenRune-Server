package dev.or2.central.worldlink.handlers

import dev.or2.central.social.SocialService
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldChatFiltersPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldFriendAddPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldFriendDelPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldIgnoreAddPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldIgnoreDelPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldPmRelayPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldSocialSyncPacket

class SocialHandler(
    private val socialService: SocialService,
) {
    fun handle(
        connection: WorldConnection,
        opcode: Int,
        input: FrameReader,
    ): HandlerResult {
        if (!connection.handshakeDone) {
            return socialNotAllowed(opcode)
        }
        val reply =
            try {
                when (opcode) {
                    WorldOpcodes.OP_WORLD_PM_RELAY ->
                        socialService.handlePmRelay(connection.worldId, WorldPmRelayPacket.decode(input))
                    WorldOpcodes.OP_WORLD_FRIEND_ADD ->
                        socialService.handleFriendAdd(connection.worldId, WorldFriendAddPacket.decode(input))
                    WorldOpcodes.OP_WORLD_FRIEND_DEL ->
                        socialService.handleFriendDel(connection.worldId, WorldFriendDelPacket.decode(input))
                    WorldOpcodes.OP_WORLD_IGNORE_ADD ->
                        socialService.handleIgnoreAdd(connection.worldId, WorldIgnoreAddPacket.decode(input))
                    WorldOpcodes.OP_WORLD_IGNORE_DEL ->
                        socialService.handleIgnoreDel(connection.worldId, WorldIgnoreDelPacket.decode(input))
                    WorldOpcodes.OP_WORLD_CHAT_FILTERS ->
                        socialService.handleChatFilters(connection.worldId, WorldChatFiltersPacket.decode(input))
                    WorldOpcodes.OP_WORLD_SOCIAL_SYNC ->
                        socialService.handleSocialSync(connection.worldId, WorldSocialSyncPacket.decode(input))
                    else -> return socialNotAllowed(opcode)
                }
            } catch (_: PacketDecodeException) {
                return socialNotAllowed(opcode)
            }

        return when (reply) {
            is SocialService.SocialReply.Ok -> HandlerResult.Reply(reply.frame)
            is SocialService.SocialReply.Fail -> HandlerResult.Reply(reply.frame)
        }
    }

    private fun socialNotAllowed(opcode: Int): HandlerResult =
        HandlerResult.Reply(
            if (opcode == WorldOpcodes.OP_WORLD_SOCIAL_SYNC) {
                dev.or2.central.worldlink.protocol.social.SocialPackets.encodeSocialSyncFail(
                    WorldOpcodes.SOCIAL_FAIL_NOT_ALLOWED,
                )
            } else {
                dev.or2.central.worldlink.protocol.social.SocialPackets.encodeSocialFail(
                    WorldOpcodes.SOCIAL_FAIL_NOT_ALLOWED,
                )
            },
        )
}
