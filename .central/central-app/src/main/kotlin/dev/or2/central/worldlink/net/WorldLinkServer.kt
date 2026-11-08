package dev.or2.central.worldlink.net

import dev.or2.central.config.WorldLinkConfig
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.WorldConnectionRegistry
import dev.or2.central.worldlink.WorldLinkHandler
import dev.or2.central.worldlink.handlers.HandlerResult
import dev.or2.central.worldlink.protocol.PacketLimits
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WorldLinkServer(
    private val config: WorldLinkConfig,
    private val handler: WorldLinkHandler,
    private val registry: WorldConnectionRegistry,
) {
    private val bossGroup = NioEventLoopGroup(1, threadFactory("worldlink-boss", true))
    private val workerGroup = NioEventLoopGroup(0, threadFactory("worldlink-io", true))
    private var serverChannel: Channel? = null

    fun start() {
        val backlog = config.soBacklog.coerceIn(64, 16_384)
        val readTimeout = config.readTimeoutSeconds.coerceIn(5, 3600)
        val bootstrap =
            ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(io.netty.channel.ChannelOption.SO_BACKLOG, backlog)
                .childOption(io.netty.channel.ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(
                    object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val connection = WorldConnection(channel = ch)
                            ch.attr(CONNECTION_KEY).set(connection)
                            val pipeline = ch.pipeline()
                            pipeline.addLast(
                                ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.SECONDS),
                            )
                            pipeline.addLast(
                                LengthFieldBasedFrameDecoder(
                                    PacketLimits.MAX_INBOUND_FRAMED_BODY,
                                    0,
                                    4,
                                    0,
                                    4,
                                ),
                            )
                            pipeline.addLast(LengthFieldPrepender(4))
                            pipeline.addLast(WorldLinkChannelHandler(handler, connection))
                        }
                    },
                )
        serverChannel = bootstrap.bind(config.port).sync().channel()
    }

    fun stop() {
        try {
            serverChannel?.close()?.sync()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }

    private companion object {
        val CONNECTION_KEY =
            io.netty.util.AttributeKey.valueOf<WorldConnection>("worldConnection")

        private fun threadFactory(baseName: String, daemon: Boolean): java.util.concurrent.ThreadFactory {
            val seq = AtomicInteger()
            return java.util.concurrent.ThreadFactory {
                Thread(it, "$baseName-${seq.incrementAndGet()}").apply {
                    isDaemon = daemon
                }
            }
        }
    }
}

private class WorldLinkChannelHandler(
    private val handler: WorldLinkHandler,
    private val connection: WorldConnection,
) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf = msg as ByteBuf
        try {
            when (val result = handler.handle(connection, buf)) {
                is HandlerResult.Reply -> {
                    ctx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(result.frame))
                    if (result.closeAfterWrite) {
                        ctx.close()
                    }
                }
                HandlerResult.NoReply -> Unit
                HandlerResult.CloseSilent -> ctx.close()
            }
            if (connection.subscribedForPush) {
                removeReadTimeout(ctx)
            }
        } catch (_: Exception) {
            if (buf.refCnt() > 0) {
                buf.release()
            }
            ctx.close()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        handler.onChannelClosed(connection)
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }
}

private fun removeReadTimeout(ctx: ChannelHandlerContext) {
    for (name in ctx.pipeline().names()) {
        if (ctx.pipeline().get(name) is ReadTimeoutHandler) {
            ctx.pipeline().remove(name)
        }
    }
}
