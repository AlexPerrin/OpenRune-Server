package org.rsmod.content.quest.manager

import dev.or2.central.account.Rights
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * osrs-plugins-reverse: `::quest name|id [start|done|reset|n]` shows or sets a quest's stage for
 * device tests (as on our own test server). The name matches a quest's display name or key: exact,
 * then a unique prefix, then a unique substring; a number matches the quest id or its dbrow.
 * Setting a stage grants no rewards.
 */
class QuestTestCommand : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("quest") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show or set a quest stage: ::quest name|id [start|done|reset|n]"
            invalidArgs = "Use as ::quest name|id [start|done|reset|n]"
            cheat { quest(this) }
        }
    }

    private fun quest(cheat: Cheat) =
        with(cheat) {
            val last = args.lastOrNull()
            val state = last?.takeIf { (it in STATES || it.toIntOrNull() != null) && args.size > 1 }
            val query = (if (state != null) args.dropLast(1) else args).joinToString(" ").lowercase()
            if (query.isBlank()) {
                player.mes("Use as ::quest name|id [start|done|reset|n] (${Quest.all().size} quests)")
                return
            }
            val quests = Quest.all().sortedBy { it.displayName }
            fun names(q: Quest) = listOf(q.displayName.lowercase(), q.key.lowercase())
            val matches = quests.filter { q -> names(q).any { query in it } }
            val id = query.toIntOrNull()
            val quest =
                (if (id != null) quests.firstOrNull { it.id == id || it.rowID == id } else null)
                    ?: quests.firstOrNull { q -> query in names(q) }
                    ?: matches.filter { q -> names(q).any { it.removePrefix("the ").startsWith(query) } }
                        .singleOrNull()
                    ?: matches.singleOrNull()
            if (quest == null) {
                player.mes(
                    if (matches.isEmpty()) "No quest matches '$query'."
                    else "Matches: ${matches.take(6).joinToString { it.displayName }}"
                )
                return
            }
            if (state != null) {
                val stage =
                    when (state) {
                        "start" -> quest.debugStartStage
                        "done" -> quest.maxSteps
                        "reset" -> 0
                        else -> state.toInt()
                    }
                quest.debugSetStage(player, stage)
            }
            val stage = quest.getQuestStage(player)
            val variable = quest.questVarbit ?: quest.questVarp
            player.mes(
                "${quest.displayName}: $variable = $stage " +
                    "(${quest.questState(player).name.lowercase()}, end ${quest.maxSteps})"
            )
        }

    private companion object {
        val STATES = setOf("start", "done", "reset")
    }
}
