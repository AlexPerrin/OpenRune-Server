package dev.or2.central.http

import dev.or2.central.db.repositories.WorldFlag
import dev.or2.central.db.repositories.WorldRepository
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json

class WorldListCache(
    private val repository: WorldRepository,
) {
    private val payload = AtomicReference(ByteArray(0))
    private val worldListJsJson = AtomicReference("")

    private val rebuildExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "central-worldlist-cache").apply { isDaemon = true }
        }

    private val json =
        Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    fun snapshot(): ByteArray = payload.get()

    fun worldListJsSnapshot(): String = worldListJsJson.get()

    fun rebuild() {
        val worlds = repository.findAllForList()
        payload.set(WorldListBinaryEncoder.encode(worlds))
        val dto =
            WorldsJsResponse(
                worlds.map { row ->
                    WorldsJsWorld(
                        id = row.worldId,
                        types = WorldFlag.runeliteTypeNamesFromMask(row.properties),
                        address = row.host,
                        activity = row.activity,
                        location = row.location,
                        players = row.population,
                    )
                },
            )
        worldListJsJson.set(json.encodeToString(dto))
    }

    /** Refreshes world-list payloads without blocking login/logout handlers. */
    fun rebuildAsync() {
        rebuildExecutor.execute {
            try {
                rebuild()
            } catch (_: Exception) {
            }
        }
    }
}
