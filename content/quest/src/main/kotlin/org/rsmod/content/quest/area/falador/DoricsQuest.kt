package org.rsmod.content.quest.area.falador

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.events.interact.LocOverrideEvents
import org.rsmod.api.player.interact.LocOpOverrides
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseMiningLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Doric's Quest. Progress is varp 31 (`varp.doricquest`), as in OSRS and
 * as Quest Helper reads it: 0 not started, 10 started, 100 complete. Dialogue follows the wiki
 * transcript; the journal text is ours. Until the quest is complete, Smith on Doric's anvils
 * brings Doric over instead (using a bar or hammer on them is not stopped).
 */
class DoricsQuest
@Inject
constructor(private val objRepo: ObjRepository, private val locOverrides: LocOpOverrides) :
    QuestScript(
        "quest_dorics",
        "varp.doricquest",
        rewards {
            xp("stat.mining", MINING_XP)
            item("obj.coins", 180, label = "180 Coins")
            extra("Use of Doric's Anvils")
        },
        ItemRewardDisplay(STEEL_PICKAXE_DISPLAY, zoom = 300),
        questVarbit = "varbit.doric_quest_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == COMPLETE) {
            "Doric's Quest end state is ${quest.maxSteps} in the cache dbrow, but the hand-in " +
                "writes $COMPLETE."
        }
        onOpNpc1(DORIC) { startDialogue(it.npc) { doricDialogue() } }

        val anvil = ANVIL.asRSCM(RSCMType.LOC)
        locOverrides.register(anvil, InteractionOp.Op1) { player, _ -> !quest.isQuestCompleted(player) }
        onProtectedEvent<LocOverrideEvents.Op>(LocOverrideEvents.key(anvil, InteractionOp.Op1)) {
            startDialogue { anvilBeforeQuest() }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Doric</col>, who lives <col=800000>north of Falador</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description("I have spoken to <red>Doric</red>, north of Falador.")
            objective(
                "He needs <red>6 clay</red>, <red>4 copper ore</red> and <red>2 iron ore</red> " +
                    "before he will let me use his anvils."
            ) {
                hasItem("clay", "I have some <red>clay</red>.")
                    .preserveObjective(strikeObjective = false)
                hasItem("copper_ore", "I have some <red>copper ore</red>.")
                    .preserveObjective(strikeObjective = false)
                hasItem("iron_ore", "I have some <red>iron ore</red>.")
                    .preserveObjective(strikeObjective = false)
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I brought Doric 6 clay, 4 copper ore and 2 iron ore for his pickaxes. He paid " +
                    "me and lets me use his anvils."
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun hasMaterials(player: Player): Boolean =
        MATERIALS.all { (obj, count) -> player.inv.count(obj) >= count }

    private fun hasExactMaterials(player: Player): Boolean =
        MATERIALS.all { (obj, count) -> player.inv.count(obj) == count }

    private suspend fun Dialogue.doricDialogue() {
        when {
            quest.isQuestCompleted(player) -> afterQuest()
            stage(player) >= STARTED -> duringQuest()
            else -> beforeQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(happy, "Hello traveller, what brings you to my humble smithy?")
        when (
            choice5(
                "I wanted to use your anvils.",
                1,
                "I want to use your whetstone.",
                2,
                "Mind your own business, shortstuff!",
                3,
                "I was just checking out the landscape.",
                4,
                "What do you make here?",
                5,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I wanted to use your anvils.")
                chatNpc(
                    neutral,
                    "My anvils get enough work with my own use. I make pickaxes, and it takes a " +
                        "lot of hard work. If you could get me some more materials, then I could " +
                        "let you use them.",
                )
                offerQuest()
            }
            2 -> {
                chatPlayer(neutral, "I wanted to use your whetstone.")
                chatNpc(
                    neutral,
                    "The whetstone is for more advanced smithing, but I could let you use it as " +
                        "well as my anvils if you could get me some more materials.",
                )
                offerQuest()
            }
            3 -> {
                chatPlayer(angry, "Mind your own business, shortstuff!")
                chatNpc(
                    angry,
                    "How nice to meet someone with such pleasant manners. Do come again when you " +
                        "need to shout at someone smaller than you!",
                )
            }
            4 -> landscape()
            5 -> whatDoYouMake()
        }
    }

    private suspend fun Dialogue.offerQuest() {
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "No, hitting rocks is for the boring people, sorry.")
            chatNpc(neutral, "That is your choice. Nice to meet you anyway.")
            return
        }
        chatPlayer(happy, "Yes, I will get you the materials.")
        quest.setQuestStage(access, STARTED)
        chatNpc(
            neutral,
            "Clay is what I use more than anything, to make casts. Could you get me 6 clay, " +
                "4 copper ore, and 2 iron ore, please? I could pay a little, and let you use my " +
                "anvils.",
        )
        chatNpc(neutral, "Take this pickaxe with you just in case you need it.")
        access.invAddOrDrop(objRepo, BRONZE_PICKAXE)
        if (hasMaterials(player)) {
            alreadyHaveMaterials()
            return
        }
        whereToFind()
    }

    private suspend fun Dialogue.alreadyHaveMaterials() {
        chatPlayer(happy, "You know, it's funny you should require those exact things!")
        chatNpc(confused, "What do you mean?")
        chatPlayer(
            happy,
            "I can usually fit 28 things in my backpack and in a world full of quite literally " +
                "limitless possibilities, a complete coincidence has occurred!",
        )
        chatNpc(confused, "I don't quite understand what you're saying?")
        chatPlayer(
            happy,
            "Well, out of pure coincidence, despite definitely not knowing what you were about " +
                "to request, I just so happened to have carried those exact items!",
        )
        if (hasExactMaterials(player)) {
            chatPlayer(happy, "In fact, in the exact quantities too!")
        }
        chatNpc(
            happy,
            "Oh my, that is a coincidence! Pass them here, please. I can spare you some coins for " +
                "your trouble, and please use my anvils any time you want.",
        )
        handIn()
    }

    private suspend fun Dialogue.whereToFind() {
        when (choice2("Where can I find those?", 1, "Certainly, I'll be right back!", 2)) {
            1 -> {
                chatPlayer(quiz, "Where can I find those?")
                chatNpc(
                    neutral,
                    "You'll be able to find all those ores in the rocks just inside the Dwarven " +
                        "Mine. Head east from here and you'll find the entrance in the side of " +
                        "Ice Mountain.",
                )
                if (player.baseMiningLvl < 15) {
                    chatPlayer(sad, "But I'm not a good enough miner to get iron ore.")
                    chatNpc(
                        neutral,
                        "Oh well, you could practice mining until you can. Can't beat a bit of " +
                            "mining - it's a useful skill. Failing that, you might be able to " +
                            "find a more experienced adventurer to buy the iron ore off.",
                    )
                }
            }
            2 -> chatPlayer(happy, "Certainly, I'll be right back!")
        }
    }

    private suspend fun Dialogue.duringQuest() {
        chatNpc(quiz, "Have you got my materials yet, traveller?")
        if (hasMaterials(player)) {
            chatPlayer(happy, "I have everything you need!")
            chatNpc(
                happy,
                "Many thanks! Pass them here, please. I can spare you some coins for your " +
                    "trouble, and please use my anvils any time you want.",
            )
            handIn()
            return
        }
        chatPlayer(sad, "Sorry, I don't have them all yet.")
        chatNpc(
            neutral,
            "Not to worry, stick at it. Remember, I need 6 clay, 4 copper ore, and 2 iron ore.",
        )
        whereToFind()
    }

    private suspend fun Dialogue.handIn() {
        val taken =
            player.invTransaction(access.inv) {
                val from = select(access.inv)
                for ((obj, count) in MATERIALS) {
                    delete {
                        this.from = from
                        this.obj = obj.asRSCM(RSCMType.OBJ)
                        this.strictCount = count
                    }
                }
            }
        if (taken.failure) {
            return
        }
        mesbox("You hand the clay, copper, and iron to Doric.")
        quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Hello traveller, what brings you to my humble smithy?")
        when (
            choice2("I was just checking out the landscape.", 1, "What do you make here?", 2)
        ) {
            1 -> landscape()
            2 -> whatDoYouMake()
        }
    }

    private suspend fun Dialogue.landscape() {
        chatPlayer(neutral, "I was just checking out the landscape.")
        chatNpc(
            happy,
            "Hope you like it. I do enjoy the solitude of my little home. If you get time, please " +
                "say hi to my friends in the Dwarven Mine.",
        )
        when (choice2("Dwarven Mine?", 1, "Will do!", 2)) {
            1 -> {
                chatPlayer(quiz, "Dwarven Mine?")
                chatNpc(
                    happy,
                    "Yep, the entrance is in the side of Ice Mountain just to the east of here. " +
                        "They're a friendly bunch. Stop in at Nurmof's store and buy one of my " +
                        "pickaxes!",
                )
            }
            2 -> chatPlayer(happy, "Will do!")
        }
    }

    private suspend fun Dialogue.whatDoYouMake() {
        chatPlayer(quiz, "What do you make here?")
        chatNpc(happy, "I make pickaxes. I am the best maker of pickaxes in the whole of Gielinor.")
        chatPlayer(quiz, "Do you have any to sell?")
        chatNpc(neutral, "Sorry, but I've got a running order with Nurmof.")
        when (choice2("Who's Nurmof?", 1, "Ah, fair enough.", 2)) {
            1 -> {
                chatPlayer(quiz, "Who's Nurmof?")
                chatNpc(
                    neutral,
                    "Nurmof has a store over in the Dwarven Mine. You can find the entrance on " +
                        "the side of Ice Mountain to the east of here.",
                )
            }
            2 -> chatPlayer(neutral, "Ah, fair enough.")
        }
    }

    private suspend fun Dialogue.anvilBeforeQuest() {
        doric(
            angry,
            "Hey, who said you could use that? My anvils get enough work with my own use. I make " +
                "pickaxes, and it takes a lot of hard work."
        )
        when (
            choice3(
                "Sorry, would it be OK if I used your anvils?",
                1,
                "No, hitting rocks is for the boring people, sorry.",
                2,
                "I didn't want to use your anvils anyway.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Sorry, would it be OK if I used your anvils?")
                if (stage(player) >= STARTED) {
                    doric(
                        neutral,
                        "Remember, I need 6 clay, 4 copper ore, and 2 iron ore."
                    )
                    return
                }
                doric(
                    neutral,
                    "If you could get me some more materials then I could let you use them.",
                )
                when (
                    choice2(
                        "Yes, I will get you materials.",
                        1,
                        "No, hitting rocks is for the boring people, sorry.",
                        2,
                    )
                ) {
                    1 -> {
                        chatPlayer(happy, "Yes, I will get you the materials.")
                        quest.setQuestStage(access, STARTED)
                        doric(
                            neutral,
                            "Clay is what I use more than anything, to make casts. Could you " +
                                "get me 6 clay, 4 copper ore, and 2 iron ore, please? I could " +
                                "pay a little, and let you use my anvils.",
                        )
                        doric(neutral, "Take this pickaxe with you just in case you need it.")
                        access.invAddOrDrop(objRepo, BRONZE_PICKAXE)
                    }
                    2 -> {
                        chatPlayer(neutral, "No, hitting rocks is for the boring people, sorry.")
                        doric(neutral, "That is your choice. Nice to meet you anyway.")
                    }
                }
            }
            2 -> {
                chatPlayer(neutral, "No, hitting rocks is for the boring people, sorry.")
                doric(neutral, "That is your choice. Nice to meet you anyway.")
            }
            3 -> {
                chatPlayer(angry, "I didn't want to use your anvils anyway.")
                doric(neutral, "That is your choice.")
            }
        }
    }

    private suspend fun Dialogue.doric(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Doric", DORIC, mesanim, text)

    companion object {
        const val STARTED = 10
        const val COMPLETE = 100

        private const val DORIC = "npc.doric"
        private const val ANVIL = "loc.dorics_anvil"
        private const val BRONZE_PICKAXE = "obj.bronze_pickaxe"
        private const val STEEL_PICKAXE_DISPLAY = "obj.steel_pickaxe"
        private const val MINING_XP = 1300.0

        val MATERIALS = listOf("obj.clay" to 6, "obj.copper_ore" to 4, "obj.iron_ore" to 2)
    }
}
