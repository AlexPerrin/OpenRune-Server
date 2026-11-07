package org.rsmod.api.net.rsprot

import com.github.michaelbull.logging.InlineLogger
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.AttributeKey
import java.util.concurrent.TimeUnit
import net.rsprot.protocol.api.handlers.idlestate.DefaultIdleStateHandlerSupplier
import net.rsprot.protocol.api.handlers.idlestate.IdleStateHandlerSupplier

private val SCENE_LOADING_SINCE: AttributeKey<Long> = AttributeKey.valueOf("scene_loading_since")

private val MAX_SCENE_LOAD_NANOS = TimeUnit.MINUTES.toNanos(2)

var Channel.sceneLoading: Boolean
    get() {
        val since = attr(SCENE_LOADING_SINCE).get() ?: return false
        return System.nanoTime() - since < MAX_SCENE_LOAD_NANOS
    }
    set(value) = attr(SCENE_LOADING_SINCE).set(if (value) System.nanoTime() else null)

class SceneLoadingIdleStateHandler(idleTime: Long) :
    IdleStateHandler(true, idleTime, idleTime, 0, TimeUnit.SECONDS) {
    override fun channelIdle(ctx: ChannelHandlerContext, evt: IdleStateEvent) {
        if (ctx.channel().sceneLoading) {
            return
        }
        logger.info { "Game connection idle (${evt.state()}), closing: ${ctx.channel()}" }
        super.channelIdle(ctx, evt)
    }

    object Supplier : IdleStateHandlerSupplier {
        override fun supply(): IdleStateHandler =
            SceneLoadingIdleStateHandler(DefaultIdleStateHandlerSupplier.Game.idleTime)
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
