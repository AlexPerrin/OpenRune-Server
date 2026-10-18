package org.rsmod.api.net.rsprot.player

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import org.rsmod.api.net.rsprot.RspClient
import org.rsmod.api.net.rsprot.RspCycle
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent

/**
 * Reconnect logins (login type 18): the client presents the seed of its previous login instead of
 * a password or token. A player whose connection dropped stays in the world for the logout grace
 * period; a matching reconnect moves that player to the new session, so the client carries on
 * where it was instead of going back to the login screen.
 */
class PlayerReconnect
@Inject
constructor(private val playerRegistry: PlayerRegistry, private val eventBus: EventBus) {
    private val logger = InlineLogger()

    /** Game thread only. */
    fun reconnect(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<XteaKey>) {
        val player = findPlayer(block)
        if (player == null) {
            logger.info { "Reconnect for an unknown session rejected (username='${block.username}')" }
            responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
            return
        }
        if (!player.canReconnect()) {
            logger.info { "Reconnect for ${player.username} rejected: logout already under way" }
            responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
            return
        }
        try {
            rebind(player, responseHandler, block)
        } catch (e: Exception) {
            logger.error(e) { "Reconnect failed for ${player.username}" }
            responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
        }
    }

    private fun findPlayer(block: LoginBlock<XteaKey>): Player? {
        val previousSeed = block.authentication.key
        var match: Player? = null
        playerRegistry.forEachOnline { player ->
            if (match != null) return@forEachOnline
            val client = player.client as? RspClient ?: return@forEachOnline
            val previous = client.session.loginBlock
            if (isSameLogin(previous.seed, previous.username, previousSeed, block.username)) {
                match = player
            }
        }
        return match
    }

    private fun rebind(
        player: Player,
        responseHandler: GameLoginResponseHandler<Player>,
        block: LoginBlock<XteaKey>,
    ) {
        val oldClient = player.client as RspClient
        val cycle = player.clientCycle as RspCycle
        val infos = oldClient.infos
        infos.playerInfo.onReconnect()
        infos.npcInfo.onReconnect()
        infos.worldEntityInfo.onReconnect()

        val response = LoginResponse.ReconnectOk(infos.playerInfo)
        val session = responseHandler.writeSuccessfulResponse(response, block, oldClient.session)

        @Suppress("UNCHECKED_CAST")
        player.client = RspClient(session, infos) as org.rsmod.game.client.Client<Any, Any>
        cycle.reconnect(session)
        player.clientDisconnected.set(false)
        player.clientDisconnectedCycles = 0
        session.setDisconnectionHook(disconnectionHook(player, session))
        oldClient.session.requestClose()

        logger.info { "${player.username} reconnected (${block.header.clientType})" }
        eventBus.publish(SessionStateEvent.Reconnect(player))
    }

    companion object {
        /**
         * A reconnect presents the seed of the login it continues. The username is compared too
         * when both logins carry one.
         */
        internal fun isSameLogin(
            previousSeed: IntArray,
            previousUsername: String,
            presentedSeed: IntArray,
            presentedUsername: String,
        ): Boolean =
            previousSeed.contentEquals(presentedSeed) &&
                (previousUsername.isBlank() ||
                    presentedUsername.isBlank() ||
                    previousUsername.equals(presentedUsername, ignoreCase = true))

        /** A player whose logout is under way (or was requested) cannot be reconnected to. */
        internal fun Player.canReconnect(): Boolean =
            !loggingOut &&
                !pendingLogout &&
                !forceDisconnect &&
                !manualLogout &&
                !closeClient &&
                !pendingCloseClient

        /**
         * Marks [player] disconnected when [session] closes, unless a reconnect already moved the
         * player to another session (the old one can close after the new one is up).
         */
        fun disconnectionHook(player: Player, session: Session<Player>): Runnable = Runnable {
            val current = player.client as? RspClient
            if (current == null || current.session === session) {
                player.clientDisconnected.set(true)
            }
        }
    }
}
