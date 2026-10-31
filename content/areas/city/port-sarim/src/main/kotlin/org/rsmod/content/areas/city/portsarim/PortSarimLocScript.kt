package org.rsmod.content.areas.city.portsarim

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState
import org.rsmod.content.quest.area.portsarim.piratestreasure.wydin
import org.rsmod.content.quest.area.portsarim.piratestreasure.wydinJobOffer
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PortSarimLocScript @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.fai_trapdoor") { climbDownTrapdoor() }
        onOpLoc1("loc.vc_manhole_open") { climbDownManhole() }
        onOpLoc1("loc.vc_ladder") { climbUpFromSewer() }
        onOpLoc1("loc.jewellersdoor") { walkThroughDoor(it.loc) }
        onOpLoc1("loc.wydindoor") { wydinBackDoor(it.loc) }
        onOpLoc1("loc.cavewall_shortcut_wyvern_west") { wyvernTunnel() }
        onOpLoc1("loc.draynor_diary_under_wall_w") { underwallTunnel(UNDERWALL_EAST_EXIT) }
        onOpLoc1("loc.draynor_diary_under_wall_e") { underwallTunnel(UNDERWALL_WEST_EXIT) }
        onOpLoc1("loc.farming_style") { climbStile(it.loc) }
    }

    private suspend fun ProtectedAccess.climbDownTrapdoor() {
        arriveDelay()
        spam("You climb down through the trapdoor.")
        telejump(player.coords.translateZ(UNDERGROUND_OFFSET))
    }

    private suspend fun ProtectedAccess.climbDownManhole() {
        arriveDelay()
        anim("seq.human_pickupfloor")
        delay(1)
        mes("You climb through the manhole")
        telejump(SEWER_LANDING)
    }

    private suspend fun ProtectedAccess.climbUpFromSewer() {
        arriveDelay()
        anim("seq.human_reachforladder")
        delay(1)
        telejump(MANHOLE_EXIT)
    }

    private suspend fun ProtectedAccess.walkThroughDoor(door: BoundLocInfo) {
        arriveDelay()
        val inside = door.coords.translateX(-1)
        val dest = if (coords.x >= door.coords.x) inside else door.coords
        passDoor(door, dest, OPEN_DOOR)
    }

    private suspend fun ProtectedAccess.passDoor(door: BoundLocInfo, dest: CoordGrid, open: String) {
        val openCoords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        locRepo.del(door, DOOR_OPEN_TICKS)
        locRepo.add(openCoords, open, DOOR_OPEN_TICKS, door.turnAngle(rotations = 1), door.shape)
        soundSynth("synth.door_open")
        playerMove(dest)
    }

    // Pirate's Treasure: only Wydin's employees, wearing a white apron, may go into the back room.
    private suspend fun ProtectedAccess.wydinBackDoor(door: BoundLocInfo) {
        arriveDelay()
        val beyond =
            when (door.angle) {
                LocAngle.West -> door.coords.translateX(-1)
                LocAngle.North -> door.coords.translateZ(1)
                LocAngle.East -> door.coords.translateX(1)
                else -> door.coords.translateZ(-1)
            }
        val (inside, outside) =
            if (backRoomDistance(door.coords) < backRoomDistance(beyond)) door.coords to beyond
            else beyond to door.coords
        val entering = backRoomDistance(coords) > backRoomDistance(inside)
        val dest = if (entering) inside else outside
        if (entering && !PiratesTreasureState.employedAtWydin(player)) {
            startDialogue {
                wydin(
                    neutral,
                    "Hey, you can't go in there. Only employees of the grocery store can go in.",
                )
                if (choice2("Well, can I get a job here?", true, "Sorry, I didn't realise.", false)) {
                    wydinJobOffer("Well, can I get a job here?")
                } else {
                    chatPlayer(neutral, "Sorry, I didn't realise.")
                }
            }
            return
        }
        if (entering && !PiratesTreasureState.wearsWhiteApron(player)) {
            startDialogue {
                wydin(neutral, "Can you put your white apron on before going in there, please?")
            }
            return
        }
        passDoor(door, dest, "loc.wydindooropen")
        if (coords != dest) telejump(dest)
    }

    private fun backRoomDistance(coords: CoordGrid): Int =
        abs(coords.x - WYDIN_BACK_ROOM.x) + abs(coords.z - WYDIN_BACK_ROOM.z)

    private suspend fun ProtectedAccess.wyvernTunnel() {
        arriveDelay()
        if (player.agilityLvl < WYVERN_TUNNEL_LEVEL) {
            objbox(
                "obj.agility_contortion",
                zoom = 400,
                "The tunnel is very dark and dangerous. You'll need an Agility level of " +
                    "$WYVERN_TUNNEL_LEVEL to try this.",
            )
            return
        }
        mes("Nothing interesting happens.")
    }

    private suspend fun ProtectedAccess.underwallTunnel(exit: CoordGrid) {
        if (player.agilityLvl < UNDERWALL_TUNNEL_LEVEL) {
            mes("You need an Agility level of $UNDERWALL_TUNNEL_LEVEL to negotiate this tunnel.")
            return
        }
        anim("seq.human_crawling")
        soundSynth("synth.underwall_tunnel_crawl")
        delay(1)
        teleport(exit)
        resetAnim()
    }

    private suspend fun ProtectedAccess.climbStile(stile: BoundLocInfo) {
        val alongZ = stile.angle == LocAngle.West || stile.angle == LocAngle.East
        val near = stile.coords
        val far = if (alongZ) near.translateZ(1) else near.translateX(1)
        val fromNear = if (alongZ) coords.z <= near.z else coords.x <= near.x
        val (start, end) = if (fromNear) near to far else far to near
        if (coords != start) {
            playerMove(start)
        }
        val facing =
            when {
                alongZ && fromNear -> FACE_NORTH
                alongZ -> FACE_SOUTH
                fromNear -> FACE_EAST
                else -> FACE_WEST
            }
        anim("seq.human_walk_style", delay = STILE_START_CYCLES)
        exactMove(start, end, STILE_START_CYCLES, STILE_END_CYCLES, facing)
        delay(STILE_TICKS)
    }

    private companion object {
        const val UNDERGROUND_OFFSET = 6400
        const val OPEN_DOOR = "loc.inactiveposhdoor"
        const val DOOR_OPEN_TICKS = 3
        const val WYVERN_TUNNEL_LEVEL = 82
        const val UNDERWALL_TUNNEL_LEVEL = 42
        const val STILE_START_CYCLES = 30
        const val STILE_END_CYCLES = 94
        const val STILE_TICKS = 3
        const val FACE_SOUTH = 0
        const val FACE_WEST = 512
        const val FACE_NORTH = 1024
        const val FACE_EAST = 1536

        val SEWER_LANDING = CoordGrid(2962, 9650, 0)
        val MANHOLE_EXIT = CoordGrid(3018, 3233, 0)
        val UNDERWALL_EAST_EXIT = CoordGrid(3070, 3257, 0)
        val UNDERWALL_WEST_EXIT = CoordGrid(3066, 3257, 0)
        val WYDIN_BACK_ROOM = CoordGrid(3009, 3207, 0)
    }
}
