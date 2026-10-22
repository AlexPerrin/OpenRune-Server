package dev.openrune.pack

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.filesystem.Cache
import java.io.File

/**
 * The stock (Jagex) clientscript ids the build knows about, so custom scripts can be numbered right
 * after them: index 12 of the cache and the symbol table of the installed CS2 project (the bundle
 * [dev.openrune.cache.tools.cs2.PackCs2] compiles). The bundle can lag the cache and the cache
 * holds earlier builds' custom scripts, so both are combined and [maxId] discounts the custom ones.
 */
object StockClientScripts {
    private val logger = InlineLogger()

    private const val CLIENTSCRIPT_INDEX = 12

    /** A wider gap between known ids ends the stock range (a stale custom block sits above it). */
    const val MAX_GAP = 256

    fun knownIds(cacheDir: File, cs2Dir: File): Set<Int> = cacheIds(cacheDir) + projectIds(cs2Dir)

    /**
     * The highest stock id: the end of the run of known ids (gaps up to [MAX_GAP] bridged), minus
     * [customIds] at its top, which are this build's own scripts from an earlier pack. Null when
     * nothing is known.
     */
    fun maxId(knownIds: Set<Int>, customIds: Set<Int>): Int? {
        val sorted = knownIds.sorted()
        if (sorted.isEmpty()) return null
        var end = sorted.first()
        for (id in sorted.drop(1)) {
            if (id - end > MAX_GAP) break
            end = id
        }
        return sorted.lastOrNull { it <= end && it !in customIds }
    }

    private fun cacheIds(cacheDir: File): Set<Int> {
        if (!cacheDir.isDirectory) {
            logger.warn { "No cache at ${cacheDir.path}; stock clientscripts from the CS2 project" }
            return emptySet()
        }
        val cache = Cache.load(cacheDir.toPath())
        return try {
            cache.archives(CLIENTSCRIPT_INDEX).toSet()
        } finally {
            cache.close()
        }
    }

    private fun projectIds(cs2Dir: File): Set<Int> {
        val symbols = File(cs2Dir, "symbols/clientscript.sym")
        if (!symbols.isFile) return emptySet()
        return symbols.useLines { lines ->
            lines.mapNotNull { it.substringBefore('\t').trim().toIntOrNull() }.toSet()
        }
    }
}
