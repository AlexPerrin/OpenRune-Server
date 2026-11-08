package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_LOGIN_FAIL,
    name = "LOGIN_FAIL",
    fields = [
        FieldKind.INT,
        FieldKind.STRING_LOGIN_SCRIPT,
        FieldKind.STRING_LOGIN_SCRIPT,
        FieldKind.STRING_LOGIN_SCRIPT,
    ],
)
object LoginFailPacket : OutboundPacket<LoginFailPacket.Payload> {
    data class Payload(
        val code: Int,
        val clientProtocolVersion: Int = 0,
        val scriptLines: Triple<String, String, String>? = null,
    )

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_LOGIN_FAIL) {
            writeInt(payload.code)
            if (payload.clientProtocolVersion >= 5 && payload.scriptLines != null) {
                writeUtf8Truncated(payload.scriptLines.first, WorldOpcodes.LOGIN_FAIL_SCRIPT_LINE_MAX_UTF8_BYTES)
                writeUtf8Truncated(payload.scriptLines.second, WorldOpcodes.LOGIN_FAIL_SCRIPT_LINE_MAX_UTF8_BYTES)
                writeUtf8Truncated(payload.scriptLines.third, WorldOpcodes.LOGIN_FAIL_SCRIPT_LINE_MAX_UTF8_BYTES)
            }
        }
}
