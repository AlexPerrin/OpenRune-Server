package dev.or2.central.notify.handlers

import dev.or2.central.notify.NotifyBroadcaster
import dev.or2.central.notify.NotifyJson
import dev.or2.central.notify.NotifyJson.int
import dev.or2.central.notify.NotifyJson.long
import dev.or2.central.notify.NotifyJson.parseObject
import dev.or2.central.notify.NotifyJson.string
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.ServerRevokeLoginPacket
import org.slf4j.LoggerFactory

@PgNotifyChannel("punishment_events")
class PunishmentNotifyHandler(
    private val broadcaster: NotifyBroadcaster,
) : PgNotifyHandler {
    private val log = LoggerFactory.getLogger(PunishmentNotifyHandler::class.java)

    override fun handle(payload: String?) {
        val root = parseObject(payload) ?: return
        val accountId = root.long("account_id") ?: return
        val characterId = root.int("character_id") ?: 0
        runCatching {
            val worlds = broadcaster.worldsForAccount(accountId)
            broadcaster.revokeSessionsForAccount(accountId)
            broadcaster.push(
                worlds,
                ServerRevokeLoginPacket.encode(ServerRevokeLoginPacket.Payload(accountId, characterId)),
                broadcastAll = true,
            )
        }.onFailure {
            log.warn("Failed punishment revoke for {}", accountId, it)
        }
    }
}
