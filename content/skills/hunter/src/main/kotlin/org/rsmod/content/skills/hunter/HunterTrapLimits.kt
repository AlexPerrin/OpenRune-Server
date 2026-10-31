package org.rsmod.content.skills.hunter

import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.game.entity.Player

@Singleton
class HunterTrapLimits {
    private val counters = mutableListOf<(Player) -> Int>()

    fun register(counter: (Player) -> Int) {
        counters += counter
    }

    fun count(player: Player): Int = counters.sumOf { it(player) }

    fun limit(player: Player, areas: AreaChecker): Int {
        val wilderness = if (player.coords.isInWilderness(areas)) 1 else 0
        return HunterTrapScript.maxTraps(player.stat(HunterTrapScript.HUNTER)) + wilderness
    }

    fun canLay(player: Player, areas: AreaChecker): Boolean {
        val limit = limit(player, areas)
        if (count(player) < limit) return true
        val amount = if (limit == 1) "one trap" else "$limit traps"
        player.mes("You may set up only $amount at a time at your Hunter level.")
        return false
    }
}
