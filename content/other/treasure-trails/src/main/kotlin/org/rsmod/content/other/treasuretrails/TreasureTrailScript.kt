package org.rsmod.content.other.treasuretrails

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invClear
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.events.interact.LocOverrideEvents
import org.rsmod.api.player.events.interact.NpcOverrideEvents
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.interact.LocOpOverrides
import org.rsmod.api.player.interact.NpcOpOverrides
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.table.TrailBeginnerCasketRow
import org.rsmod.api.table.TrailEasyCasketRow
import org.rsmod.api.table.TrailHardCasketRow
import org.rsmod.api.table.TrailMediumCasketRow
import org.rsmod.api.table.cluehelper.CluehelperOutfitRow
import org.rsmod.content.interfaces.emotes.PlayEmote
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Treasure Trails on Jagex's own clue data (the `cluehelper_*` dbtables, see [TrailSteps]). A
 * trail is a number of steps ([TrailTier.lengths]); reading the scroll opens `trail_cluetext` (203)
 * with the step's text or the step's map interface: what RuneLite's Clue Scroll plugin reads. Steps
 * are npc talks, Charlie the Tramp's tasks, Reldo's hot-cold device, loc searches, digs and emotes
 * (Uri then appears). Tiers with one scroll obj per step swap the obj as the trail moves on. A
 * finished trail leaves a reward casket, whose opening message and `trail_rewardinv` (141) are what
 * the Loot Tracker records.
 */
