package dev.or2.central.discord

import dev.or2.central.config.DiscordRuntimeConfig
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

class DiscordInteractionListener(
    private val link: DiscordLinkService,
    private val messenger: DiscordLinkMessenger,
    private val config: DiscordRuntimeConfig,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(DiscordInteractionListener::class.java)

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.componentId
        if (!customId.startsWith(config.buttonPrefix)) {
            return
        }
        event.deferReply(true).queue()

        val selectedCode = customId.removePrefix(config.buttonPrefix).toIntOrNull()
        if (selectedCode == null) {
            replyEphemeral(event, "Invalid code selection.")
            return
        }

        val discordUserId = event.user.idLong
        val pending = link.findPendingForDiscordUser(discordUserId)
        if (pending == null) {
            replyEphemeral(event, "Your linking session expired. Run `::discordlink` in-game again.")
            return
        }

        if (selectedCode != pending.code) {
            handleWrongCode(event, pending.accountId, discordUserId)
            return
        }

        when (link.completeLink(pending.accountId, discordUserId)) {
            LinkCompleteResult.Linked ->
                replyEphemeral(event, "Congratulations, you have successfully linked your account!")
            LinkCompleteResult.AlreadyLinked ->
                replyEphemeral(event, "This game account is already linked to Discord.")
            LinkCompleteResult.Failed ->
                replyEphemeral(event, "Could not save your Discord link. Run `::discordlink` in-game to try again.")
        }
    }

    private fun handleWrongCode(
        event: ButtonInteractionEvent,
        accountId: Int,
        discordUserId: Long,
    ) {
        val attempts = link.recordWrongAttempt(accountId)
        if (attempts <= 0) {
            replyEphemeral(event, "Your linking session expired. Run `::discordlink` in-game again.")
            return
        }
        if (attempts >= config.maxWrongAttempts) {
            link.verificationMessageFor(discordUserId)?.let { ref ->
                messenger.deleteVerificationMessage(ref.channelId, ref.messageId)
            }
            link.invalidatePending(accountId)
            replyEphemeral(event, "Too many wrong attempts. Run `::discordlink` in-game to try again.")
            return
        }
        replyEphemeral(event, "Unfortunately you have chosen the wrong code. Please try again.")
    }

    private fun replyEphemeral(event: ButtonInteractionEvent, message: String) {
        event.hook.sendMessage(message).setEphemeral(true).queue()
    }
}
