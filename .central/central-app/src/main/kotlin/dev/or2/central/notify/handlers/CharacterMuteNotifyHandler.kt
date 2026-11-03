package dev.or2.central.notify.handlers

import dev.or2.central.notify.NotifyBroadcaster
import dev.or2.central.notify.NotifyJson.int
import dev.or2.central.notify.NotifyJson.long
import dev.or2.central.notify.NotifyJson.parseObject
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.ServerMuteUpdatePacket
import org.slf4j.LoggerFactory

@PgNotifyChannel("character_mute_events")
class CharacterMuteNotifyHandler(
    private val broadcaster: NotifyBroadcaster,
) : PgNotifyHandler {
    private val log = LoggerFactory.getLogger(CharacterMuteNotifyHandler::class.java)

    override fun handle(payload: String?) {
        val root = parseObject(payload) ?: return
        val accountId = root.long("account_id") ?: return
        val characterId = root.int("character_id") ?: return
        val until = root.long("muted_until_epoch_millis") ?: return
        runCatching {
            val worlds = broadcaster.worldsForAccount(accountId)
            broadcaster.push(
                worlds,
                ServerMuteUpdatePacket.encode(ServerMuteUpdatePacket.Payload(accountId, characterId, until)),
                broadcastAll = true,
            )
        }.onFailure {
            log.warn("Mute failed for {}", accountId, it)
        }
    }
}
