package org.rsmod.content.travel.jewellery

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.api.script.onOpWorn5
import org.rsmod.api.script.onOpWorn6
import org.rsmod.api.script.onOpWorn7
import org.rsmod.api.table.JewelleryTeleportDestsRow
import org.rsmod.api.table.JewelleryTeleportItemsRow
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Games necklace, ring of dueling, amulet of glory, skills necklace and combat bracelet teleports
 * (OSRS Wiki item pages). Rub (held
 * op 4) opens the teleport menu (interface 187 through script 217 with hotkeys, which RuneLite's
 * Better Teleport Menu decorates); the worn ops after Remove teleport straight to a destination.
 * Each teleport uses a charge: "You rub the necklace..." then "Your games necklace has three uses
 * left." or, on the last charge, "Your games necklace crumbles to dust." (wiki; the glory's last
 * charge leaves an uncharged amulet).
 *
 * Guesses: the menu title, the glory/skills/combat charge lines ("...has three charges left.", "You
 * use your amulet's last charge."), the purple colour value, the teleport timing (the standard 714/111
 * jewellery animation, 4 ticks), the plane of the Corporeal Beast and Tears of Guthix tiles. Not
 * done: destination requirements (Tears of Guthix quest, Zeah visit, Colosseum Hero title), the
 * wiki's random landing radius, the Wilderness 30 glory rule beyond the teleport validator's type.
 */
class JewelleryTeleportScript
@Inject
constructor(private val validator: PlayerTeleportValidator, private val areaChecker: AreaChecker) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (item in JewelleryTeleportItemsRow.all()) {
            val obj = item.obj.internalName
            onOpHeld4(obj) { rub(item) }
            destinations(item).forEachIndexed { i, dest ->
                val go: suspend ProtectedAccess.() -> Unit = { teleport(item, dest, player.worn) }
                when (i) {
                    0 -> onOpWorn2(obj) { go() }
                    1 -> onOpWorn3(obj) { go() }
                    2 -> onOpWorn4(obj) { go() }
                    3 -> onOpWorn5(obj) { go() }
                    4 -> onOpWorn6(obj) { go() }
                    5 -> onOpWorn7(obj) { go() }
                }
            }
        }
    }

    suspend fun ProtectedAccess.rub(item: JewelleryTeleportItemsRow) {
        val dests = destinations(item)
        val choice = menu(MENU_TITLE, hotkeys = true, choices = dests.map { it.name })
        val dest = dests.getOrNull(choice) ?: return
        teleport(item, dest, inv)
    }

    /** Uses one charge of [item] in [from] and teleports to [dest]; nothing happens when denied. */
    suspend fun ProtectedAccess.teleport(
        item: JewelleryTeleportItemsRow,
        dest: JewelleryTeleportDestsRow,
        from: Inventory,
    ) {
        if (actionDelay > mapClock) return
        val type = if (item.wilderness30 == 1) TeleportType.MemberLevel30 else TeleportType.Standard
        val denial = validator.validate(player, type, areaChecker)
        if (denial != null) {
            mes(denial, ChatType.Engine)
            return
        }
        if (!useCharge(item, from)) return
        actionDelay = mapClock + TELEPORT_DELAY + 1
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_DELAY)
        telejump(dest.coords, type)
        resetAnim()
    }

    /** Swaps [item] in [from] for its next charge (or removes it) and sends the charge messages. */
    fun ProtectedAccess.useCharge(item: JewelleryTeleportItemsRow, from: Inventory): Boolean {
        val obj = item.obj.internalName
        val used =
            if (item.crumbles == 1) invDel(from, obj, 1) else invReplace(from, obj, 1, item.next.internalName)
        if (used.failure) return false
        mes("You rub the ${item.rubWord}...")
        val left = item.charges - 1
        val line =
            when {
                item.crumbles == 1 -> "Your ${item.name} crumbles to dust."
                left == 0 -> "You use your ${item.rubWord}'s last charge."
                item.wilderness30 == 1 -> "Your ${item.name} has ${words(left)} charge${plural(left)} left."
                else -> "Your ${item.name} has ${words(left)} use${plural(left)} left."
            }
        mes("<col=$PURPLE>$line</col>")
        return true
    }

    private fun destinations(item: JewelleryTeleportItemsRow): List<JewelleryTeleportDestsRow> =
        JewelleryTeleportDestsRow.all().filter { it.family == item.family }.sortedBy { it.index }

    private fun plural(count: Int): String = if (count == 1) "" else "s"

    private fun words(count: Int): String = NUMBER_WORDS.getOrElse(count) { count.toString() }

    companion object {
        const val MENU_TITLE = "Where would you like to teleport to?"
        const val PURPLE = "7f007f"
        const val TELEPORT_SOUND = "synth.teleport_all"
        const val TELEPORT_DELAY = 4
        const val TELEPORT_SPOTANIM_HEIGHT = 92
        val TELEPORT_ANIM: String = RSCM.getReverseMapping(RSCMType.SEQ, 714)
        val TELEPORT_SPOTANIM: String = RSCM.getReverseMapping(RSCMType.SPOTANIM, 111)
        val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven")
    }
}
