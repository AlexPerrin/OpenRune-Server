package dev.or2.central.notify.handlers

import dev.or2.central.http.WorldListCache
import dev.or2.central.notify.PgNotifyChannel
import dev.or2.central.notify.PgNotifyHandler
import org.slf4j.LoggerFactory

@PgNotifyChannel("world_list_events")
class WorldListInvalidateHandler(
    private val worldListCache: WorldListCache,
) : PgNotifyHandler {
    private val log = LoggerFactory.getLogger(WorldListInvalidateHandler::class.java)

    override fun handle(payload: String?) {
        runCatching { worldListCache.rebuild() }
            .onFailure { log.warn("Failed to rebuild world list cache after NOTIFY", it) }
    }
}
