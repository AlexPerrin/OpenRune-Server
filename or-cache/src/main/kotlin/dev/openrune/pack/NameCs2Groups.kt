package dev.openrune.pack

import dev.openrune.cache.CLIENTSCRIPT
import dev.openrune.cache.CacheDelegate
import dev.openrune.cache.tools.TaskPriority
import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache
import java.io.File

class NameCs2Groups(private val clientscripts: List<String>, private val cs2Dir: File) : CacheTask() {
    override val priority: TaskPriority
        get() = TaskPriority.CS2

    override fun init(cache: Cache) {
        val index = (cache as CacheDelegate).library.index(CLIENTSCRIPT)
        val projectSymbols = File(cs2Dir, "symbols/clientscript.sym")
        val stock = if (projectSymbols.isFile) symbolNames(projectSymbols.readLines()) else emptyMap()
        for (archive in index.archives()) {
            if (archive.hashName == 0) {
                stock[archive.id]?.let { index.add(archive.id, it.hashCode()) }
            }
        }
        val names = symbolNames(clientscripts)
        for ((id, name) in names) {
            if (index.contains(id)) {
                index.add(id, name.hashCode())
            }
        }
        val maxCustom = names.keys.maxOrNull() ?: return
        index.archiveIds().filter { it > maxCustom }.forEach(index::remove)
    }

    private fun symbolNames(lines: List<String>): Map<Int, String> = lines.mapNotNull { line ->
        line.substringBefore('\t').toIntOrNull()?.let {
            it to line.substringAfter('\t').substringBefore('\t').substringBefore(' ')
        }
    }.toMap()
}
