package org.rsmod.content.skills.farming

import jakarta.inject.Inject
import java.time.Clock
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.table.farming.FarmingCropsRow
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Allotment, flower and herb patches at Catherby, Falador, Ardougne, Morytania, Hosidius and Civitas
 * illa Fortis (OSRS Wiki, Farming). Each patch keeps its `farming_transmit` value (weeds 0-3, then
 * the crop's growth values from [FarmingCropsRow]) and harvest lives in a server varp, and the
 * minute it last grew in another. Crops grow on the farming clock (every 10/5/20 minutes of UTC time
 * for allotments/flowers/herbs), also while logged out. While the player is in a farming area the
 * area's patches are written to `farming_transmit_a-e`, exactly what the patch multilocs and
 * RuneLite's Time Tracking read.
 *
 * Deviations: no disease, compost, watering, protection payments, weed regrowth, tool leprechaun or
 * digging up growing crops.
 */
class FarmingScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    var clock: Clock = Clock.systemUTC()

    override fun ScriptContext.startup() {
        for (patch in AREAS.flatMap { it.patches }) {
            onOpLoc1(patch.loc) { op1(patch, it.loc) }
            onOpLoc2(patch.loc) { inspect(patch) }
            onOpLocU(patch.loc) { useOn(patch, it.loc, it.objType.internalName) }
        }
        onPlayerLogin { player.softTimer(TRANSMIT_TIMER, TRANSMIT_INTERVAL) }
        onPlayerSoftTimer(TRANSMIT_TIMER) {
            transmit(player)
            player.softTimer(TRANSMIT_TIMER, TRANSMIT_INTERVAL)
        }
        onCommand("growpatches") {
            desc = "Grow your farming patches: ::growpatches [cycles] (default: fully grown)"
            cheat { fastForward() }
        }
    }

    private suspend fun ProtectedAccess.op1(patch: Patch, loc: BoundLocInfo) {
        val value = value(player, patch)
        when {
            value < WEEDS_CLEAR -> rake(patch, loc)
            cropAt(patch, value)?.let { value == it.harvestValue } == true -> harvest(patch, loc)
        }
    }

    private suspend fun ProtectedAccess.rake(patch: Patch, loc: BoundLocInfo) {
        if (RAKE !in inv) {
            mes("You need a rake to weed a farming patch.")
            return
        }
        anim(RAKE_ANIM)
        delay(RAKE_DELAY)
        if (rakeOnce(patch) < WEEDS_CLEAR) opLoc1(loc)
    }

    fun ProtectedAccess.rakeOnce(patch: Patch): Int {
        val value = value(player, patch)
        if (value >= WEEDS_CLEAR) return value
        setState(player, patch, value + 1, 0)
        invAddOrDrop(objRepo, WEEDS)
        statAdvance(FARMING, WEED_XP)
        return value + 1
    }

    private suspend fun ProtectedAccess.harvest(patch: Patch, loc: BoundLocInfo) {
        val crop = cropAt(patch, value(player, patch)) ?: return
        if (patch.type != FLOWER && SPADE !in inv) {
            mes("You need a spade to do that.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        anim(if (patch.type == ALLOTMENT) DIG_ANIM else PICK_ANIM)
        delay(HARVEST_DELAY)
        if (harvestOnce(patch, crop) == Harvest.MORE) opLoc1(loc)
    }

    enum class Harvest {
        NONE,
        MORE,
        EMPTY,
    }

    fun ProtectedAccess.harvestOnce(patch: Patch, crop: FarmingCropsRow): Harvest {
        val state = player.vars[patch.stateVarp]
        if (state and VALUE_MASK != crop.harvestValue || inv.isFull()) return Harvest.NONE
        if (patch.type == FLOWER) {
            val count = flowerYield(crop, stat(FARMING)) { bound -> random.of(bound) }
            invAddOrDrop(objRepo, crop.produce.internalName, count)
            return empty(patch, crop, harvestXp = true)
        }
        if (invAdd(inv, crop.produce.internalName).failure) return Harvest.NONE
        statAdvance(FARMING, crop.harvestXp / 10.0)
        val saved = random.of(256) < saveChance(crop, stat(FARMING))
        val lives = (state shr LIVES_SHIFT) - if (saved) 0 else 1
        if (lives <= 0) return empty(patch, crop, harvestXp = false)
        setState(player, patch, crop.harvestValue, lives)
        return Harvest.MORE
    }

    private fun ProtectedAccess.empty(patch: Patch, crop: FarmingCropsRow, harvestXp: Boolean): Harvest {
        setState(player, patch, WEEDS_CLEAR, 0)
        val xp = crop.plantXp + if (harvestXp) crop.harvestXp else 0
        statAdvance(FARMING, xp / 10.0)
        mes("The ${patch.type.patchName} is now empty.")
        return Harvest.EMPTY
    }

    private suspend fun ProtectedAccess.useOn(patch: Patch, loc: BoundLocInfo, obj: String) {
        when {
            obj == RAKE && value(player, patch) < WEEDS_CLEAR -> rake(patch, loc)
            CROPS.any { it.seed.internalName == obj } -> {
                if (plant(patch, obj)) {
                    anim(DIBBER_ANIM)
                    delay(PLANT_DELAY)
                }
            }
            else -> mes("Nothing interesting happens.")
        }
    }

    fun ProtectedAccess.plant(patch: Patch, seed: String): Boolean {
        val crop = CROPS.first { it.seed.internalName == seed }
        val value = value(player, patch)
        val seeds = if (patch.type == ALLOTMENT) ALLOTMENT_SEEDS else 1
        val seedName = crop.seed.name.lowercase()
        when {
            crop.patch != patch.type.id -> mes("You can't plant a $seedName in this patch.")
            value < WEEDS_CLEAR -> mes("This patch needs weeding first.")
            value > WEEDS_CLEAR -> mes("You can only plant ${seedName}s in an empty patch.")
            DIBBER !in inv -> mes("You need a seed dibber to plant seeds.")
            stat(FARMING) < crop.level ->
                mes("You must be a level ${crop.level} Farmer to plant those.")
            inv.count(seed) < seeds -> mes("You need $seeds ${seedName}s to grow those.")
            invDel(inv, seed, seeds).failure -> return false
            else -> {
                setState(player, patch, crop.plantValue, HARVEST_LIVES)
                VarPlayerIntMapSetter.set(player, patch.timeVarp, nowMinutes())
                val what = if (seeds == 1) "a $seedName" else "$seeds ${seedName}s"
                mes("You plant $what in the ${patch.type.patchName}.")
                return true
            }
        }
        return false
    }

    private fun ProtectedAccess.inspect(patch: Patch) {
        val value = value(player, patch)
        val crop = cropAt(patch, value)
        val text =
            when {
                value < WEEDS_CLEAR -> "The patch needs weeding."
                crop == null -> "The patch is empty and weeded."
                value == crop.harvestValue -> "The patch is fully grown."
                else -> "The patch has something growing in it."
            }
        mes("This is ${patch.type.article} ${patch.type.patchName}. $text")
    }

    private fun Cheat.fastForward() {
        val cycles = args.firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE
        for (patch in AREAS.flatMap { it.patches }) {
            val crop = cropAt(patch, value(player, patch)) ?: continue
            val grown = growth(crop, value(player, patch), cycles)
            setState(player, patch, grown, player.vars[patch.stateVarp] shr LIVES_SHIFT)
        }
        transmit(player)
        player.mes("Your farming patches grow.")
    }

    fun value(player: Player, patch: Patch): Int {
        grow(player, patch)
        return player.vars[patch.stateVarp] and VALUE_MASK
    }

    private fun grow(player: Player, patch: Patch) {
        val state = player.vars[patch.stateVarp]
        val value = state and VALUE_MASK
        val crop = cropAt(patch, value) ?: return
        if (value == crop.harvestValue) return
        val now = nowMinutes()
        val rate = patch.type.cycleMinutes
        val cycles = now / rate - player.vars[patch.timeVarp] / rate
        if (cycles <= 0) return
        VarPlayerIntMapSetter.set(player, patch.timeVarp, now)
        setState(player, patch, growth(crop, value, cycles), state shr LIVES_SHIFT)
    }

    fun transmit(player: Player) {
        val area = AREAS.firstOrNull { it.contains(player.coords) }
        for ((index, varbit) in TRANSMIT_VARBITS.withIndex()) {
            val patch = area?.patches?.getOrNull(index)
            VarPlayerIntMapSetter.set(player, varbit, patch?.let { value(player, it) } ?: 0)
        }
    }

    private fun setState(player: Player, patch: Patch, value: Int, lives: Int) {
        VarPlayerIntMapSetter.set(player, patch.stateVarp, value or (lives shl LIVES_SHIFT))
        if (AREAS.first { patch in it.patches }.contains(player.coords)) {
            VarPlayerIntMapSetter.set(player, patch.transmitVarbit, value)
        }
    }

    private fun nowMinutes(): Int = (clock.millis() / 60_000L).toInt()

    enum class PatchType(val id: Int, val patchName: String, val article: String, val cycleMinutes: Int) {
        Allotment(0, "allotment", "an", 10),
        Flower(1, "flower patch", "a", 5),
        Herb(2, "herb patch", "a", 20),
    }

    data class Patch(val loc: String, val type: PatchType, val key: String, val transmitVarbit: String) {
        val stateVarp: String get() = "varp.farming_${key}_state"
        val timeVarp: String get() = "varp.farming_${key}_time"
    }

    class Area(val mapSquares: Set<Int>, val inBounds: (CoordGrid) -> Boolean, val patches: List<Patch>) {
        fun contains(coords: CoordGrid): Boolean {
            val square = (coords.x shr 6 shl 8) or (coords.z shr 6)
            return square in mapSquares && inBounds(coords)
        }
    }

    companion object {
        const val FARMING = "stat.farming"
        const val RAKE = "obj.rake"
        const val DIBBER = "obj.dibber"
        const val SPADE = "obj.spade"
        const val WEEDS = "obj.weeds"
        const val RAKE_ANIM = "seq.farming_raking"
        const val DIBBER_ANIM = "seq.farming_seed_dibbing"
        const val DIG_ANIM = "seq.human_dig"
        const val PICK_ANIM = "seq.picking_low"
        const val TRANSMIT_TIMER = "timer.farming_transmit"
        const val TRANSMIT_INTERVAL = 5
        const val RAKE_DELAY = 3
        const val HARVEST_DELAY = 2
        const val PLANT_DELAY = 3
        const val WEEDS_CLEAR = 3
        const val WEED_XP = 4.0
        const val ALLOTMENT_SEEDS = 3
        const val HARVEST_LIVES = 3
        const val VALUE_MASK = 0xFF
        const val LIVES_SHIFT = 8

        val ALLOTMENT = PatchType.Allotment
        val FLOWER = PatchType.Flower
        val HERB = PatchType.Herb

        val TRANSMIT_VARBITS =
            listOf("a", "b", "c", "d", "e").map { "varbit.farming_transmit_$it" }

        private fun patches(region: String, allotment1: Int, allotment2: Int, flower: Int, herb: Int) =
            listOf(
                Patch("loc.farming_veg_patch_$allotment1", ALLOTMENT, "${region}_a", TRANSMIT_VARBITS[0]),
                Patch("loc.farming_veg_patch_$allotment2", ALLOTMENT, "${region}_b", TRANSMIT_VARBITS[1]),
                Patch("loc.farming_flower_patch_$flower", FLOWER, "${region}_c", TRANSMIT_VARBITS[2]),
                Patch("loc.farming_herb_patch_$herb", HERB, "${region}_d", TRANSMIT_VARBITS[3]),
            )

        /** The areas RuneLite's FarmingWorld reads these patches in. */
        val AREAS =
            listOf(
                Area(
                    setOf(11062, 11061, 11318, 11317),
                    { it.x < 2816 || it.z >= 3456 || (it.x < 2840 && it.z >= 3440 && it.level == 0) },
                    patches("catherby", 3, 4, 2, 2),
                ),
                Area(setOf(12083), { it.z >= 3272 }, patches("falador", 1, 2, 1, 1)),
                Area(setOf(10548), { true }, patches("ardougne", 5, 6, 3, 3)),
                Area(setOf(14391, 14390), { true }, patches("morytania", 7, 8, 4, 4)),
                Area(setOf(6967, 6711), { true }, patches("hosidius", 10, 11, 5, 6)),
                Area(
                    setOf(6192, 6447, 6448, 6449, 6191, 6193),
                    { true },
                    patches("civitas", 16, 17, 8, 8),
                ),
            )

        val CROPS: List<FarmingCropsRow> by lazy { FarmingCropsRow.all() }

        fun cropAt(patch: Patch, value: Int): FarmingCropsRow? =
            CROPS.firstOrNull {
                it.patch == patch.type.id &&
                    (value == it.harvestValue || value in it.plantValue until it.plantValue + it.stages)
            }

        fun growth(crop: FarmingCropsRow, value: Int, cycles: Int): Int {
            if (value == crop.harvestValue) return value
            val stage = value - crop.plantValue + cycles.toLong()
            return if (stage >= crop.stages) crop.harvestValue else stage.toInt() + crop.plantValue
        }

        /** Out of 256 (OSRS Wiki, Farming: Variable crop yield). */
        fun saveChance(crop: FarmingCropsRow, level: Int): Int {
            val f = level.coerceIn(1, 99)
            val scaled = (crop.ctsLow * (99 - f) + crop.ctsHigh * (f - 1)) / 98.0
            return 1 + (scaled + 0.5).toInt()
        }

        /** [nextInt] returns 0 until its bound (exclusive). */
        fun flowerYield(crop: FarmingCropsRow, level: Int, nextInt: (Int) -> Int): Int =
            crop.fixedYield + if (crop.levelBonus) nextInt(level.coerceAtLeast(1)) / 10 else 0
    }
}
