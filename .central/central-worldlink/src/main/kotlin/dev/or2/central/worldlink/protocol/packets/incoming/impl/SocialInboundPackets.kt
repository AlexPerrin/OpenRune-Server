package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming
import dev.or2.central.worldlink.protocol.social.SocialPackets

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_PM_RELAY,
    name = "WORLD_PM_RELAY",
    fields = [
        FieldKind.INT,
        FieldKind.BYTE,
        FieldKind.STRING_96,
        FieldKind.STRING_96,
        FieldKind.STRING_PM,
    ],
)
object WorldPmRelayPacket : InboundPacket<SocialPackets.PmRelayPayload> {
    override fun decode(input: FrameReader): SocialPackets.PmRelayPayload = SocialPackets.decodePmRelay(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_FRIEND_ADD,
    name = "WORLD_FRIEND_ADD",
    fields = [FieldKind.INT, FieldKind.STRING_96],
)
object WorldFriendAddPacket : InboundPacket<SocialPackets.NameActionPayload> {
    override fun decode(input: FrameReader): SocialPackets.NameActionPayload = SocialPackets.decodeNameAction(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_FRIEND_DEL,
    name = "WORLD_FRIEND_DEL",
    fields = [FieldKind.INT, FieldKind.STRING_96],
)
object WorldFriendDelPacket : InboundPacket<SocialPackets.NameActionPayload> {
    override fun decode(input: FrameReader): SocialPackets.NameActionPayload = SocialPackets.decodeNameAction(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_IGNORE_ADD,
    name = "WORLD_IGNORE_ADD",
    fields = [FieldKind.INT, FieldKind.STRING_96],
)
object WorldIgnoreAddPacket : InboundPacket<SocialPackets.NameActionPayload> {
    override fun decode(input: FrameReader): SocialPackets.NameActionPayload = SocialPackets.decodeNameAction(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_IGNORE_DEL,
    name = "WORLD_IGNORE_DEL",
    fields = [FieldKind.INT, FieldKind.STRING_96],
)
object WorldIgnoreDelPacket : InboundPacket<SocialPackets.NameActionPayload> {
    override fun decode(input: FrameReader): SocialPackets.NameActionPayload = SocialPackets.decodeNameAction(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_CHAT_FILTERS,
    name = "WORLD_CHAT_FILTERS",
    fields = [FieldKind.INT, FieldKind.BYTE, FieldKind.BYTE, FieldKind.BYTE],
)
object WorldChatFiltersPacket : InboundPacket<SocialPackets.ChatFiltersPayload> {
    override fun decode(input: FrameReader): SocialPackets.ChatFiltersPayload = SocialPackets.decodeChatFilters(input)
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_SOCIAL_SYNC,
    name = "WORLD_SOCIAL_SYNC",
    fields = [FieldKind.INT],
)
object WorldSocialSyncPacket : InboundPacket<SocialPackets.CharacterPayload> {
    override fun decode(input: FrameReader): SocialPackets.CharacterPayload = SocialPackets.decodeSocialSync(input)
}
