package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Clock
import java.time.LocalTime
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Crystal implings (OSRS Wiki, Crystal impling): one per world in Prifddinas, at one of 12 spawn
 * spots as one of 17 named implings. A new one appears 3 minutes after each :00 and :30 (UTC here)
 * and one at server start. Hunter 80 (90 barehanded), 280 XP, into a crystal impling jar.
 * The catch chance is the crystal impling's own curve (wiki chart, Hunter info): 116/256 at 80 to
 * 142/256 at 99 with a butterfly net, 20/256 (7.8%) more barehanded or with a magic net.
 */
@Singleton
class CrystalImplingScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
    private val implings: ImplingScript,
) : PluginScript() {
    private var current: Npc? = null
    private var lastSlot: Int = -1
    var clock: Clock = Clock.systemUTC()

    override fun ScriptContext.startup() {
        for (name in NAMES) {
            onOpNpc1(name) {
                with(implings) {
                    catch(it.npc, LEVEL, JAR, XP, roll = { net -> rollCatch(net) }) { remove(it.npc) }
                }
            }
        }
        onEvent<GameLifecycle.LateCycle> { tick() }
    }

    fun tick() {
        val slot = halfHourSlot(LocalTime.now(clock))
        if (slot == lastSlot) return
        if (lastSlot != -1 && !isVisibleTime(LocalTime.now(clock))) return
        lastSlot = slot
        spawn()
    }

    fun spawn(): Npc? {
        current?.let { remove(it) }
        val type =
            ServerCacheManager.getNpc(NAMES[random.of(NAMES.size)].asRSCM(RSCMType.NPC))
                ?: return null
        val npc = Npc(type, SPOTS[random.of(SPOTS.size)])
        npcRepo.add(npc, Int.MAX_VALUE)
        current = npc
        return npc
    }

    private fun ProtectedAccess.rollCatch(net: String?): Boolean {
        val bonus = net != ImplingScript.NET
        val low = if (bonus) BONUS_LOW else NET_LOW
        val high = if (bonus) BONUS_HIGH else NET_HIGH
        return statRandom(ImplingScript.HUNTER, low, high, invisibleBoost = 0)
    }

    fun current(): Npc? = current?.takeIf { it.isSlotAssigned }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        if (current === npc) current = null
    }

    companion object {
        const val LEVEL = 80
        const val XP = 280
        const val JAR = "obj.ii_captured_impling_12"
        const val VISIBLE_AFTER_MINUTES = 3

        /**
         * The OSRS skilling curve (low at level 1, high at 99, out of 256) that reproduces every
         * point of the wiki's chart: butterfly net, and barehanded or magic net.
         */
        const val NET_LOW = 8
        const val NET_HIGH = 141
        const val BONUS_LOW = 28
        const val BONUS_HIGH = 161

        val NAMES =
            listOf(
                "johnny", "junior", "andy", "joey", "trouble", "hingy", "zolty", "neil", "yanny",
                "matty", "stace", "ian", "jamie", "damo", "xander", "steveo", "stewie",
            ).map { "npc.ii_impling_type_12_$it" }

        /** The wiki's spawn map for Prifddinas. */
        val SPOTS =
            listOf(
                3290 to 6117, 3307 to 6041, 3210 to 6110, 3213 to 6049, 3237 to 6102,
                3308 to 6117, 3240 to 6046, 3214 to 6113, 3290 to 6049, 3234 to 6072,
                3240 to 6120, 3234 to 6129,
            ).map { (x, z) -> CoordGrid(x, z, 0) }

        /** Which half hour of the day [time] falls in, counting from 3 minutes past. */
        fun halfHourSlot(time: LocalTime): Int =
            ((time.toSecondOfDay() / 60 - VISIBLE_AFTER_MINUTES + 1440) % 1440) / 30

        fun isVisibleTime(time: LocalTime): Boolean = time.minute % 30 >= VISIBLE_AFTER_MINUTES
    }
}
