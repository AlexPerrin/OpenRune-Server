package org.rsmod.api.net.rsprot

import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcInfoPacket
import net.rsprot.protocol.game.outgoing.info.util.BuildArea
import net.rsprot.protocol.game.outgoing.info.util.PacketResult
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.map.RebuildRegionV2
import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone
import net.rsprot.protocol.game.outgoing.map.util.ReferenceZone
import net.rsprot.protocol.game.outgoing.misc.client.PacketGroupStart
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialFollows
import net.rsprot.protocol.message.OutgoingGameMessage
import net.rsprot.protocol.message.ZoneProt
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.client.ClientCycle
import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class RspCycle(
    private val session: Session<Player>,
    private val infos: Infos,
    private val regions: RegionRegistry,
) : ClientCycle {
    private val avatar = RspAvatarSync(infos)

    private var knownBuildArea: CoordGrid = CoordGrid.NULL

    /** Set by [forceSceneRebuild]; cleared once [rebuildArea] has acted on it. */
    private var pendingSceneRebuild: Boolean = false

    private var knownRegionUid: Int? = null

    private var cachedRegionZoneProvider: RebuildRegionV2.RebuildRegionZoneProvider? = null

    private var reconnectClient: RspClient? = null

    private var reconnectRebuild: OutgoingGameMessage? = null

    private val playerInfo
        get() = infos.playerInfo

    private val worldArea: Int
        get() = -1

    fun init(player: Player) {
        avatar.updateCoords(player)
        player.queueRebuildLogin()
    }

    fun reconnect(player: Player, client: RspClient) {
        reconnectClient = client
        avatar.updateCoords(player)
        knownBuildArea = player.buildArea
        forceSceneRebuild()
        player.regionRebuildPending = true
    }

    override fun forceSceneRebuild() {
        // A flag, not a cleared `knownBuildArea`: `NULL` there means "login rebuild already sent".
        pendingSceneRebuild = true
        knownRegionUid = null
        cachedRegionZoneProvider = null
    }

    private fun Player.queueRebuildLogin() {
        val rebuild = RebuildLoginV2(x shr 3, z shr 3, worldArea, playerInfo)
        queueRebuild(rebuild)
    }

    private fun queueRebuild(rebuild: OutgoingGameMessage) {
        if (reconnectClient != null) {
            reconnectRebuild = rebuild
            return
        }
        session.ctx.channel().sceneLoading = true
        session.queue(rebuild)
    }

    override fun update(player: Player) {
        reconnectClient?.group = ArrayList()
        avatar.updateMovement(player)
        player.rebuildArea()
        avatar.updateExtendedInfo(player)
    }

    override fun flush(player: Player) {
        val client = reconnectClient
        if (client != null) {
            flushReconnect(client)
            return
        }
        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets

        session.queue(rootPackets.activeWorld)
        session.queue(rootPackets.npcUpdateOrigin)
        session.queuePacketResult(rootPackets.worldEntityInfo)
        session.queuePacketResult(rootPackets.playerInfo)
        session.queueNpcInfoPacket(rootPackets.npcInfo)

        for (world in infoPackets.activeWorlds) {
            session.queue(world.activeWorld)
            session.queue(world.npcUpdateOrigin)
            session.queueNpcInfoPacket(world.npcInfo)
        }

        session.queue(rootPackets.activeWorld)
    }

    private fun flushReconnect(client: RspClient) {
        val written = client.group.orEmpty()
        client.group = null
        reconnectClient = null
        val (zoneUpdates, others) = written.partition { it.isZoneUpdate() }

        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets
        val group = ArrayList<OutgoingGameMessage>()
        group += rootPackets.activeWorld
        reconnectRebuild?.let { group += it }
        reconnectRebuild = null
        group += rootPackets.npcUpdateOrigin
        group += rootPackets.worldEntityInfo.getOrThrow()
        group += rootPackets.playerInfo.getOrThrow()
        group.addNpcInfo(rootPackets.npcInfo)
        for (world in infoPackets.activeWorlds) {
            group += world.activeWorld
            group += world.npcUpdateOrigin
            group.addNpcInfo(world.npcInfo)
        }
        if (infoPackets.activeWorlds.isNotEmpty()) {
            group += rootPackets.activeWorld
        }
        group += zoneUpdates
        session.queue(PacketGroupStart(group))
        for (message in others) {
            session.queue(message)
        }
    }

    private fun MutableList<OutgoingGameMessage>.addNpcInfo(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        add(result.getOrThrow())
    }

    private fun OutgoingGameMessage.isZoneUpdate(): Boolean =
        this is ZoneProt ||
            this is UpdateZoneFullFollows ||
            this is UpdateZonePartialFollows ||
            this is UpdateZonePartialEnclosed

    override fun release() {
        reconnectClient?.group = null
        reconnectClient = null
        reconnectRebuild = null
        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets

        releasePacketResult(rootPackets.worldEntityInfo)
        releasePacketResult(rootPackets.playerInfo)
        releaseNpcInfoPacket(rootPackets.npcInfo)
        for (world in infoPackets.activeWorlds) {
            releaseNpcInfoPacket(world.npcInfo)
        }
    }

    private fun <T : OutgoingGameMessage> Session<Player>.queuePacketResult(
        result: PacketResult<T>
    ) {
        queue(result.getOrThrow())
    }

    private fun Session<Player>.queueNpcInfoPacket(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        queuePacketResult(result)
    }

    private fun <T : OutgoingGameMessage> releasePacketResult(result: PacketResult<T>) {
        result.getOrThrow().safeRelease()
    }

    private fun releaseNpcInfoPacket(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        result.getOrThrow().safeRelease()
    }

    private fun Player.rebuildArea() {
        val staleBuildArea = pendingSceneRebuild || knownBuildArea != buildArea
        val recalcBuildArea = staleBuildArea && buildArea != CoordGrid.NULL
        if (recalcBuildArea) {
            val zone = ZoneKey.from(buildArea)
            val area = BuildArea(zone.x, zone.z)
            infos.updateRootBuildArea(area)
        }

        val forceRegionRebuild =
            regionRebuildPending && regionUid != null && knownBuildArea != CoordGrid.NULL
        if (forceRegionRebuild) {
            knownRegionUid = null
            cachedRegionZoneProvider = null
        }

        if (!recalcBuildArea && !forceRegionRebuild) {
            return
        }
        pendingSceneRebuild = false

        // Skip log-in rebuild as RebuildLogin is already sent, unless a login script moved the
        // player into a region: the client then needs the region's rebuild on the same tick.
        if (knownBuildArea == CoordGrid.NULL) {
            knownBuildArea = buildArea
            if (regionUid == null) {
                return
            }
        }

        if (regionUid == null) {
            val rebuild = RebuildNormalV2(x shr 3, z shr 3, worldArea)
            knownBuildArea = buildArea
            knownRegionUid = null
            cachedRegionZoneProvider = null
            queueRebuild(rebuild)
            return
        }

        val region = regions[coords]

        // The player's region uid should be reassigned every cycle before calling this function,
        // as such we should expect the region to always be valid at this point.
        checkNotNull(region) { "Unexpected invalid region: uid=$regionUid, coords=$coords" }

        // TODO: When implementing `net` module properly, figure out what the best way would be to
        //  "invalidate" the `cachedRebuildRegion` if the region is somehow altered. This can
        //  happen in regions such as the Gauntlet. (If we decide to keep this as a cached value
        //  as opposed to reconstructing it every time)
        if (regionUid != knownRegionUid) {
            cachedRegionZoneProvider = createRegionZoneProvider(region)
            knownRegionUid = regionUid
        }

        val zoneProvider = cachedRegionZoneProvider ?: createRegionZoneProvider(region)
        val rebuild = RebuildRegionV2(x shr 3, z shr 3, true, zoneProvider)
        knownBuildArea = buildArea
        cachedRegionZoneProvider = zoneProvider
        queueRebuild(rebuild)
    }

    private fun createRegionZoneProvider(region: Region): RebuildRegionV2.RebuildRegionZoneProvider {
        val regionZones = region.toZoneList()
        val rebuildZones = regionZones.associateWith { zone ->
            val copyZone = regions[zone]
            if (copyZone == RegionZoneCopy.NULL) {
                return@associateWith null
            }
            ReferenceZone(copyZone.packed)
        }
        val zoneProvider = RebuildRegionV2.RebuildRegionZoneProvider { zoneX, zoneZ, level ->
            rebuildZones[ZoneKey(zoneX, zoneZ, level)]?.let { ref ->
                RebuildRegionZone(zoneX = ref.zoneX, zoneZ = ref.zoneZ, level = ref.level, rotation = ref.rotation)
            }
        }
        return zoneProvider
    }
}
