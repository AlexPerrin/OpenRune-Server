package dev.or2.central.worldlink.handlers

import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.packets.incoming.impl.HeartbeatPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.HeartbeatAckPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.LoginFailPacket

class HeartbeatHandler(
    private val sessionService: dev.or2.central.session.SessionService,
) {
    fun handle(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) {
            return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_NOT_HANDSHAKEN)))
        }
        val payload =
            try {
                HeartbeatPacket.decode(input)
            } catch (_: PacketDecodeException) {
                return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_BAD_FRAME)))
            }
        if (!sessionService.touchSession(payload.token, connection.worldId)) {
            return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_BAD_TOKEN)))
        }
        return HandlerResult.Reply(HeartbeatAckPacket.encode(Unit))
    }
}
