package org.rsmod.content.areas.misc.wizards_tower.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.rmBackstory
import org.rsmod.content.quest.area.lumbridge.rmKnowName
import org.rsmod.content.quest.area.lumbridge.rmKnowOthers
import org.rsmod.content.quest.area.lumbridge.rmNotesGiven
import org.rsmod.content.quest.area.lumbridge.rmOwedTalisman
import org.rsmod.content.quest.area.lumbridge.rmPackage
import org.rsmod.content.quest.area.lumbridge.rmTalismanGiven
import org.rsmod.content.skills.runecrafting.essence.teleportToRuneEssenceMine
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ArchmageSedridor
@Inject
constructor(
    private val runeMysteries: RuneMysteriesQuest,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val quest
        get() = runeMysteries.quest

    override fun ScriptContext.startup() {
        onOpNpc1("npc.head_wizard") { startSedridorDialogue(it.npc) }
        onOpNpc3("npc.head_wizard") {
            if (quest.isQuestCompleted(player)) {
                teleportToRuneEssenceMine(it.npc, worldRepo)
            }
        }
    }

    private suspend fun ProtectedAccess.startSedridorDialogue(npc: Npc) {
        startDialogue(npc) { sedridorDialogue() }
    }

    private suspend fun Dialogue.sedridorDialogue() {
        val stage = quest.getQuestStage(player)
        when {
            quest.isQuestCompleted(player) -> sedridorAfterQuest()
            stage == 0 -> sedridorBeforeQuest()
            stage == RuneMysteriesQuest.STAGE_TALISMAN -> sedridorIntroduction()
            stage == RuneMysteriesQuest.STAGE_MET_SEDRIDOR && !player.rmTalismanGiven -> sedridorWelcomeBack()
            stage == RuneMysteriesQuest.STAGE_MET_SEDRIDOR -> {
                chatPlayer(quiz, "So is that talisman of any use to you?")
                examineTalisman()
            }
            stage == RuneMysteriesQuest.STAGE_AGREED && !player.rmPackage -> {
                chatNpc(
                    happy,
                    "Hello again, adventurer. Please, take this package of research notes to Aubury in " +
                        "Varrock. He runs a rune shop in the south east of the city.",
                )
                givePackage()
            }
            stage == RuneMysteriesQuest.STAGE_AGREED -> sedridorPackageFollowUp()
            else -> sedridorReceiveNotes()
        }
    }

    private suspend fun Dialogue.sedridorBeforeQuest() {
        chatNpc(
            happy,
            "Welcome adventurer, to the world renowned Wizards' Tower, home to the Order of " +
                "Wizards. How may I help you?",
        )
        chatPlayer(quiz, "I'm just looking around.")
        chatNpc(
            happy,
            "Well, take care adventurer. You stand on the ruins of the old destroyed Wizards' " +
                "Tower. Strange and powerful magicks lurk here.",
        )
    }

    private suspend fun Dialogue.sedridorIntroduction() {
        chatNpc(
            happy,
            "Welcome adventurer, to the world renowned Wizards' Tower, home to the Order of " +
                "Wizards. We are the oldest and most prestigious group of wizards around. Now, " +
                "how may I help you?",
        )
        chatPlayer(quiz, "Are you Sedridor?")
        chatNpc(quiz, "Sedridor? What is it you want with him?")
        chatPlayer(
            happy,
            "The Duke of Lumbridge sent me to find him. I have this talisman he found. He said " +
                "Sedridor would be interested in it.",
        )
        chatNpc(quiz, "Did he now? Well hand it over then, and we'll see what all the hubbub is about.")
        when (
            choice3(
                "Okay, here you are.",
                1,
                "No, I'll only give it to Sedridor.",
                2,
                "No, I don't think you are Sedridor.",
                3,
            )
        ) {
            1 -> handInTalisman()
            2 -> {
                chatPlayer(angry, "No, I'll only give it to Sedridor.")
                chatNpc(
                    happy,
                    "Well good news, for I am Sedridor! Now, hand it over and let me have a proper " +
                        "look at it, hmm?",
                )
                when (choice2("Okay, here you are.", 1, "No, I don't think you are Sedridor.", 2)) {
                    1 -> handInTalisman()
                    2 -> doubtIdentity()
                }
            }
            3 -> doubtIdentity()
        }
    }

    private suspend fun Dialogue.doubtIdentity() {
        chatPlayer(angry, "No, I don't think you are Sedridor.")
        chatNpc(
            quiz,
            "Hmm... Well, I admire your caution adventurer. Perhaps I can prove myself? I " +
                "will use my mental powers to discover...",
        )
        chatNpc(happy, "Your name is... ${player.displayName}!")
        player.rmKnowName = true
        chatPlayer(shocked, "You're right! How did you know that?")
        chatNpc(
            happy,
            "Well I am the Archmage you know! You don't get to my position without learning " +
                "a few tricks along the way!",
        )
        chatNpc(quiz, "So now that I have proved myself to you, why don't you hand over that talisman, hmm?")
        handInTalisman()
    }

    private suspend fun Dialogue.handInTalisman() {
        chatPlayer(happy, "Okay, here you are.")
        if (!takeTalisman()) {
            quest.setQuestStage(access, RuneMysteriesQuest.STAGE_MET_SEDRIDOR)
            chatNpc(confused, "...")
            chatPlayer(confused, "...")
            chatNpc(angry, "Well?")
            chatPlayer(sad, "I don't seem to have it with me.")
            chatNpc(confused, "Hmm? You are a very odd person. Come back again when you have it.")
            return
        }
        examineTalisman()
    }

    private suspend fun Dialogue.sedridorWelcomeBack() {
        chatNpc(quiz, "Welcome back, adventurer. Do you have that talisman now?")
        if (RuneMysteriesQuest.AIR_TALISMAN !in player.inv) {
            chatPlayer(sad, "Not yet.")
            chatNpc(happy, "Well come back when you have it.")
            return
        }
        chatPlayer(happy, "Here you go.")
        if (takeTalisman()) {
            examineTalisman()
        }
    }

    private fun Dialogue.takeTalisman(): Boolean {
        if (access.invDel(access.inv, RuneMysteriesQuest.AIR_TALISMAN).failure) {
            return false
        }
        quest.setQuestStage(access, RuneMysteriesQuest.STAGE_MET_SEDRIDOR)
        player.rmTalismanGiven = true
        return true
    }

    private suspend fun Dialogue.examineTalisman() {
        objbox(RuneMysteriesQuest.AIR_TALISMAN, "You hand the talisman to Sedridor.")
        chatNpc(
            quiz,
            "Hmm... Doesn't seem to be anything too special. Just a normal air talisman by the " +
                "looks of things. Still, looks can be deceiving. Let me take a closer look...",
        )
        objbox(
            RuneMysteriesQuest.AIR_TALISMAN,
            "Sedridor murmurs some sort of incantation and the talisman glows slightly.",
        )
        chatNpc(
            happy,
            "How interesting... It would appear I spoke too soon. There's more to this talisman " +
                "than meets the eye. In fact, it may well be the last piece of the puzzle.",
        )
        chatPlayer(quiz, "Puzzle?")
        chatNpc(
            happy,
            "Indeed! The lost legacy of the first tower. This talisman may in fact be key to " +
                "finding the forgotten essence mine!",
        )
        chatPlayer(confused, "First tower? Forgotten essence mine? What are you on about?")
        chatNpc(happy, "Ah, my apologies, adventurer. Allow me to fill you in.")
        when (choice2("Go ahead.", 1, "Actually, I'm not interested.", 2)) {
            1 -> sedridorLore()
            2 -> {
                chatPlayer(bored, "Actually, I'm not interested.")
                chatNpc(
                    sad,
                    "Oh... Well I guess the short of it is that this talisman could be key to " +
                        "helping us rediscover an important teleportation incantation.",
                )
                chatNpc(
                    happy,
                    "With it, we'll be able to access a hidden essence mine, our lost source of " +
                        "rune essence.",
                )
                player.rmBackstory = true
            }
        }
        sedridorAskDeliverPackage()
    }

    private suspend fun Dialogue.sedridorLore() {
        chatPlayer(happy, "Go ahead.")
        chatNpc(happy, "As you are likely aware, when we cast spells, we do so using the power of runes.")
        chatNpc(
            happy,
            "These runes are crafted from a highly unique material, and then imbued with magical " +
                "power from various runic altars. Different altars create different runes with " +
                "different magical effects.",
        )
        chatNpc(
            happy,
            "The process of imbuing runes is called runecrafting. Legend has it that this was once " +
                "a common art, but the secrets of how to do it were lost until just under two " +
                "hundred years ago.",
        )
        chatNpc(
            happy,
            "The rediscovery of runecrafting had such a large impact on the world, that it marked " +
                "the dawn of the Fifth Age. It also resulted in the birth of our order, and the " +
                "construction of the first Wizards' Tower.",
        )
        chatPlayer(quiz, "If it was the first tower, I'm guessing it doesn't exist anymore? What happened?")
        chatNpc(
            sad,
            "It was burnt down by traitorous members of our own order. They followed the evil god " +
                "of chaos, Zamorak, and they wished to claim our magical discoveries in his name.",
        )
        chatNpc(
            sad,
            "When the tower burnt down, much was lost, including an important incantation. A spell " +
                "that could be used to teleport to a hidden essence mine.",
        )
        chatPlayer(quiz, "The essence mine you mentioned earlier, I assume?")
        chatNpc(
            happy,
            "Precisely. Rune essence is the material used to make runes, but it is incredibly rare. " +
                "That essence mine was the only place it could be found that our order knew of.",
        )
        chatNpc(sad, "Since the incantation was lost, we have struggled to maintain our stocks of rune essence.")
        chatNpc(
            sad,
            "There are seemingly those out there that still know where to find some, but while they " +
                "have been willing to sell essence to us, they have refused to share knowledge on " +
                "how to find it ourselves.",
        )
        chatPlayer(
            quiz,
            "I'm starting to see why this is so important. So you think this talisman can help you " +
                "rediscover that incantation?",
        )
        chatNpc(
            happy,
            "I do! All magic leaves traces, and from what I can tell, this talisman was used heavily " +
                "during the time of the first tower.",
        )
        chatNpc(
            happy,
            "It would have been taken to the essence mine many times, and the magical energies there " +
                "will have left an imprint on it. To think that it was hidden in Lumbridge all this time!",
        )
        player.rmBackstory = true
        chatPlayer(quiz, "So what happens now?")
    }

    private suspend fun Dialogue.sedridorAskDeliverPackage() {
        chatNpc(
            happy,
            "It is critical I share this discovery with my associate, Aubury, as soon as possible. " +
                "He's not much of a wizard, but he's an expert on runecrafting, and his insight will " +
                "be essential.",
        )
        chatNpc(
            quiz,
            "Would you be willing to visit him for me? I would go myself, but I wish to study this " +
                "talisman some more.",
        )
        when (choice2("Yes, certainly.", 1, "No, I'm busy.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes, certainly.")
                quest.setQuestStage(access, RuneMysteriesQuest.STAGE_AGREED)
                chatNpc(
                    happy,
                    "He runs a rune shop in the south east of Varrock. Please, take this package of " +
                        "research notes to him. If all goes well, the secrets of the essence mine may " +
                        "soon be ours once more!",
                )
                givePackage()
            }
            2 -> {
                chatPlayer(sad, "No, I'm busy.")
                chatNpc(
                    sad,
                    "As you wish adventurer. I will continue to study this talisman you have brought " +
                        "me. Return here if you find yourself with some spare time to help me.",
                )
            }
        }
    }

    private suspend fun Dialogue.givePackage() {
        if (access.invAdd(access.inv, RuneMysteriesQuest.RESEARCH_PACKAGE).failure) {
            chatNpc(sad, "You don't have room for the package. Come back when you have some space.")
            return
        }
        access.invDel(access.bank, RuneMysteriesQuest.RESEARCH_PACKAGE)
        player.rmPackage = true
        objbox(RuneMysteriesQuest.RESEARCH_PACKAGE, "Sedridor hands you a package.")
        chatNpc(happy, "Best of luck, ${player.displayName}.")
        if (!player.rmKnowName) {
            chatPlayer(confused, "I don't remember telling you my name... How do you know it?")
            chatNpc(happy, "Really now? I am the Archmage you know.")
        }
    }

    private suspend fun Dialogue.sedridorPackageFollowUp() {
        chatNpc(quiz, "Hello again, adventurer. Did you take that package to Aubury?")
        if (RuneMysteriesQuest.RESEARCH_PACKAGE in player.inv) {
            chatPlayer(sad, "Not yet.")
            chatNpc(happy, "He runs a rune shop in the south east of Varrock. Please deliver it to him soon.")
            return
        }
        chatPlayer(sad, "I lost it. Could I have another?")
        chatNpc(happy, "Well it's a good job I have copies of everything.")
        givePackage()
    }

    private suspend fun Dialogue.sedridorReceiveNotes() {
        chatNpc(
            happy,
            "Ah, ${player.displayName}. How goes your quest? Have you delivered my research to Aubury yet?",
        )
        chatPlayer(happy, "Yes, I have. He gave me some notes to give to you.")
        chatNpc(happy, "Wonderful! Let's have a look then.")
        if (player.rmNotesGiven) {
            sedridorCompletion()
            return
        }
        if (access.invDel(access.inv, RuneMysteriesQuest.RESEARCH_NOTES).failure) {
            chatPlayer(sad, "Err, you're not going to believe this...")
            chatNpc(angry, "What?")
            chatPlayer(sad, "I don't have them.")
            chatNpc(
                angry,
                "Right... You're rather careless aren't you. I suggest you go and speak to Aubury " +
                    "once more. With luck he will have made copies.",
            )
            return
        }
        player.rmNotesGiven = true
        player.rmOwedTalisman = true
        objbox(RuneMysteriesQuest.RESEARCH_NOTES, "You hand the notes to Sedridor.")
        sedridorCompletion()
    }

    private suspend fun Dialogue.sedridorCompletion() {
        chatNpc(happy, "Alright, let's see what Aubury has for us...")
        chatNpc(happy, "Yes, this is it! The lost incantation!")
        chatPlayer(quiz, "So you'll be able to access that essence mine now?")
        chatNpc(
            happy,
            "That's right! Because of you, our order finally has a proper source of rune essence " +
                "again! Thank you, friend.",
        )
        chatNpc(
            happy,
            "If you ever want to access the essence mine yourself, just let me know. It's the least " +
                "I can do.",
        )
        chatNpc(
            happy,
            "I will also share the incantation with others, including Aubury. When I do, I'll let " +
                "them know that you are to be given unlimited access to the mine.",
        )
        chatNpc(
            happy,
            "Oh, and you can have this air talisman back as well. I have no further need of it, and " +
                "I'm sure you will find it useful.",
        )
        if (player.rmOwedTalisman) {
            if (access.invAdd(access.inv, RuneMysteriesQuest.AIR_TALISMAN).success) {
                player.rmOwedTalisman = false
            } else {
                chatNpc(sad, "Ah, your inventory is full. Come and speak to me again for the talisman.")
            }
        }
        chatNpc(
            happy,
            "In case you didn't know, the talisman can be used to craft air runes. Just take it to " +
                "the Air Altar south of Falador along with some rune essence.",
        )
        chatNpc(
            happy,
            "Don't worry if you can't find the altar. The talisman can guide you there. You may " +
                "find talismans for other altars as well while adventuring. They'll let you craft " +
                "other types of rune.",
        )
        chatPlayer(happy, "Great! Thanks!")
        chatNpc(happy, "My pleasure!")
        quest.setQuestStage(access, RuneMysteriesQuest.STAGE_COMPLETE)
        when (choice4(TELEPORT, 1, WHO_ELSE_KNOWS, 2, OLD_TOWER, 3, "I'd better get going.", 4)) {
            1 -> teleport()
            2 -> whoElseKnows()
            3 -> oldTower()
            4 -> chatPlayer(happy, "I'd better get going.")
        }
    }

    private suspend fun Dialogue.sedridorAfterQuest() {
        chatPlayer(happy, "Hello there.")
        chatNpc(happy, "Hello again, ${player.displayName}. What can I do for you?")
        if (player.rmOwedTalisman) {
            giveOwedAirTalisman()
        }
        when (
            choice4(
                TELEPORT,
                1,
                WHO_ELSE_KNOWS,
                2,
                OLD_TOWER,
                3,
                "Nothing thanks, I'm just looking around.",
                4,
            )
        ) {
            1 -> teleport()
            2 -> whoElseKnows()
            3 -> oldTower()
            4 -> {
                chatPlayer(quiz, "Nothing thanks, I'm just looking around.")
                chatNpc(
                    happy,
                    "Well, take care. You stand on the ruins of the old destroyed Wizards' Tower. " +
                        "Strange and powerful magicks lurk here.",
                )
            }
        }
    }

    private suspend fun Dialogue.giveOwedAirTalisman() {
        chatNpc(happy, "Ah, before I forget - I still have that air talisman for you.")
        if (access.invAdd(access.inv, RuneMysteriesQuest.AIR_TALISMAN).failure) {
            chatNpc(sad, "Your inventory is still full. Free up some space and speak to me again.")
            return
        }
        player.rmOwedTalisman = false
        objbox(RuneMysteriesQuest.AIR_TALISMAN, "Sedridor hands you an air talisman.")
    }

    private suspend fun Dialogue.teleport() {
        chatPlayer(quiz, TELEPORT)
        teleportToRuneEssenceMine(worldRepo)
    }

    private suspend fun Dialogue.whoElseKnows() {
        chatPlayer(quiz, WHO_ELSE_KNOWS)
        player.rmKnowOthers = true
        chatNpc(
            happy,
            "Apart from myself, there's also Aubury in Varrock, Wizard Cromperty in East " +
                "Ardougne, Brimstail in the Tree Gnome Stronghold and Wizard Distentor in " +
                "Yanille's Wizards' Guild.",
        )
        when (choice3(TELEPORT, 1, OLD_TOWER, 2, "Thanks for the information.", 3)) {
            1 -> teleport()
            2 -> oldTower()
            3 -> thanks()
        }
    }

    private suspend fun Dialogue.oldTower() {
        chatPlayer(quiz, OLD_TOWER)
        chatNpc(
            happy,
            "Of course. The first Wizards' Tower was built at the same time the Order of " +
                "Wizards was founded. It was at the dawn of the Fifth Age, when the secrets " +
                "of runecrafting were rediscovered.",
        )
        chatNpc(
            happy,
            "For years, the tower was a hub of magical research. Wizards of all races and " +
                "religions were welcomed into our order.",
        )
        chatNpc(
            sad,
            "Alas, that openness is what ultimately led to disaster. The wizards who served " +
                "Zamorak, the evil god of chaos, tried to claim our magical discoveries in " +
                "his name.",
        )
        chatNpc(
            sad,
            "They failed, but in retaliation, they burnt the entire tower to the ground. " +
                "Years of work was destroyed.",
        )
        chatNpc(
            happy,
            "The tower was soon rebuilt of course, but even now we are still trying to regain " +
                "knowledge that was lost.",
        )
        chatNpc(
            happy,
            "That's why I spend my time down here, in fact. This basement is all that is left " +
                "of the old tower, and I believe there are still some secrets to discover here.",
        )
        chatNpc(
            happy,
            "Of course, one secret I am no longer looking for is the teleportation " +
                "incantation to the Rune Essence Mine. We have you to thank for that.",
        )
        when (choice3(TELEPORT, 1, WHO_ELSE_KNOWS, 2, "Thanks for the information.", 3)) {
            1 -> teleport()
            2 -> whoElseKnows()
            3 -> thanks()
        }
    }

    private suspend fun Dialogue.thanks() {
        chatPlayer(happy, "Thanks for the information.")
        chatNpc(happy, "My pleasure.")
    }

    private companion object {
        const val TELEPORT = "Can you teleport me to the Rune Essence Mine?"
        const val WHO_ELSE_KNOWS = "Who else knows the teleport to the Rune Essence Mine?"
        const val OLD_TOWER = "Could you tell me about the old Wizards' Tower?"
    }
}
