package dev.or2.central.discord

import dev.or2.central.config.DiscordRuntimeConfig
import dev.or2.central.db.repositories.SessionRepository
import javax.sql.DataSource

object DiscordRuntime {
    fun create(
        dataSource: DataSource,
        config: DiscordRuntimeConfig,
        sessionRepository: SessionRepository,
    ): Components {
        val guildMembers = DiscordGuildMembers(config.guildId)
        val linkMessenger = DiscordLinkMessenger(config.buttonPrefix)
        val linkService = DiscordLinkService(dataSource, guildMembers, config)
        val interactionListener =
            DiscordInteractionListener(
                link = linkService,
                messenger = linkMessenger,
                config = config,
            )
        val botService =
            DiscordBotService(
                config = config,
                sessionRepository = sessionRepository,
                interactionListener = interactionListener,
                guildMembers = guildMembers,
                linkMessenger = linkMessenger,
            )
        return Components(
            linkService = linkService,
            linkMessenger = linkMessenger,
            botService = botService,
        )
    }

    data class Components(
        val linkService: DiscordLinkService,
        val linkMessenger: DiscordLinkMessenger,
        val botService: DiscordBotService,
    )
}
