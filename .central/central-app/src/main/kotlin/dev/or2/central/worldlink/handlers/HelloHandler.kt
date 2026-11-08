package dev.or2.central.worldlink.handlers

import dev.or2.central.auth.PasswordAuthConfig
import dev.or2.central.db.repositories.WorldRepository
import dev.or2.central.http.WorldKeyVerifier
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldHelloPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.HelloAckPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.HelloRejectPacket

sealed class HandlerResult {
    data class Reply(val frame: ByteArray, val closeAfterWrite: Boolean = false) : HandlerResult()

    data object NoReply : HandlerResult()

    data object CloseSilent : HandlerResult()
}

class HelloHandler(
    private val worldRepository: WorldRepository,
    private val worldKeyVerifier: WorldKeyVerifier,
    private val passwordAuth: PasswordAuthConfig,
) {
    fun handle(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (connection.handshakeDone) {
            return HandlerResult.Reply(HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_PROTOCOL)), true)
        }
        val payload =
            try {
                WorldHelloPacket.decode(input)
            } catch (_: PacketDecodeException) {
                return HandlerResult.Reply(HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_PROTOCOL)), true)
            }
        if (payload.magic != WorldOpcodes.MAGIC) {
            return HandlerResult.Reply(HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_PROTOCOL)), true)
        }
        if (payload.version < WorldOpcodes.MIN_CLIENT_PROTOCOL_VERSION ||
            payload.version > WorldOpcodes.MAX_CLIENT_PROTOCOL_VERSION
        ) {
            return HandlerResult.Reply(HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_PROTOCOL)), true)
        }
        connection.protocolVersion = payload.version
        val row =
            worldRepository.findForAuth(payload.worldId)
                ?: return HandlerResult.Reply(HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_UNKNOWN_WORLD)), true)
        if (!row.enabled) {
            return HandlerResult.Reply(HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_WORLD_DISABLED)), true)
        }
        if (!worldKeyVerifier.verify(row, payload.key)) {
            return HandlerResult.Reply(HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_BAD_KEY)), true)
        }
        connection.worldId = payload.worldId
        connection.maxPlayers = row.maxPlayers
        connection.loginRestrictionsEnabled = row.loginRestrictionsEnabled
        connection.loginMinTotalLevel = row.loginMinTotalLevel.coerceAtLeast(0)
        connection.loginMinRightsToken = row.loginMinRightsToken
        connection.loginGateMinLevelEnabled = row.loginGateMinLevelEnabled
        connection.loginGateRightsEnabled = row.loginGateRightsEnabled
        connection.loginGateWhitelistEnabled = row.loginGateWhitelistEnabled
        connection.realmDevMode = row.realmDevMode
        connection.handshakeDone = true
        return HandlerResult.Reply(HelloAckPacket.encode(passwordAuth))
    }
}
