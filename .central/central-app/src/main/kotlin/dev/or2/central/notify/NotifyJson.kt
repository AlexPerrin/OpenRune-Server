package dev.or2.central.notify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object NotifyJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseObject(payload: String?): JsonObject? =
        payload?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

    fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.content?.toLongOrNull()

    fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.content?.toIntOrNull()

    fun JsonObject.string(name: String, default: String = ""): String = this[name]?.jsonPrimitive?.content ?: default
}
