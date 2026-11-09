package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.auth.PasswordAuthConfig
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_HELLO_ACK,
    name = "HELLO_ACK",
    allowedBodyBytes = [0, PasswordAuthConfig.WIRE_BYTES],
)
object HelloAckPacket : OutboundPacket<PasswordAuthConfig> {
    override fun encode(payload: PasswordAuthConfig): ByteArray =
        outboundFrame(WorldOpcodes.OP_HELLO_ACK) {
            val wire = PasswordAuthConfig.encodeWire(payload)
            writeBytes(wire)
        }

    fun decodeAuthConfig(reader: FrameReader): PasswordAuthConfig {
        val trailing = reader.trailingUnreadBytes()
        return when (trailing) {
            0 -> PasswordAuthConfig.DEFAULT
            PasswordAuthConfig.WIRE_BYTES -> {
                val wire = reader.readFully(PasswordAuthConfig.WIRE_BYTES)
                PasswordAuthConfig.decodeWire(wire)
            }
            else -> throw IllegalArgumentException("HELLO_ACK auth block must be 0 or ${PasswordAuthConfig.WIRE_BYTES} bytes")
        }
    }

    fun decodeAuthConfig(frame: ByteArray): PasswordAuthConfig {
        if (frame.isEmpty() || (frame[0].toInt() and 0xFF) != WorldOpcodes.OP_HELLO_ACK) {
            return PasswordAuthConfig.DEFAULT
        }
        return when (frame.size - 1) {
            0 -> PasswordAuthConfig.DEFAULT
            PasswordAuthConfig.WIRE_BYTES -> PasswordAuthConfig.decodeWire(frame, offset = 1)
            else -> PasswordAuthConfig.DEFAULT
        }
    }
}
