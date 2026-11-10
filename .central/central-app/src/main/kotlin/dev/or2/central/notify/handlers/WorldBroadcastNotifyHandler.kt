package dev.or2.central.notify.handlers

import dev.or2.central.notify.NotifyBroadcaster
import dev.or2.central.notify.NotifyJson.int
import dev.or2.central.notify.NotifyJson.parseObject
import dev.or2.central.notify.NotifyJson.string
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.ServerBroadcastPacket

@PgNotifyChannel("world_broadcast_events")
class WorldBroadcastNotifyHandler(
    private val broadcaster: NotifyBroadcaster,
) : PgNotifyHandler {
    override fun handle(payload: String?) {
        val root = parseObject(payload) ?: return
        val worldId = root.int("world_id")
        val scope = worldId ?: 0
        val frame =
            ServerBroadcastPacket.encode(
                ServerBroadcastPacket.Payload(
                    worldScope = scope,
                    message = root.string("message"),
                    url = root.string("url"),
                    icon = root.string("icon"),
                ),
            )
        broadcaster.push(if (worldId != null) listOf(worldId) else emptyList(), frame, broadcastAll = worldId == null)
    }
}
