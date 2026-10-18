package org.rsmod.api.music.plugin.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.music.MusicPlayMode
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class MusicTabScript @Inject constructor(private val musicPlayer: MusicPlayer) : PluginScript() {
    private var Player.playMode by enumVarp<MusicPlayMode>("varp.musicplay")

    /** The jukebox list's children: the clientscript makes one per music row that is not hidden. */
    private val tracks: List<MusicRow> by lazy {
        MusicRow.all().filter { it.hidden != true }.sortedBy { it.rowId }
    }

    override fun ScriptContext.startup() {
        onIfOpen("interface.music") {
            player.ifSetEvents("component.music:jukebox", tracks.indices, IfEvent.Op1, IfEvent.Op2)
        }
        onIfOverlayButton("component.music:area") { selectPlayMode(MusicPlayMode.Area, "area") }
        onIfOverlayButton("component.music:shuffle") {
            selectPlayMode(MusicPlayMode.Random, "shuffle")
        }
        onIfOverlayButton("component.music:single") {
            selectPlayMode(MusicPlayMode.Manual, "single")
        }
        onIfOverlayButton("component.music:jukebox") { selectTrack(it.comsub, it.op) }
    }

    private fun ProtectedAccess.selectPlayMode(mode: MusicPlayMode, name: String) {
        player.playMode = mode
        mes("You are now using the $name music mode.")
    }

    private fun ProtectedAccess.selectTrack(index: Int, op: IfButtonOp) {
        val track = tracks.getOrNull(index) ?: return
        when (op) {
            IfButtonOp.Op1 -> {
                if (!musicPlayer.isUnlocked(player, track)) {
                    mes("You have not unlocked this piece of music yet!")
                    return
                }
                player.playMode = MusicPlayMode.Manual
                musicPlayer.play(player, track)
            }
            IfButtonOp.Op2 -> mes("This track unlocks ${track.unlockhint}")
            else -> Unit
        }
    }
}
