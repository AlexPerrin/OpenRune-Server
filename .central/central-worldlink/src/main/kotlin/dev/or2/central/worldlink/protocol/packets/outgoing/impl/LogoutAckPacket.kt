package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_LOGOUT_ACK,
    name = "LOGOUT_ACK",
    allowedBodyBytes = [0],
)
object LogoutAckPacket : OutboundPacket<Unit> {
    override fun encode(payload: Unit): ByteArray = outboundFrame(WorldOpcodes.OP_LOGOUT_ACK) {}
}
