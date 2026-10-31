package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Apothecary's Potions (interface 556): the Apothecary brews a strength potion, an energy potion
 * or an antipoison from the player's ingredients and coins, Buy-1/5/X/All per row.
 */
class ApothecaryPotions : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc3("npc.apothecary") { openApothecaryPotions() }
        for (potion in ApothecaryPotion.entries) {
            onIfModalButton(potion.button) { brewRequested(potion, it.op) }
        }
    }

    private suspend fun ProtectedAccess.brewRequested(potion: ApothecaryPotion, op: IfButtonOp) {
        val requested =
            when (op) {
                IfButtonOp.Op1 -> 1
                IfButtonOp.Op2 -> 5
                IfButtonOp.Op3 -> countDialog()
                IfButtonOp.Op4 -> Int.MAX_VALUE
                else -> return
            }
        brewApothecaryPotions(potion, requested)
    }
}

enum class ApothecaryPotion(
    private val component: String,
    val title: String,
    val product: String,
    val ingredients: List<Pair<String, Int>>,
) {
    Strength(
        "strength",
        "Strength potion",
        "obj.strength4",
        listOf("obj.limpwurt_root" to 1, "obj.red_spiders_eggs" to 1, COINS to 5),
    ),
    Energy(
        "energy",
        "Energy potion",
        "obj.4dose1energy",
        listOf("obj.limpwurt_root" to 2, "obj.chocolate_dust" to 1),
    ),
    Antipoison(
        "antipoison",
        "Antipoison",
        "obj.4doseantipoison",
        listOf("obj.limpwurt_root" to 1, "obj.cadavaberries" to 1, COINS to 5),
    );

    val button: String = "component.apothecary_potions:$component"
    val pic: String = "component.apothecary_potions:${component}_pic"
    val titleText: String = "component.apothecary_potions:${component}_title"
    val contentsText: String = "component.apothecary_potions:${component}_contents"
}

fun ProtectedAccess.openApothecaryPotions() {
    ifOpenMainModal(APOTHECARY_POTIONS)
    for (potion in ApothecaryPotion.entries) {
        ifSetEvents(potion.button, -1..-1, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4)
        ifSetObj(potion.pic, potion.product, PIC_ZOOM)
        ifSetText(potion.titleText, potion.title)
        ifSetText(potion.contentsText, potion.contents())
    }
}

/** Brews up to [requested] potions, as many as every ingredient (and the coins) allows. */
fun ProtectedAccess.brewApothecaryPotions(potion: ApothecaryPotion, requested: Int): Int {
    val affordable = potion.ingredients.minOf { (obj, count) -> inv.count(obj) / count }
    val amount = minOf(requested, affordable)
    if (amount <= 0) {
        if (requested > 0) mes("You don't have the ingredients for the Apothecary to make that.")
        return 0
    }
    val brewed =
        player
            .invTransaction(inv) {
                val target = select(inv)
                for ((obj, count) in potion.ingredients) {
                    delete(target, obj.asRSCM(RSCMType.OBJ), count * amount)
                }
                add(target, potion.product.asRSCM(RSCMType.OBJ), count = amount)
            }
            .success
    if (!brewed) {
        mes("You don't have enough inventory space.")
        return 0
    }
    val name = potion.title.lowercase()
    mes(if (amount == 1) "The Apothecary makes you a $name." else "The Apothecary makes you $amount ${name}s.")
    return amount
}

private fun ApothecaryPotion.contents(): String =
    ingredients.joinToString(", ") { (obj, count) ->
        when {
            obj == COINS -> "$count coins"
            count > 1 -> "$count x ${ingredientName(obj)}"
            else -> ingredientName(obj)
        }
    }

private fun ingredientName(obj: String): String =
    ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj

private const val COINS = "obj.coins"
private const val APOTHECARY_POTIONS = "interface.apothecary_potions"
private const val PIC_ZOOM = 380
