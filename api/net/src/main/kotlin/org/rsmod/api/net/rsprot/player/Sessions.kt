package org.rsmod.api.net.rsprot.player

import com.github.michaelbull.logging.InlineLogger
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

data class SessionStart(val player: Player, val session: Session<Player>) : UnboundEvent

data class SessionReconnect(val player: Player, val session: Session<Player>) : UnboundEvent

private val logger = InlineLogger()

fun GameLoginResponseHandler<Player>.refuse(username: String, response: LoginResponse, reason: String = "") {
    logger.info { "Login refused: user='$username' response=$response${if (reason.isEmpty()) "" else " reason=$reason"}" }
    writeFailedResponse(response)
}

fun disconnectionHook(player: Player, session: Session<Player>): Runnable = Runnable {
    logger.info { "Game session lost: user='${player.username}' channel=${session.ctx.channel()}" }
    player.clientDisconnected.set(true)
}
