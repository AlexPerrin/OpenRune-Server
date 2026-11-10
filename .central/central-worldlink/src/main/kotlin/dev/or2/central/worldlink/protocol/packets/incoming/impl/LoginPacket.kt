package dev.or2.central.worldlink.protocol.packets.incoming.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.InboundPacket
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketIncoming

@WorldPacketIncoming(
    opcode = WorldOpcodes.OP_LOGIN,
    name = "LOGIN",
    fields = [
        FieldKind.STRING_LOGIN_USERNAME,
        FieldKind.STRING_LOGIN_PASSWORD,
        FieldKind.INT_OPTIONAL,
    ],
)
object LoginPacket : InboundPacket<LoginPacket.Payload> {
    data class Payload(
        val username: String,
        val password: String,
        val characterId: Int?,
    )

    override fun decode(input: FrameReader): Payload {
        val username = input.readUtf8LenPrefixed()
        val password = input.readUtf8LenPrefixed()
        val trailing = input.trailingUnreadBytes()
        val characterId =
            when (trailing) {
                0 -> null
                4 -> input.readInt()
                else -> throw PacketDecodeException("invalid login trailer length: $trailing")
            }
        input.requireFullyConsumed()
        return Payload(username, password, characterId)
    }
}
