package org.rsmod.content.quest.area.draynor

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.subjectPronoun
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class ErnestTheChicken
@Inject
constructor(private val npcRepo: NpcRepository) :
    QuestScript(
        "quest_ernestthechicken",
        "varp.haunted",
        rewards {
            item("obj.coins", ErnestProgress.CoinReward, label = "${ErnestProgress.CoinReward} Coins")
        },
        ItemRewardDisplay("obj.coins"),
        questVarbit = "varp.haunted",
    ) {

    private val logger = InlineLogger()

    override fun ScriptContext.init() {
        onOpNpc1("npc.veronica") { startVeronica(it.npc) }
        onOpNpc1("npc.professor_oddenstein") { startOddenstein(it.npc) }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Veronica</col> outside <col=800000>Draynor Manor</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "<red>Veronica</red> is worried about her fiance <red>Ernest</red>, who went into " +
                    "<red>Draynor Manor</red> to ask for directions and never came out."
            )

            objective("I should speak to whoever lives in the manor.") {
                stageAtLeast(2, "I found Ernest. He is a chicken.", finalise = true).strike()
            }

            if (progress.foundErnest(access.player) && !progress.hasReceivedParts(access.player)) {
                line(
                    "<red>Professor Oddenstein</red> needs three parts for his machine before he can " +
                        "turn Ernest back."
                )
                objective("I still need to find the <red>rubber tube</red>.") {
                    hasItem("rubber_tube", "I have found the <red>rubber tube</red>.")
                }
                objective("I still need to find the <red>pressure gauge</red>.") {
                    hasItem("pressure_gauge", "I have found the <red>pressure gauge</red>.")
                }
                objective("I still need to find the <red>oil can</red>.") {
                    hasItem("oil_can", "I have found the <red>oil can</red>.")
                }
            }

            objective(
                "I gave all three parts to <red>Professor Oddenstein</red>. " +
                    "I should speak to him to finish restoring Ernest."
            ) {
                visibleWhen { progress.hasReceivedParts(access.player) }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Veronica's fiance Ernest had been turned into a chicken by Professor Oddenstein, " +
                    "who lives in Draynor Manor."
            )
            line(
                "I recovered the rubber tube, pressure gauge and oil can that the house gremlins had " +
                    "stolen, and Oddenstein turned Ernest back into a man."
            )
        }

    private val progress = ErnestProgress(quest)

    private suspend fun ProtectedAccess.startVeronica(npc: Npc) = startDialogue(npc) { veronica() }

    private suspend fun ProtectedAccess.startOddenstein(npc: Npc) =
        startDialogue(npc) { oddenstein() }

    private suspend fun Dialogue.veronica() {
        if (quest.isQuestCompleted(player)) {
            chatNpc(happy, "Thank you for rescuing Ernest.")
            chatPlayer(quiz, "Where is he now?")
            chatNpc(
                happy,
                "Oh he went off to talk to some green warty guy. I'm sure he'll be back soon.",
            )
            return
        }
        if (quest.isQuestInProgress(player)) {
            chatNpc(sad, "Have you found my sweetheart yet?")
            if (!progress.foundErnest(player)) {
                chatPlayer(sad, "No, not yet.")
                return
            }
            chatPlayer(neutral, "Yes, he's a chicken.")
            chatNpc(
                angry,
                "I know he's not exactly brave but I think you're being a bit harsh.",
            )
            chatPlayer(
                neutral,
                "No no, he's been turned into an actual chicken by a mad scientist.",
            )
            chatNpc(shocked, "Eeeeeek!")
            chatNpc(sad, "My poor darling, why must these things happen to us.")
            chatPlayer(neutral, "Well I'm doing my best to turn him back.")
            chatNpc(sad, "Well be quick, I'm sure being a chicken can't be good for him.")
            return
        }

        chatNpc(sad, "Can you please help me? I'm in a terrible spot of trouble.")
        val start =
            choice2(
                "Aha, sounds like a quest. I'll help.",
                true,
                "No, I'm looking for something to kill.",
                false,
            )
        if (!start) {
            chatPlayer(angry, "No, I'm looking for something to kill.")
            chatNpc(shocked, "Oooh, you violent person you.")
            return
        }

        chatPlayer(happy, "Aha, sounds like a quest. I'll help.")
        chatNpc(
            neutral,
            "Yes yes, I suppose it is a quest. My fiance Ernest and I came upon this house.",
        )
        chatNpc(
            neutral,
            "Seeing as we were a little lost Ernest decided to go in and ask for directions.",
        )
        chatNpc(
            sad,
            "That was an hour ago. That house looks spooky, can you go and see if you can find " +
                "him for me?",
        )
        chatPlayer(neutral, "Ok, I'll see what I can do.")
        chatNpc(happy, "Thank you, thank you. I'm very grateful.")
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.oddenstein() {
        if (quest.isQuestInProgress(player) && progress.foundErnest(player)) {
            if (progress.hasReceivedParts(player)) {
                restore()
                return
            }
            chatNpc(neutral, "Have you found anything yet?")
            if (progress.hasAllParts(player)) handIn() else progressReport()
            return
        }

        chatNpc(neutral, "Be careful in here, there's lots of dangerous equipment.")
        val machine =
            if (quest.isQuestCompleted(player)) "What does the portal machine do?"
            else "What does this machine do?"
        val option =
            if (quest.isQuestInProgress(player)) {
                choice3(
                    "I'm looking for a guy called Ernest.",
                    0,
                    machine,
                    1,
                    "Is this your house?",
                    2,
                )
            } else {
                choice2(machine, 1, "Is this your house?", 2)
            }
        when (option) {
            0 -> introduceErnest()
            1 -> if (quest.isQuestCompleted(player)) portalMachine() else brokenMachine()
            else -> {
                chatPlayer(quiz, "Is this your house?")
                chatNpc(
                    neutral,
                    "No, I'm just one of the tenants. It belongs to the count who lives in the " +
                        "basement.",
                )
            }
        }
    }

    private suspend fun Dialogue.brokenMachine() {
        chatPlayer(quiz, "What does this machine do?")
        chatNpc(
            neutral,
            "Nothing at the moment... It's broken. It's meant to be a transmutation machine.",
        )
        chatNpc(
            neutral,
            "It has also spent time as a time travel machine, and a dramatic lightning " +
                "generator, and a thing for generating monsters.",
        )
    }

    private suspend fun Dialogue.portalMachine() {
        chatPlayer(quiz, "What does the portal machine do?")
        chatNpc(
            happy,
            "Ah, an interesting question! I believe it should create a rift in the very fabric " +
                "of space! Opening a portal to some, hitherto undiscovered plane that exists " +
                "parallel to our own.",
        )
        chatPlayer(confused, "Huh?")
        chatNpc(neutral, "It's a teleport machine.")
    }

    private suspend fun Dialogue.progressReport() {
        val tube = player.inv.count(RubberTube) > 0
        val gauge = player.inv.count(PressureGauge) > 0
        val oil = player.inv.count(OilCan) > 0
        val (held, needed) =
            when {
                tube && gauge -> "I've got the rubber tube and the pressure gauge." to "oil can"
                tube && oil -> "I've got the oil can and the rubber tube." to "pressure gauge"
                gauge && oil -> "I've got the oil can and the pressure gauge." to "rubber tube"
                tube -> "I've only got the rubber tube so far." to "pressure gauge and oil can"
                gauge -> "I've only got the pressure gauge so far." to "oil can and rubber tube"
                oil -> "I've only got the oil can so far." to "pressure gauge and rubber tube"
                else -> null to null
            }
        if (held == null) {
            chatPlayer(sad, "I'm afraid I don't have any of them yet!")
            chatNpc(
                neutral,
                "I need a rubber tube, a pressure gauge and a can of oil. Then your friend " +
                    "can stop being a chicken.",
            )
            return
        }
        chatPlayer(neutral, held)
        if (listOf(tube, gauge, oil).count { it } == 1) {
            chatNpc(neutral, "It's a good start, but I'll need my $needed too.")
            chatPlayer(neutral, "Ok, I'll go and look for them.")
        } else {
            chatNpc(neutral, "That's good, but I still need my $needed.")
            chatPlayer(neutral, "Ok, I'll go and look for it.")
        }
    }

    private suspend fun Dialogue.introduceErnest() {
        chatPlayer(quiz, "I'm looking for a guy called Ernest.")
        chatNpc(happy, "Ah Ernest, top notch bloke. He's helping me with my experiments.")
        chatPlayer(quiz, "So you know where he is then?")
        chatNpc(neutral, "He's that chicken over there.")
        chatPlayer(quiz, "Ernest is a chicken...? Are you sure?")
        chatNpc(
            neutral,
            "Oh, he isn't normally a chicken, or at least he wasn't until he helped me test my " +
                "pouletmorph machine.",
        )
        chatNpc(
            neutral,
            "It was originally going to be called a transmutation machine. But after testing " +
                "pouletmorph seems more appropriate.",
        )
        val glad =
            choice2(
                "I'm glad Veronica didn't actually get engaged to a chicken.",
                true,
                "Change him back this instant!",
                false,
            )
        if (glad) {
            chatPlayer(
                laugh,
                "I'm glad Veronica didn't actually get engaged to a chicken.",
            )
            chatNpc(quiz, "Who's Veronica?")
            chatPlayer(neutral, "Ernest's fiancee. She probably doesn't want to marry a chicken.")
            chatNpc(happy, "Ooh I dunno. She could have free eggs for breakfast.")
            chatPlayer(neutral, "I think you'd better change him back.")
        } else {
            chatPlayer(angry, "Change him back this instant!")
        }
        chatNpc(sad, "Umm... It's not so easy...")
        chatNpc(
            sad,
            "My machine is broken, and the house gremlins have run off with some vital bits.",
        )
        chatPlayer(neutral, "Well I can look for them.")
        chatNpc(
            happy,
            "That would be a help. They'll be somewhere in the manor house or its grounds, the " +
                "gremlins never get further than the entrance gate.",
        )
        chatNpc(
            neutral,
            "I'm missing the pressure gauge and a rubber tube. They've also taken my oil can, " +
                "which I'm going to need to get this thing started again.",
        )
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.handIn() {
        chatPlayer(happy, "I have everything!")
        chatNpc(happy, "Give 'em here then.")
        if (!progress.receiveParts(access)) {
            return
        }
        access.mes("You give the rubber tube, a pressure gauge, and a can of oil to the professor.")
        chatNpc(neutral, "Let's get this fixed then.")
        restore()
    }

    private suspend fun Dialogue.restore() {
        if (!progress.canReceiveReward(access)) {
            chatNpc(neutral, "Make some room for your reward, then speak to me again.")
            return
        }

        access.mes("Oddenstein starts up the machine.")
        access.delay(2)
        access.mes("The machine hums and shakes.")
        access.delay(2)

        val chicken = findChicken()
        val at = chicken?.coords ?: ChickenSpawn
        chicken?.spotanim(Smokepuff)
        access.soundSynth(TransformSound)
        access.mes("The machine turns Ernest back into human form.")
        access.delay(1)

        progress.restoreErnest(access) {
            spawnHumanErnest(at)
            val thanks =
                when (player.subjectPronoun()) {
                    "He" -> "Thank you sir. "
                    "She" -> "Thank you m'lady. "
                    else -> "Thank you. "
                }
            chatNpcSpecific(
                "Ernest",
                "npc.ernest",
                happy,
                thanks + "It was dreadfully irritating being a chicken. How can I ever thank you?",
            )
            chatPlayer(happy, "Well a cash reward is always nice...")
            chatNpcSpecific("Ernest", "npc.ernest", happy, "Of course, of course.")
        }
    }

    private fun findChicken(): Npc? =
        npcRepo.findAll(ZoneKey.from(MachineCoord), zoneRadius = 1).firstOrNull {
            it.isType(ChickenNpc)
        }

    private fun spawnHumanErnest(coords: CoordGrid) {
        val type = ServerCacheManager.getNpc(HumanErnest.asRSCM(RSCMType.NPC))
        if (type == null) {
            logger.warn {
                "No npc type '$HumanErnest'; Ernest will vanish instead of turning human."
            }
            return
        }
        npcRepo.add(Npc(type, coords), duration = HumanErnestLifespan)
    }

    private companion object {
        private const val RubberTube = "obj.rubber_tube"
        private const val PressureGauge = "obj.pressure_gauge"
        private const val OilCan = "obj.oil_can"

        private const val TransformSound = "synth.chicken_into_human"
        private const val Smokepuff = "spotanim.draynor_ernest_smokepuff"

        private const val ChickenNpc = "npc.ernest_multichicken"

        private const val HumanErnest = "npc.ernest_multiernest"

        private const val HumanErnestLifespan = 25

        private val ChickenSpawn = CoordGrid(3110, 3364, 2)

        private val MachineCoord = CoordGrid(3111, 3366, 2)
    }
}
