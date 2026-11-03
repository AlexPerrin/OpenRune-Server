package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.social.SocialPackets
import dev.or2.central.worldlink.protocol.social.SocialSyncSnapshot

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_WORLD_SOCIAL_OK,
    name = "WORLD_SOCIAL_OK",
    allowedBodyBytes = [0],
)
object WorldSocialOkPacket : OutboundPacket<Unit> {
    override fun encode(payload: Unit): ByteArray = SocialPackets.encodeSocialOk()
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_WORLD_SOCIAL_SYNC_FAIL,
    name = "WORLD_SOCIAL_SYNC_FAIL",
    fields = [FieldKind.BYTE],
)
object WorldSocialSyncFailPacket : OutboundPacket<WorldSocialSyncFailPacket.Payload> {
    data class Payload(val reason: Int)

    override fun encode(payload: Payload): ByteArray = SocialPackets.encodeSocialSyncFail(payload.reason)
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_WORLD_SOCIAL_SYNC_OK,
    name = "WORLD_SOCIAL_SYNC_OK",
    fields = [
        FieldKind.BYTE,
        FieldKind.BYTE,
        FieldKind.BYTE,
        FieldKind.SOCIAL_FRIEND_LIST,
        FieldKind.SOCIAL_IGNORE_LIST,
    ],
)
object WorldSocialSyncOkPacket : OutboundPacket<SocialSyncSnapshot> {
    override fun encode(payload: SocialSyncSnapshot): ByteArray = SocialPackets.encodeSocialSyncOk(payload)

    fun decode(input: FrameReader): SocialSyncSnapshot = SocialPackets.decodeSocialSyncOk(input)
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_SERVER_PRIVATE_MESSAGE,
    name = "SERVER_PRIVATE_MESSAGE",
    fields = [
        FieldKind.INT,
        FieldKind.INT,
        FieldKind.INT,
        FieldKind.BYTE,
        FieldKind.STRING_96,
        FieldKind.STRING_PM,
    ],
)
object ServerPrivateMessagePacket : OutboundPacket<dev.or2.central.worldlink.protocol.social.PrivateMessagePush> {
    override fun encode(payload: dev.or2.central.worldlink.protocol.social.PrivateMessagePush): ByteArray =
        SocialPackets.encodeServerPrivateMessage(payload)
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_SERVER_FRIEND_PRESENCE,
    name = "SERVER_FRIEND_PRESENCE",
    fields = [
        FieldKind.INT,
        FieldKind.INT,
        FieldKind.INT,
        FieldKind.STRING_96,
        FieldKind.STRING_96,
    ],
)
object ServerFriendPresencePacket : OutboundPacket<dev.or2.central.worldlink.protocol.social.FriendPresencePush> {
    override fun encode(payload: dev.or2.central.worldlink.protocol.social.FriendPresencePush): ByteArray =
        SocialPackets.encodeServerFriendPresence(payload)
}
