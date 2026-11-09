package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_SERVER_DISPLAY_NAME_SYNC,
    name = "SERVER_DISPLAY_NAME_SYNC",
    fields = [
        FieldKind.LONG,
        FieldKind.INT,
        FieldKind.STRING_96,
        FieldKind.STRING_96,
    ],
)
object ServerDisplayNameSyncPacket : OutboundPacket<ServerDisplayNameSyncPacket.Payload> {
    data class Payload(
        val accountId: Long,
        val characterId: Int,
        val newDisplayName: String,
        val priorDisplayName: String,
    )

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_DISPLAY_NAME_SYNC) {
            writeLong(payload.accountId)
            writeInt(payload.characterId)
            writeUtf8Truncated(payload.newDisplayName, 96)
            writeUtf8Truncated(payload.priorDisplayName, 96)
        }
}
