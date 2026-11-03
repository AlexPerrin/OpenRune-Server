package dev.or2.central.worldlink

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import java.util.concurrent.ConcurrentHashMap

data class WorldConnection(
    var worldId: Int = 0,
    val channel: Channel,
    var protocolVersion: Int = 0,
    var handshakeDone: Boolean = false,
    var subscribedForPush: Boolean = false,
    var maxPlayers: Int? = null,
    var loginRestrictionsEnabled: Boolean = false,
    var loginMinTotalLevel: Int = 0,
    var loginMinRightsToken: String? = null,
    var loginGateMinLevelEnabled: Boolean = false,
    var loginGateRightsEnabled: Boolean = false,
    var loginGateWhitelistEnabled: Boolean = false,
    var realmDevMode: Boolean = false,
)

class WorldConnectionRegistry {
    private val byWorldId = ConcurrentHashMap<Int, WorldConnection>()

    fun attach(connection: WorldConnection) {
        require(connection.worldId > 0) { "worldId must be set before attach" }
        byWorldId[connection.worldId] = connection
    }

    fun detach(worldId: Int) {
        byWorldId.remove(worldId)
    }

    fun get(worldId: Int): WorldConnection? = byWorldId[worldId]

    fun registeredWorldIds(): List<Int> = byWorldId.keys().toList()

    fun send(worldId: Int, frame: ByteArray): Boolean {
        val conn = byWorldId[worldId] ?: return false
        sendOnChannel(conn.channel, frame)
        return true
    }

    /** Push to a subscribed world-link channel (PM, friend presence, admin NOTIFY). */
    fun push(worldId: Int, frame: ByteArray): Boolean {
        val conn = byWorldId[worldId] ?: return false
        if (!conn.subscribedForPush) {
            return false
        }
        if (!conn.channel.isActive) {
            return false
        }
        sendOnChannel(conn.channel, frame)
        return true
    }

    fun broadcast(frame: ByteArray, worldIds: Collection<Int> = registeredWorldIds()) {
        for (worldId in worldIds) {
            send(worldId, frame)
        }
    }

    fun broadcastAll(frame: ByteArray) {
        broadcast(frame, registeredWorldIds())
    }

    private fun sendOnChannel(channel: Channel, frame: ByteArray) {
        channel.eventLoop().execute {
            if (channel.isActive) {
                channel.writeAndFlush(Unpooled.wrappedBuffer(frame))
            }
        }
    }

    /** Returns false when the push socket is not connected. */
    fun hasActivePushChannel(worldId: Int): Boolean {
        val conn = byWorldId[worldId] ?: return false
        return conn.subscribedForPush && conn.channel.isActive
    }
}

fun ByteBuf.toByteArrayAndRelease(): ByteArray {
    val bytes = ByteArray(readableBytes())
    readBytes(bytes)
    release()
    return bytes
}
