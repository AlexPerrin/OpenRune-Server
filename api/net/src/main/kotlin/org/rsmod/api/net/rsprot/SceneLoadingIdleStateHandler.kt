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

private val SCENE_LOADING: AttributeKey<Boolean> = AttributeKey.valueOf("scene_loading")

var Channel.sceneLoading: Boolean
    get() = attr(SCENE_LOADING).get() == true
    set(value) = attr(SCENE_LOADING).set(value)

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
