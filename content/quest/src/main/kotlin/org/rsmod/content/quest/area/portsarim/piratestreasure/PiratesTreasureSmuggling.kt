package org.rsmod.content.quest.area.portsarim.piratestreasure

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.CRATE_CAPACITY
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.EMPLOYED_PLANTATION
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.FETCH_RUM
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.KARAMJA_RUM
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.RUM_IN_CRATE
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.RUM_NONE
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.RUM_SHIPPED
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.crateBananas
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.crateRum
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.employment
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pirate's Treasure on Karamja and in Wydin's back room: Luthas and his banana crate, Zembo's
 * bar and Wydin's grocery crate (the banana trees are generic Pickables). Wording from the wiki
 * transcripts; where they have none, from the 2004Scape reconstruction.
 */
class PiratesTreasureSmuggling
@Inject
constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(LUTHAS) { startDialogue(it.npc) { luthasDialogue() } }
        onOpNpc1(ZEMBO) { startDialogue(it.npc) { zemboDialogue() } }
        onOpNpc3(ZEMBO) { openBar(player) }
        onOpLoc1(BANANA_CRATE) { searchBananaCrate() }
        onOpLoc2(BANANA_CRATE) { fillBananaCrate() }
        onOpLocU(BANANA_CRATE, BANANA) { packBanana() }
        onOpLocU(BANANA_CRATE, KARAMJA_RUM) { stashRum() }
        onOpLoc1(GROCERY_CRATE) { searchGroceryCrate() }
    }

    private suspend fun Dialogue.luthasDialogue() {
        if (!PiratesTreasureState.employedAtPlantation(player)) {
            chatNpc(happy, "Hello I'm Luthas, I run the banana plantation here.")
            val employ =
                choice2(
                    "Could you offer me employment on your plantation?",
                    true,
                    "That customs officer is annoying isn't she?",
                    false,
                )
            if (employ) offerEmployment() else annoyingCustoms()
            return
        }
        if (player.crateBananas >= CRATE_CAPACITY) {
            payForCrate()
            return
        }
        chatNpc(quiz, "Have you completed your task yet?")
        val topic =
            choice4(
                "What did I have to do again?",
                LuthasTopic.Task,
                "No, the crate isn't full yet.",
                LuthasTopic.NotFull,
                "So where are these bananas going to be delivered to?",
                LuthasTopic.Delivery,
                "That customs officer is annoying isn't she?",
                LuthasTopic.Customs,
            )
        when (topic) {
            LuthasTopic.Task -> {
                chatPlayer(quiz, "What did I have to do again?")
                chatNpc(
                    happy,
                    "There's a crate ready to be loaded onto the ship. If you could fill it up " +
                        "with bananas, I'll pay you 30 gold.",
                )
            }
            LuthasTopic.NotFull -> {
                chatPlayer(sad, "No, the crate isn't full yet...")
                chatNpc(angry, "Well come back when it is.")
            }
            LuthasTopic.Delivery -> whereDelivered()
            else -> annoyingCustoms()
        }
    }

    private suspend fun Dialogue.offerEmployment() {
        chatPlayer(quiz, "Could you offer me employment on your plantation?")
        chatNpc(
            happy,
            "Yes, I can sort something out. There's a crate ready to be loaded onto the ship.",
        )
        chatNpc(
            happy,
            "You wouldn't believe the demand for bananas from Wydin's shop over in Port Sarim. I " +
                "think this is the third crate I've shipped him this month..",
        )
        chatNpc(happy, "If you could fill it up with bananas, I'll pay you 30 gold.")
        player.employment = player.employment or EMPLOYED_PLANTATION
    }

    private suspend fun Dialogue.payForCrate() {
        chatPlayer(happy, "I've filled a crate with bananas.")
        chatNpc(happy, "Well done, here's your payment.")
        if (access.invAdd(access.inv, COINS, CRATE_PAYMENT).failure) {
            chatNpc(neutral, "You'll need to make some room for your payment first.")
            return
        }
        access.mes("Luthas hands you 30 coins.")
        player.employment = player.employment and EMPLOYED_PLANTATION.inv()
        player.crateBananas = 0
        if (player.crateRum == RUM_IN_CRATE) {
            player.crateRum = RUM_SHIPPED
        }
        val topic =
            choice4(
                "Will you pay me for another crate full?",
                LuthasTopic.Another,
                "Thank you, I'll be on my way",
                LuthasTopic.Leave,
                "So where are these bananas going to be delivered to?",
                LuthasTopic.Delivery,
                "That customs officer is annoying isn't she?",
                LuthasTopic.Customs,
            )
        when (topic) {
            LuthasTopic.Another -> {
                chatPlayer(quiz, "Will you pay me for another crate full?")
                chatNpc(happy, "Yes certainly.")
                chatNpc(
                    neutral,
                    "If you go outside you should see the old crate has been loaded on to the " +
                        "ship, and there is another empty crate in its place.",
                )
                player.employment = player.employment or EMPLOYED_PLANTATION
            }
            LuthasTopic.Leave -> chatPlayer(happy, "Thank you, I'll be on my way.")
            LuthasTopic.Delivery -> whereDelivered()
            else -> annoyingCustoms()
        }
    }

    private suspend fun Dialogue.whereDelivered() {
        chatPlayer(quiz, "So where are these bananas going to be delivered to?")
        chatNpc(neutral, "I sell them to Wydin who runs a grocery store in Port Sarim.")
    }

    private suspend fun Dialogue.annoyingCustoms() {
        chatPlayer(shifty, "That customs officer is annoying isn't she?")
        chatNpc(neutral, "Well I know her pretty well. She doesn't cause me any trouble any more.")
        chatNpc(
            neutral,
            "She doesn't even search my export crates any more. She knows they only contain " +
                "bananas.",
        )
        chatPlayer(quiz, "Really? How interesting. Whereabouts do you send those to?")
        chatNpc(
            neutral,
            "There is a little shop over in Port Sarim that buys them up by the crate. I believe " +
                "it is run by a man called Wydin.",
        )
    }

    private suspend fun Dialogue.zemboDialogue() {
        chatNpc(
            happy,
            "Hey, are you wanting to try some of my fine wines and spirits? All brewed locally on " +
                "Karamja island.",
        )
        if (choice2("Yes please.", true, "No, thank you.", false)) {
            chatPlayer(happy, "Yes please.")
            openBar(player)
        } else {
            chatPlayer(neutral, "No, thank you.")
        }
    }

    private fun openBar(player: Player) {
        shops.open(
            player = player,
            title = "Karamja Wines, Spirits, and Beers.",
            shopInv = "inv.boozeshop",
            buyPercentage = BAR_BUY,
            sellPercentage = BAR_SELL,
            changePercentage = BAR_CHANGE,
        )
    }

    private suspend fun ProtectedAccess.searchBananaCrate() {
        arriveDelay()
        val bananas = player.crateBananas
        val rum = player.crateRum == RUM_IN_CRATE
        when {
            bananas == 0 && !rum -> mes("The crate is completely empty.")
            bananas == 0 ->
                mes(
                    "There is some rum in here, although with no bananas to cover it. It is a " +
                        "little obvious."
                )
            else -> {
                if (bananas >= CRATE_CAPACITY) {
                    mes("The crate is full of bananas.")
                } else {
                    mes("The crate has $bananas banana${if (bananas == 1) "" else "s"} inside.")
                }
                if (rum) mes("There is also some rum stashed in here too.")
            }
        }
    }

    private suspend fun ProtectedAccess.fillBananaCrate() {
        arriveDelay()
        if (!PiratesTreasureState.employedAtPlantation(player)) {
            mes("Why would I want to do that?")
            return
        }
        val space = CRATE_CAPACITY - player.crateBananas
        if (space <= 0) {
            mes("The crate is already full.")
            return
        }
        val held = inv.count(BANANA)
        if (held == 0) {
            mes("You don't have any bananas.")
            return
        }
        val packed = minOf(space, held)
        if (invDel(inv, BANANA, packed).failure) return
        player.crateBananas += packed
        anim("seq.human_pickuptable")
        mesbox("You pack all your bananas into the crate.")
    }

    private suspend fun ProtectedAccess.packBanana() {
        arriveDelay()
        if (!PiratesTreasureState.employedAtPlantation(player)) {
            mes("Why would I want to do that?")
            return
        }
        if (player.crateBananas >= CRATE_CAPACITY) {
            mes("The crate is already full.")
            return
        }
        if (invDel(inv, BANANA, 1).failure) return
        player.crateBananas += 1
        anim("seq.human_pickuptable")
        mesbox("You pack a banana into the crate.")
    }

    private suspend fun ProtectedAccess.stashRum() {
        arriveDelay()
        if (!PiratesTreasureState.employedAtPlantation(player)) {
            mes("Why would I want to do that?")
            return
        }
        when {
            PiratesTreasureState.stage(player) != FETCH_RUM -> mes("I see no reason to do that.")
            player.crateRum == RUM_SHIPPED ->
                mes("There is already some rum in Wydin's store, I should go and get that first.")
            player.crateRum == RUM_IN_CRATE -> mes("There's already some rum in here...")
            else -> {
                if (invDel(inv, KARAMJA_RUM, 1).failure) return
                player.crateRum = RUM_IN_CRATE
                anim("seq.human_pickuptable")
                mesbox("You stash the rum in the crate.")
            }
        }
    }

    private suspend fun ProtectedAccess.searchGroceryCrate() {
        arriveDelay()
        mes("There are a lot of bananas in the crate.")
        delay(SEARCH_DELAY)
        if (player.crateRum == RUM_SHIPPED) {
            if (invAdd(inv, KARAMJA_RUM).failure) {
                mes("You don't have enough inventory space.")
                return
            }
            player.crateRum = RUM_NONE
            anim("seq.human_pickuptable")
            mes("You find your bottle of rum in amongst the bananas.")
            delay(1)
        }
        if (!choice2("Yes.", true, "No.", false, title = "Do you want to take a banana?")) return
        if (invAdd(inv, BANANA).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        anim("seq.human_pickuptable")
        mes("You take a banana.")
    }

    private enum class LuthasTopic {
        Task,
        NotFull,
        Delivery,
        Customs,
        Another,
        Leave,
    }

    private companion object {
        const val LUTHAS = "npc.luthas"
        const val ZEMBO = "npc.zembo"
        const val BANANA = "obj.banana"
        const val COINS = "obj.coins"
        const val BANANA_CRATE = "loc.bananacrate"
        const val GROCERY_CRATE = "loc.grocerycrate"
        const val CRATE_PAYMENT = 30
        const val SEARCH_DELAY = 3
        const val BAR_BUY = 70.0
        const val BAR_SELL = 100.0
        const val BAR_CHANGE = 2.0
    }
}