@Singleton
class TreasureTrailScript
@Inject
constructor(
    private val npcOverrides: NpcOpOverrides,
    private val musicPlayer: MusicPlayer,
    private val locOverrides: LocOpOverrides,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val collision: CollisionFlagMap,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : PluginScript(), SpadeDigHook {
    private val uris = HashMap<Player, Npc>()
    private val foes = HashMap<Player, Npc>()

    fun uriOf(player: Player): Npc? = uris[player]

    override fun ScriptContext.startup() {
        for (obj in clueObjs()) {
            onOpHeld1(obj) { read(it.type) }
            onOpHeld2(obj) { checkSteps(it.type) }
        }
        for (challenge in challenges()) {
            onOpHeld1(objName(challenge.obj)) { readChallenge(challenge) }
        }
        for (box in puzzleBoxes()) {
            onOpHeld1(objName(box.obj)) { openPuzzle(box) }
        }
        onIfModalButton(PUZZLE_PIECES) { slidePiece(it.comsub) }
        for (tier in TrailTier.entries) {
            onOpHeld1(tier.scrollBox) { openScrollBox(tier) }
        }
        for (tier in TrailTier.entries) onOpHeld1(tier.casket) { openCasketAndShow(tier) }
        onOpHeld1(DEVICE) { feelDevice() }
        onIfClose(REWARD_INTERFACE) { player.invClear(player.invMap.getOrPut(REWARD_INV)) }
        registerNpcSteps()
        registerLocSteps()
        onEvent<PlayEmote> { emoted(player, seq.id) }
        for (tier in TrailTier.entries) onOpNpc1(tier.uri) { talkToUri(it.npc) }
        onPlayerLogout {
            uris.remove(player)?.let(::leave)
            foes.remove(player)?.let(::leave)
        }
        onCommand("clue") {
            desc = "Get a clue scroll: ::clue [beginner|easy|medium|hard|solve] [step row or 'list'] [steps left]"
            cheat(::giveClue)
        }
    }

    private fun clueObjs(): Set<String> =
        TrailTier.entries
            .flatMap { tier -> tier.clueObj?.let(::listOf) ?: TrailSteps.STEP_OBJS.getValue(tier).values.map(::objName) }
            .plus(TrailTier.entries.mapNotNull { it.dropObj })
            .toSet()

    private fun challenges(): List<Challenge> =
        TrailSteps.ALL.values.flatten().filterIsInstance<TrailStep.Talk>().mapNotNull { it.challenge }.distinctBy { it.obj }

    private fun puzzleBoxes(): List<PuzzleBox> =
        TrailSteps.ALL.values.flatten().filterIsInstance<TrailStep.Talk>().mapNotNull { it.puzzle }.distinctBy { it.obj }

    private fun ProtectedAccess.readChallenge(challenge: Challenge) {
        ifOpenMainModal(CLUE_TEXT_INTERFACE)
        ifSetText(CLUE_TEXT, challenge.question)
    }

    private fun ScriptContext.registerNpcSteps() {
        val npcs = TrailSteps.ALL.values.flatten().flatMap { npcsOf(it) }.toSet()
        for (npc in npcs) {
            npcOverrides.register(npc, InteractionOp.Op1) { player, _ -> npcStep(player, npc) != null }
            onProtectedEvent<NpcOverrideEvents.Op>(NpcOverrideEvents.key(npc, InteractionOp.Op1)) {
                talkToStepNpc(it.npc, npc)
            }
        }
    }

    private fun ScriptContext.registerLocSteps() {
        val searches = TrailSteps.ALL.values.flatten().filterIsInstance<TrailStep.Search>()
        for ((loc, op) in searches.flatMap { it.locs }.map { it.first }.toSet().mapNotNull { loc -> TrailSteps.stepOp(loc)?.let { loc to it } }) {
            locOverrides.register(loc, op) { player, bound -> searchStep(player, loc, bound.coords) != null }
            onProtectedEvent<LocOverrideEvents.Op>(LocOverrideEvents.key(loc, op)) {
                val step = searchStep(player, loc, it.loc.coords) as? TrailStep.Search ?: return@onProtectedEvent
                if (step.key != null && invDel(inv, objName(step.key)).failure) return@onProtectedEvent
                completeStep(step)
            }
        }
    }

    private fun npcsOf(step: TrailStep): Set<Int> =
        when (step) {
            is TrailStep.Talk -> step.npcs
            is TrailStep.Charlie -> step.npcs
            is TrailStep.HotCold -> step.npcs
            is TrailStep.Music -> step.npcs
            else -> emptySet()
        }

    private fun ProtectedAccess.read(obj: ItemServerType) {
        val tier = tierOf(obj.id) ?: return
        val step = stepForRead(player, tier, obj.id)
        val map = (step as? TrailStep.Dig)?.map ?: (step as? TrailStep.Search)?.map
        if (map != null) {
            ifOpenMainModal(map)
            return
        }
        ifOpenMainModal(CLUE_TEXT_INTERFACE)
        ifSetText(CLUE_TEXT, readText(player, step))
    }

    fun readText(player: Player, step: TrailStep): String {
        if (step is TrailStep.Charlie) {
            val task = step.tasks.firstOrNull { it.row == player.vars[step.tier.auxVarp] }
            if (task != null) return task.text
        }
        return step.text ?: ""
    }

    /** The step the scroll in hand shows; a new scroll (or a dropped generic one) starts a trail. */
    fun stepForRead(player: Player, tier: TrailTier, obj: Int): TrailStep {
        val current = currentStep(player, tier)
        val objRow = TrailSteps.STEP_OBJS.getValue(tier).entries.firstOrNull { it.value == obj }?.key
        if (current != null && (objRow == null || objRow == current.row)) return current
        if (current != null && objRow != null && player.vars[tier.stepsLeftVarp] > 0) {
            TrailSteps.byRow(tier, objRow)?.let {
                setStep(player, it, player.vars[tier.stepsLeftVarp])
                return it
            }
        }
        val step = objRow?.let { TrailSteps.byRow(tier, it) } ?: pickStep(tier, null)
        setStep(player, step, random.pick(tier.lengths))
        VarPlayerIntMapSetter.set(player, tier.stepsDoneVarp, 0)
        swapClueObj(player, obj, step)
        return step
    }

    @OptIn(UncheckedType::class)
    private fun swapClueObj(player: Player, from: Int, step: TrailStep) {
        val to = TrailSteps.clueObj(step)
        if (from == to) return
        val slot = player.inv.indexOfFirst { it?.id == from }
        if (slot >= 0) player.inv[slot] = InvObj(to, 1)
    }

    fun ProtectedAccess.checkSteps(obj: ItemServerType) {
        val tier = tierOf(obj.id) ?: return
        val done = player.vars[tier.stepsDoneVarp]
        mes(checkStepsMessage(tier, done))
    }

    private suspend fun ProtectedAccess.talkToStepNpc(npc: Npc, npcType: Int) {
        val step = npcStep(player, npcType) ?: return
        val puzzle = (step as? TrailStep.Talk)?.puzzle
        if (puzzle != null) {
            talkWithPuzzle(npc, step as TrailStep.Talk, puzzle)
            return
        }
        val challenge = (step as? TrailStep.Talk)?.challenge
        if (challenge != null) {
            talkWithChallenge(npc, step as TrailStep.Talk, challenge)
            return
        }
        when (step) {
            is TrailStep.Charlie -> talkToCharlie(npc, step)
            is TrailStep.HotCold -> talkToReldo(npc)
            is TrailStep.Music -> talkToCecilia(npc, step)
            else ->
                startDialogue(npc) {
                    chatNpc(neutral, "Well done.")
                    access.completeStep(step)
                }
        }
    }

    /**
     * Medium and up: the npc hands out the challenge scroll, then asks for the answer (count
     * dialog); a right answer takes the challenge scroll and moves the trail on. The dialogue
     * wording is a best guess (OSRS Wiki: Challenge scroll; no transcript).
     */
    private suspend fun ProtectedAccess.talkWithChallenge(npc: Npc, step: TrailStep.Talk, challenge: Challenge) {
        val scroll = objName(challenge.obj)
        if (!playerContainsObj(scroll)) {
            if (inv.freeSpace() == 0) {
                startDialogue(npc) { chatNpc(neutral, "You'll need some space in your inventory for my question.") }
                return
            }
            invAdd(inv, scroll)
            startDialogue(npc) {
                chatNpc(neutral, "Ah, a clue scroll. Answer my question and I'll give you the next clue.")
                objbox(scroll, "${npc.name} has given you a challenge scroll!")
            }
            return
        }
        startDialogue(npc) { chatNpc(quiz, "Please enter the answer to the question.") }
        val answer = countDialog("Enter amount:")
        if (answer != challenge.answer) {
            startDialogue(npc) { chatNpc(sad, "That isn't right, keep trying.") }
            return
        }
        if (!holdsClue(player, step) || invDel(inv, scroll).failure) return
        startDialogue(npc) {
            chatNpc(happy, "Spot on!")
            access.completeStep(step)
        }
    }

    /**
     * Hard and up: the npc hands out a scrambled puzzle box; once the box's slide puzzle is solved,
     * talking again takes the box and moves the trail on. The npc lines are best guesses.
     */
    private suspend fun ProtectedAccess.talkWithPuzzle(npc: Npc, step: TrailStep.Talk, box: PuzzleBox) {
        val boxName = objName(box.obj)
        if (!playerContainsObj(boxName)) {
            if (inv.freeSpace() == 0) {
                startDialogue(npc) { chatNpc(neutral, "You'll need some space in your inventory for my puzzle.") }
                return
            }
            invAdd(inv, boxName)
            setPuzzle(player, TrailPuzzle.scramble(box.puzzle) { random.of(0, it - 1) })
            startDialogue(npc) {
                chatNpc(neutral, "Ah, a clue scroll. Solve this puzzle and I'll give you the next clue.")
                objbox(boxName, "${npc.name} has given you a puzzle box!")
            }
            return
        }
        if (!TrailPuzzle.isSolved(box.puzzle, puzzleSlots(player))) {
            startDialogue(npc) { chatNpc(neutral, "You haven't solved the puzzle yet. Come back when you have.") }
            return
        }
        if (!holdsClue(player, step) || invDel(inv, boxName).failure) return
        invClear(player.invMap.getOrPut(PUZZLE_INV))
        startDialogue(npc) {
            chatNpc(happy, "Well done, you solved it!")
            access.completeStep(step)
        }
    }

    fun ProtectedAccess.openPuzzle(box: PuzzleBox) {
        if (puzzleSlots(player).filterNotNull().toSet() != TrailPuzzle.pieces(box.puzzle).toSet()) {
            setPuzzle(player, TrailPuzzle.scramble(box.puzzle) { random.of(0, it - 1) })
        }
        VarPlayerIntMapSetter.set(player, PUZZLE_VARP, box.puzzle)
        ifOpenMainModal(PUZZLE_INTERFACE)
        ifSetEvents(PUZZLE_PIECES, 0 until TrailPuzzle.SLOTS, IfEvent.Op1)
        invTransmit(player.invMap.getOrPut(PUZZLE_INV))
    }

    fun ProtectedAccess.slidePiece(slot: Int) {
        val slid = TrailPuzzle.slide(puzzleSlots(player), slot) ?: return
        setPuzzle(player, slid)
    }

    fun puzzleSlots(player: Player): List<Int?> {
        val inv = player.invMap.getOrPut(PUZZLE_INV)
        return (0 until TrailPuzzle.SLOTS).map { inv[it]?.id }
    }

    @OptIn(UncheckedType::class)
    fun setPuzzle(player: Player, slots: List<Int?>) {
        val inv = player.invMap.getOrPut(PUZZLE_INV)
        for ((slot, obj) in slots.withIndex()) {
            if (inv[slot]?.id != obj) inv[slot] = obj?.let { InvObj(it, 1) }
        }
    }

    private suspend fun ProtectedAccess.talkToCharlie(npc: Npc, step: TrailStep.Charlie) {
        val title = if (player.appearance.bodyType == Constants.bodytype_a) "sir" else "madam"
        val task = step.tasks.firstOrNull { it.row == player.vars[step.tier.auxVarp] }
        if (task == null) {
            val picked = random.pick(step.tasks)
            startDialogue(npc) {
                chatNpc(neutral, "Please, $title, can you help me.")
                chatPlayer(neutral, "What can I do for you?")
                chatNpc(neutral, picked.request)
                VarPlayerIntMapSetter.set(player, step.tier.auxVarp, picked.row)
                chatPlayer(happy, "Sure, I'll be glad to help a person in need.")
            }
            return
        }
        if (objName(task.obj) !in inv) {
            startDialogue(npc) { chatNpc(neutral, task.request) }
            return
        }
        val result = handInToCharlie(step)
        if (result == Advance.NONE) return
        val item = ServerCacheManager.getItem(task.obj)?.name?.lowercase() ?: "item"
        startDialogue(npc) {
            mesbox("You give Charlie the $item.")
            access.showAdvance(step.tier, result, byWhom = "Charlie gives you")
            chatNpc(happy, "Thank you so much $title.")
        }
    }

    fun ProtectedAccess.handInToCharlie(step: TrailStep.Charlie): Advance {
        val task = step.tasks.firstOrNull { it.row == player.vars[step.tier.auxVarp] } ?: return Advance.NONE
        if (!holdsClue(player, step) || objName(task.obj) !in inv) return Advance.NONE
        if (invDel(inv, objName(task.obj)).failure) return Advance.NONE
        return advanceTrail(step)
    }

    private suspend fun ProtectedAccess.talkToCecilia(npc: Npc, step: TrailStep.Music) {
        if (musicPlayer.isPlaying(player, step.music)) {
            val result = advanceTrail(step)
            startDialogue(npc) {
                chatNpc(happy, "Thank you, I love that song!")
                when (result) {
                    Advance.NEXT_CLUE -> {
                        val obj = objName(TrailSteps.clueObj(checkNotNull(currentStep(player, step.tier))))
                        objbox(obj, "Cecilia offers you another clue.")
                    }
                    Advance.CASKET -> objbox(step.tier.casket, "You've obtained a casket!")
                    Advance.NONE -> Unit
                }
            }
            return
        }
        startDialogue(npc) {
            chatNpc(quiz, "Have you come to play music to me?")
            if (choice2("Yes, I have.", true, "No, I'm not interested in that.", false)) {
                chatPlayer(neutral, "Yes, I have.")
                chatNpc(neutral, "Select the song I asked you to play, and speak to me again.")
            } else {
                chatPlayer(neutral, "No, I'm not interested in that.")
                chatNpc(
                    happy,
                    "Then you must have come to gaze in rapture at the natural beauty of Falador's parkland!",
                )
            }
        }
    }

    private suspend fun ProtectedAccess.talkToReldo(npc: Npc) {
        if (playerContainsObj(DEVICE)) {
            startDialogue(npc) {
                chatPlayer(neutral, "Hey Reldo, I have one of those clue scrolls again, do you have any advice?")
                chatNpc(
                    neutral,
                    "That strange device will guide you in the same way the key did. Feel it, but " +
                        "be careful as it is very powerful. Good luck.",
                )
            }
            return
        }
        startDialogue(npc) {
            chatPlayer(neutral, "Hi Reldo, I have come to ask you to help with something.")
            chatNpc(neutral, "What can I do for you ${player.displayName}?")
            mesbox("You show Reldo your clue scroll.")
            chatNpc(quiz, "Ah! Do you know about hot and cold clues?")
            if (choice2("Yes, I do.", true, "No, I don't.", false)) {
                chatPlayer(neutral, "Yes, I do.")
            } else {
                chatPlayer(neutral, "No, I don't.")
                chatNpc(
                    neutral,
                    "Ah, you use a strange device to tell you how far away you are from the place " +
                        "you need to be. When you get to your destination you dig.",
                )
            }
            chatNpc(neutral, "Well then use this strange device to feel where your destination is and dig.")
            if (access.inv.freeSpace() == 0) {
                chatNpc(neutral, "You should make some room for it before I can give you it.")
                return@startDialogue
            }
            access.invAdd(access.inv, DEVICE)
        }
    }

    fun ProtectedAccess.feelDevice() {
        val step = currentStep(player, TrailTier.Beginner) as? TrailStep.HotCold
        val spot = step?.let { hotColdSpot(player, it) }
        if (step == null || spot == null || !holdsClue(player, step)) {
            mes("Nothing interesting happens.")
            return
        }
        mes(deviceMessage(player, spot.coord))
    }

    fun hotColdSpot(player: Player, step: TrailStep.HotCold): HotColdSpot? =
        step.spots.firstOrNull { it.row == player.vars[HOT_COLD_SPOT_VARP] }

    fun deviceMessage(player: Player, spot: CoordGrid): String {
        val distance =
            if (player.coords.level == spot.level) player.coords.chebyshevDistance(spot) else Int.MAX_VALUE - 1
        val last = player.vars[HOT_COLD_LAST_VARP] - 1
        VarPlayerIntMapSetter.set(player, HOT_COLD_LAST_VARP, distance + 1)
        val temperature = TEMPERATURES.first { distance >= it.first }.second
        return when {
            last < 0 -> "The device is $temperature."
            distance < last -> "The device is $temperature, and warmer than last time."
            distance > last -> "The device is $temperature, but colder than last time."
            else -> "The device is $temperature, and the same temperature as last time."
        }
    }

    fun emoted(player: Player, seq: Int) {
        val steps = TrailTier.entries.mapNotNull { currentStep(player, it) as? TrailStep.Emote }
        val second = steps.firstOrNull { it.seq2 == seq && uris[player]?.isSlotAssigned == true }
        if (second != null) {
            if (holdsClue(player, second)) VarPlayerIntMapSetter.set(player, second.tier.auxVarp, SECOND_EMOTE_DONE)
            return
        }
        val step = steps.firstOrNull { it.seq == seq } ?: return
        if (!holdsClue(player, step) || !step.covers(player.coords, step.coord, TrailSteps.EMOTE_RADIUS)) return
        if (uris[player]?.isSlotAssigned == true) return
        val agentDue = step.tier == TrailTier.Hard && player.vars[step.tier.stepsDoneVarp] > 0
        if (agentDue && player.vars[step.tier.auxVarp] != FOE_DEFEATED) {
            summonFoe(player, doubleAgent(player), "I expect you to die!")
            return
        }
        summonUri(player, step)
    }

    private fun summonUri(player: Player, step: TrailStep.Emote) {
        if (uris[player]?.isSlotAssigned == true) return
        val uri = Npc(step.tier.uri, besideTile(player.coords))
        uri.mode = NpcMode.None
        npcRepo.add(uri, URI_LIFETIME)
        uri.assignSpawnOwner(player, mapClock.cycle)
        uri.facePlayer(player)
        uris[player] = uri
    }

    private suspend fun ProtectedAccess.talkToUri(uri: Npc) {
        val step = TrailTier.entries.firstNotNullOfOrNull { currentStep(player, it) as? TrailStep.Emote }
        val owned = uris[player] === uri
        val secondDone = step?.seq2 == null || player.vars[step.tier.auxVarp] == SECOND_EMOTE_DONE
        if (!owned || step == null || !wearsOutfit(player, step.outfits) || !secondDone) {
            startDialogue(uri) { chatNpc(neutral, "I do not believe we have any business, Comrade.") }
            return
        }
        uris.remove(player)
        startDialogue(uri) {
            chatNpc(neutral, random.pick(URI_LINES))
            chatPlayer(confused, "What?")
            access.completeStep(step)
        }
        leave(uri)
    }

    override fun claims(player: Player): Boolean = digStep(player) != null

    override suspend fun ProtectedAccess.dig() {
        val step = digStep(player) ?: return
        if (step is TrailStep.Dig && step.foes.isNotEmpty() && player.vars[step.tier.auxVarp] != FOE_DEFEATED) {
            summonFoe(player, step.foes.first(), wizardCall(step.foes.first()))
            return
        }
        completeStep(step)
    }

    /**
     * Hard coordinate wizards and the hard Double agent (OSRS Wiki): the foe appears beside the
     * player, calls out and attacks; killing it ([foeKilled]) lets the dig or Uri go ahead. One foe
     * per player at a time.
     */
    fun summonFoe(player: Player, type: Int, call: String) {
        if (foes[player]?.isSlotAssigned == true) return
        val foe = Npc(dev.openrune.rscm.RSCM.getReverseMapping(RSCMType.NPC, type), besideTile(player.coords))
        npcRepo.add(foe, FOE_LIFETIME)
        foe.assignSpawnOwner(player, mapClock.cycle)
        foes[player] = foe
        foe.say(call)
        foe.opPlayer2(player, aiPlayerInteractions)
    }

    fun foeOf(player: Player): Npc? = foes[player]

    fun foeKilled(player: Player, npc: Npc) {
        if (foes[player] !== npc) return
        foes.remove(player)
        val step = TrailTier.entries.firstNotNullOfOrNull { tier ->
            currentStep(player, tier)?.takeIf { it is TrailStep.Dig || it is TrailStep.Emote }
        } ?: return
        VarPlayerIntMapSetter.set(player, step.tier.auxVarp, FOE_DEFEATED)
        if (step is TrailStep.Emote && wearsOutfit(player, step.outfits)) summonUri(player, step)
    }

    private fun wizardCall(type: Int): String =
        if (type == ZAMORAK_WIZARD.asRSCM(RSCMType.NPC)) "Die, human!" else "For Saradomin!"

    private fun doubleAgent(player: Player): Int {
        val c = player.coords
        val wilderness = c.x in WILDERNESS_X && c.z in WILDERNESS_Z
        return (if (wilderness) DOUBLE_AGENT_WILDERNESS else DOUBLE_AGENT).asRSCM(RSCMType.NPC)
    }

    fun digStep(player: Player): TrailStep? =
        TrailTier.entries.firstNotNullOfOrNull { tier ->
            when (val step = currentStep(player, tier)) {
                is TrailStep.Dig -> step.takeIf { it.covers(player.coords, it.coord, it.radius) }
                is TrailStep.HotCold -> {
                    val spot = hotColdSpot(player, step)
                    step.takeIf { spot != null && it.covers(player.coords, spot.coord, TrailSteps.HOT_COLD_RADIUS) }
                }
                else -> null
            }?.takeIf { holdsClue(player, it) }
        }

    private suspend fun ProtectedAccess.completeStep(step: TrailStep) {
        showAdvance(step.tier, advanceTrail(step), byWhom = null)
    }

    private suspend fun ProtectedAccess.showAdvance(tier: TrailTier, result: Advance, byWhom: String?) {
        when (result) {
            Advance.NEXT_CLUE -> {
                val obj = objName(TrailSteps.clueObj(checkNotNull(currentStep(player, tier))))
                objbox(obj, if (byWhom == null) "You've found a new clue!" else "$byWhom a new clue!")
            }
            Advance.CASKET -> objbox(tier.casket, if (byWhom == null) "You've obtained a casket!" else "$byWhom a casket!")
            Advance.NONE -> Unit
        }
    }

    fun ProtectedAccess.advanceTrail(step: TrailStep): Advance {
        val tier = step.tier
        val obj = TrailSteps.clueObj(step)
        if (player.inv.count(objName(obj)) == 0) return Advance.NONE
        val left = player.vars[tier.stepsLeftVarp] - 1
        if (left > 0) {
            val done = player.vars[tier.stepsDoneVarp] + 1
            val next = pickStep(tier, step)
            setStep(player, next, left)
            VarPlayerIntMapSetter.set(player, tier.stepsDoneVarp, done)
            swapClueObj(player, obj, next)
            return Advance.NEXT_CLUE
        }
        if (invDel(inv, objName(obj)).failure) return Advance.NONE
        clearTrail(player, tier)
        invAdd(inv, tier.casket)
        return Advance.CASKET
    }

    enum class Advance {
        NONE,
        NEXT_CLUE,
        CASKET,
    }

    fun ProtectedAccess.openScrollBox(tier: TrailTier) {
        if (holdsTierClue(tier)) {
            mes("The clue box refuses to open - you already have a clue.")
            return
        }
        if (invDel(inv, tier.scrollBox).failure) return
        invAdd(inv, tier.dropObj ?: objName(TrailSteps.clueObj(pickStep(tier, null))))
    }

    private fun ProtectedAccess.holdsTierClue(tier: TrailTier): Boolean {
        val objs = (tier.clueObj?.let { setOf(it.asRSCM(RSCMType.OBJ)) } ?: TrailSteps.STEP_OBJS.getValue(tier).values.toSet()) +
            listOfNotNull(tier.dropObj?.asRSCM(RSCMType.OBJ))
        return objs.any { playerContainsObj(objName(it)) }
    }

    private fun ProtectedAccess.openCasketAndShow(tier: TrailTier) {
        val rewards = openCasket(tier) ?: return
        val rewardInv = player.invMap.getOrPut(REWARD_INV)
        invClear(rewardInv)
        for (reward in rewards) {
            invAdd(rewardInv, reward.obj.internalName, reward.count, cert = reward.noted, strict = false)
        }
        invTransmit(rewardInv)
        ifOpenMainModal(REWARD_INTERFACE)
    }

    fun ProtectedAccess.openCasket(tier: TrailTier): List<Reward>? {
        val freed = if (inv.count(tier.casket) == 1) 1 else 0
        val space = tier.rolls.last
        if (inv.freeSpace() + freed < space) {
            mes("You need at least $space free inventory spaces to open the casket.")
            return null
        }
        if (invDel(inv, tier.casket).failure) return null
        val completed = player.vars[tier.completedVarp] + 1
        VarPlayerIntMapSetter.set(player, tier.completedVarp, completed)
        invClear(player.invMap.getOrPut(REWARD_INV))
        mes("Well done, you've completed the Treasure Trail!")
        val plural = if (completed == 1) "" else "s"
        mes("You have completed <col=ef1020>$completed</col> ${tier.label} Treasure Trail$plural.")
        val rewards = rollRewards(tier, random.of(tier.rolls)) { random.of(it) }
        for (reward in rewards) {
            val obj = reward.obj.internalName
            if (invAdd(inv, obj, reward.count, cert = reward.noted).failure) {
                invAddOrDrop(objRepo, obj, reward.count)
            }
        }
        return rewards
    }

    fun giveClue(cheat: Cheat) {
        val player = cheat.player
        if (cheat.args.getOrNull(0) == "solve") {
            val box = puzzleBoxes().firstOrNull { player.inv.count(objName(it.obj)) > 0 }
            if (box == null) {
                player.mes("You have no puzzle box.")
                return
            }
            setPuzzle(player, TrailPuzzle.solved(box.puzzle))
            player.mes("Puzzle box solved.")
            return
        }
        val tier = TrailTier.entries.firstOrNull { it.label == cheat.args.getOrNull(0) } ?: TrailTier.Beginner
        val steps = TrailSteps.ALL.getValue(tier)
        val arg = cheat.args.getOrNull(1)
        if (arg == "list") {
            for (chunk in steps.map { "${it.row} ${it::class.simpleName}" }.chunked(LIST_CHUNK)) {
                player.mes("${tier.label} steps: ${chunk.joinToString()}")
            }
            return
        }
        val step = arg?.toIntOrNull()?.let { TrailSteps.byRow(tier, it) } ?: pickStep(tier, null)
        val left = cheat.args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, tier.lengths.max()) ?: 1
        removeClues(player, tier)
        if (player.invAdd(player.inv, objName(TrailSteps.clueObj(step))).failure) {
            player.mes("You need a free inventory space.")
            return
        }
        setStep(player, step, left)
        VarPlayerIntMapSetter.set(player, tier.stepsDoneVarp, 0)
        player.mes("${tier.label} clue: row ${step.row} (${step::class.simpleName}), $left step(s) left.")
    }

    private fun removeClues(player: Player, tier: TrailTier) {
        val objs = (tier.clueObj?.let { setOf(it.asRSCM(RSCMType.OBJ)) } ?: TrailSteps.STEP_OBJS.getValue(tier).values.toSet()) +
            listOfNotNull(tier.dropObj?.asRSCM(RSCMType.OBJ)) + challenges().map { it.obj } + puzzleBoxes().map { it.obj }
        for (slot in player.inv.indices) {
            if (player.inv[slot]?.id in objs) player.inv[slot] = null
        }
    }

    /** Any step of [tier] but [previous]. */
    fun pickStep(tier: TrailTier, previous: TrailStep?): TrailStep =
        random.pick(TrailSteps.ALL.getValue(tier).filter { previous == null || it.row != previous.row })

    private fun setStep(player: Player, step: TrailStep, left: Int) {
        val tier = step.tier
        VarPlayerIntMapSetter.set(player, tier.stepVarp, step.row)
        VarPlayerIntMapSetter.set(player, tier.stepsLeftVarp, left)
        VarPlayerIntMapSetter.set(player, tier.auxVarp, 0)
        if (step is TrailStep.HotCold) {
            VarPlayerIntMapSetter.set(player, HOT_COLD_SPOT_VARP, random.pick(step.spots).row)
            VarPlayerIntMapSetter.set(player, HOT_COLD_LAST_VARP, 0)
        }
    }

    private fun clearTrail(player: Player, tier: TrailTier) {
        for (varp in listOf(tier.stepVarp, tier.stepsLeftVarp, tier.stepsDoneVarp, tier.auxVarp)) {
            VarPlayerIntMapSetter.set(player, varp, 0)
        }
    }

    fun npcStep(player: Player, npcType: Int): TrailStep? =
        TrailTier.entries.firstNotNullOfOrNull { tier ->
            currentStep(player, tier)?.takeIf { npcType in npcsOf(it) && holdsClue(player, it) }
        }

    fun searchStep(player: Player, loc: Int, coords: CoordGrid): TrailStep? =
        TrailTier.entries.firstNotNullOfOrNull { tier ->
            (currentStep(player, tier) as? TrailStep.Search)?.takeIf { step ->
                step.locs.any { it.first == loc && it.second == coords } &&
                    holdsClue(player, step) &&
                    (step.key == null || player.inv.count(objName(step.key)) > 0)
            }
        }

    fun dropKey(key: String, coords: CoordGrid, player: Player) {
        objRepo.add(key, coords, KEY_DROP_DURATION, receiver = player)
    }

    /** The key a kill of [npcType] drops for [player]'s key step, if they do not hold it yet. */
    fun keyDrop(player: Player, npcType: Int): String? =
        TrailTier.entries.firstNotNullOfOrNull { tier ->
            val step = currentStep(player, tier) as? TrailStep.Search ?: return@firstNotNullOfOrNull null
            val key = step.key ?: return@firstNotNullOfOrNull null
            objName(key).takeIf {
                npcType in step.keyNpcs && holdsClue(player, step) && player.inv.count(it) == 0
            }
        }

    fun holdsClue(player: Player, step: TrailStep): Boolean = player.inv.count(objName(TrailSteps.clueObj(step))) > 0

    private fun leave(npc: Npc) {
        if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun besideTile(centre: CoordGrid): CoordGrid {
        for ((x, z) in ADJACENT_OFFSETS) {
            val candidate = centre.translate(x, z)
            if (collision[candidate.x, candidate.z, candidate.level] and BLOCKED_FLAGS == 0) {
                return candidate
            }
        }
        return centre
    }

    data class Reward(val obj: ItemServerType, val count: Int, val noted: Boolean)

    data class CasketRow(val obj: ItemServerType, val min: Int, val max: Int, val weight: Int, val noted: Boolean)

    companion object {
        /**
         * Check's message (RSProx: "You have completed 3 steps on this master clue scroll."), with
         * [tier]'s lowercase name as the tier word.
         */
        fun checkStepsMessage(tier: TrailTier, done: Int): String =
            "You have completed $done ${if (done == 1) "step" else "steps"} on this ${tier.label} clue scroll."

        const val DEVICE = "obj.beginner_device"
        const val CLUE_TEXT_INTERFACE = "interface.trail_cluetext"
        const val CLUE_TEXT = "component.trail_cluetext:text"
        const val REWARD_INTERFACE = "interface.trail_rewardscreen"
        const val REWARD_INV = "inv.trail_rewardinv"
        const val PUZZLE_INV = "inv.trail_puzzleinv"
        const val PUZZLE_INTERFACE = "interface.trail_slidepuzzle"
        const val PUZZLE_PIECES = "component.trail_slidepuzzle:pieces"
        const val PUZZLE_VARP = "varp.if1"
        const val HOT_COLD_SPOT_VARP = "varp.trail_beginner_hot_cold_spot"
        const val HOT_COLD_LAST_VARP = "varp.trail_beginner_hot_cold_last"
        const val URI_LIFETIME = 100
        const val SECOND_EMOTE_DONE = 1
        const val FOE_DEFEATED = 3
        const val FOE_LIFETIME = 500
        const val ZAMORAK_WIZARD = "npc.trail_hard"
        const val DOUBLE_AGENT = "npc.double_agent_2"
        const val DOUBLE_AGENT_WILDERNESS = "npc.double_agent_1"
        val WILDERNESS_X = 2944..3391
        val WILDERNESS_Z = 3520..3967
        const val KEY_DROP_DURATION = 200
        const val LIST_CHUNK = 12
        const val BLOCKED_FLAGS = CollisionFlag.BLOCK_WALK or CollisionFlag.BLOCK_PLAYERS
        val ADJACENT_OFFSETS =
            listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, -1 to 1, 1 to 1)

        /** OSRS Wiki, Strange device: the reading for a Chebyshev distance of at least `first`. */
        val TEMPERATURES =
            listOf(
                500 to "ice cold",
                200 to "very cold",
                150 to "cold",
                100 to "warm",
                70 to "hot",
                30 to "very hot",
                4 to "incredibly hot",
                0 to "visibly shaking",
            )

        val CASKETS: Map<TrailTier, List<CasketRow>> by lazy {
            mapOf(
                TrailTier.Beginner to
                    TrailBeginnerCasketRow.all().map { CasketRow(it.obj, it.min, it.max, it.weight, it.noted) },
                TrailTier.Easy to
                    TrailEasyCasketRow.all().map { CasketRow(it.obj, it.min, it.max, it.weight, it.noted) },
                TrailTier.Medium to
                    TrailMediumCasketRow.all().map { CasketRow(it.obj, it.min, it.max, it.weight, it.noted) },
                TrailTier.Hard to
                    TrailHardCasketRow.all().map { CasketRow(it.obj, it.min, it.max, it.weight, it.noted) },
            )
        }

        val URI_LINES =
            listOf(
                "A great captain is always ready to change course.",
                "Actions have consequences.",
                "Can I have your teabag?",
                "I am the egg man, are you one of the egg men?",
                "I believe that it is very rainy in Varrock.",
                "I quite fancy an onion.",
                "My magic carpet is full of eels.",
                "Once, I was a poor man, but then I found a party hat.",
                "The slowest of fishermen catch the swiftest of fish.",
                "What's cheese?",
                "Would you like to buy a pewter spoon?",
            )

        fun objName(id: Int): String = dev.openrune.rscm.RSCM.getReverseMapping(RSCMType.OBJ, id)

        fun tierOf(obj: Int): TrailTier? =
            TrailTier.entries.firstOrNull { tier ->
                tier.clueObj?.asRSCM(RSCMType.OBJ) == obj ||
                    tier.dropObj?.asRSCM(RSCMType.OBJ) == obj ||
                    obj in TrailSteps.STEP_OBJS.getValue(tier).values
            }

        fun currentStep(player: Player, tier: TrailTier): TrailStep? =
            TrailSteps.byRow(tier, player.vars[tier.stepVarp])

        /** Every outfit slot that lists items needs one of them worn (any outfit row may match). */
        fun wearsOutfit(player: Player, outfits: List<CluehelperOutfitRow>): Boolean {
            if (outfits.isEmpty()) return true
            val worn = player.worn.objs.filterNotNull().map { it.id }.toSet()
            return outfits.any { outfit -> outfitSlots(outfit).all { slot -> slot.any { it.id in worn } } }
        }

        fun outfitSlots(outfit: CluehelperOutfitRow): List<List<ItemServerType>> =
            listOf(
                    outfit.wearposHat,
                    outfit.wearposBack,
                    outfit.wearposFront,
                    outfit.wearposRhand,
                    outfit.wearposTorso,
                    outfit.wearposLhand,
                    outfit.wearposLegs,
                    outfit.wearposHands,
                    listOfNotNull(outfit.wearposFeet),
                    outfit.wearposRing,
                    listOfNotNull(outfit.wearposQuiver),
                )
                .filter { it.isNotEmpty() }

        /** [rolls] picks from [tier]'s casket table; [nextInt] returns 0 until its bound (exclusive). */
        fun rollRewards(tier: TrailTier, rolls: Int, nextInt: (Int) -> Int): List<Reward> {
            val table = CASKETS.getValue(tier)
            val total = table.sumOf { it.weight }
            val rewards = mutableListOf<Reward>()
            repeat(rolls) {
                val row = pickReward(table, nextInt(total))
                val count = if (row.max > row.min) row.min + nextInt(row.max - row.min + 1) else row.min
                val same = rewards.indexOfFirst { it.obj.id == row.obj.id && it.noted == row.noted }
                if (same >= 0) {
                    rewards[same] = rewards[same].copy(count = rewards[same].count + count)
                } else {
                    rewards += Reward(row.obj, count, row.noted)
                }
            }
            return rewards
        }

        private fun pickReward(table: List<CasketRow>, roll: Int): CasketRow {
            var left = roll
            for (row in table) {
                if (left < row.weight) return row
                left -= row.weight
            }
            error("Casket roll $roll is past the table's total weight")
        }
    }
}
