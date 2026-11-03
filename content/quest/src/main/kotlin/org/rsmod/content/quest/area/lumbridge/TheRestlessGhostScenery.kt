package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.constants
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

internal class TheRestlessGhostScenery(
    private val quest: Quest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val cutscene: TheRestlessGhostCutscene,
    private val aiPlayerInteractions: AiPlayerInteractions,
) {

    private var Player.altarSkullTaken by intVarBit(AltarVarbit)
    private var Player.altarSkeletonRisen by intVarBit(SkeletonVarbit)
    private var Player.coffinSkullReturned by intVarBit(CoffinVarbit)

    fun ScriptContext.register() {

        onPlayerSoftTimer(GhostSpawnTimer) { player.spawnGhost() }

        onPlayerSoftTimer(SkeletonAttackTimer) { player.skeletonAttack() }

        onPlayerCoordsChanged { restoreScenerySkeleton(player) }

        onOpLoc1(ClosedCoffin) { openCoffin(it.loc) }
        onOpLoc1(OpenCoffin) { searchOpenCoffin(it.loc) }
        onOpLoc2(OpenCoffin) { closeCoffin(it.loc) }

        onOpLocU(OpenCoffin, GhostSkull) { useSkullOnOpenCoffin(it.loc) }
        onOpLocU(ClosedCoffin, GhostSkull) { mesbox("Maybe I should open it first.") }

        onOpLoc1(Altar) { searchAltar(it.loc) }

        onOpNpcU(
            checkNotNull(ServerCacheManager.getNpc(GhostNpc.asRSCM())),
            checkNotNull(ServerCacheManager.getItem(GhostSkull.asRSCM())),
        ) { offerSkullToGhost(it.npc) }

        onOpObj3(checkNotNull(ServerCacheManager.getItem(GhostSkull.asRSCM()))) { takeSkull(it.obj) }

        onOpHeld1(GhostSkull) { inspectSkull() }
        onOpHeld5(GhostSkull) { dropSkull(it.slot) }
    }

    // Use a soft timer for cleanup; soft queues would close unrelated modals while pending.
    private suspend fun ProtectedAccess.openCoffin(coffin: BoundLocInfo) {
        arriveDelay()
        faceSquare(coffin.coords)
        delay(1)

        anim(OpenCoffinAnim)
        soundSynth(CoffinOpenSound)
        delay(1)

        locRepo.change(coffin, OpenCoffin, CoffinOpenTicks)
        raiseGhost(coffin)
        mesbox("You open the coffin.")
    }

    private fun ProtectedAccess.raiseGhost(coffin: BoundLocInfo) {
        if (!player.ghostCanRise()) {
            return
        }

        val ghostTile = coffin.ghostTile()

        if (ghostAlreadyPresent(ghostTile)) {
            return
        }

        soulApproach(coffin.spiritSource(), ghostTile)
        soundSynth(GhostApproachSound)
        player.restlessGhostRisingTile = ghostTile.packed
        player.softTimer(GhostSpawnTimer, GhostSpawnDelayTicks)
    }

    private fun Player.spawnGhost() {
        clearSoftTimer(GhostSpawnTimer)

        val savedTile = restlessGhostRisingTile
        restlessGhostRisingTile = 0
        if (savedTile == 0) return
        val tile = CoordGrid(savedTile)
        val player = this

        if (!ghostCanRise()) {
            return
        }

        if (ghostAlreadyPresent(tile)) {
            return
        }

        val ghost = Npc(GhostNpc, tile)
        npcRepo.add(ghost, GhostLifespanTicks)
        ghost.noneMode()
        ghost.anim(GhostAppearAnim)
        player.soundSynth(GhostAppearSound)
    }

    private fun Player.ghostCanRise(): Boolean {
        val stage = quest.getQuestStage(this)
        return stage >= TheRestlessGhostStage.HasAmulet && stage < TheRestlessGhostStage.Complete
    }

    private fun restoreScenerySkeleton(player: Player) {
        if (player.altarSkeletonRisen == 0 || player.coords.z >= UndergroundZ) {
            return
        }
        player.altarSkeletonRisen = 0
    }

    private suspend fun ProtectedAccess.closeCoffin(coffin: BoundLocInfo) {
        arriveDelay()
        mes("You close the coffin.")
        faceSquare(coffin.coords)
        anim(CloseCoffinAnim)
        soundSynth(CoffinCloseSound)
        delay(1)
        locRepo.change(coffin, ClosedCoffin, CoffinClosedTicks)
    }

    private suspend fun ProtectedAccess.searchOpenCoffin(coffin: BoundLocInfo) {
        arriveDelay()
        faceSquare(coffin.coords)

        if (quest.isQuestCompleted(player)) {
            mesbox("There's a nice and complete skeleton in here!")
        } else {
            mesbox("There's a skeleton without a skull in here.")
        }
    }

    private suspend fun ProtectedAccess.useSkullOnOpenCoffin(coffin: BoundLocInfo) {
        if (quest.getQuestStage(player) != TheRestlessGhostStage.SkullFound) {
            searchOpenCoffin(coffin)
            return
        }
        arriveDelay()
        faceSquare(coffin.coords)
        returnSkull(coffin)
    }

    private suspend fun ProtectedAccess.returnSkull(coffin: BoundLocInfo) {
        mes("You put the skull in the coffin.")
        anim(PlaceSkullAnim)
        soundSynth(PlaceSkullSound)
        if (invDel(inv, GhostSkull).failure) {
            return
        }

        player.coffinSkullReturned = 1

        delay(1)
        cutscene.release(this)

        locRepo.change(coffin, OpenCoffin, CoffinOpenTicks)
    }

    private suspend fun ProtectedAccess.searchAltar(altar: BoundLocInfo) {
        arriveDelay()
        faceSquare(altar.coords)

        if (quest.isQuestCompleted(player)) {
            mesbox(
                "Surprisingly, this altar is empty. Probably because you took the skull off it " +
                    "earlier."
            )
            return
        }

        if (quest.getQuestStage(player) < TheRestlessGhostStage.GhostSpoken) {
            mesbox(ScarySkull)
            return
        }

        if (hasSkull()) {
            mesbox(AlreadyHaveSkull)
            return
        }

        val skeletonTile = altar.coords.translateZ(SkeletonSpawnOffsetZ)
        val busySkeleton = npcRepo.findAll(ZoneKey.from(skeletonTile), 1).firstOrNull {
            it.isType(SkeletonNpc) && (it.interaction as? InteractionPlayer)?.target.let { target ->
                target != null && target != player
            }
        }
        if (busySkeleton != null) {
            npcRepo.del(busySkeleton, Int.MAX_VALUE)
        }
        if (busySkeleton != null || player.altarSkeletonRisen == 0) {
            mes("The skeleton in the corner suddenly comes to life!")
            wakeSkeleton(skeletonTile)
            player.altarSkeletonRisen = 1
        }

        player.altarSkullTaken = 1
        if (invAdd(inv, GhostSkull, 1).failure) {
            objRepo.add(GhostSkull, player.coords, FloorSkullTicks, player)
        }

        if (quest.getQuestStage(player) == TheRestlessGhostStage.GhostSpoken) {
            quest.setQuestStage(this, TheRestlessGhostStage.SkullFound)
        }
    }

    private fun ProtectedAccess.hasSkull(): Boolean =
        inv.contains(GhostSkull) || bank.contains(GhostSkull)

    internal suspend fun ProtectedAccess.takeSkull(obj: Obj) {
        if (quest.getQuestStage(player) !in TheRestlessGhostStage.GhostSpoken until TheRestlessGhostStage.Complete) {
            mesbox(ScarySkull)
            return
        }
        if (hasSkull()) {
            mesbox(AlreadyHaveSkull)
            return
        }
        if (player.coords != obj.coords) {
            delay(1)
            anim(PlaceSkullAnim)
        }
        if (inv.isFull()) {
            mes(constants.dm_take_invspace)
            return
        }
        if (!objRepo.del(obj)) {
            mes(constants.dm_take_taken)
            return
        }
        soundSynth(TakeSound)
        invAdd(inv, GhostSkull, 1)
    }

    private fun ProtectedAccess.wakeSkeleton(tile: CoordGrid) {
        val skeleton = Npc(SkeletonNpc, tile)
        npcRepo.add(skeleton, SkeletonLifespanTicks)
        skeleton.anim(SkeletonRiseAnim)
        soundSynth(SkeletonWakeSound)

        player.attr[RISEN_SKELETON] = skeleton
        player.softTimer(SkeletonAttackTimer, SkeletonRiseTicks)
    }

    private fun ghostAlreadyPresent(tile: CoordGrid): Boolean =
        npcRepo.findAll(tile).any { it.isType(GhostNpc) }

    private fun Player.skeletonAttack() {
        clearSoftTimer(SkeletonAttackTimer)

        val skeleton = attr[RISEN_SKELETON]
        attr.remove(RISEN_SKELETON)

        if (skeleton != null && skeleton.isSlotAssigned && skeleton.isType(SkeletonNpc)) {
            skeleton.opPlayer2(this, aiPlayerInteractions)
        }
    }

    private suspend fun ProtectedAccess.offerSkullToGhost(ghost: Npc) {
        startDialogue(ghost) {
            mesbox("I can't give it to him. It goes right through him.")
            chatNpc(neutral, "If you just put it in my coffin that should do the trick...")
        }
    }

    private suspend fun ProtectedAccess.inspectSkull() {
        mesbox(
            "It's the skull of the ghost that is haunting Lumbridge graveyard. Maybe I should " +
                "return this back to the ghost's coffin."
        )
    }

    private suspend fun ProtectedAccess.dropSkull(slot: Int) {
        startDialogue {
            mesbox("Dropping this skull here will destroy it!")
            val confirmed =
                choice2(
                    "Yes.",
                    true,
                    "No.",
                    false,
                    title = "Are you sure you want to drop the skull?",
                )
            if (!confirmed) {
                return@startDialogue
            }
            access.invDel(access.inv, GhostSkull, count = 1, slot = slot)
            access.soundSynth(DestroySound)
        }
    }

    private fun soulApproach(from: CoordGrid, to: CoordGrid) {
        worldRepo.projAnim(
            ProjAnim(
                spotanim = SoulSpotanim.asRSCM(RSCMType.SPOTANIM),
                startHeight = ApproachStartHeight,
                endHeight = 0,
                startTime = ApproachStartTime,
                endTime = ApproachEndTime,
                angle = ApproachAngle,
                progress = ApproachProgress,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = from,
                endCoord = to,
            )
        )
    }

    private fun BoundLocInfo.spiritSource(): CoordGrid = coords.translateX(1)

    private fun BoundLocInfo.ghostTile(): CoordGrid = coords.translateX(1).translateZ(3)

    private companion object {
        const val ClosedCoffin = "loc.shutghostcoffin"
        const val OpenCoffin = "loc.openghostcoffin"
        const val Altar = "loc.restless_ghost_altar"

        const val GhostNpc = "npc.ghostx"
        const val SkeletonNpc = "npc.skull_skeleton"
        const val GhostSkull = "obj.ghostskull"

        const val AltarVarbit = "varbit.restless_ghost_altar_var"
        const val SkeletonVarbit = "varbit.restless_ghost_skeleton_var"
        const val CoffinVarbit = "varbit.restless_ghost_coffin_var"

        const val OpenCoffinAnim = "seq.human_openchest"
        const val CloseCoffinAnim = "seq.human_closechest"
        const val PlaceSkullAnim = "seq.human_pickuptable"
        const val GhostAppearAnim = "seq.restless_ghost_appear"
        const val SkeletonRiseAnim = "seq.restless_skeleton_get_up"

        const val CoffinOpenSound = "synth.coffin_open"
        const val CoffinCloseSound = "synth.coffin_close"
        const val GhostApproachSound = "synth.rg_ghost_approach"
        const val GhostAppearSound = "synth.bigghost_appear"
        const val PlaceSkullSound = "synth.rg_place_skull"
        const val SkeletonWakeSound = "synth.rg_skeleton_awake"
        const val DestroySound = "synth.destroy_object"
        const val TakeSound = "synth.pick2"

        const val ScarySkull =
            "That skull looks scary. I've got no reason to take it, I think I'll leave it alone."
        const val AlreadyHaveSkull = "You already have the Ghost's skull."

        const val SoulSpotanim = "spotanim.restless_ghost_travel_spotanim"
        const val ApproachStartHeight = 163
        const val ApproachStartTime = 28
        const val ApproachEndTime = 51
        const val ApproachAngle = 15
        const val ApproachProgress = 32

        const val GhostLifespanTicks = 100
        const val SkeletonLifespanTicks = 100
        const val CoffinOpenTicks = 300
        const val FloorSkullTicks = 200

        const val GhostSpawnTimer = "timer.restless_ghost_coffin_ghost"
        const val SkeletonAttackTimer = "timer.restless_skeleton_attack"

        val RISEN_SKELETON = AttributeKey<Npc>(temp = true)

        const val SkeletonRiseTicks = 3

        const val GhostSpawnDelayTicks = 2

        const val UndergroundZ = 6400

        const val CoffinClosedTicks = 100

        const val SkeletonSpawnOffsetZ = 3
    }
}
