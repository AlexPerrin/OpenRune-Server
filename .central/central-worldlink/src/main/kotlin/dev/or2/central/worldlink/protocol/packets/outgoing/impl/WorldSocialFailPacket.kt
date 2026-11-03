package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_WORLD_SOCIAL_FAIL,
    name = "WORLD_SOCIAL_FAIL",
    fields = [FieldKind.BYTE],
)
object WorldSocialFailPacket : OutboundPacket<WorldSocialFailPacket.Payload> {
    data class Payload(val reason: Int)

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_SOCIAL_FAIL) {
            writeByte(payload.reason)
        }
}
