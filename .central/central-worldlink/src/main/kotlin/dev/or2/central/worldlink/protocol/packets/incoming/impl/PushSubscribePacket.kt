package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_PUSH_SUBSCRIBE,
    name = "PUSH_SUBSCRIBE",
    allowedBodyBytes = [0],
)
object PushSubscribePacket : InboundPacket<Unit> {
    override fun decode(input: FrameReader) {
        input.requireFullyConsumed()
    }
}
