package org.rsmod.content.events.randomevents

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.table.RandomEventZonesRow
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

@Singleton
class RandomEventLocations
@Inject
constructor(private val locRepo: LocRepository, private val areas: AreaChecker) {
    private val bankBooth by lazy { "content.bank_booth".asRSCM(RSCMType.CONTENT) }
    private val bankChest by lazy { "content.bank_chest".asRSCM(RSCMType.CONTENT) }

    fun isDisallowed(coords: CoordGrid): Boolean {
        if (RegionRegistry.inWorkingArea(coords)) {
            return true
        }
        val zone = zones.firstOrNull { it.contains(coords) }
        if (zone != null) {
            return !zone.allowed
        }
        return isInBank(coords)
    }

    fun isInBank(coords: CoordGrid): Boolean {
        val minZoneX = (coords.x - BANK_BOOTH_RADIUS) shr ZONE_SHIFT
        val maxZoneX = (coords.x + BANK_BOOTH_RADIUS) shr ZONE_SHIFT
        val minZoneZ = (coords.z - BANK_BOOTH_RADIUS) shr ZONE_SHIFT
        val maxZoneZ = (coords.z + BANK_BOOTH_RADIUS) shr ZONE_SHIFT
        for (zoneX in minZoneX..maxZoneX) {
            for (zoneZ in minZoneZ..maxZoneZ) {
                val zone = ZoneKey(zoneX, zoneZ, coords.level)
                for (loc in locRepo.findAll(zone)) {
                    val content = ServerCacheManager.getObject(loc.id)?.contentGroup ?: continue
                    val radius =
                        when (content) {
                            bankBooth -> BANK_BOOTH_RADIUS
                            bankChest -> BANK_CHEST_RADIUS
                            else -> continue
                        }
                    if (loc.coords.chebyshevDistance(coords) <= radius) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private val zones by lazy {
        RandomEventZonesRow.all().map { row ->
            val area = row.area?.let { RSCM.getReverseMapping(RSCMType.AREA, it.id) }
            Zone(area, row.centre, row.radius ?: 0, row.allowed ?: false)
        }
    }

    private inner class Zone(
        val area: String?,
        val centre: CoordGrid?,
        val radius: Int,
        val allowed: Boolean,
    ) {
        fun contains(coords: CoordGrid): Boolean =
            when {
                area != null -> areas.inArea(area, coords)
                centre != null ->
                    coords.level == centre.level && coords.chebyshevDistance(centre) <= radius
                else -> false
            }
    }

    companion object {
        const val BANK_BOOTH_RADIUS = 7
        const val BANK_CHEST_RADIUS = 2
        private const val ZONE_SHIFT = 3
    }
}
