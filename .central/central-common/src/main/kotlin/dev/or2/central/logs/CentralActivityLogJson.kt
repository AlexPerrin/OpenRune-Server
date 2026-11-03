package dev.or2.central.logs

import kotlinx.serialization.json.Json

/** Shared JSON rules for Central activity logs (game server + Central must match). */
public object CentralActivityLogJson {
    public val json: Json =
        Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
}
