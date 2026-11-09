package dev.or2.central.worldlink.protocol.discord

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_GAME_DISCORD_LINK_PENDING,
    name = "GAME_DISCORD_LINK_PENDING",
    fields = [FieldKind.INT, FieldKind.STRING_128],
)
object GameDiscordLinkPendingPacket : InboundPacket<GameDiscordLinkPendingPacket.Payload> {
    data class Payload(
        val accountId: Int,
        val discordUsername: String,
    )

    override fun decode(input: FrameReader): Payload =
        Payload(
            accountId = input.readInt(),
            discordUsername = input.readUtf8LenPrefixed(),
        )
}

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_GAME_DISCORD_LINK_INVALIDATE,
    name = "GAME_DISCORD_LINK_INVALIDATE",
    fields = [FieldKind.INT],
)
object GameDiscordLinkInvalidatePacket : InboundPacket<GameDiscordLinkInvalidatePacket.Payload> {
    data class Payload(val accountId: Int)

    override fun decode(input: FrameReader): Payload = Payload(accountId = input.readInt())
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_GAME_DISCORD_LINK_PENDING_OK,
    name = "GAME_DISCORD_LINK_PENDING_OK",
    fields = [FieldKind.INT, FieldKind.BYTE],
)
object GameDiscordLinkPendingOkPacket : OutboundPacket<GameDiscordLinkPendingOkPacket.Payload> {
    data class Payload(
        val code: Int,
        val dmSent: Boolean,
    )

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_GAME_DISCORD_LINK_PENDING_OK) {
            writeInt(payload.code)
            writeByte(if (payload.dmSent) 1 else 0)
        }

    fun decode(frame: ByteArray): Payload {
        val body = frame.copyOfRange(1, frame.size)
        val input = FrameReader(frame[0].toInt() and 0xFF, io.netty.buffer.Unpooled.wrappedBuffer(body), body.size)
        return try {
            Payload(
                code = input.readInt(),
                dmSent = input.readUnsignedByte() == 1,
            )
        } finally {
            input.close()
        }
    }
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_GAME_DISCORD_LINK_PENDING_FAIL,
    name = "GAME_DISCORD_LINK_PENDING_FAIL",
    fields = [FieldKind.BYTE],
)
object GameDiscordLinkPendingFailPacket : OutboundPacket<GameDiscordLinkPendingFailPacket.Payload> {
    data class Payload(val reason: Int)

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_GAME_DISCORD_LINK_PENDING_FAIL) {
            writeByte(payload.reason)
        }
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_GAME_DISCORD_LINK_INVALIDATE_ACK,
    name = "GAME_DISCORD_LINK_INVALIDATE_ACK",
    allowedBodyBytes = [0],
)
object GameDiscordLinkInvalidateAckPacket : OutboundPacket<Unit> {
    override fun encode(payload: Unit): ByteArray =
        outboundFrame(WorldOpcodes.OP_GAME_DISCORD_LINK_INVALIDATE_ACK) {}
}

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_SERVER_DISCORD_ID_SYNC,
    name = "SERVER_DISCORD_ID_SYNC",
    fields = [FieldKind.LONG, FieldKind.STRING_96],
)
object ServerDiscordIdSyncPacket : OutboundPacket<ServerDiscordIdSyncPacket.Payload> {
    data class Payload(
        val accountId: Long,
        val discordId: String,
    )

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_DISCORD_ID_SYNC) {
            writeLong(payload.accountId)
            writeUtf8Truncated(payload.discordId, 96)
        }
}

object GameToCentralDiscordPackets {
    fun linkPending(accountId: Int, discordUsername: String): ByteArray =
        outboundFrame(WorldOpcodes.OP_GAME_DISCORD_LINK_PENDING) {
            writeInt(accountId)
            writeUtf8Truncated(discordUsername, WorldOpcodes.GAME_DISCORD_LINK_USERNAME_MAX_UTF8)
        }

    fun linkInvalidate(accountId: Int): ByteArray =
        outboundFrame(WorldOpcodes.OP_GAME_DISCORD_LINK_INVALIDATE) {
            writeInt(accountId)
        }
}
