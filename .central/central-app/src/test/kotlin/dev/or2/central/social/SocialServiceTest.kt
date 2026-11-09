package dev.or2.central.social

import dev.or2.central.db.repositories.SessionRepository
import dev.or2.central.worldlink.WorldConnection
import dev.or2.central.worldlink.WorldConnectionRegistry
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.social.SocialPackets
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocialServiceTest {
    private val unusedRepository = CentralSocialRepository(throwingDataSource())

    @Test
    fun pmRelayRoutesToTargetWorld() {
        val graph = SocialGraphStore()
        graph.putCharacterName(1, "Alice", null)
        graph.putCharacterName(2, "Bob", null)

        val online = OnlinePresenceIndex()
        online.put(1, 10, 100L)
        online.put(2, 20, 200L)

        val registry = WorldConnectionRegistry()
        val targetChannel = EmbeddedChannel()
        registry.attach(
            WorldConnection(
                worldId = 20,
                channel = targetChannel,
                handshakeDone = true,
                subscribedForPush = true,
            ),
        )

        val presence =
            SocialPresenceResolver(
                sessionRepository = unusedSessionRepository(),
                repository = unusedRepository,
                onlineIndex = online,
                sessionTtlMillis = 60_000L,
                sessionWorldForCharacter = { _, _ -> null },
            )

        val service =
            SocialService(
                graph = graph,
                onlineIndex = online,
                persistence = SocialGraphPersistence(unusedRepository),
                repository = unusedRepository,
                presence = presence,
                registry = registry,
            )

        val reply =
            service.handlePmRelay(
                worldId = 10,
                payload =
                    SocialPackets.PmRelayPayload(
                        fromCharacterId = 1,
                        senderCrown = 0,
                        targetName = "Bob",
                        senderDisplayName = "Alice",
                        message = "hello",
                    ),
            )

        assertTrue(reply is SocialService.SocialReply.Ok)
        targetChannel.runPendingTasks()
        assertTrue(targetChannel.isActive)
        val frame = targetChannel.readOutboundFrame()
        assertEquals(WorldOpcodes.OP_SERVER_PRIVATE_MESSAGE, frame[0].toInt() and 0xFF)
    }

    @Test
    fun pmRelayFailsWhenTargetOffline() {
        val graph = SocialGraphStore()
        graph.putCharacterName(1, "Alice", null)
        graph.putCharacterName(2, "Bob", null)

        val online = OnlinePresenceIndex()
        online.put(1, 10, 100L)

        val presence =
            SocialPresenceResolver(
                sessionRepository = unusedSessionRepository(),
                repository = unusedRepository,
                onlineIndex = online,
                sessionTtlMillis = 60_000L,
                sessionWorldForCharacter = { _, _ -> null },
            )

        val service =
            SocialService(
                graph = graph,
                onlineIndex = online,
                persistence = SocialGraphPersistence(unusedRepository),
                repository = unusedRepository,
                presence = presence,
                registry = WorldConnectionRegistry(),
            )

        val reply =
            service.handlePmRelay(
                worldId = 10,
                payload =
                    SocialPackets.PmRelayPayload(
                        fromCharacterId = 1,
                        senderCrown = 0,
                        targetName = "Bob",
                        senderDisplayName = "Alice",
                        message = "hello",
                    ),
            )

        assertTrue(reply is SocialService.SocialReply.Fail)
        val failOp = reply.frame[0].toInt() and 0xFF
        assertEquals(WorldOpcodes.OP_WORLD_SOCIAL_FAIL, failOp)
    }

    private fun unusedSessionRepository(): SessionRepository = SessionRepository(throwingDataSource())

    private fun throwingDataSource(): DataSource =
        object : DataSource {
            override fun getConnection(): Connection = error("unused")

            override fun getConnection(
                username: String?,
                password: String?,
            ): Connection = error("unused")

            override fun getLogWriter(): PrintWriter? = null

            override fun setLogWriter(out: PrintWriter?) = Unit

            override fun setLoginTimeout(seconds: Int) = Unit

            override fun getLoginTimeout(): Int = 0

            override fun getParentLogger(): Logger = throw java.sql.SQLFeatureNotSupportedException()

            override fun <T : Any?> unwrap(iface: Class<T>?): T = throw java.sql.SQLFeatureNotSupportedException()

            override fun isWrapperFor(iface: Class<*>?): Boolean = false
        }
}

private fun EmbeddedChannel.readOutboundFrame(): ByteArray {
    val buf = readOutbound<ByteBuf>() ?: error("no outbound frame")
    val bytes = ByteArray(buf.readableBytes())
    buf.readBytes(bytes)
    buf.release()
    return bytes
}
