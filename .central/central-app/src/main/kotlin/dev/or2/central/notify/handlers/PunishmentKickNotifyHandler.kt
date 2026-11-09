package dev.or2.central.notify.handlers

import dev.or2.central.notify.NotifyBroadcaster
import dev.or2.central.notify.NotifyJson.int
import dev.or2.central.notify.NotifyJson.long
import dev.or2.central.notify.NotifyJson.parseObject
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.ServerKickPacket
import org.slf4j.LoggerFactory

@PgNotifyChannel("punishment_kick_events")
class PunishmentKickNotifyHandler(
    private val broadcaster: NotifyBroadcaster,
) : PgNotifyHandler {
    private val log = LoggerFactory.getLogger(PunishmentKickNotifyHandler::class.java)

    override fun handle(payload: String?) {
        val root = parseObject(payload) ?: return
        val accountId = root.long("account_id") ?: return
        val characterId = root.int("character_id") ?: 0
        runCatching {
            val worlds = broadcaster.worldsForAccount(accountId)
            broadcaster.push(
                worlds,
                ServerKickPacket.encode(ServerKickPacket.Payload(accountId, characterId)),
                broadcastAll = true,
            )
        }.onFailure {
            log.warn("Kick failed for {}", accountId, it)
        }
    }
}
