package org.rsmod.content.other.treasuretrails.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class TreasureTrailsPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(
            TrailDataTables.charlie(),
            TrailDataTables.maps(),
            TrailDataTables.emotes(),
            BeginnerCasketTable.rewards(),
            EasyCasketTable.rewards(),
            MediumCasketTable.rewards(),
            HardCasketTable.rewards(),
        )
}
