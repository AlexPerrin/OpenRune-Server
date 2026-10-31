package org.rsmod.content.areas.city.portsarim.travel

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KaramjaFerryScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (sailor in SARIM_CREW) {
            onOpNpc1(sailor) { startDialogue(it.npc) { offerTrip() } }
            onOpNpc3(sailor) { payAndSail(MUSA_POINT, MUSA_POINT_NAME) }
        }
        onOpNpc1(CUSTOMS_OFFICER) { startDialogue(it.npc) { customsOfficer() } }
        onOpNpc3(CUSTOMS_OFFICER) {
            if (PiratesTreasureState.carriesRum(player)) {
                confiscateRum()
            } else {
                payAndSail(PORT_SARIM, PORT_SARIM_NAME)
            }
        }
    }

    private suspend fun Dialogue.offerTrip() {
        chatNpc(
            quiz,
            "Hello there. Do you want to go on a trip to Karamja? We can take you to Musa Point " +
                "for only $FARE coins.",
        )
        if (!choice2("Yes please.", true, "No thank you.", false)) {
            chatPlayer(neutral, "No thank you.")
            chatNpc(neutral, "Fair enough.")
            return
        }
        chatPlayer(happy, "Yes please.")
        board(MUSA_POINT, MUSA_POINT_NAME)
    }

    // Rum smuggling (Pirate's Treasure) from the wiki transcript. Karamja gloves and the Ring of
    // Charos(a) are not handled.
    private suspend fun Dialogue.customsOfficer() {
        chatNpc(neutral, "Can I help you?")
        val journey =
            choice2(
                "Can I journey on this ship?",
                true,
                "Does Karamja have unusual customs then?",
                false,
            )
        if (!journey) {
            chatPlayer(quiz, "Does Karamja have any unusual customs then?")
            chatNpc(neutral, "I'm not that sort of customs officer.")
            return
        }
        chatPlayer(quiz, "Can I journey on this ship?")
        chatNpc(neutral, "You need to be searched before you can board.")
        while (true) {
            val topic =
                choice3(
                    "Why?",
                    CustomsTopic.Why,
                    "Search away. I have nothing to hide.",
                    CustomsTopic.Search,
                    "You're not putting your hands on my things!",
                    CustomsTopic.Refuse,
                )
            when (topic) {
                CustomsTopic.Why -> {
                    chatPlayer(quiz, "Why?")
                    chatNpc(neutral, "Because Asgarnia has banned the import of intoxicating spirits.")
                }
                CustomsTopic.Search -> return search()
                CustomsTopic.Refuse -> {
                    chatPlayer(angry, "You're not putting your hands on my things!")
                    chatNpc(neutral, "You're not getting on this ship then.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.search() {
        chatPlayer(neutral, "Search away. I have nothing to hide.")
        if (PiratesTreasureState.carriesRum(player)) {
            chatNpc(angry, "Aha, trying to smuggle rum are we?")
            chatPlayer(shifty, "Umm... it's for personal use?")
            access.confiscateRum()
            return
        }
        chatNpc(
            neutral,
            "Well you've got some odd stuff, but it's all legal. Now you need to pay a boarding " +
                "charge of $FARE coins.",
        )
        if (choice2("Okay.", true, "Oh, I'll not bother then.", false)) {
            chatPlayer(neutral, "Okay.")
            board(PORT_SARIM, PORT_SARIM_NAME)
        } else {
            chatPlayer(neutral, "Oh, I'll not bother then.")
        }
    }

    private fun ProtectedAccess.confiscateRum() {
        invDel(inv, PiratesTreasureState.KARAMJA_RUM, inv.count(PiratesTreasureState.KARAMJA_RUM))
        mes("The customs officer confiscates your rum.")
        mes("You will need to find some way to smuggle it off the island...")
    }

    private suspend fun Dialogue.board(dest: CoordGrid, destName: String) {
        if (!access.payFare(FARE)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        access.sailFerry(dest, destName, FARE)
    }

    private suspend fun ProtectedAccess.payAndSail(dest: CoordGrid, destName: String) {
        if (!payFare(FARE)) {
            mes("You do not have enough coins to pay passage, you need $FARE.")
            return
        }
        sailFerry(dest, destName, FARE)
    }

    private enum class CustomsTopic {
        Why,
        Search,
        Refuse,
    }

    private companion object {
        const val FARE = 30
        const val CUSTOMS_OFFICER = "npc.customs_officer"
        const val MUSA_POINT_NAME = "Musa Point"
        const val PORT_SARIM_NAME = "Port Sarim"

        val SARIM_CREW = listOf("npc.seaman_lorris", "npc.seaman_thresnor", "npc.captain_tobias")
        val MUSA_POINT = CoordGrid(2956, 3146, 0)
        val PORT_SARIM = CoordGrid(3029, 3217, 0)
    }
}
