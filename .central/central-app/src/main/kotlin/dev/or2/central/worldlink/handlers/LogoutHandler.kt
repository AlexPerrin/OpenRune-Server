package dev.or2.central.worldlink.handlers

import dev.or2.central.db.repositories.SessionRepository
import dev.or2.central.http.WorldListCache
import dev.or2.central.social.SocialService
import dev.or2.central.util.sha256
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.packets.incoming.impl.LogoutPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.LoginFailPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.LogoutAckPacket

class LogoutHandler(
    private val socialService: SocialService,
    private val sessionRepository: SessionRepository,
    private val worldListCache: WorldListCache?,
) {
    fun handle(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) {
            return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_NOT_HANDSHAKEN)))
        }
        val payload =
            try {
                LogoutPacket.decode(input)
            } catch (_: PacketDecodeException) {
                return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_BAD_FRAME)))
            }
        val row = sessionRepository.findByTokenHash(sha256(payload.token))
        if (row == null || row.worldId != connection.worldId) {
            return HandlerResult.Reply(LoginFailPacket.encode(LoginFailPacket.Payload(WorldOpcodes.LOGIN_FAIL_BAD_TOKEN)))
        }
        row.characterId?.let { characterId ->
            socialService.onCharacterOffline(connection.worldId, characterId)
        }
        sessionRepository.deleteById(row.id)
        worldListCache?.rebuildAsync()
        return HandlerResult.Reply(LogoutAckPacket.encode(Unit), closeAfterWrite = true)
    }
}
