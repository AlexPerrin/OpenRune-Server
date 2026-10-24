package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import kotlin.math.min
import net.rsprot.protocol.api.IncomingPacketTrace
import net.rsprot.protocol.api.OutgoingPacketTrace
import net.rsprot.protocol.game.outgoing.misc.client.UrlOpen
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.UpdateStat
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.XpSource
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.debug.TestFlags
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * osrs-plugins-reverse: the `::` commands our Android device tests use that OpenRune lacks
 * (`::freeze`, `::npcsay`, `::options`, `::url`, `::drop`, `::pos`, `::heal`). The rest map onto OpenRune's own commands: `::give` and
 * `::empty` are aliases of `::item` and `::invclear`, `::quest` lives in the quest module.
 */
class TestCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("freeze", "Toggle NPC wandering for device tests", ::freeze)
        onCommand("npcsay", "Make every NPC within $NEARBY tiles say a text", ::npcSay) {
            invalidArgs = "Use as ::npcsay text"
        }
        onCommand("options", "Open a chatmenu with 2-5 options (::options One Two Three)", ::options)
        onCommand("url", "Send URL_OPEN (::url https://example.com)", ::url)
        onCommand("pos", "Current coordinates (x z level), like the old test server", ::pos)
        onCommand("mapanim", "World spotanim (MAP_ANIM) at your tile (::mapanim id [height] [delay] [dx dz])", ::mapAnim) {
            invalidArgs = "Use as ::mapanim spotanimId [height] [delay] [dx dz]"
        }
        onCommand("heal", "Restore hitpoints, prayer, drained stats and run energy", ::heal)
        onCommand("drop", "Close the connection without logging out (the client reconnects)", ::drop)
        onCommand("trace", "Log every incoming game packet (docker logs) on/off", ::trace)
        onCommand("traceout", "Log the first n outgoing packets of new Android sessions (docker logs; 0 = off)", ::traceOut)
        onCommand("setxp", "Set a stat's xp, up to 200M (::setxp mining 20000000)", ::setXp) {
            invalidArgs = "Use as ::setxp skill xp"
        }
        onCommand("addxp", "Add xp to a stat, up to 200M (::addxp mining 1000000)", ::addXp) {
            invalidArgs = "Use as ::addxp skill xp"
        }
    }

    private fun freeze(cheat: Cheat) =
        with(cheat) {
            TestFlags.npcsFrozen = !TestFlags.npcsFrozen
            player.mes("NPC wandering ${if (TestFlags.npcsFrozen) "off" else "on"}.")
        }

    private fun npcSay(cheat: Cheat) =
        with(cheat) {
            val text = args.joinToString(" ")
            if (text.isBlank()) {
                player.mes("Use as ::npcsay text")
                return
            }
            val npcs = nearbyNpcs(player)
            npcs.forEach { it.say(text) }
            player.mes("${npcs.size} NPCs say: $text")
        }

    private fun options(cheat: Cheat): Unit =
        with(cheat) {
            val choices = args.ifEmpty { listOf("Yes", "No", "Maybe") }.take(5)
            if (choices.size < 2) {
                player.mes("Use as ::options One Two [Three] [Four] [Five]")
                return
            }
            protectedAccess.launch(player) {
                val picked =
                    when (choices.size) {
                        2 -> choice2(choices[0], 1, choices[1], 2)
                        3 -> choice3(choices[0], 1, choices[1], 2, choices[2], 3)
                        4 -> choice4(choices[0], 1, choices[1], 2, choices[2], 3, choices[3], 4)
                        else ->
                            choice5(
                                choices[0], 1, choices[1], 2, choices[2], 3, choices[3], 4,
                                choices[4], 5,
                            )
                    }
                mes("Picked option $picked: ${choices[picked - 1]}")
            }
            Unit
        }

    private fun url(cheat: Cheat) =
        with(cheat) {
            val target = args.firstOrNull() ?: "https://example.com"
            player.client.write(UrlOpen(target))
            player.mes("URL_OPEN sent: $target")
        }

    private fun trace(cheat: Cheat) =
        with(cheat) {
            IncomingPacketTrace.enabled = !IncomingPacketTrace.enabled
            player.mes("Incoming packet trace ${if (IncomingPacketTrace.enabled) "on" else "off"}.")
        }

    /** Logs the first n outgoing packets of the next Android logins (`OUT #..`); 0 turns it off. */
    private fun pos(cheat: Cheat) =
        with(cheat) {
            val c = player.coords
            player.mes("${c.x} ${c.z} ${c.level}")
        }

    private fun mapAnim(cheat: Cheat) =
        with(cheat) {
            val nums = args.map { it.toIntOrNull() }
            val id = nums.getOrNull(0)
            if (id == null || id < 0 || nums.any { it == null }) {
                player.mes("Use as ::mapanim spotanimId [height] [delay] [dx dz]")
                return
            }
            val height = nums.getOrNull(1) ?: 0
            val delay = nums.getOrNull(2) ?: 0
            val dx = nums.getOrNull(3) ?: 0
            val dz = nums.getOrNull(4) ?: 0
            val coord = player.coords.translate(dx, dz)
            worldRepo.spotanimMap(SpotanimType(id), coord, height, delay)
            player.mes("Map anim $id at ${coord.x} ${coord.z} ${coord.level} (height=$height, delay=$delay)")
        }

    private fun heal(cheat: Cheat) =
        with(cheat) {
            for (stat in ServerCacheManager.getStats().values) {
                player.statRestore(RSCM.getReverseMapping(RSCMType.STAT, stat.id))
            }
            player.runEnergy = MAX_RUN_ENERGY
            UpdateRun.energy(player, player.runEnergy)
            player.mes("Restored.")
        }

    private fun drop(cheat: Cheat) =
        with(cheat) {
            player.client.close()
        }

    private fun traceOut(cheat: Cheat) =
        with(cheat) {
            val n = args.firstOrNull()?.toIntOrNull() ?: if (OutgoingPacketTrace.loginPackets > 0) 0 else 700
            OutgoingPacketTrace.loginPackets = n.coerceAtLeast(0)
            player.mes("Outgoing packet trace: first ${OutgoingPacketTrace.loginPackets} packets of new Android sessions.")
        }

    @OptIn(InternalApi::class)
    private fun setXp(cheat: Cheat) =
        with(cheat) {
            val stat = statArg(args.getOrNull(0)) ?: return player.mes("Unknown skill.")
            val target = args.getOrNull(1)?.toLongOrNull()?.coerceIn(0, MAX_XP.toLong())?.toInt()
                ?: return player.mes("Use as ::setxp skill xp")
            val current = player.statMap.getXP(stat)
            if (target >= current) {
                player.statAdvance(stat, (target - current).toDouble(), 1.0, 1.0, XpSource.Manual)
            } else {
                val type = checkNotNull(ServerCacheManager.getStats(stat.asRSCM(RSCMType.STAT)))
                val level = min(PlayerSkillXPTable.getLevelFromXP(target), type.maxLevel)
                player.statMap.setXP(stat, target)
                player.statMap.setBaseLevel(stat, level.toByte())
                player.statMap.setCurrentLevel(stat, level.toByte())
                UpdateStat.update(player, type, target, level, level)
                player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
                PlayerInterfaceUpdates.updateCombatLevel(player)
            }
            player.mes("${stat.removePrefix("stat.")} xp: ${player.statMap.getXP(stat)}")
        }

    private fun addXp(cheat: Cheat) =
        with(cheat) {
            val stat = statArg(args.getOrNull(0)) ?: return player.mes("Unknown skill.")
            val amount = args.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 }
                ?: return player.mes("Use as ::addxp skill xp")
            player.statAdvance(stat, amount, 1.0, 1.0, XpSource.Manual)
            player.mes("${stat.removePrefix("stat.")} xp: ${player.statMap.getXP(stat)}")
        }

    private fun statArg(name: String?): String? {
        val key = name?.lowercase() ?: return null
        return ServerCacheManager.getStats().values
            .map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }
            .firstOrNull { it == "stat.$key" || it.removePrefix("stat.").startsWith(key) }
    }

    private fun nearbyNpcs(player: Player): List<Npc> =
        npcRepo.findAll(ZoneKey.from(player.coords), (NEARBY + 7) / 8)
            .filter { it.isVisible && it.coords.level == player.coords.level }
            .filter { it.coords.chebyshevDistance(player.coords) <= NEARBY }
            .toList()

    private companion object {
        const val MAX_RUN_ENERGY = 10000
        const val NEARBY = 16
        const val MAX_XP = 200_000_000
    }
}
