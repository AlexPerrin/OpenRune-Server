package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_SERVER_BROADCAST,
    name = "SERVER_BROADCAST",
    fields = [
        FieldKind.INT,
        FieldKind.STRING_2048,
        FieldKind.STRING_2048,
        FieldKind.STRING_2048,
    ],
)
object ServerBroadcastPacket : OutboundPacket<ServerBroadcastPacket.Payload> {
    data class Payload(val worldScope: Int, val message: String, val url: String, val icon: String)

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_BROADCAST) {
            writeInt(payload.worldScope)
            writeUtf8Truncated(payload.message, 2048)
            writeUtf8Truncated(payload.url, 2048)
            writeUtf8Truncated(payload.icon, 2048)
        }
}
