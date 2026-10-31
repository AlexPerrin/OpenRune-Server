package org.rsmod.api.net.rsprot

import jakarta.inject.Inject
import net.rsprot.protocol.api.NetworkService
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// shortcut: test server only (local/*): one static raft off Port Sarim, no riding or steering;
// drop it once upstream has world entities (OpenRune PR #182).
class TestWorldEntityScript
@Inject
constructor(private val service: NetworkService<Player>, private val regionRepo: RegionRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.Startup> { spawnRaft() }
    }

    private fun spawnRaft() {
        val template =
            RegionTemplate.create {
                copyAllLevels(RAFT_TEMPLATE_ZONE_X, RAFT_TEMPLATE_ZONE_Z) {
                    zoneWidth = 1
                    zoneLength = 1
                }
            }
        val region = checkNotNull(regionRepo.add(template)) { "No region for the test raft." }
        regionRepo.protect(region)
        val zone = region.southWestZone
        service.worldEntityAvatarFactory.alloc(
            index = RAFT_INDEX,
            id = RAFT_WORLDENTITY,
            ownerIndex = -1,
            sizeX = 1,
            sizeZ = 1,
            southWestZoneX = zone.x,
            southWestZoneZ = zone.z,
            minLevel = 0,
            maxLevel = 3,
            fineX = RAFT_X * 128 + 64,
            fineZ = RAFT_Z * 128 + 64,
            projectedLevel = 0,
            activeLevel = 1,
            angle = 0,
        )
        TestWorldEntities.regions[RAFT_INDEX] = region
    }

    private companion object {
        const val RAFT_INDEX = 1
        const val RAFT_WORLDENTITY = 1
        const val RAFT_TEMPLATE_ZONE_X = 480
        const val RAFT_TEMPLATE_ZONE_Z = 807
        const val RAFT_X = 3053
        const val RAFT_Z = 3193
    }
}

internal object TestWorldEntities {
    val regions = HashMap<Int, Region>()
}
