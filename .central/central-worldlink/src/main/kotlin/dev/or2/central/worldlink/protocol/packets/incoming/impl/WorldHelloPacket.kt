package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.PacketLimits
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_WORLD_HELLO,
    name = "WORLD_HELLO",
    fields = [
        FieldKind.INT,
        FieldKind.USHORT,
        FieldKind.INT,
        FieldKind.BYTES_WORLD_KEY,
    ],
)
object WorldHelloPacket : InboundPacket<WorldHelloPacket.Payload> {
    data class Payload(
        val magic: Int,
        val version: Int,
        val worldId: Int,
        val key: ByteArray,
    )

    override fun decode(input: FrameReader): Payload {
        val magic = input.readMagic()
        val version = input.readUnsignedShort()
        val worldId = input.readInt()
        val keyLen = input.readUnsignedShort()
        if (keyLen > PacketLimits.WORLD_KEY_MAX_BYTES) {
            throw PacketDecodeException("world key too long: $keyLen")
        }
        val key = input.readFully(keyLen)
        input.requireFullyConsumed()
        return Payload(magic, version, worldId, key)
    }
}
