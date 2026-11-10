package dev.or2.central.notify.handlers

import dev.or2.central.notify.NotifyBroadcaster
import dev.or2.central.notify.NotifyJson.long
import dev.or2.central.notify.NotifyJson.parseObject
import dev.or2.central.notify.NotifyJson.string
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import dev.or2.central.worldlink.protocol.discord.ServerDiscordIdSyncPacket
import org.slf4j.LoggerFactory

@PgNotifyChannel("account_discord_id_events")
class AccountDiscordIdNotifyHandler(
    private val broadcaster: NotifyBroadcaster,
) : PgNotifyHandler {
    private val log = LoggerFactory.getLogger(AccountDiscordIdNotifyHandler::class.java)

    override fun handle(payload: String?) {
        val root = parseObject(payload) ?: return
        val accountId = root.long("account_id") ?: return
        val discordId = root.string("discord_id")
        runCatching {
            val worlds = broadcaster.worldsForAccount(accountId)
            val frame =
                ServerDiscordIdSyncPacket.encode(
                    ServerDiscordIdSyncPacket.Payload(
                        accountId = accountId,
                        discordId = discordId,
                    ),
                )
            broadcaster.push(worlds, frame, broadcastAll = true)
        }.onFailure {
            log.warn("Discord id push failed for account {}", accountId, it)
        }
    }
}
