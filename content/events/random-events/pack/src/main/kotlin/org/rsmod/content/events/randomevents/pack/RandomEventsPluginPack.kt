package org.rsmod.content.events.randomevents.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class RandomEventsPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(RandomEventZonesTable.zones())
}
