package org.rsmod.api.stats.plugin.levelup

import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.StatAdvance

public class LevelUpGuideHook : PlayerPostTickHook {
    private val guideUnlocks by lazy { LevelUpUnlocks.load() }

    override fun onPostTick(player: Player) {
        val queues = player.engineQueueList.iterator() ?: return
        while (queues.hasNext()) {
            val advance = queues.next().args as? StatAdvance ?: continue
            player.guideList(advance)
        }
        queues.cleanUp()
    }

    private fun Player.guideList(advance: StatAdvance) {
        val stat = LevelUpStat.of(advance.stat) ?: return
        val level = statBase(advance.stat.internalName)
        // UNVERIFIED: the guide list setting turning off the list, flash and hint (guess)
        if (vars[GUIDE_LIST_DISABLED_VARBIT] != 0) {
            return
        }
        if (guideUnlocks.between(stat.guide, advance.previousLevel, level).isEmpty()) {
            return
        }
        if (vars[stat.listVarbit] == 0) {
            VarPlayerIntMapSetter.set(this, stat.listVarbit, advance.previousLevel + 1)
        }
        VarPlayerIntMapSetter.set(this, FLASHSIDE_VARBIT, FLASHSIDE_STATS)
    }

    private companion object {
        const val GUIDE_LIST_DISABLED_VARBIT = "varbit.option_level_up_guide_list_disabled"
        const val FLASHSIDE_VARBIT = "varbit.flashside"
        const val FLASHSIDE_STATS = 2
    }
}
