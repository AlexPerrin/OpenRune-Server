package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_HEARTBEAT,
    name = "HEARTBEAT",
    fields = [FieldKind.FIXED_TOKEN],
)
object HeartbeatPacket : InboundPacket<HeartbeatPacket.Payload> {
    data class Payload(val token: ByteArray)

    override fun decode(input: FrameReader): Payload = Payload(input.readFixedToken())
}
