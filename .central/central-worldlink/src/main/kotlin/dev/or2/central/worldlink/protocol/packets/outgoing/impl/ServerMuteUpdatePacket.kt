package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_SERVER_MUTE_UPDATE,
    name = "SERVER_MUTE_UPDATE",
    fields = [FieldKind.LONG, FieldKind.INT, FieldKind.LONG],
)
object ServerMuteUpdatePacket : OutboundPacket<ServerMuteUpdatePacket.Payload> {
    data class Payload(val accountId: Long, val characterId: Int, val mutedUntilEpochMillis: Long)

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_MUTE_UPDATE) {
            writeLong(payload.accountId)
            writeInt(payload.characterId)
            writeLong(payload.mutedUntilEpochMillis)
        }
}
