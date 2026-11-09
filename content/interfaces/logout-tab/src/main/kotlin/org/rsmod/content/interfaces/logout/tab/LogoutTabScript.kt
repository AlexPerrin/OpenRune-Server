package org.rsmod.content.interfaces.logout.tab

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.or2.central.db.repositories.WorldFlag
import dev.or2.sql.OpenRuneSql
import jakarta.inject.Inject
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.WorldTransfer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LogoutTabScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val database: GameDatabase,
    private val config: ServerConfig,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton("component.logout:logout") { player.requestLogout() }
        onIfOverlayButton("component.logout:world_switcher") { openWorldSwitcher() }
        onIfOverlayButton("component.worldswitcher:close") { closeWorldSwitcher() }
        onIfOverlayButton("component.worldswitcher:logout") { player.requestLogout() }
        onIfOverlayButton("component.worldswitcher:buttons") {
            if (it.op == IfButtonOp.Op1) {
                switchWorld(it.comsub)
            }
        }
    }

    private fun Player.requestLogout() {
        ifClose(eventBus)
        protectedAccess.launch(this) {
            if (isBusy2 && mapClock > preventLogoutUntil) {
                playerWalk(coords)
            }
            logOut()
        }
    }

    private fun ProtectedAccess.openWorldSwitcher() {
        runClientScript(SIDEBUTTON_SWITCH.asRSCM(RSCMType.CLIENTSCRIPT), LOGOUT_TAB)
        ifOpenOverlay("interface.worldswitcher", "component.toplevel_osrs_stretch:side10")
        ifSetEvents("component.toplevel_osrs_stretch:stone10", -1..-1, IfEvent.Op1)
        ifSetEvents("component.worldswitcher:buttons", 0..600, IfEvent.Op1, IfEvent.Op2)
    }

    private fun ProtectedAccess.closeWorldSwitcher() {
        ifOpenOverlay("interface.logout", "component.toplevel_osrs_stretch:side10")
        ifSetEvents("component.toplevel_osrs_stretch:stone10", -1..-1, IfEvent.Op1)
    }

    private fun ProtectedAccess.switchWorld(world: Int) {
        if (mapClock - player.vars["varp.lastcombat"] < SWITCH_COMBAT_DELAY) {
            mes("You cannot switch worlds so soon after combat.")
            return
        }
        if (world == config.world) {
            return
        }
        player.worldTransfer = findWorld(world) ?: return
        player.requestLogout()
    }

    private fun findWorld(world: Int): WorldTransfer? =
        database.withTransactionBlocking { connection ->
            connection.prepareStatement(OpenRuneSql.text("central/world/find_all_for_list.sql")).use {
                it.executeQuery().use { rows ->
                    var found: WorldTransfer? = null
                    while (found == null && rows.next()) {
                        if (rows.getInt("world_id") == world) {
                            val flags = WorldFlag.maskFromCsv(rows.getString("flags") ?: "")
                            found = WorldTransfer(rows.getString("host"), world, flags)
                        }
                    }
                    found
                }
            }
        }

    private companion object {
        const val SIDEBUTTON_SWITCH = "clientscript.[clientscript,toplevel_sidebutton_switch]"
        const val LOGOUT_TAB = 10
        const val SWITCH_COMBAT_DELAY = 20
    }
}
