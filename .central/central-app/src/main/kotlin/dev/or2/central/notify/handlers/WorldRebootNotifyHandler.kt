package dev.or2.central.notify.handlers

import dev.or2.central.notify.NotifyBroadcaster
import dev.or2.central.notify.NotifyJson.int
import dev.or2.central.notify.NotifyJson.long
import dev.or2.central.notify.NotifyJson.parseObject
import dev.or2.central.notify.NotifyJson.string
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.ServerRebootPacket

@PgNotifyChannel("world_reboot_events")
class WorldRebootNotifyHandler(
    private val broadcaster: NotifyBroadcaster,
) : PgNotifyHandler {
    override fun handle(payload: String?) {
        val root = parseObject(payload) ?: return
        val op = root.string("op")
        val worldId = root.int("world_id")
        val scope = worldId ?: 0
        val frame =
            when (op) {
                "clear" ->
                    ServerRebootPacket.encode(
                        ServerRebootPacket.Payload(clear = true, worldScope = scope, rebootAtMs = 0L, message = ""),
                    )
                "set" -> {
                    val at = root.long("reboot_at_ms") ?: return
                    val msg = root.string("message")
                    ServerRebootPacket.encode(
                        ServerRebootPacket.Payload(clear = false, worldScope = scope, rebootAtMs = at, message = msg),
                    )
                }
                else -> return
            }
        broadcaster.push(if (worldId != null) listOf(worldId) else emptyList(), frame, broadcastAll = worldId == null)
    }
}
