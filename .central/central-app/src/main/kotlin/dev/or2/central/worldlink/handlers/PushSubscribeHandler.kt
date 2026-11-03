package dev.or2.central.worldlink.handlers

import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.packets.incoming.impl.PushSubscribePacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.LoginFailPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.PushSubscribeAckPacket

class PushSubscribeHandler {
    fun handle(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) {
            return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_NOT_HANDSHAKEN)))
        }
        try {
            PushSubscribePacket.decode(input)
        } catch (_: PacketDecodeException) {
            return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_BAD_FRAME)))
        }
        connection.subscribedForPush = true
        return HandlerResult.Reply(PushSubscribeAckPacket.encode(Unit))
    }
}
