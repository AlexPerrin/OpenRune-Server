package org.rsmod.content.events.randomevents

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.manager.chooseLampSkill
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Singleton
class GenieRandomEvent
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val locations: RandomEventLocations,
) : PluginScript() {
    private val genies = HashMap<Player, Genie>()

    var currentTimeMillis: () -> Long = System::currentTimeMillis

    override fun ScriptContext.startup() {
        onPlayerLogin { login(player) }
        onPlayerLogout { genies.remove(player)?.let { leave(it.npc) } }
        onPlayerSoftTimer(SCHEDULE_TIMER) { check(player) }
        onPlayerSoftTimer(GENIE_TIMER) { tick(player) }
        onOpNpc1(GENIE) { talk(it.npc) }
        onOpNpc5(GENIE) { dismiss(it.npc) }
        onOpHeld1(LAMP) { rub() }
        onCommand("randomevent") {
            desc = "Spawn the Genie random event beside you"
            cheat(::summon)
        }
    }

    fun login(player: Player) {
        val now = currentTimeMillis()
        var due = nextEventAt(player) ?: (now + eventDelayMillis())
        while (due <= now) {
            due += offlineExtensionMillis()
        }
        player.attr[NEXT_EVENT_AT] = due
        player.softTimer(SCHEDULE_TIMER, CHECK_INTERVAL)
    }

    fun nextEventAt(player: Player): Long? = player.attr[NEXT_EVENT_AT]?.toLong()

    fun check(player: Player): CheckResult {
        val now = currentTimeMillis()
        val due = nextEventAt(player)
        if (due == null) {
            player.attr[NEXT_EVENT_AT] = now + eventDelayMillis()
            player.softTimer(SCHEDULE_TIMER, CHECK_INTERVAL)
            return CheckResult.NotDue
        }
        if (now < due) {
            player.softTimer(SCHEDULE_TIMER, CHECK_INTERVAL)
            return CheckResult.NotDue
        }
        if (isOccupied(player)) {
            player.softTimer(SCHEDULE_TIMER, BUSY_RETRY_INTERVAL)
            return CheckResult.Delayed
        }
        player.attr[NEXT_EVENT_AT] = now + eventDelayMillis()
        player.softTimer(SCHEDULE_TIMER, CHECK_INTERVAL)
        val result =
            when {
                player in genies -> CheckResult.AlreadyActive
                player.transmog != null -> CheckResult.MissedTransformed
                locations.isDisallowed(player.coords) -> CheckResult.MissedLocation
                else -> CheckResult.Spawned
            }
        if (result == CheckResult.Spawned) {
            spawn(player)
        }
        return result
    }

    // Make-X repeats run as weak queues here.
    private fun isOccupied(player: Player): Boolean =
        player.ui.modals.isNotEmpty() || player.weakQueueList.isNotEmpty

    private fun summon(cheat: Cheat) {
        val player = cheat.player
        if (player in genies) {
            player.mes("Your Genie is already here.")
            return
        }
        spawn(player)
    }

    fun spawn(player: Player): Npc {
        val npc = Npc(GENIE, besideTile(player.coords))
        npc.mode = NpcMode.None
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        npc.assignSpawnOwner(player, mapClock.cycle)
        npc.facePlayer(player)
        npc.anim(WAVE)
        npc.say("Greetings, ${title(player)} ${player.displayName}!")
        smokePuff(npc.coords)
        worldRepo.soundArea(npc.coords, ARRIVE_SOUND, radius = SOUND_RADIUS)
        worldRepo.soundArea(npc.coords, PUFF_SOUND, radius = SOUND_RADIUS)
        player.softTimer(GENIE_TIMER, CALL_INTERVAL)
        genies[player] = Genie(npc)
        return npc
    }

    fun genieOf(player: Player): Npc? = genies[player]?.npc

    private fun tick(owner: Player) {
        val genie = genies[owner]
        if (genie == null || !genie.npc.isSlotAssigned) {
            genies.remove(owner)
            return
        }
        val npc = genie.npc
        if (genie.ignored) {
            genies.remove(owner)
            leave(npc)
            return
        }
        if (genie.handedOver) {
            if (mapClock.cycle >= genie.leaveCycle) {
                genies.remove(owner)
                leave(npc)
            } else {
                owner.softTimer(GENIE_TIMER, genie.leaveCycle - mapClock.cycle)
            }
            return
        }
        if (owner.transmog != null) {
            genies.remove(owner)
            leave(npc)
            return
        }
        if (genie.calls + 1 >= LIFETIME_TICKS / CALL_INTERVAL) {
            npc.say("Maybe later.")
            genies[owner] = genie.copy(ignored = true)
            owner.softTimer(GENIE_TIMER, IGNORED_LEAVE_TICKS)
            return
        }
        val besideOwner =
            npc.coords.level == owner.coords.level && npc.coords.chebyshevDistance(owner.coords) <= 1
        if (!besideOwner) {
            npc.teleport(collision, besideTile(owner.coords))
        }
        npc.facePlayer(owner)
        npc.anim(WAVE)
        val name = "${title(owner)} ${owner.displayName}"
        npc.say(random.pick("Hello, $name?", "Ehem... $name?", "I'm waiting for you, $name!"))
        genies[owner] = genie.copy(calls = genie.calls + 1)
        owner.softTimer(GENIE_TIMER, CALL_INTERVAL)
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val owner = genies.entries.firstOrNull { it.value.npc === npc }?.key
        if (owner !== player) {
            val pronoun = if (owner != null && isMale(owner)) "he" else "she"
            startDialogue(npc) {
                chatNpc(
                    neutral,
                    "Sorry, I'm trying to talk to someone else, but $pronoun seems to be ignoring me.",
                )
            }
            return
        }
        if (!handOver(player)) {
            startDialogue(npc) { chatNpc(neutral, "Have a nice day, ${title(player)}.") }
            return
        }
        startDialogue(npc) {
            chatNpc(
                happy,
                "Ah, so you are there, ${title(player)} ${player.displayName}. I'm so glad you " +
                    "summoned me. Please take this lamp and make your wish.",
            )
        }
    }

    fun handOver(player: Player): Boolean {
        val genie = genies[player]
        if (genie == null || genie.handedOver || genie.ignored) return false
        val leaveCycle = mapClock.cycle + HAND_OVER_TICKS
        genies[player] = genie.copy(leaveCycle = leaveCycle, handedOver = true)
        player.softTimer(GENIE_TIMER, HAND_OVER_TICKS)
        player.invAddOrDrop(objRepo, LAMP, duration = FULL_INV_DROP_TICKS)
        player.mes("Your reward is: <col=ff0000>1</col> x <col=ff0000>Lamp</col>.")
        return true
    }

    private fun ProtectedAccess.dismiss(npc: Npc) {
        val owner = genies.entries.firstOrNull { it.value.npc === npc }?.key
        if (owner !== player) {
            mes("He isn't interested in you.")
            return
        }
        genies.remove(player)
        leave(npc)
    }

    private suspend fun ProtectedAccess.rub() {
        val stat = chooseLampSkill("Choose the stat you wish to be advanced!") ?: return
        val xp = statBase(stat) * LAMP_XP_PER_LEVEL
        if (invDel(inv, LAMP).failure) return
        statAdvance(stat, xp.toDouble())
        soundSynth(WISH_SOUND)
        val skill =
            ServerCacheManager.getStats(stat.asRSCM(RSCMType.STAT))
                ?.displayName
                ?.replaceFirstChar(Char::uppercaseChar)
        mesbox("Your wish has been granted!<br>You have been awarded $xp $skill XP!")
    }

    private fun leave(npc: Npc) {
        if (!npc.isSlotAssigned) return
        val coords = npc.coords
        npcRepo.del(npc, Int.MAX_VALUE)
        smokePuff(coords)
        worldRepo.soundArea(coords, PUFF_SOUND, radius = SOUND_RADIUS)
    }

    private fun smokePuff(coords: CoordGrid) {
        worldRepo.spotanimMap(SpotanimType(SMOKE_PUFF.asRSCM(RSCMType.SPOTANIM)), coords, SMOKE_HEIGHT)
    }

    private fun besideTile(centre: CoordGrid): CoordGrid {
        for ((x, z) in ADJACENT_OFFSETS) {
            val candidate = centre.translate(x, z)
            if (collision[candidate.x, candidate.z, candidate.level] and BLOCKED_FLAGS == 0) {
                return candidate
            }
        }
        return centre
    }

    private fun isMale(player: Player): Boolean =
        player.appearance.bodyType == Constants.bodytype_a

    private fun title(player: Player): String = if (isMale(player)) "Master" else "Mistress"

    private fun eventDelayMillis(): Long = random.of(MIN_EVENT_DELAY_MS, MAX_EVENT_DELAY_MS).toLong()

    private fun offlineExtensionMillis(): Long =
        random.of(MIN_OFFLINE_EXTENSION_MS, MAX_OFFLINE_EXTENSION_MS).toLong()

    private data class Genie(
        val npc: Npc,
        val leaveCycle: Int = 0,
        val calls: Int = 0,
        val handedOver: Boolean = false,
        val ignored: Boolean = false,
    )

    enum class CheckResult {
        NotDue,
        Delayed,
        AlreadyActive,
        MissedTransformed,
        MissedLocation,
        Spawned,
    }

    companion object {
        const val GENIE = "npc.macro_geni"
        const val LAMP = "obj.macro_genilamp"
        const val SCHEDULE_TIMER = "timer.random_event"
        const val GENIE_TIMER = "timer.random_event_genie"

        val NEXT_EVENT_AT = AttributeKey<Number>(persistenceKey = "random_event_next_at")

        const val LIFETIME_TICKS = 100
        const val HAND_OVER_TICKS = 10
        const val IGNORED_LEAVE_TICKS = 3
        const val CALL_INTERVAL = 10
        const val LAMP_XP_PER_LEVEL = 10

        const val WAVE = "seq.emote_wave"
        const val SMOKE_PUFF = "spotanim.smokepuff"
        const val SMOKE_HEIGHT = 124
        const val ARRIVE_SOUND = "synth.macro_genie_appear"
        const val PUFF_SOUND = "synth.smokepuff"
        const val WISH_SOUND = "synth.found_gem"
        const val SOUND_RADIUS = 7

        const val CHECK_INTERVAL = 500
        const val BUSY_RETRY_INTERVAL = 1

        const val FULL_INV_DROP_TICKS = 18_000

        private const val MINUTE_MS = 60_000
        const val MIN_EVENT_DELAY_MS = 60 * MINUTE_MS
        const val MAX_EVENT_DELAY_MS = 120 * MINUTE_MS
        const val MIN_OFFLINE_EXTENSION_MS = 10 * MINUTE_MS
        const val MAX_OFFLINE_EXTENSION_MS = 70 * MINUTE_MS

        const val BLOCKED_FLAGS = CollisionFlag.BLOCK_WALK or CollisionFlag.BLOCK_PLAYERS
        val ADJACENT_OFFSETS =
            listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, -1 to 1, 1 to 1)
    }
}
