package org.rsmod.content.quest.area.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Vampyre Slayer. Progress is varp 178 (`varp.vampire`), as in OSRS and as
 * Quest Helper reads it: 0 not started, 1 started (see Dr Harlow), 2 stake received (kill Count
 * Draynor), 3 complete. Dialogue follows the wiki transcript; the manor is [VampyreSlayerManor].
 */
class VampyreSlayer
@Inject
constructor(private val objRepo: ObjRepository) :
    QuestScript(
        "quest_vampyreslayer",
        "varp.vampire",
        rewards { xp("stat.attack", ATTACK_XP) },
        ItemRewardDisplay(STAKE, zoom = 250),
        questVarbit = "varbit.vampyre_slayer_progress",
    ) {
    private var Player.askedHarlow by intVarBit("varbit.vampyre_asked_harlow")
    private var Player.harlowGivenBeer by intVarBit("varbit.harlow_given_beer")
    private var Player.harlowGivenStake by intVarBit("varbit.harlow_given_stake")
    private var Player.morganThanked by intVarBit("varbit.morgan_postquest_dialogue")

    override fun ScriptContext.init() {
        check(quest.maxSteps == COMPLETE) {
            "Vampyre Slayer end state is ${quest.maxSteps} in the cache dbrow, but the kill " +
                "writes $COMPLETE."
        }
        onOpNpc1(MORGAN) { startDialogue(it.npc) { morganDialogue() } }
        onOpNpc1(HARLOW) { startDialogue(it.npc) { harlowDialogue() } }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Morgan</col> who is in <col=800000>Draynor Village</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "<red>Morgan</red> in Draynor Village wants me to slay <red>Count Draynor</red>, the " +
                    "vampyre in the manor to the north."
            )
            if (stage(player.player) < GOT_STAKE) {
                objective(
                    "He said <red>Dr Harlow</red>, a retired vampyre hunter in the <red>Blue Moon " +
                        "Inn</red> in Varrock, could help."
                ) {}
            } else {
                objective(
                    "Dr Harlow gave me a <red>stake</red>. I need a <red>hammer</red> to drive it into " +
                        "the Count's chest once he is weak, and <red>garlic</red> will weaken him."
                ) {}
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "With Dr Harlow's stake and a hammer I slew Count Draynor in the basement of his " +
                    "manor."
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    private suspend fun Dialogue.morganDialogue() {
        when (stage(player)) {
            0 -> morganStart()
            STARTED, GOT_STAKE -> morganDuring()
            else -> morganAfter()
        }
    }

    private suspend fun Dialogue.morganStart() {
        chatNpc(worried, "Could it be? A bold adventurer! Please, you must help us!")
        chatPlayer(quiz, "What is it? What's the problem?")
        chatNpc(
            worried,
            "It's the evil vampyre, Count Draynor! For too long he's terrorised us from his manor " +
                "to the north. Someone finally needs to put a stop to him once and for all!",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(
                worried,
                "An evil vampyre? I don't think this is the job for me. It sounds far too scary!",
            )
            chatNpc(
                sad,
                "I understand... Hopefully someone else will come along to finally save us from " +
                    "this evil.",
            )
            return
        }
        chatPlayer(neutral, "Sounds like a job for me. Where should I start?")
        quest.setQuestStage(access, STARTED)
        chatNpc(
            happy,
            "Oh, thank goodness! I've been hoping this day would come for a long time, so I've " +
                "made sure to do my research. I've heard of a retired vampyre hunter called Dr " +
                "Harlow who lives in Varrock.",
        )
        chatNpc(
            neutral,
            "If you speak to him, I'm sure he'll be able to help. I hear he's a bit of an old soak " +
                "these days, so he spends most of his time in the Blue Moon Inn.",
        )
        morganOptions(stillWorking = false)
    }

    private suspend fun Dialogue.morganDuring() {
        chatNpc(quiz, "Adventurer! Has Count Draynor been dealt with yet?")
        if (stage(player) >= GOT_STAKE) {
            chatPlayer(
                neutral,
                "I'm still working on it, but I could use some garlic to help me out.",
            )
            chatNpc(
                happy,
                "Ah, yes! I heard vampyres didn't like garlic. I keep some in the cupboard " +
                    "upstairs, just in case Count Draynor comes and tries anything!",
            )
            morganOptions(stillWorking = true)
            return
        }
        chatPlayer(neutral, "I'm still working on it.")
        chatNpc(worried, "Please hurry! I'm sure Dr Harlow in Varrock's Blue Moon Inn can help.")
        morganOptions(stillWorking = false)
    }

    private suspend fun Dialogue.morganOptions(stillWorking: Boolean) {
        val leave =
            if (stillWorking) "Perfect, thanks." else "Alright, I'll see about paying him a visit."
        when (choice2("What else can you tell me about Count Draynor?", 1, leave, 2)) {
            1 -> morganLegends()
            2 -> {
                chatPlayer(neutral, leave)
                if (stillWorking) {
                    chatNpc(happy, "Good luck in Draynor Manor, adventurer.")
                } else {
                    chatNpc(happy, "Thank you, brave adventurer.")
                }
            }
        }
    }

    private suspend fun Dialogue.morganLegends() {
        chatPlayer(quiz, "What else can you tell me about this vampyre?")
        chatNpc(happy, "All sorts of things! I've studied the legends extensively!")
        chatPlayer(confused, "Wait... legends? What do you mean legends?")
        chatNpc(
            neutral,
            "The legends of Count Draynor! It's said that long ago, he travelled here from dark " +
                "lands in the east. He built Draynor Manor, and from there he began his campaign " +
                "of darkness against our peaceful lands!",
        )
        chatPlayer(
            quiz,
            "Long ago? If this has been going on for so long, why has no one tried to stop him?",
        )
        chatNpc(
            neutral,
            "Many have tried. All have failed. Vampyres are not killed easily. Only those with the " +
                "right skills and equipment have any hope of slaying one.",
        )
        chatPlayer(
            quiz,
            "Like this Dr Harlow? Home come he's never tried to kill this Count Draynor?",
        )
        chatNpc(confused, "I'm... not sure. Perhaps he was always too busy killing other vampyres?")
        chatPlayer(
            confused,
            "Hmm... Something seems amiss here. Are you sure you're telling me everything?",
        )
        chatNpc(
            shifty,
            "Well... it is true that some claim Count Draynor hasn't left his manor for " +
                "generations now.",
        )
        chatPlayer(quiz, "So he's not actually attacked the village?")
        chatNpc(shifty, "Er... no. At least, not for a long time! He used to though!")
        chatPlayer(quiz, "When?")
        chatNpc(shifty, "Well... not since I've moved here at least.")
        chatPlayer(
            quiz,
            "So if there's no actual danger, why are you so keen for someone to kill this vampyre?",
        )
        chatNpc(
            angry,
            "Because what if he does start attacking again? Are we just meant to live our lives " +
                "constantly fearing the future?",
        )
        chatPlayer(neutral, "It doesn't really sound like anyone is living in fear apart from you.")
        chatNpc(
            angry,
            "More fool them! Count Draynor has cast a shadow over Draynor Village for " +
                "generations. They even named the place after him for Saradomin's sake!",
        )
        chatNpc(
            worried,
            "Even if the people here don't realise it, we will never be at peace until we know " +
                "he's dead!",
        )
        chatPlayer(neutral, "If you're sure... I guess I'll go and see this Dr Harlow.")
        chatNpc(happy, "Thank you, brave adventurer.")
    }

    private suspend fun Dialogue.morganAfter() {
        if (player.morganThanked == 0) {
            chatPlayer(happy, "I have some good news. Count Draynor is no more!")
            player.morganThanked = 1
            chatNpc(
                happy,
                "He's really gone? Finally, we can live without fear! Thank you, thank you! " +
                    "You're a true hero!",
            )
            chatPlayer(happy, "I'm happy to have helped.")
            return
        }
        chatNpc(happy, "Once again, thank you for slaying that vampyre! You will always be a hero!")
        chatPlayer(happy, "Don't mention it.")
    }

    private suspend fun Dialogue.harlowDialogue() {
        val stage = stage(player)
        when {
            stage >= GOT_STAKE && stage < COMPLETE -> harlowAfterStake()
            stage == STARTED && player.askedHarlow == 1 -> harlowBeer()
            else -> harlowFirst(stage == STARTED)
        }
    }

    private suspend fun Dialogue.harlowFirst(onQuest: Boolean) {
        chatNpc(drunk, "Buy me a drink pleassh...")
        if (!onQuest) {
            chatPlayer(neutral, "I think you've had enough.")
            return
        }
        when (
            choice2(
                "I need your help dealing with a vampyre.",
                1,
                "I think you've had enough.",
                2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I need your help dealing with a vampyre.")
                chatNpc(drunk, "A vampyre you shhay...?")
                chatPlayer(neutral, "Not just any vampyre. Count Draynor.")
                chatNpc(drunk, "Draynor? Well, buy me a beer firsht...")
                chatPlayer(quiz, "Are you sure you've not had enough?")
                chatNpc(drunk, "Huh? No, I don't think ssho. Now, buy ush a beer.")
                player.askedHarlow = 1
            }
            2 -> chatPlayer(neutral, "I think you've had enough.")
        }
    }

    private suspend fun Dialogue.harlowBeer() {
        chatNpc(drunk, "Have yoush got me a beer?")
        val hasBeer = player.inv.count(BEER) > 0
        val first = if (hasBeer) "Yes, here you go." else "I'll go and get you one."
        when (choice2(first, 1, "No. I think you've had enough.", 2)) {
            1 ->
                if (hasBeer) {
                    giveBeer()
                } else {
                    chatPlayer(neutral, "I'll go and get you one.")
                    chatNpc(drunk, "Cheersh, matey...")
                }
            2 -> {
                chatPlayer(neutral, "No. I think you've had enough.")
                chatNpc(drunk, "No help for yoush then.")
            }
        }
    }

    private suspend fun Dialogue.giveBeer() {
        chatPlayer(happy, "Yes, here you go.")
        if (access.invDel(access.inv, BEER, 1).failure) {
            return
        }
        player.harlowGivenBeer = 1
        mesbox("You give a beer to Dr Harlow.")
        chatNpc(drunk, "Cheersh, matey...")
        chatPlayer(neutral, "Now, about Count Draynor...")
        chatNpc(
            drunk,
            "Yesh, Count Draynor! The evil nashty vampyre that no one's ever sheen! Every nowsh " +
                "and then, some adventurer... theysh go and try to kill him, but none of 'em ever " +
                "comesh back.",
        )
        chatNpc(
            drunk,
            "You want to havesh a go? Then don't be likesh them! Be prepared! Vampyres... Theysh " +
                "hard to kill. Some... maybe imposhible.",
        )
        chatPlayer(quiz, "So how do I make sure I'm prepared?")
        chatNpc(
            drunk,
            "Most vampyres regenerate. Yoush need to stop them. I knowsh a few ways, but the " +
                "easiest ish a stake. Here, You can havesh thish one.",
        )
        access.invAddOrDrop(objRepo, STAKE)
        player.harlowGivenStake = 1
        quest.setQuestStage(access, GOT_STAKE)
        objbox(STAKE, "Dr Harlow hands you a stake.")
        stakeInstructions()
        harlowTopics(notYet = false)
    }

    private suspend fun Dialogue.stakeInstructions() {
        chatNpc(
            drunk,
            "Takesh that to Draynor Manor. Find the vampyre inshide and show him what for! When " +
                "hesh weak, use a hammer to drive the stake in! Mosht general stores have them.",
        )
        chatNpc(
            drunk,
            "Oh, and yoush should take some garlic with you as well. Vampyres don't likesh garlic.",
        )
        chatPlayer(quiz, "Garlic? Hmm... I'll see if Morgan knows where I can get some.")
    }

    private suspend fun Dialogue.harlowAfterStake() {
        chatNpc(drunk, "Yoush back! Killed that vampyre yet?")
        val hasStake =
            player.inv.count(STAKE) > 0 || player.invMap.getOrPut("inv.bank").count(STAKE) > 0
        if (!hasStake) {
            chatPlayer(sad, "No. I lost that stake you gave me.")
            chatNpc(drunk, "Oh, hangsh on then...")
            access.invAddOrDrop(objRepo, STAKE)
            objbox(STAKE, "Dr Harlow hands you a stake.")
            chatNpc(
                drunk,
                "Good job I've gotsh some spares. Now, takesh that to Draynor Manor. Find the " +
                    "vampyre inshide and show him what for! When hesh weak, use a hammer to " +
                    "drive the stake in! Mosht general stores have them.",
            )
            chatNpc(
                drunk,
                "Oh, and yoush should take some garlic with you as well. Vampyres don't likesh " +
                    "garlic.",
            )
            chatPlayer(quiz, "Garlic? Hmm... I'll see if Morgan knows where I can get some.")
        }
        harlowTopics(notYet = true)
    }

    private suspend fun Dialogue.harlowTopics(notYet: Boolean) {
        val leave = if (notYet) "Not yet. I'd best get going." else "I'd best get going."
        while (true) {
            when (
                choice4(
                    "What do I need to do again?",
                    1,
                    "You said no one's ever seen Count Draynor?",
                    2,
                    "So you were once a proper vampyre hunter?",
                    3,
                    leave,
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What do I need to do again?")
                    chatNpc(
                        drunk,
                        "Go to Draynor Manor. Find the vampyre inshide and show him what for! " +
                            "When hesh weak, use a hammer to drive that stake I gave you in! " +
                            "Mosht general stores have them.",
                    )
                    chatNpc(
                        drunk,
                        "Oh, and yoush should take some garlic with you as well. Vampyres " +
                            "don't likesh garlic.",
                    )
                    chatPlayer(quiz, "Garlic? Hmm... I'll see if Morgan knows where I can get some.")
                }
                2 -> neverSeen()
                3 -> properHunter()
                else -> {
                    chatPlayer(neutral, leave)
                    chatNpc(drunk, "Good luck, matey...")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.neverSeen() {
        chatPlayer(quiz, "You said no one's ever seen Count Draynor?")
        chatNpc(drunk, "No, butsh every now and then someonesh tried to kill him.")
        chatPlayer(
            quiz,
            "But they've never come back? Meaning someone or something killed them most likely. " +
                "Presumably Count Draynor?",
        )
        chatNpc(drunk, "Yesh, I'd say so.")
        chatPlayer(quiz, "So why does he never leave his manor?")
        chatNpc(drunk, "Who knowsh. Most likely he's grown too weak to do ssho.")
        chatPlayer(quiz, "Weak? So he shouldn't be too hard to kill then?")
        chatNpc(
            drunk,
            "Even a weak vampyre ish very dangerous. Just go ashk those other adventurers thatsh " +
                "never came back.",
        )
    }

    private suspend fun Dialogue.properHunter() {
        chatPlayer(quiz, "So you were once a proper vampyre hunter?")
        chatNpc(
            drunk,
            "Yesh! I travelled their homeland to the easht. Morytania! I killed lotsh of vampyres " +
                "over there!",
        )
        chatPlayer(quiz, "What was the most dangerous vampyre you killed?")
        chatNpc(drunk, "Dangerous? Oh, well... Ish never killed one of the really dangerous ones.")
        chatPlayer(quiz, "Why not?")
        chatNpc(drunk, "Can't. Theysh too strong. Nothing cansh hurt them.")
        chatPlayer(worried, "Oh... But what if Count Draynor's like that?")
        chatNpc(
            drunk,
            "Yoush don't get many vampyres around here. Pretty much none, actshually. There's " +
                "speshial magic that stops them coming over here. Any that are here... theysh " +
                "weak... and trapped.",
        )
        chatPlayer(quiz, "So how come you've never tried to kill him yourself?")
        chatNpc(
            drunk,
            "Why bother? Hesh not killed anyone in a long time, so no one really caresh about him " +
                "that much, apart from the odd adventurer thatsh looking to prove themshelves.",
        )
        chatPlayer(neutral, "I see. Fair enough.")
    }

    companion object {
        const val STARTED = 1
        const val GOT_STAKE = 2
        const val COMPLETE = 3

        private const val MORGAN = "npc.morgan"
        private const val HARLOW = "npc.dr_harlow"

        const val STAKE = "obj.stake"
        const val HAMMER = "obj.hammer"
        const val BEER = "obj.beer"
        const val GARLIC = "obj.garlic"
        private const val ATTACK_XP = 4825.0
    }
}

/**
 * Draynor Manor's side of Vampyre Slayer: Morgan's garlic cupboard, the coffin and Count Draynor.
 * Count Draynor rises for the player who opens the coffin; at 0 hitpoints he only dies to a stake
 * and a hammer, otherwise he heals to full. Garlic weakens him as he rises (wiki: 10 damage, -10
 * Attack and Strength, -40 Defence; the wiki times it to the first melee attack, here it is applied
 * at once).
 */
class VampyreSlayerManor
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val protectedAccess: ProtectedAccessLauncher,
    private val npcDeath: NpcDeath,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : PluginScript() {
    private class Fight(val player: Player, val count: Npc)

    private val fights = HashMap<Long, Fight>()

    private val quest: Quest
        get() = checkNotNull(Quest.get(QUEST_KEY)) { "Vampyre Slayer is not registered." }

    private fun stage(player: Player): Int = quest.getQuestStage(player)

    private fun countType(): NpcServerType =
        ServerCacheManager.getNpc(COUNT.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $COUNT")

    override fun ScriptContext.startup() {
        onOpLoc1(CUPBOARD_SHUT) { openCupboard(it.loc) }
        onOpLoc1(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc2(CUPBOARD_OPEN) { closeCupboard(it.loc) }
        onOpLoc1(COFFIN) { openCoffin(it.loc) }
        onNpcQueue(countType(), "queue.death") { countDeath() }
        onPlayerLogout {
            val fight = player.uuid?.let(fights::remove) ?: return@onPlayerLogout
            if (fight.count.isSlotAssigned) npcRepo.del(fight.count, Int.MAX_VALUE)
        }
    }

    private suspend fun ProtectedAccess.openCupboard(loc: BoundLocInfo) {
        arriveDelay()
        anim("seq.human_opencupboard")
        locRepo.change(loc, CUPBOARD_OPEN, CUPBOARD_OPEN_TICKS)
    }

    private fun ProtectedAccess.closeCupboard(loc: BoundLocInfo) {
        anim("seq.human_opencupboard")
        locRepo.change(loc, CUPBOARD_SHUT, Int.MAX_VALUE)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        if (invAdd(inv, VampyreSlayer.GARLIC).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        startDialogue { objbox(VampyreSlayer.GARLIC, "The cupboard contains garlic. You take a clove.") }
    }

    private suspend fun ProtectedAccess.openCoffin(loc: BoundLocInfo) {
        arriveDelay()
        val stage = stage(player)
        if (stage < VampyreSlayer.STARTED || stage >= VampyreSlayer.COMPLETE) {
            mes("The coffin is sealed shut.")
            return
        }
        val uuid = player.uuid ?: return
        val existing = fights[uuid]
        if (existing != null && existing.count.isSlotAssigned) {
            return
        }
        anim("seq.human_openchest")
        delay(1)
        val count = Npc(COUNT, loc.coords.translateZ(-1))
        npcRepo.add(count, COUNT_LIFESPAN)
        fights[uuid] = Fight(player, count)
        count.anim("seq.vampireslayer_arise")
        delay(2)
        if (VampyreSlayer.GARLIC in inv) {
            mes("The vampyre seems to be weakened by the garlic you're carrying.")
            count.hitpoints = (count.hitpoints - GARLIC_DAMAGE).coerceAtLeast(1)
            count.attackLvl = (count.attackLvl - 10).coerceAtLeast(1)
            count.strengthLvl = (count.strengthLvl - 10).coerceAtLeast(1)
            count.defenceLvl = (count.defenceLvl - 40).coerceAtLeast(1)
        }
        count.opPlayer2(player, aiPlayerInteractions)
    }

    private suspend fun StandardNpcAccess.countDeath() {
        val entry = fights.entries.firstOrNull { it.value.count === npc }
        if (entry == null) {
            npcDeath.deathNoDrops(this)
            return
        }
        val player = entry.value.player
        val hasStake = player.inv.count(VampyreSlayer.STAKE) > 0
        val hasHammer = player.inv.count(VampyreSlayer.HAMMER) > 0
        if (!hasStake || !hasHammer) {
            if (hasStake) {
                player.mes("You're unable to push the stake far enough in!")
            }
            player.mes("The vampyre seems to regenerate!")
            npc.heal(npc.baseHitpointsLvl, showHitsplat = true)
            return
        }
        player.mes("You hammer the stake into the vampyre's chest!")
        fights.remove(entry.key)
        npcDeath.deathWithDrops(this)
        complete(player)
    }

    @OptIn(InternalApi::class)
    private fun complete(player: Player) {
        val finish: suspend ProtectedAccess.() -> Unit = { quest.completeQuest(this) }
        if (!protectedAccess.launch(player, block = finish)) {
            protectedAccess.launchLenient(player, finish)
        }
    }

    private companion object {
        const val QUEST_KEY = "quest_vampyreslayer"
        const val COUNT = "npc.count_draynor"
        const val COFFIN = "loc.vampcoffin"
        const val CUPBOARD_SHUT = "loc.garliccupboardshut"
        const val CUPBOARD_OPEN = "loc.garliccupboardopen"
        const val CUPBOARD_OPEN_TICKS = 300
        const val COUNT_LIFESPAN = 500
        const val GARLIC_DAMAGE = 10
    }
}
