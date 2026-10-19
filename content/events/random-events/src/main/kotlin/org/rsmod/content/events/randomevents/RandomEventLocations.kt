package org.rsmod.content.events.randomevents

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
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
        if (coords.inGrandExchange()) {
            return true
        }
        if (NO_EVENT_AREAS.any { areas.inArea(it, coords) }) {
            return true
        }
        if (BANKS_WITH_EVENTS.any { areas.inArea(it, coords) }) {
            return false
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

    private fun CoordGrid.inGrandExchange(): Boolean =
        level == 0 &&
            chebyshevDistance(GRAND_EXCHANGE_CENTRE) <= GRAND_EXCHANGE_RADIUS

    companion object {
        const val BANK_BOOTH_RADIUS = 7
        const val BANK_CHEST_RADIUS = 2
        private const val ZONE_SHIFT = 3

        val GRAND_EXCHANGE_CENTRE = CoordGrid(3165, 3490, 0)
        const val GRAND_EXCHANGE_RADIUS = 13

        val NO_EVENT_AREAS =
            listOf(
                "area.godwars_dungeon",
                "area.ancient_cavern",
                "area.waterbirth_dungeon",
                "area.tapoyauik",
                "area.charred_island_dungeon",
            )

        val BANKS_WITH_EVENTS = listOf("area.ferox_enclave")
    }
}
