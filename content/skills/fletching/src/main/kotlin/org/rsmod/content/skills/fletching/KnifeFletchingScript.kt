package org.rsmod.content.skills.fletching

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.fletching.FletchingKnifeRow
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * A knife on logs opens the skill menu with the arrow shafts and unstrung bows those logs make
 * (`dbtable.fletching_knife`), then cuts one log per action: 2 ticks to the first, 3 between the
 * rest (OSRS Wiki).
 */
class KnifeFletchingScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (log in FletchingKnifeRow.all().map { it.log.internalName }.distinct()) {
            onOpHeldU(KNIFE, log) { chooseProduct(log) }
        }
        onPlayerQueueWithArgs<CutTask>(QUEUE) { cutStep(it.args) }
    }

    private suspend fun ProtectedAccess.chooseProduct(log: String) {
        val rows = FletchingKnifeRow.all().filter { it.log.internalName == log }
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = rows.map { SkillMultiEntry(it.product.internalName) },
                maxCountProvider = { inventory, _ -> inventory.count(log) },
            ),
        ) { selection ->
            val row = rows.firstOrNull { it.product.internalName == selection.entry.internal }
            if (row != null) startCutting(row, selection.amount)
        }
    }

    private fun ProtectedAccess.startCutting(row: FletchingKnifeRow, amount: Int) {
        if (statBase(FLETCHING) < row.level) {
            mes("You need a Fletching level of ${row.level} to make that.")
            return
        }
        weakQueue(QUEUE, FIRST_DELAY, CutTask(row, amount, 0))
    }

    private fun ProtectedAccess.cutStep(task: CutTask) {
        if (!cutOnce(task.row)) {
            resetAnim()
            return
        }
        val done = task.done + 1
        if (done < task.amount) {
            weakQueue(QUEUE, CUT_DELAY, CutTask(task.row, task.amount, done))
        } else {
            resetAnim()
        }
    }

    /** Cuts one log into [row]'s product; `false` (nothing taken) when it cannot. */
    fun ProtectedAccess.cutOnce(row: FletchingKnifeRow): Boolean {
        if (statBase(FLETCHING) < row.level || KNIFE !in inv || row.log.internalName !in inv) {
            return false
        }
        val cut =
            player.invTransaction(inv) {
                val from = select(inv)
                delete {
                    this.from = from
                    this.obj = row.log.id
                    this.strictCount = 1
                }
                insert {
                    this.into = from
                    this.obj = row.product.id
                    this.strictCount = row.count
                }
            }
        if (cut.failure) {
            mes("You don't have enough inventory space.")
            return false
        }
        anim(CUT_ANIM)
        statAdvance(FLETCHING, row.xpTenths / XP_DIVISOR)
        mes(cutMessage(row), ChatType.Spam)
        return true
    }

    private fun cutMessage(row: FletchingKnifeRow): String =
        if (row.count > 1) {
            "You carefully cut the wood into ${row.count} arrow shafts."
        } else {
            "You carefully cut the wood into ${row.product.name.lowercase().withArticle()}."
        }

    private fun String.withArticle(): String = if (first() in "aeiou") "an $this" else "a $this"

    private data class CutTask(val row: FletchingKnifeRow, val amount: Int, val done: Int)

    companion object {
        const val KNIFE = "obj.knife"
        const val FLETCHING = "stat.fletching"
        const val QUEUE = "queue.fletching_knife"
        const val CUT_ANIM = "seq.human_fletching"
        const val FIRST_DELAY = 2
        const val CUT_DELAY = 3
        const val XP_DIVISOR = 10.0
    }
}
