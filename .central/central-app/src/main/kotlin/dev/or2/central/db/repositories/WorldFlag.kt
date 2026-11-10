package dev.or2.central.db.repositories

enum class WorldFlag(
    val mask: Int,
    val runeliteTypeName: String? = null,
) {
    MEMBERS(0x1, "MEMBERS"),
    PVPWORLD(0x4, "PVP"),
    BOUNTYWORLD(0x20, "BOUNTY"),
    PVPARENA(0x40, "PVP_ARENA"),
    HIGHLEVELONLY_1500(0x80, "SKILL_TOTAL"),
    SPEEDRUN(0x100, "QUEST_SPEEDRUNNING"),
    EXTRAHARDWILDERNESS(0x400, "HIGH_RISK"),
    LASTMANSTANDING(0x4000, "LAST_MAN_STANDING"),
    BETA_WORLD(0x10000, "BETA_WORLD"),
    LEGACY_ONLY(0x400000, "LEGACY_ONLY"),
    EOC_ONLY(0x800000, "EOC_ONLY"),
    NOSAVE_MODE(0x2000000, "NOSAVE_MODE"),
    TOURNAMENT_WORLD(0x4000000, "TOURNAMENT"),
    FRESHSTART(0x8000000, "FRESH_START_WORLD"),
    DEADMAN(0x20000000, "DEADMAN"),
    SEASONAL(0x40000000, "SEASONAL"),
    ;

    companion object {
        private val runeliteOrderedFlags: List<WorldFlag> =
            listOf(
                MEMBERS, PVPWORLD, BOUNTYWORLD, PVPARENA, HIGHLEVELONLY_1500, SPEEDRUN,
                EXTRAHARDWILDERNESS, LASTMANSTANDING, BETA_WORLD, LEGACY_ONLY, EOC_ONLY,
                NOSAVE_MODE, TOURNAMENT_WORLD, FRESHSTART, DEADMAN, SEASONAL,
            )

        fun runeliteTypeNamesFromMask(mask: Int): List<String> =
            runeliteOrderedFlags.mapNotNull { flag ->
                if ((flag.mask and mask) == 0) null else flag.runeliteTypeName
            }

        fun maskFromCsv(csv: String): Int {
            val trimmed = csv.trim()
            if (trimmed.isEmpty()) return 0
            return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.fold(0) { acc, token ->
                val key = token.uppercase()
                val flag =
                    entries.find { it.name == key }
                        ?: entries.find { it.runeliteTypeName?.uppercase() == key }
                        ?: throw IllegalArgumentException("Unknown worlds flag: $token")
                acc or flag.mask
            }
        }
    }
}
