package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.table.ImplingTypesRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** One line of an impling jar's loot: [count] from [min] to [max], chance [num]/[den]. */
data class ImplingDrop(
    val obj: Int,
    val min: Int,
    val max: Int,
    val noted: Boolean,
    val num: Int,
    val den: Int,
) {
    val isNothing: Boolean get() = obj == -1

    companion object {
        fun nothing(num: Int, den: Int): ImplingDrop = ImplingDrop(-1, 0, 0, false, num, den)
    }
}

/**
 * An impling jar's loot (OSRS Wiki): an optional pre-roll (gourmet: grubby key), one roll on the
 * main table, then each tertiary drop (clue scrolls) rolled on its own.
 */
data class ImplingLootTable(
    val preRoll: List<ImplingDrop>,
    val main: List<ImplingDrop>,
    val tertiary: List<ImplingDrop>,
)

/**
 * Loot on a caught impling jar: the loot goes to the inventory and the empty impling jar comes back
 * into the same slot, except for a 10% chance that it breaks (Mod Ash). Needs one free slot.
 */
class ImplingLootScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        val jars = ImplingTypesRow.all().map { it.jar.internalName } + CRYSTAL_JAR
        for (jar in jars.distinct()) {
            // "Loot" is the jars' third inventory op in the cache (the client sends op 3), not the
            // first: register on whichever op carries it.
            val type = ServerCacheManager.getItem(jar.asRSCM(RSCMType.OBJ))
            when (type?.interfaceOptions?.indexOfFirst { it.equals(LOOT, ignoreCase = true) }) {
                0 -> onOpHeld1(jar) { loot(it.slot, jar) }
                1 -> onOpHeld2(jar) { loot(it.slot, jar) }
                3 -> onOpHeld4(jar) { loot(it.slot, jar) }
                else -> onOpHeld3(jar) { loot(it.slot, jar) }
            }
        }
    }

    suspend fun ProtectedAccess.loot(slot: Int, jar: String) {
        val table = ImplingLootTables.tables[jar.asRSCM(RSCMType.OBJ)]
        if (table == null) {
            mes("Nothing interesting happens.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You need some free inventory space to loot the jar.")
            return
        }
        val drops = roll(table) { bound -> random.of(bound) }
        if (invDel(inv, jar, slot = slot).failure) return
        if (random.of(JAR_BREAK_DEN) >= JAR_BREAK_NUM) {
            invAdd(inv, ImplingScript.IMPLING_JAR, slot = slot)
        }
        for (drop in drops) {
            val count = if (drop.max > drop.min) random.of(drop.min, drop.max) else drop.min
            if (invAdd(inv, RSCM.getReverseMapping(RSCMType.OBJ, drop.obj), count, cert = drop.noted).failure) {
                // Full inventory (several drops at once): the rest is lost rather than dropped.
                break
            }
        }
    }

    companion object {
        const val CRYSTAL_JAR = "obj.ii_captured_impling_12"
        const val LOOT = "Loot"
        const val JAR_BREAK_NUM = 1
        const val JAR_BREAK_DEN = 10

        /** The drops of one loot: [nextInt] returns 0 until its bound (exclusive). */
        fun roll(table: ImplingLootTable, nextInt: (Int) -> Int): List<ImplingDrop> {
            val drops = mutableListOf<ImplingDrop>()
            val pre = table.preRoll.firstOrNull { hits(it, nextInt) }
            if (pre != null) {
                drops += pre
            } else {
                pick(table.main, nextInt)?.let { drops += it }
            }
            drops += table.tertiary.filter { hits(it, nextInt) }
            return drops.filterNot(ImplingDrop::isNothing)
        }

        private fun hits(drop: ImplingDrop, nextInt: (Int) -> Int): Boolean =
            nextInt(drop.den) < drop.num

        private fun pick(drops: List<ImplingDrop>, nextInt: (Int) -> Int): ImplingDrop? {
            if (drops.isEmpty()) return null
            val den = drops.fold(1L) { acc, d -> lcm(acc, d.den.toLong()) }
            val weights = drops.map { it.num * (den / it.den) }
            var r = nextInt(weights.sum().toInt()).toLong()
            for ((i, w) in weights.withIndex()) {
                if (r < w) return drops[i]
                r -= w
            }
            return null
        }

        private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

        private fun lcm(a: Long, b: Long): Long = a / gcd(a, b) * b
    }
}
