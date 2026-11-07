package dev.openrune.pack

import dev.openrune.cache.CacheDelegate
import dev.openrune.cache.tools.TaskPriority
import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache

class NameCs2Groups(private val clientscripts: List<String>) : CacheTask() {
    override val priority: TaskPriority
        get() = TaskPriority.CS2

    override fun init(cache: Cache) {
        val index = (cache as CacheDelegate).library.index(CLIENTSCRIPTS)
        val names = clientscripts.mapNotNull { line ->
            line.substringBefore('\t').toIntOrNull()?.let {
                it to line.substringAfter('\t').substringBefore('\t').substringBefore(' ')
            }
        }.toMap()
        for ((id, name) in names) {
            if (index.contains(id)) {
                index.add(id, name.hashCode())
            }
        }
        val maxCustom = names.keys.maxOrNull() ?: return
        index.archiveIds().filter { it > maxCustom }.forEach(index::remove)
    }

    private companion object {
        const val CLIENTSCRIPTS = 12
    }
}
