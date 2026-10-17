package org.rsmod.content.travel.jewellery.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class JewelleryTeleportsPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(JewelleryTeleportItemsTable.items(), JewelleryTeleportDestsTable.dests())
}
