package dev.or2.sql

import java.util.concurrent.ConcurrentHashMap

/**
 * Loads SQL files from classpath under `sql/`.
 * Supports simple string replacement using marker → value pairs.
 */
object OpenRuneSql {

    private const val PREFIX = "sql/"
    private val cache = ConcurrentHashMap<String, String>()

    fun text(path: String): String =
        cache.computeIfAbsent(normalize(path)) { load(it) }

    fun text(
        path: String,
        vararg replacements: Pair<String, String>
    ): String {
        val base = text(path)

        if (replacements.isEmpty()) return base

        return replacements.fold(base) { acc, (key, value) ->
            acc.replace(key, value)
        }
    }

    private fun load(normalizedPath: String): String {
        val fullPath = PREFIX + normalizedPath

        val stream = OpenRuneSql::class.java.classLoader
            .getResourceAsStream(fullPath)
            ?: error("Missing SQL resource on classpath: $fullPath")

        return stream.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
    }

    private fun normalize(path: String): String =
        path.trim().removePrefix("/")
}