package dev.or2.central.notify.handlers

import dev.or2.central.notify.NotifyBroadcaster
import dev.or2.central.notify.NotifyJson.int
import dev.or2.central.notify.NotifyJson.long
import dev.or2.central.notify.NotifyJson.parseObject
import dev.or2.central.notify.NotifyJson.string
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import dev.or2.central.social.SocialService
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.ServerDisplayNameSyncPacket
import org.slf4j.LoggerFactory

@PgNotifyChannel("character_display_name_events")
class CharacterDisplayNameNotifyHandler(
    private val broadcaster: NotifyBroadcaster,
    private val socialService: SocialService,
) : PgNotifyHandler {
    private val log = LoggerFactory.getLogger(CharacterDisplayNameNotifyHandler::class.java)

    override fun handle(payload: String?) {
        val root = parseObject(payload) ?: return
        val accountId = root.long("account_id") ?: return
        val characterId = root.int("character_id") ?: return
        val displayName = root.string("display_name")
        val previousDisplayName = root.string("previous_display_name")
        runCatching {
            socialService.updateDisplayName(characterId, displayName, previousDisplayName)
            val worlds = broadcaster.worldsForAccount(accountId)
            val frame =
                ServerDisplayNameSyncPacket.encode(
                    ServerDisplayNameSyncPacket.Payload(
                        accountId = accountId,
                        characterId = characterId,
                        newDisplayName = displayName,
                        priorDisplayName = previousDisplayName,
                    ),
                )
            broadcaster.push(worlds, frame, broadcastAll = true)
        }.onFailure {
            log.warn("Display name push failed for account {}", accountId, it)
        }
    }
}
