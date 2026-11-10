package dev.or2.central.worldlink.protocol.packets.outgoing.impl

import dev.or2.central.account.Rights
import dev.or2.central.worldlink.protocol.FieldKind
import dev.or2.central.worldlink.protocol.OutboundPacket
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.WorldPacketOutgoing
import dev.or2.central.worldlink.protocol.outboundFrame

@WorldPacketOutgoing(
    opcode = WorldOpcodes.OP_LOGIN_OK,
    name = "LOGIN_OK",
    fields = [
        FieldKind.FIXED_TOKEN,
        FieldKind.LONG,
        FieldKind.STRING_LOGIN_RIGHTS,
    ],
)
object LoginOkPacket : OutboundPacket<LoginOkPacket.Payload> {
    data class Payload(
        val token: ByteArray,
        val accountId: Long,
        val rights: Rights,
        val realmDevMode: Boolean,
        val clientProtocolVersion: Int,
    )

    override fun encode(payload: Payload): ByteArray =
        outboundFrame(WorldOpcodes.OP_LOGIN_OK) {
            require(payload.token.size == WorldOpcodes.TOKEN_BYTES)
            writeShort(payload.token.size)
            writeBytes(payload.token)
            writeLong(payload.accountId)
            if (payload.clientProtocolVersion >= 3) {
                writeUtf8Truncated(
                    payload.rights.wireName(payload.realmDevMode),
                    WorldOpcodes.LOGIN_OK_RIGHTS_MAX_BYTES,
                )
            }
        }
}
