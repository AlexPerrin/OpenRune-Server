package org.rsmod.content.skills.fletching.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Knife-on-log fletching: one row per product. Levels, experience (in tenths) and the shaft counts
 * are from the OSRS Wiki (Arrow shaft and the unstrung bow pages); items are cache objs.
 */
object FletchingKnifeTable {
    const val LOG = 0
    const val PRODUCT = 1
    const val COUNT = 2
    const val LEVEL = 3
    const val XP_TENTHS = 4

    private data class Row(
        val key: String,
        val log: String,
        val product: String,
        val count: Int,
        val level: Int,
        val xpTenths: Int,
    )

    private val ROWS =
        listOf(
            Row("shafts_logs", "obj.logs", "obj.arrow_shaft", 15, 1, 50),
            Row("shortbow", "obj.logs", "obj.unstrung_shortbow", 1, 5, 50),
            Row("longbow", "obj.logs", "obj.unstrung_longbow", 1, 10, 100),
            Row("shafts_oak", "obj.oak_logs", "obj.arrow_shaft", 30, 15, 100),
            Row("oak_shortbow", "obj.oak_logs", "obj.unstrung_oak_shortbow", 1, 20, 165),
            Row("oak_longbow", "obj.oak_logs", "obj.unstrung_oak_longbow", 1, 25, 250),
            Row("shafts_willow", "obj.willow_logs", "obj.arrow_shaft", 45, 30, 150),
            Row("willow_shortbow", "obj.willow_logs", "obj.unstrung_willow_shortbow", 1, 35, 333),
            Row("willow_longbow", "obj.willow_logs", "obj.unstrung_willow_longbow", 1, 40, 415),
            Row("shafts_maple", "obj.maple_logs", "obj.arrow_shaft", 60, 45, 200),
            Row("maple_shortbow", "obj.maple_logs", "obj.unstrung_maple_shortbow", 1, 50, 500),
            Row("maple_longbow", "obj.maple_logs", "obj.unstrung_maple_longbow", 1, 55, 583),
            Row("shafts_yew", "obj.yew_logs", "obj.arrow_shaft", 75, 60, 250),
            Row("yew_shortbow", "obj.yew_logs", "obj.unstrung_yew_shortbow", 1, 65, 675),
            Row("yew_longbow", "obj.yew_logs", "obj.unstrung_yew_longbow", 1, 70, 750),
            Row("shafts_magic", "obj.magic_logs", "obj.arrow_shaft", 90, 75, 300),
            Row("magic_shortbow", "obj.magic_logs", "obj.unstrung_magic_shortbow", 1, 80, 833),
            Row("magic_longbow", "obj.magic_logs", "obj.unstrung_magic_longbow", 1, 85, 915),
            Row("shafts_redwood", "obj.redwood_logs", "obj.arrow_shaft", 105, 90, 350),
        )

    fun fletchingKnife() =
        dbTable("dbtable.fletching_knife", serverOnly = true) {
            column("log", LOG, VarType.OBJ)
            column("product", PRODUCT, VarType.OBJ)
            column("count", COUNT, VarType.INT)
            column("level", LEVEL, VarType.INT)
            column("xp_tenths", XP_TENTHS, VarType.INT)

            for (r in ROWS) {
                row("dbrow.fletching_knife_${r.key}") {
                    columnRSCM(LOG, r.log)
                    columnRSCM(PRODUCT, r.product)
                    column(COUNT, r.count)
                    column(LEVEL, r.level)
                    column(XP_TENTHS, r.xpTenths)
                }
            }
        }
}
