package org.rsmod.content.quest.area.portsarim.piratestreasure

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.COMPLETE
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.EMPLOYED_PLANTATION
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.EMPLOYED_WYDIN
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.FETCH_RUM
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.KARAMJA_RUM
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.NOT_STARTED
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.READ_NOTE
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.RECEIVED_KEY
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.RUM_IN_CRATE
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.RUM_NONE
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.RUM_SHIPPED
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.crateBananas
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.crateRum
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.employment
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureState.gardenerMet
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pirate's Treasure. Progress is varp 71 (`varp.hunt`), as in OSRS and as
 * Quest Helper reads it: 0 not started, 1 fetching the rum, 2 key received, 3 note read, 4
 * complete. Quest Helper reads the rum-smuggling sub-state from the journal text, so the stage-1
 * journal uses OSRS's wording (as reconstructed by the 2004Scape project). Dialogue follows the wiki
 * transcript. Karamja and Wydin's back room are [PiratesTreasureSmuggling]; the customs officer and
 * Wydin's own dialogue are in the Port Sarim module.
 */
class PiratesTreasure
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
) :
    QuestScript(
        PiratesTreasureState.QUEST_KEY,
        "varp.hunt",
        rewards { extra("One-Eyed Hector's Treasure Chest") },
        ItemRewardDisplay(CASKET, zoom = 300),
        questVarbit = "varbit.pirates_treasure_progress",
    ),
    SpadeDigHook {
    private val gardeners = HashMap<Long, Npc>()

    override fun ScriptContext.init() {
        check(quest.maxSteps == COMPLETE) {
            "Pirate's Treasure end state is ${quest.maxSteps} in the cache dbrow, but the dig " +
                "writes $COMPLETE."
        }
        onOpNpc1(REDBEARD) { startDialogue(it.npc) { redbeardDialogue() } }
        onOpNpcU(npcType(REDBEARD), objType(KARAMJA_RUM)) {
            startDialogue(it.npc) { rumOnRedbeard() }
        }
        onOpNpcU(npcType(REDBEARD), objType(CASKET)) {
            startDialogue(it.npc) { offerShare() }
        }
        onOpLoc1(CHEST) { mes("The chest is locked.") }
        onOpLocU(CHEST, CHEST_KEY) { unlockChest(it.loc) }
        onOpHeld1(PIRATE_MESSAGE) { readMessage() }
        onOpHeld1(CASKET) { openCasket() }
        onPlayerLogout {
            val gardener = player.uuid?.let(gardeners::remove) ?: return@onPlayerLogout
            if (gardener.isSlotAssigned) npcRepo.del(gardener, Int.MAX_VALUE)
        }
    }

    override fun subTitle(): String =
        "speaking to <col=800000>Redbeard Frank</col> who is at <col=800000>Port Sarim</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            when (stage(player.player)) {
                FETCH_RUM -> fetchRumLog(player.player)
                RECEIVED_KEY -> receivedKeyLog(player.player)
                else -> readNoteLog()
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            JOURNAL_DONE_FRANK.forEach { line(it) }
            line("")
            JOURNAL_DONE_SMUGGLED.forEach { line(it) }
            line("")
            JOURNAL_DONE_INN.forEach { line(it) }
            line("")
            JOURNAL_DONE_NOTE.forEach { line(it) }
            line("")
            JOURNAL_DONE_READ.forEach { line(it) }
        }

    private fun QuestJournalBuilder.fetchRumLog(player: Player) {
        line("I have spoken to <red>Redbeard Frank</red>. He has agreed to tell me")
        line("the location of some <red>treasure</red> for some <red>Karamja Rum</red>.")
        line("")
        val employed = player.employment
        val rum = player.crateRum
        val carries = PiratesTreasureState.carriesRum(player)
        when {
            employed and EMPLOYED_WYDIN != 0 -> wydinLog(rum, carries)
            rum == RUM_SHIPPED -> {
                struckSmugglingLines()
                line("I have spoken to <red>Luthas</red>, and the crate has been shipped to")
                line("<red>Wydin's store</red> in <red>Port Sarim</red>. Now all I have to do is get to it.")
            }
            employed and EMPLOYED_PLANTATION != 0 -> plantationLog(player, rum, carries)
            carries -> {
                line("I have the <red>rum</red>, and now I need to find a way to get the rum")
                line("off <red>Karamja</red>. This might be tricky, as the <red>Customs Officers</red>")
                line("are searching people for it.")
            }
            else -> {
                line("I need to go to <red>Karamja</red> and buy some <red>rum</red>. I hope it is")
                line("not too expensive.")
            }
        }
    }

    private fun QuestJournalBuilder.plantationLog(player: Player, rum: Int, carries: Boolean) {
        line("I have taken employment on the <red>banana plantation</red>, as the")
        line("<red>Customs Officers</red> might not notice the <red>rum</red> if it is covered in")
        line("<red>bananas</red>.")
        line("")
        when {
            rum == RUM_NONE && carries -> {
                line("I'm sure I will be able to hide my <red>rum</red> in the next crate")
                line("destined for <red>Wydin's store</red>.")
            }
            rum == RUM_IN_CRATE && player.crateBananas < PiratesTreasureState.CRATE_CAPACITY -> {
                line("I have hidden my <red>rum</red> in the crate. I should fill it with")
                line("<red>bananas</red> and speak to <red>Luthas</red> and have it shipped over.")
            }
            rum == RUM_IN_CRATE -> {
                line("I have hidden my rum in the crate and filled it with bananas.")
                line("I should speak to <red>Luthas</red> and have it shipped over.")
            }
            else -> {
                line("Now all I need is some <red>rum</red> to hide in the next crate destined")
                line("for <red>Wydin's store</red>.")
            }
        }
    }

    private fun QuestJournalBuilder.wydinLog(rum: Int, carries: Boolean) {
        when {
            rum == RUM_SHIPPED -> {
                struckSmugglingLines()
                strike("I have spoken to Luthas, and the crate has been shipped to")
                strike("Wydin's store in Port Sarim. Now all I have to do is")
                strike("get to it.")
                line("")
                line("I have taken a job at <red>Wydin's store</red>. I now have access to the")
                line("back room of his shop where the <red>rum</red> is hidden.")
            }
            rum == RUM_NONE && carries -> {
                line("I have the Karamja Rum. I should take it to <red>Redbeard Frank</red>.")
            }
            rum == RUM_NONE -> {
                line("I had some rum, but I seem to have lost it. I will need to")
                line("smuggle some more off <red>Karamja</red>.")
            }
            else -> {
                line("I have taken a job at <red>Wydin's store</red>. I now have access to the")
                line("back room of his shop where the <red>rum</red> is hidden.")
            }
        }
    }

    private fun QuestJournalBuilder.struckSmugglingLines() {
        strike("I have taken employment on the banana plantation, as the")
        strike("Customs Officers might not notice the rum if it is covered in")
        strike("bananas.")
        line("")
        strike("I have hidden my rum in the crate. I should fill it with")
        strike("bananas and speak to Luthas to have it shipped over.")
        line("")
    }

    private fun QuestJournalBuilder.receivedKeyLog(player: Player) {
        JOURNAL_DONE_FRANK.forEach { strike(it) }
        line("")
        JOURNAL_DONE_SMUGGLED.forEach { strike(it) }
        line("")
        line("I have given the rum to <red>Redbeard Frank</red>. He has told me that the")
        line("<red>treasure</red> is hidden in the chest in the upstairs room of the")
        line("<red>Blue Moon Inn</red> in <red>Varrock</red>.")
        line("")
        when {
            player.inv.count(PIRATE_MESSAGE) > 0 -> {
                line("I have opened the chest in the <red>Blue Moon</red>, and found a <red>note</red>")
                line("inside. I think it will tell me where to dig.")
            }
            player.inv.count(CHEST_KEY) > 0 -> {
                line("I have a <red>key</red> that can be used to unlock the chest that hold the")
                line("treasure.")
            }
            else -> {
                line("I have lost the <red>key</red> that <red>Redbeard Frank</red> gave me. I should")
                line("see if he has another.")
            }
        }
    }

    private fun QuestJournalBuilder.readNoteLog() {
        JOURNAL_DONE_FRANK.forEach { strike(it) }
        line("")
        JOURNAL_DONE_SMUGGLED.forEach { strike(it) }
        line("")
        JOURNAL_DONE_INN.forEach { strike(it) }
        line("")
        JOURNAL_DONE_NOTE.forEach { strike(it) }
        line("")
        line("The note reads <red>'Visit the city of the White Knights. In the park,</red>")
        line("<red>Saradomin points to the X which marks the spot.'</red>")
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    private suspend fun Dialogue.redbeardDialogue() {
        chatNpc(laugh, "Arr, Matey!")
        when (stage(player)) {
            NOT_STARTED -> redbeardStart()
            FETCH_RUM -> redbeardAsksForRum()
            RECEIVED_KEY -> {
                if (!ownsKeyOrMessage()) replaceKey()
                redbeardOptions()
            }
            else -> redbeardOptions()
        }
    }

    private suspend fun Dialogue.redbeardStart() {
        val topic =
            choice3(
                "I'm in search of treasure.",
                RedbeardTopic.Treasure,
                "Arr!",
                RedbeardTopic.Arr,
                "Do you have anything for trade?",
                RedbeardTopic.Trade,
            )
        when (topic) {
            RedbeardTopic.Treasure -> searchForTreasure()
            RedbeardTopic.Arr -> {
                arr()
                redbeardStart()
            }
            RedbeardTopic.Trade -> trade()
        }
    }

    private suspend fun Dialogue.redbeardOptions() {
        while (choice2("Arr!", true, "Do you have anything for trade?", false)) {
            arr()
        }
        trade()
    }

    private suspend fun Dialogue.arr() {
        chatPlayer(laugh, "Arr!")
        chatNpc(laugh, "Arr!")
    }

    private suspend fun Dialogue.trade() {
        chatPlayer(quiz, "Do you have anything for trade?")
        chatNpc(
            neutral,
            "Nothin' at the moment, but then again the Customs Agents are on the warpath right now.",
        )
    }

    private suspend fun Dialogue.searchForTreasure() {
        chatPlayer(shifty, "I'm in search of treasure.")
        chatNpc(
            shifty,
            "Arr, treasure you be after eh? Well I might be able to tell you where to find some... " +
                "For a price...",
        )
        chatPlayer(quiz, "What sort of price?")
        chatNpc(
            neutral,
            "Well for example if you can get me a bottle of rum... Not just any rum mind...",
        )
        chatNpc(
            happy,
            "I'd like some rum made on Karamja Island. There's no rum like Karamja Rum!",
        )
        if (!startQuestPrompt(quest)) {
            chatPlayer(neutral, "Not right now.")
            chatNpc(
                neutral,
                "Fair enough. I'll still be here and thirsty whenever you feel like helpin' out.",
            )
            return
        }
        chatPlayer(happy, "Ok, I will bring you some rum.")
        quest.setQuestStage(access, FETCH_RUM)
        chatNpc(happy, "Yer a saint, although it'll take a miracle to get it off Karamja.")
        chatPlayer(quiz, "What do you mean?")
        customsWarning()
        chatPlayer(neutral, "Well I'll give it a shot.")
        chatNpc(laugh, "Arr, that's the spirit!")
    }

    private suspend fun Dialogue.customsWarning() {
        chatNpc(
            neutral,
            "The Customs office has been clampin' down on the export of spirits. You seem like a " +
                "resourceful young ${ladOrLass()}, I'm sure ye'll " +
                "be able to find a way to slip the stuff past them.",
        )
    }

    private suspend fun Dialogue.redbeardAsksForRum() {
        chatNpc(quiz, "Have ye brought some rum for yer ol' mate Frank?")
        if (PiratesTreasureState.carriesRum(player)) {
            chatPlayer(happy, "Yes, I've got some.")
            handOverRum()
            return
        }
        chatPlayer(sad, "No, not yet.")
        chatNpc(neutral, "Not surprising, tis no easy task to get it off Karamja.")
        chatPlayer(quiz, "What do you mean?")
        customsWarning()
        chatPlayer(neutral, "Well I'll give it another shot.")
    }

    private suspend fun Dialogue.rumOnRedbeard() {
        if (stage(player) != FETCH_RUM) {
            chatNpc(happy, "Thanks fer the thought, but I still have to finish this bottle.")
            return
        }
        chatPlayer(happy, "Frank, I have some Karamja Rum.")
        handOverRum()
    }

    private suspend fun Dialogue.handOverRum() {
        chatNpc(
            shifty,
            "Now a deal's a deal, I'll tell ye about the treasure. I used to serve under a pirate " +
                "captain called One-Eyed Hector.",
        )
        chatNpc(
            shifty,
            "Hector were very successful and became very rich. But about a year ago we were " +
                "boarded by the Customs and Excise Agents.",
        )
        chatNpc(
            sad,
            "Hector were killed along with many of the crew, I were one of the few to escape and " +
                "I escaped with this.",
        )
        if (access.invReplace(access.inv, KARAMJA_RUM, 1, CHEST_KEY).failure) {
            return
        }
        quest.setQuestStage(access, RECEIVED_KEY)
        objbox(CHEST_KEY, "Frank happily takes the rum... and hands you a key.")
        chatNpc(
            shifty,
            "This be Hector's key. I believe it opens his chest in his old room in the Blue Moon " +
                "Inn in Varrock.",
        )
        chatNpc(neutral, "With any luck his treasure will be in there.")
        val why =
            choice2(
                "Ok thanks, I'll go and get it.",
                false,
                "So why didn't you ever get it?",
                true,
            )
        if (!why) {
            chatPlayer(happy, "Ok thanks, I'll go and get it.")
            return
        }
        chatPlayer(quiz, "So why didn't you ever get it?")
        chatNpc(sad, "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken trouble maker.")
    }

    private suspend fun Dialogue.replaceKey() {
        chatPlayer(sad, "I seem to have lost my chest key...")
        chatNpc(
            neutral,
            "Arr, silly you. Fortunately I took the precaution to have another one made.",
        )
        if (access.invAdd(access.inv, CHEST_KEY).failure) {
            mesbox("You don't have enough inventory space.")
            return
        }
        objbox(CHEST_KEY, "Frank hands you a chest key.")
    }

    private suspend fun Dialogue.offerShare() {
        if (stage(player) < COMPLETE) {
            chatNpc(happy, "Thanks fer the thought, but I still have to finish this bottle.")
            return
        }
        chatPlayer(happy, "I have the treasure, would you like a share?")
        chatNpc(
            happy,
            "No ${ladOrLass()}, you got it fair and square.",
        )
        chatNpc(happy, "You enjoy it. It's what Hector would have wanted.")
    }

    private fun Dialogue.ladOrLass(): String =
        if (player.appearance.bodyType == Appearance.BODY_TYPE_A) "lad" else "lass"

    private fun Dialogue.ownsKeyOrMessage(): Boolean =
        listOf(CHEST_KEY, PIRATE_MESSAGE).any { access.inv.count(it) > 0 || access.bank.count(it) > 0 }

    private suspend fun ProtectedAccess.unlockChest(chest: BoundLocInfo) {
        arriveDelay()
        if (stage(player) < RECEIVED_KEY) {
            mes("Nothing interesting happens.")
            return
        }
        mes("You unlock the chest.")
        locRepo.change(chest, CHEST_OPEN, CHEST_OPEN_TICKS)
        mes("All that's in the chest is a message...")
        delay(2)
        if (invReplace(inv, CHEST_KEY, 1, PIRATE_MESSAGE).failure) {
            return
        }
        mes("You take the message from the chest.")
    }

    private fun ProtectedAccess.readMessage() {
        if (stage(player) == RECEIVED_KEY) {
            quest.setQuestStage(this, READ_NOTE)
        }
        ifOpenMainModal("interface.trail_cluetext")
        ifSetText("component.trail_cluetext:text", PIRATE_MESSAGE_TEXT)
    }

    private suspend fun ProtectedAccess.openCasket() {
        val opened =
            player.invTransaction(inv) {
                val from = select(inv)
                delete {
                    this.from = from
                    this.obj = CASKET.asRSCM(RSCMType.OBJ)
                    this.strictCount = 1
                }
                for ((obj, count) in CASKET_CONTENTS) {
                    insert {
                        this.into = from
                        this.obj = obj.asRSCM(RSCMType.OBJ)
                        this.strictCount = count
                    }
                }
            }
        if (opened.failure) {
            mes("You don't have enough inventory space.")
            return
        }
        mesbox("You open the casket, and find One-Eyed Hector's treasure.")
    }

    override fun claims(player: Player): Boolean =
        stage(player) == READ_NOTE &&
            player.coords.level == DIG_SPOT.level &&
            player.coords.chebyshevDistance(DIG_SPOT) <= DIG_RADIUS

    override suspend fun ProtectedAccess.beforeDig(): Boolean {
        val gardener = player.uuid?.let(gardeners::get)
        if (gardener != null && gardener.isSlotAssigned) {
            mes("I can't dig up anything with him attacking me!")
            return false
        }
        return true
    }

    override suspend fun ProtectedAccess.dig() {
        if (player.gardenerMet == 0) {
            summonGardener()
            return
        }
        if (invAdd(inv, CASKET).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("You dig a hole in the ground...")
        mes("and find a little chest of treasure.")
        quest.completeQuest(this)
    }

    private fun ProtectedAccess.summonGardener() {
        val uuid = player.uuid ?: return
        val gardener = Npc(GARDENER, gardenerSpawnTile())
        npcRepo.add(gardener, GARDENER_LIFESPAN)
        gardeners[uuid] = gardener
        player.gardenerMet = 1
        gardener.say("First moles, now this! Take this, vandal!")
        gardener.opPlayer2(player, aiPlayerInteractions)
    }

    private fun ProtectedAccess.gardenerSpawnTile(): CoordGrid = player.coords.translateX(1)

    private fun npcType(npc: String) =
        checkNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))) { "Missing npc: $npc" }

    private fun objType(obj: String) =
        checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { "Missing obj: $obj" }

    private enum class RedbeardTopic {
        Treasure,
        Arr,
        Trade,
    }

    companion object {
        const val REDBEARD = "npc.redbeard_frank"
        const val GARDENER = "npc.pirate_irate_gardener"
        const val CHEST = "loc.piratechest"
        const val CHEST_OPEN = "loc.piratechestopen"
        const val CHEST_KEY = "obj.chest_key"
        const val PIRATE_MESSAGE = "obj.piratemessage"
        const val CASKET = "obj.pirate_casket"
        const val CHEST_OPEN_TICKS = 10
        const val GARDENER_LIFESPAN = 200
        const val DIG_RADIUS = 1

        val DIG_SPOT = CoordGrid(2999, 3383, 0)

        val CASKET_CONTENTS = listOf("obj.coins" to 450, "obj.gold_ring" to 1, "obj.emerald" to 1)

        const val PIRATE_MESSAGE_TEXT =
            "Visit the city of the White Knights. In the park, Saradomin points to the X which " +
                "marks the spot."

        val JOURNAL_DONE_FRANK =
            listOf(
                "I have spoken to Redbeard Frank. He has agreed to tell me",
                "the location of some treasure for some Karamja Rum.",
            )
        val JOURNAL_DONE_SMUGGLED =
            listOf(
                "I have smuggled some rum off Karamja, and retrieved it",
                "from the back room of Wydin's shop.",
            )
        val JOURNAL_DONE_INN =
            listOf(
                "I have given the rum to Redbeard Frank. He has told me",
                "that the treasure is hidden in the chest in the upstairs",
                "room of the Blue Moon Inn in Varrock.",
            )
        val JOURNAL_DONE_NOTE =
            listOf(
                "I have opened the chest in the Blue Moon, and found a",
                "note inside. I think it will tell me where to dig.",
            )
        val JOURNAL_DONE_READ =
            listOf(
                "The note reads 'Visit the city of the White Knights. In the",
                "park, Saradomin points to the X which marks the spot.'",
            )
    }
}
