package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.table.hunter.HunterRumourHuntersRow
import org.rsmod.api.table.hunter.HunterRumoursRow
import org.rsmod.game.entity.Player

/**
 * Hunters' Rumours state and rules (OSRS Wiki: Hunters' Rumours), kept in server varps: one rumour
 * per guild hunter (`hunter_rumour_<name>` = rumour index), the hunter last talked to about a
 * rumour (`hunter_rumour_current`), catches since the last rare part (`hunter_rumour_pity`).
 *
 * - A catch of the current rumour's creature rolls the rare part (1 in rate) or gives it at the
 *   pity threshold. No part while any rare part is held. A full inventory drops a rolled part and
 *   destroys a pity part (wiki).
 * - Turning in takes the part and gives (Hunter level + 5) * modifier XP and the tier's loot sack.
 *
 * Deviation: only creatures this server can catch are assigned (wagtail, grey and carnivorous
 * chinchompas, herbiboar).
 */
@Singleton
class HunterRumours @Inject constructor(private val objRepo: ObjRepository, private val random: GameRandom) {
    val hunters: List<HunterRumourHuntersRow> by lazy { HunterRumourHuntersRow.all().sortedBy { it.index } }
    val rumours: List<HunterRumoursRow> by lazy { HunterRumoursRow.all().sortedBy { it.index } }

    fun hunterVarp(hunter: HunterRumourHuntersRow): String = "varp.hunter_rumour_${hunter.name.lowercase()}"

    fun rumourOf(player: Player, hunter: HunterRumourHuntersRow): HunterRumoursRow? =
        rumours.firstOrNull { it.index == player.vars[hunterVarp(hunter)] }

    fun currentHunter(player: Player): HunterRumourHuntersRow? =
        hunters.firstOrNull { it.index + 1 == player.vars[CURRENT] }

    fun currentRumour(player: Player): HunterRumoursRow? = currentHunter(player)?.let { rumourOf(player, it) }

    /** Picks a rumour [hunter] gives that no other hunter has assigned. Null when none fits. */
    fun assign(player: Player, hunter: HunterRumourHuntersRow, forced: HunterRumoursRow? = null): HunterRumoursRow? {
        val level = player.statBase(HUNTER)
        if (level < hunter.level) return null
        val taken = hunters.filter { it != hunter }.mapNotNull { rumourOf(player, it)?.index }.toSet()
        val options =
            rumours.filter {
                it.hunters and (1 shl hunter.index) != 0 && level >= it.level && it.index !in taken
            }
        val rumour = forced?.takeIf { it in options } ?: options.randomOrNull() ?: return null
        VarPlayerIntMapSetter.set(player, hunterVarp(hunter), rumour.index)
        VarPlayerIntMapSetter.set(player, CURRENT, hunter.index + 1)
        VarPlayerIntMapSetter.set(player, PITY, 0)
        return rumour
    }

    /** Called for every successful catch of [creature] (a creature npc's internal name). */
    fun onCatch(player: Player, creature: String) {
        val rumour = currentRumour(player) ?: return
        if (rumour.creature.internalName != creature) return
        val pity = player.vars[PITY] + 1
        VarPlayerIntMapSetter.set(player, PITY, pity)
        if (rumours.any { it.part.internalName in player.inv }) return
        val pityDrop = pity >= rumour.pity
        if (!pityDrop && random.of(rumour.rate) != 0) return
        if (player.inv.freeSpace() == 0 && pityDrop) return
        player.invAddOrDrop(objRepo, rumour.part.internalName)
        VarPlayerIntMapSetter.set(player, PITY, 0)
        player.mes(FOUND_MESSAGE)
    }

    /** Hands in [hunter]'s rumour if its rare part is carried. False when there is nothing to hand in. */
    fun turnIn(player: Player, hunter: HunterRumourHuntersRow): Boolean {
        val rumour = rumourOf(player, hunter) ?: return false
        if (player.invDel(player.inv, rumour.part.id, 1).failure) return false
        VarPlayerIntMapSetter.set(player, hunterVarp(hunter), 0)
        if (player.vars[CURRENT] == hunter.index + 1) VarPlayerIntMapSetter.set(player, CURRENT, 0)
        VarPlayerIntMapSetter.set(player, COMPLETED, player.vars[COMPLETED] + 1)
        player.statAdvance(HUNTER, turnInXp(player.statBase(HUNTER), hunter.modifier).toDouble())
        player.invAddOrDrop(objRepo, hunter.lootSack.internalName)
        return true
    }

    /** The whistle's Rumour op (wiki example: "Your current rumour target is a grey chinchompa..."). */
    fun whistleMessage(player: Player): String {
        val hunter = currentHunter(player)
        val rumour = hunter?.let { rumourOf(player, it) } ?: return NO_RUMOUR_MESSAGE
        return "Your current rumour target is ${article(rumour.name)} ${rumour.name}. You'll need to bring back " +
            "some of its ${rumour.partText}. ${hunter.name} was the source of the rumour."
    }

    private fun <T> List<T>.randomOrNull(): T? = if (isEmpty()) null else this[random.of(size)]

    companion object {
        const val HUNTER = "stat.hunter"
        const val CURRENT = "varp.hunter_rumour_current"
        const val PITY = "varp.hunter_rumour_pity"
        const val COMPLETED = "varp.hunter_rumours_completed"
        const val FOUND_MESSAGE =
            "<col=0dc10d>You find a rare piece of the creature! You should take it back to the Hunter Guild.</col>"
        const val NO_RUMOUR_MESSAGE = "You don't have a rumour to investigate at the moment."

        /** Wiki: (Hunter level + 5) * modifier (50 novice/adept, 55 expert, 60 master). */
        fun turnInXp(level: Int, modifier: Int): Int = (level + 5) * modifier

        fun article(name: String): String = if (name.first().lowercaseChar() in "aeiou") "an" else "a"
    }
}
