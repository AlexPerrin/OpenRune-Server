package org.rsmod.content.areas.city.varrock.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.rmNotes
import org.rsmod.content.quest.area.lumbridge.rmNotesGiven
import org.rsmod.content.skills.runecrafting.essence.teleportToRuneEssenceMine
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Aubury
@Inject
constructor(
    private val shops: Shops,
    private val runeMysteries: RuneMysteriesQuest,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val quest
        get() = runeMysteries.quest

    override fun ScriptContext.startup() {
        onOpNpc1("npc.aubury") { startAuburyDialogue(it.npc) }
        onOpNpc3("npc.aubury") { player.openAuburyShop(it.npc) }
        onOpNpc4("npc.aubury") {
            if (quest.isQuestCompleted(player)) {
                teleportToRuneEssenceMine(it.npc, worldRepo)
            }
        }
    }

    private suspend fun ProtectedAccess.startAuburyDialogue(npc: Npc) {
        startDialogue(npc) { auburyDialogue(npc) }
    }

    private suspend fun Dialogue.auburyDialogue(npc: Npc) {
        when (quest.getQuestStage(player)) {
            RuneMysteriesQuest.STAGE_AGREED -> auburyDeliverPackage(npc)
            RuneMysteriesQuest.STAGE_PACKAGE_GIVEN -> auburyPackageGiven(npc)
            RuneMysteriesQuest.STAGE_PACKAGE_READ ->
                when {
                    !player.rmNotes -> {
                        chatNpc(happy, TAKE_NOTES_BACK)
                        chatPlayer(happy, "Okay.")
                        if (giveNotes()) {
                            offerTea()
                        }
                    }
                    !player.rmNotesGiven -> auburyNotesFollowUp(npc)
                    else -> auburyShopDialogue(npc, includeTeleport = false)
                }
            else -> auburyShopDialogue(npc, includeTeleport = quest.isQuestCompleted(player))
        }
    }

    private suspend fun Dialogue.auburyDeliverPackage(npc: Npc) {
        chatNpc(quiz, "Do you want to buy some runes?")
        when (
            choice3(
                "Yes please!",
                1,
                "I've been sent here with a package for you.",
                2,
                "Oh, it's a rune shop. No thank you, then.",
                3,
            )
        ) {
            1 -> yesPlease(npc)
            2 -> {
                chatPlayer(happy, "I've been sent here with a package for you.")
                chatNpc(quiz, "A package? From who?")
                chatPlayer(happy, "From Sedridor at the Wizards' Tower.")
                chatNpc(
                    shocked,
                    "From Sedridor? But... surely, he can't have? Please, let me have it. It must be " +
                        "extremely important for him to have sent a stranger.",
                )
                if (access.invDel(access.inv, RuneMysteriesQuest.RESEARCH_PACKAGE).failure) {
                    chatPlayer(sad, "Uh... yeah... about that... I kind of don't have it with me...")
                    chatNpc(
                        angry,
                        "What kind of person says they have a delivery for me, but not with them? Honestly.",
                    )
                    chatNpc(angry, "Come back when you have it.")
                    return
                }
                quest.setQuestStage(access, RuneMysteriesQuest.STAGE_PACKAGE_GIVEN)
                objbox(RuneMysteriesQuest.RESEARCH_PACKAGE, "You hand the package to Aubury.")
                chatNpc(happy, "Now, let's have a look...")
                readPackage()
            }
            3 -> {
                chatPlayer(bored, "Oh, it's a rune shop. No thank you, then.")
                chatNpc(sad, "Well, if you find someone who does want runes, please send them my way.")
            }
        }
    }

    private suspend fun Dialogue.auburyPackageGiven(npc: Npc) {
        chatNpc(quiz, "Do you want to buy some runes?")
        when (
            choice3(
                "Anything useful in that package I gave you?",
                1,
                "Yes please!",
                2,
                "No thank you.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Anything useful in that package I gave you?")
                chatNpc(happy, "Well, let's have a look...")
                readPackage()
            }
            2 -> yesPlease(npc)
            3 -> noThankYou()
        }
    }

    private suspend fun Dialogue.readPackage() {
        objbox(RuneMysteriesQuest.RESEARCH_PACKAGE, "Aubury goes through the package of research notes.")
        quest.setQuestStage(access, RuneMysteriesQuest.STAGE_PACKAGE_READ)
        chatNpc(shocked, "This... this is incredible.")
        chatNpc(
            happy,
            "My gratitude to you adventurer for bringing me these research notes. Thanks to " +
                "you, I think we finally have it.",
        )
        chatPlayer(quiz, "You mean the incantation?")
        chatNpc(quiz, "Well when we combine my own research with this latest discovery, I think we might just...")
        chatNpc(
            happy,
            "No, no, I'm getting ahead of myself. The signs are promising, but let's not jump " +
                "to any conclusions just yet.",
        )
        chatNpc(happy, TAKE_NOTES_BACK)
        if (giveNotes()) {
            offerTea()
        }
    }

    private suspend fun Dialogue.giveNotes(): Boolean {
        if (access.invAdd(access.inv, RuneMysteriesQuest.RESEARCH_NOTES).failure) {
            chatNpc(sad, "You don't have room for the notes. Come back when you have some space.")
            return false
        }
        access.invDel(access.bank, RuneMysteriesQuest.RESEARCH_NOTES)
        player.rmNotes = true
        objbox(RuneMysteriesQuest.RESEARCH_NOTES, "Aubury hands you some research notes.")
        return true
    }

    private suspend fun Dialogue.offerTea() {
        chatNpc(happy, "Before you leave, why not have a cup of tea?")
        when (choice2("I'd love a cup of tea.", 1, "No, thank you.", 2)) {
            1 -> {
                chatPlayer(happy, "I'd love a cup of tea.")
                player.runEnergy = Constants.run_max_energy
                UpdateRun.energy(player, player.runEnergy)
                player.say("Aaah, nothing like a nice cuppa tea!")
            }
            2 -> chatPlayer(quiz, "No, thank you.")
        }
    }

    private suspend fun Dialogue.auburyNotesFollowUp(npc: Npc) {
        chatNpc(quiz, "Hello. Did you take those notes back to Sedridor?")
        if (RuneMysteriesQuest.RESEARCH_NOTES !in player.inv) {
            chatPlayer(sad, "Sorry, but I lost them.")
            chatNpc(
                happy,
                "Well, luckily I have duplicates. It's a good thing they are written in code. I " +
                    "wouldn't want the wrong kind of person to get access to the information " +
                    "contained within.",
            )
            giveNotes()
            return
        }
        chatPlayer(happy, "I'm still working on it.")
        chatNpc(
            happy,
            "Don't take too long. He'll be eager to see if this is indeed the breakthrough we " +
                "were hoping for.",
        )
        chatNpc(quiz, "Now, did you want to buy some runes?")
        when (choice2("Yes please!", 1, "No thank you.", 2)) {
            1 -> yesPlease(npc)
            2 -> noThankYou()
        }
    }

    private suspend fun Dialogue.auburyShopDialogue(npc: Npc, includeTeleport: Boolean) {
        chatNpc(quiz, "Do you want to buy some runes?")
        if (!includeTeleport) {
            when (choice2("Yes please!", 1, "Oh, it's a rune shop. No thank you, then.", 2)) {
                1 -> yesPlease(npc)
                2 -> {
                    chatPlayer(bored, "Oh, it's a rune shop. No thank you, then.")
                    chatNpc(sad, "Well, if you find someone who does want runes, please send them my way.")
                }
            }
            return
        }
        when (
            choice3(
                "Yes please!",
                1,
                "No thank you.",
                2,
                "Can you teleport me to the Rune Essence?",
                3,
            )
        ) {
            1 -> yesPlease(npc)
            2 -> noThankYou()
            3 -> {
                chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
                chatNpc(
                    happy,
                    "Of course. By the way, if you end up making any runes from the essence you " +
                        "mine, I'll happily buy them from you.",
                )
                teleportToRuneEssenceMine(worldRepo)
            }
        }
    }

    private suspend fun Dialogue.yesPlease(npc: Npc) {
        chatPlayer(happy, "Yes please!")
        player.openAuburyShop(npc)
    }

    private suspend fun Dialogue.noThankYou() {
        chatPlayer(quiz, "No thank you.")
        chatNpc(sad, "Well, if you find someone who does want runes, please send them my way.")
    }

    private fun Player.openAuburyShop(npc: Npc) {
        shops.open(this, npc, "Aubury's Rune Shop.", "inv.runeshop")
    }

    private companion object {
        const val TAKE_NOTES_BACK =
            "Here, take these notes back to Sedridor. They should hopefully give him everything he needs."
    }
}
