package dev.or2.central.worldlink.handlers

import dev.or2.central.config.DiscordRuntimeConfig
import dev.or2.central.discord.DiscordLinkMessenger
import dev.or2.central.discord.DiscordLinkService
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.protocol.PacketDecodeException
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.discord.GameDiscordLinkInvalidateAckPacket
import dev.or2.central.worldlink.protocol.discord.GameDiscordLinkInvalidatePacket
import dev.or2.central.worldlink.protocol.discord.GameDiscordLinkPendingFailPacket
import dev.or2.central.worldlink.protocol.discord.GameDiscordLinkPendingOkPacket
import dev.or2.central.worldlink.protocol.discord.GameDiscordLinkPendingPacket
import dev.or2.central.worldlink.protocol.FrameReader
import org.slf4j.LoggerFactory

class DiscordLinkHandler(
    private val linkService: DiscordLinkService,
    private val messenger: DiscordLinkMessenger,
    private val discordConfig: DiscordRuntimeConfig,
) {
    private val log = LoggerFactory.getLogger(DiscordLinkHandler::class.java)

    fun handlePending(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) {
            return unavailable()
        }
        val payload =
            try {
                GameDiscordLinkPendingPacket.decode(input)
            } catch (_: PacketDecodeException) {
                return fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_BAD_FRAME)
            }

        return handlePending(payload.accountId, payload.discordUsername)
    }

    fun handleInvalidate(connection: WorldConnection, input: FrameReader): HandlerResult {
        if (!connection.handshakeDone) {
            return unavailable()
        }
        val payload =
            try {
                GameDiscordLinkInvalidatePacket.decode(input)
            } catch (_: PacketDecodeException) {
                return fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_BAD_FRAME)
            }
        return handleInvalidate(payload.accountId)
    }

    private fun handlePending(
        accountId: Int,
        discordUsername: String,
    ): HandlerResult {
        if (accountId <= 0 || discordUsername.isBlank()) {
            return fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_BAD_FRAME)
        }

        if (linkService.accountDiscordId(accountId) != null) {
            return fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_ALREADY_LINKED)
        }

        val discordUserId = linkService.resolveDiscordUserId(discordUsername)
        if (discordUserId == null) {
            log.info(
                "Discord link lookup failed for accountId={} query='{}'",
                accountId,
                discordUsername,
            )
            return fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_DISCORD_NOT_FOUND)
        }

        val code =
            runCatching {
                linkService.createGamePending(
                    accountId = accountId,
                    discordUserId = discordUserId,
                )
            }.getOrElse {
                return fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_UNAVAILABLE)
            }

        val pending = linkService.findPendingByAccountId(accountId)
        val dmSent =
            if (pending != null && discordConfig.enabled) {
                val codes = linkService.generateCodeGrid(pending.code)
                messenger.sendVerificationDm(discordUserId, codes) { channelId, messageId ->
                    linkService.attachVerificationMessage(discordUserId, channelId, messageId)
                }
                true
            } else {
                false
            }

        return HandlerResult.Reply(
            GameDiscordLinkPendingOkPacket.encode(
                GameDiscordLinkPendingOkPacket.Payload(code = code, dmSent = dmSent),
            ),
            closeAfterWrite = true,
        )
    }

    private fun handleInvalidate(accountId: Int): HandlerResult {
        if (accountId <= 0) {
            return fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_BAD_FRAME)
        }
        linkService.invalidatePending(accountId)
        return HandlerResult.Reply(
            GameDiscordLinkInvalidateAckPacket.encode(Unit),
            closeAfterWrite = true,
        )
    }

    private fun unavailable(): HandlerResult =
        fail(WorldOpcodes.GAME_DISCORD_LINK_PENDING_FAIL_UNAVAILABLE)

    private fun fail(reason: Int): HandlerResult =
        HandlerResult.Reply(
            GameDiscordLinkPendingFailPacket.encode(GameDiscordLinkPendingFailPacket.Payload(reason)),
            closeAfterWrite = true,
        )
}
