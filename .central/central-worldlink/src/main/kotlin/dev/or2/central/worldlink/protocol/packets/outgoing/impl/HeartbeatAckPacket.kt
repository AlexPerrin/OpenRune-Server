package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_HEARTBEAT_ACK,
    name = "HEARTBEAT_ACK",
)
object HeartbeatAckPacket : OutboundPacket<Unit> {
    override fun encode(payload: Unit): ByteArray = outboundFrame(WorldOpcodes.OP_HEARTBEAT_ACK) {}
}
