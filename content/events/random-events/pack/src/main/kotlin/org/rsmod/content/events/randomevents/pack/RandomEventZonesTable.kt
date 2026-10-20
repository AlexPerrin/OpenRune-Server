package org.rsmod.content.events.randomevents.pack

import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

object RandomEventZonesTable {

    const val AREA = 0
    const val ALLOWED = 1
    const val CENTRE = 2
    const val RADIUS = 3

    fun zones() =
        dbTable("dbtable.random_event_zones", serverOnly = true) {
            column("area", AREA, VarType.AREA)
            column("allowed", ALLOWED, VarType.BOOLEAN)
            column("centre", CENTRE, VarType.COORDGRID)
            column("radius", RADIUS, VarType.INT)

            area("dbrow.random_event_zone_godwars_dungeon", "area.godwars_dungeon", allowed = false)
            area("dbrow.random_event_zone_ancient_cavern", "area.ancient_cavern", allowed = false)
            area(
                "dbrow.random_event_zone_waterbirth_dungeon",
                "area.waterbirth_dungeon",
                allowed = false,
            )
            area("dbrow.random_event_zone_tapoyauik", "area.tapoyauik", allowed = false)
            area(
                "dbrow.random_event_zone_charred_island_dungeon",
                "area.charred_island_dungeon",
                allowed = false,
            )
            area("dbrow.random_event_zone_ferox_enclave", "area.ferox_enclave", allowed = true)

            row("dbrow.random_event_zone_grand_exchange") {
                column(ALLOWED, false)
                columnCoord(CENTRE, CoordGrid(3165, 3490, 0))
                column(RADIUS, 13)
            }
        }

    private fun DBTableBuilder.area(
        row: String,
        area: String,
        allowed: Boolean,
    ) {
        row(row) {
            columnRSCM(AREA, area)
            column(ALLOWED, allowed)
        }
    }
}
