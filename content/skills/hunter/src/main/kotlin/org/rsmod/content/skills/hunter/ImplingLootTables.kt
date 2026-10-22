package org.rsmod.content.skills.hunter

// Generated from the OSRS Wiki's impling jar pages (DropsLineReward, ?action=raw), 2026-10-16.
// Item ids are OSRS obj ids; names are the wiki's. Main tables sum to 1 ("Nothing" included).
// The lucky impling (a roll on a clue reward table) is not covered.

object ImplingLootTables {
    val tables: Map<Int, ImplingLootTable> =
        mapOf(
            // Baby impling jar; main table total 1
            11238 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(1755, 1, 1, false, 1, 10), // Chisel
                    ImplingDrop(1734, 1, 1, false, 1, 10), // Thread
                    ImplingDrop(1733, 1, 1, false, 1, 10), // Needle
                    ImplingDrop(946, 1, 1, false, 1, 10), // Knife
                    ImplingDrop(1985, 1, 1, false, 1, 10), // Cheese
                    ImplingDrop(2347, 1, 1, false, 1, 10), // Hammer
                    ImplingDrop(1759, 1, 1, false, 1, 10), // Ball of wool
                    ImplingDrop(319, 1, 1, false, 1, 10), // Anchovies
                    ImplingDrop.nothing(1, 10),
                    ImplingDrop(2007, 1, 1, false, 1, 100), // Spice
                    ImplingDrop(1779, 1, 1, false, 1, 100), // Flax
                    ImplingDrop(7170, 1, 1, false, 1, 100), // Mud pie
                    ImplingDrop(401, 1, 1, false, 1, 100), // Seaweed
                    ImplingDrop(1438, 1, 1, false, 1, 100), // Air talisman
                    ImplingDrop(2355, 1, 1, false, 1, 100), // Silver bar
                    ImplingDrop(1607, 1, 1, false, 1, 100), // Sapphire
                    ImplingDrop(1743, 1, 1, false, 1, 100), // Hard leather
                    ImplingDrop(379, 1, 1, false, 1, 100), // Lobster
                    ImplingDrop(1761, 1, 1, false, 1, 100), // Soft clay
                ),
                tertiary = listOf(
                    ImplingDrop(23182, 1, 1, false, 1, 50), // Clue scroll (beginner)
                    ImplingDrop(2677, 1, 1, false, 1, 100), // Clue scroll (easy)
                ),
            ),
            // Young impling jar; main table total 1
            11240 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(1539, 5, 5, false, 1, 10), // Steel nails
                    ImplingDrop(1523, 1, 1, false, 1, 10), // Lockpick
                    ImplingDrop(7936, 1, 1, false, 1, 10), // Pure essence
                    ImplingDrop(361, 1, 1, false, 1, 10), // Tuna
                    ImplingDrop(1901, 1, 1, false, 1, 10), // Chocolate slice
                    ImplingDrop(1353, 1, 1, false, 1, 10), // Steel axe
                    ImplingDrop(2293, 1, 1, false, 1, 10), // Meat pizza
                    ImplingDrop(453, 1, 1, false, 1, 10), // Coal
                    ImplingDrop(1777, 1, 1, false, 1, 10), // Bow string
                    ImplingDrop(231, 1, 1, false, 1, 100), // Snape grass
                    ImplingDrop(1761, 1, 1, false, 1, 100), // Soft clay
                    ImplingDrop(1097, 1, 1, false, 1, 100), // Studded chaps
                    ImplingDrop(1157, 1, 1, false, 1, 100), // Steel full helm
                    ImplingDrop(8778, 1, 1, false, 1, 100), // Oak plank
                    ImplingDrop(133, 1, 1, false, 1, 100), // Defence potion(3)
                    ImplingDrop(2359, 1, 1, false, 1, 100), // Mithril bar
                    ImplingDrop(855, 1, 1, false, 1, 100), // Yew longbow
                    ImplingDrop(7178, 1, 1, false, 1, 100), // Garden pie
                    ImplingDrop(247, 1, 1, false, 1, 100), // Jangerberries
                ),
                tertiary = listOf(
                    ImplingDrop(23182, 1, 1, false, 1, 25), // Clue scroll (beginner)
                    ImplingDrop(2677, 1, 1, false, 1, 50), // Clue scroll (easy)
                ),
            ),
            // Gourmet impling jar; main table total 1
            11242 to ImplingLootTable(
                preRoll = listOf(
                    ImplingDrop(23499, 1, 1, false, 1, 500), // Grubby key
                ),
                main = listOf(
                    ImplingDrop(361, 1, 1, false, 1, 5), // Tuna
                    ImplingDrop(365, 1, 1, false, 1, 10), // Bass
                    ImplingDrop(2011, 1, 1, false, 1, 10), // Curry
                    ImplingDrop(2327, 1, 1, false, 1, 10), // Meat pie
                    ImplingDrop(1897, 1, 1, false, 1, 10), // Chocolate cake
                    ImplingDrop(5004, 1, 1, false, 1, 10), // Frog spawn
                    ImplingDrop(2007, 1, 1, false, 1, 10), // Spice
                    ImplingDrop(5970, 1, 1, false, 1, 10), // Curry leaf
                    ImplingDrop(1885, 1, 1, false, 1, 100), // Ugthanki kebab
                    ImplingDrop(379, 4, 4, true, 1, 100), // Lobster
                    ImplingDrop(385, 3, 3, true, 1, 100), // Shark
                    ImplingDrop(7188, 1, 1, false, 1, 100), // Fish pie
                    ImplingDrop(5755, 1, 1, false, 1, 100), // Chef's delight
                    ImplingDrop(10136, 5, 5, true, 1, 100), // Rainbow fish
                    ImplingDrop(7178, 6, 6, true, 1, 100), // Garden pie
                    ImplingDrop(373, 3, 3, true, 1, 100), // Swordfish
                    ImplingDrop(5406, 1, 1, false, 1, 100), // Strawberries(5)
                    ImplingDrop(3144, 2, 2, true, 1, 100), // Cooked karambwan
                ),
                tertiary = listOf(
                    ImplingDrop(2677, 1, 1, false, 1, 25), // Clue scroll (easy)
                ),
            ),
            // Earth impling jar; main table total 1
            11244 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(1442, 1, 1, false, 1, 10), // Fire talisman
                    ImplingDrop(1440, 1, 1, false, 1, 10), // Earth talisman
                    ImplingDrop(5535, 1, 1, false, 1, 10), // Earth tiara
                    ImplingDrop(557, 32, 32, false, 1, 10), // Earth rune
                    ImplingDrop(447, 1, 1, false, 1, 10), // Mithril ore
                    ImplingDrop(1783, 4, 4, true, 1, 10), // Bucket of sand
                    ImplingDrop(237, 1, 1, false, 1, 10), // Unicorn horn
                    ImplingDrop(6032, 6, 6, true, 1, 10), // Compost
                    ImplingDrop(444, 1, 1, false, 1, 10), // Gold ore
                    ImplingDrop(2353, 1, 1, false, 1, 100), // Steel bar
                    ImplingDrop(1273, 1, 1, false, 1, 100), // Mithril pickaxe
                    ImplingDrop(5311, 2, 2, false, 1, 100), // Wildblood seed
                    ImplingDrop(5104, 2, 2, false, 1, 100), // Jangerberry seed
                    ImplingDrop(6034, 2, 2, true, 1, 100), // Supercompost
                    ImplingDrop(447, 3, 3, true, 1, 100), // Mithril ore
                    ImplingDrop(5294, 2, 2, false, 1, 100), // Harralander seed
                    ImplingDrop(453, 6, 6, true, 1, 100), // Coal
                    ImplingDrop(1605, 2, 2, true, 1, 100), // Emerald
                    ImplingDrop(1603, 1, 1, false, 1, 100), // Ruby
                ),
                tertiary = listOf(
                    ImplingDrop(2801, 1, 1, false, 1, 100), // Clue scroll (medium)
                ),
            ),
            // Essence impling jar; main table total 1
            11246 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(7936, 20, 20, true, 1, 10), // Pure essence
                    ImplingDrop(555, 30, 30, false, 1, 10), // Water rune
                    ImplingDrop(556, 30, 30, false, 1, 10), // Air rune
                    ImplingDrop(554, 50, 50, false, 1, 10), // Fire rune
                    ImplingDrop(558, 25, 25, false, 1, 10), // Mind rune
                    ImplingDrop(559, 28, 28, false, 1, 10), // Body rune
                    ImplingDrop(562, 4, 4, false, 1, 10), // Chaos rune
                    ImplingDrop(564, 4, 4, false, 1, 10), // Cosmic rune
                    ImplingDrop(1448, 1, 1, false, 1, 10), // Mind talisman
                    ImplingDrop(7936, 35, 35, true, 1, 100), // Pure essence
                    ImplingDrop(4699, 4, 4, false, 1, 100), // Lava rune
                    ImplingDrop(4698, 4, 4, false, 1, 100), // Mud rune
                    ImplingDrop(4697, 4, 4, false, 1, 100), // Smoke rune
                    ImplingDrop(4694, 4, 4, false, 1, 100), // Steam rune
                    ImplingDrop(560, 13, 13, false, 1, 100), // Death rune
                    ImplingDrop(563, 13, 13, false, 1, 100), // Law rune
                    ImplingDrop(565, 7, 7, false, 1, 100), // Blood rune
                    ImplingDrop(566, 11, 11, false, 1, 100), // Soul rune
                    ImplingDrop(561, 13, 13, false, 1, 100), // Nature rune
                ),
                tertiary = listOf(
                    ImplingDrop(2801, 1, 1, false, 1, 50), // Clue scroll (medium)
                ),
            ),
            // Eclectic impling jar; main table total 1
            11248 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(1273, 1, 1, false, 1, 10), // Mithril pickaxe
                    ImplingDrop(5970, 1, 1, false, 1, 10), // Curry leaf
                    ImplingDrop(231, 1, 1, false, 1, 10), // Snape grass
                    ImplingDrop(556, 30, 58, false, 1, 10), // Air rune
                    ImplingDrop(8778, 4, 4, true, 1, 10), // Oak plank
                    ImplingDrop(4527, 1, 1, false, 1, 10), // Empty candle lantern
                    ImplingDrop(444, 1, 1, false, 1, 10), // Gold ore
                    ImplingDrop(2357, 5, 5, true, 1, 10), // Gold bar
                    ImplingDrop(237, 1, 1, false, 1, 10), // Unicorn horn
                    ImplingDrop(1199, 1, 1, false, 1, 100), // Adamant kiteshield
                    ImplingDrop(2493, 1, 1, false, 1, 100), // Blue d'hide chaps
                    ImplingDrop(10083, 1, 1, false, 1, 100), // Red spiky vambraces
                    ImplingDrop(1213, 1, 1, false, 1, 100), // Rune dagger
                    ImplingDrop(1391, 1, 1, false, 1, 100), // Battlestaff
                    ImplingDrop(449, 10, 10, true, 1, 100), // Adamantite ore
                    ImplingDrop(5759, 2, 2, true, 1, 100), // Slayer's respite
                    ImplingDrop(7208, 1, 1, false, 1, 100), // Wild pie
                    ImplingDrop(5321, 3, 3, false, 1, 100), // Watermelon seed
                    ImplingDrop(1601, 1, 1, false, 1, 100), // Diamond
                ),
                tertiary = listOf(
                    ImplingDrop(2801, 1, 1, false, 1, 25), // Clue scroll (medium)
                ),
            ),
            // Nature impling jar; main table total 1
            11250 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(5100, 1, 1, false, 1, 10), // Limpwurt seed
                    ImplingDrop(5104, 1, 1, false, 1, 10), // Jangerberry seed
                    ImplingDrop(5281, 1, 1, false, 1, 10), // Belladonna seed
                    ImplingDrop(5294, 1, 1, false, 1, 10), // Harralander seed
                    ImplingDrop(6016, 1, 1, false, 1, 10), // Cactus spine
                    ImplingDrop(1513, 1, 1, false, 1, 10), // Magic logs
                    ImplingDrop(253, 4, 4, true, 1, 10), // Tarromin
                    ImplingDrop(5974, 1, 1, false, 1, 10), // Coconut
                    ImplingDrop(5297, 1, 1, false, 1, 10), // Irit seed
                    ImplingDrop(5286, 1, 1, false, 1, 100), // Curry tree seed
                    ImplingDrop(5285, 1, 1, false, 1, 100), // Orange tree seed
                    ImplingDrop(3000, 1, 1, false, 1, 100), // Snapdragon
                    ImplingDrop(5299, 1, 1, false, 1, 100), // Kwuarm seed
                    ImplingDrop(5298, 5, 5, false, 1, 100), // Avantoe seed
                    ImplingDrop(5313, 1, 1, false, 1, 100), // Willow seed
                    ImplingDrop(5304, 1, 1, false, 1, 100), // Torstol seed
                    ImplingDrop(5295, 1, 1, false, 1, 100), // Ranarr seed
                    ImplingDrop(269, 2, 2, true, 1, 100), // Torstol
                    ImplingDrop(5303, 1, 1, false, 1, 100), // Dwarf weed seed
                ),
                tertiary = listOf(
                    ImplingDrop(2722, 1, 1, false, 1, 100), // Clue scroll (hard)
                ),
            ),
            // Magpie impling jar; main table total 1
            11252 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(1747, 6, 6, true, 2, 21), // Black dragonhide
                    ImplingDrop(1700, 3, 3, true, 1, 21), // Diamond amulet
                    ImplingDrop(1731, 3, 3, true, 1, 21), // Amulet of power
                    ImplingDrop(2568, 3, 3, true, 1, 21), // Ring of forging
                    ImplingDrop(3391, 1, 1, false, 1, 21), // Splitbark gauntlets
                    ImplingDrop(4097, 1, 1, false, 1, 21), // Mystic boots
                    ImplingDrop(4095, 1, 1, false, 1, 21), // Mystic gloves
                    ImplingDrop(1347, 1, 1, false, 1, 21), // Rune warhammer
                    ImplingDrop(2570, 4, 4, true, 1, 21), // Ring of life
                    ImplingDrop(1185, 1, 1, false, 1, 21), // Rune sq shield
                    ImplingDrop(1215, 1, 1, false, 1, 21), // Dragon dagger
                    ImplingDrop(5541, 1, 1, false, 1, 21), // Nature tiara
                    ImplingDrop(2363, 2, 2, true, 1, 21), // Runite bar
                    ImplingDrop(1601, 4, 4, true, 1, 21), // Diamond
                    ImplingDrop(5287, 1, 1, false, 1, 21), // Pineapple seed
                    ImplingDrop(2550, 3, 3, true, 1, 21), // Ring of recoil
                    ImplingDrop(987, 1, 1, false, 1, 21), // Loop half of key
                    ImplingDrop(985, 1, 1, false, 1, 21), // Tooth half of key
                    ImplingDrop(5300, 1, 1, false, 1, 21), // Snapdragon seed
                    ImplingDrop(993, 1, 1, false, 1, 21), // Sinister key
                ),
                tertiary = listOf(
                    ImplingDrop(2722, 1, 1, false, 1, 50), // Clue scroll (hard)
                ),
            ),
            // Ninja impling jar; main table total 1
            11254 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(6328, 1, 1, false, 1, 19), // Snakeskin boots
                    ImplingDrop(3385, 1, 1, false, 1, 19), // Splitbark helm
                    ImplingDrop(4097, 1, 1, false, 1, 19), // Mystic boots
                    ImplingDrop(1113, 1, 1, false, 1, 19), // Rune chainbody
                    ImplingDrop(4095, 1, 1, false, 1, 19), // Mystic gloves
                    ImplingDrop(6313, 1, 1, false, 1, 19), // Opal machete
                    ImplingDrop(3101, 1, 1, false, 1, 19), // Rune claws
                    ImplingDrop(1333, 1, 1, false, 1, 19), // Rune scimitar
                    ImplingDrop(5680, 1, 1, false, 1, 19), // Dragon dagger(p+)
                    ImplingDrop(892, 70, 70, false, 1, 19), // Rune arrow
                    ImplingDrop(811, 70, 70, false, 1, 19), // Rune dart
                    ImplingDrop(868, 40, 40, false, 1, 19), // Rune knife
                    ImplingDrop(805, 50, 50, false, 1, 19), // Rune thrownaxe
                    ImplingDrop(9342, 2, 2, false, 1, 19), // Onyx bolts
                    ImplingDrop(9194, 4, 4, false, 1, 19), // Onyx bolt tips
                    ImplingDrop(1747, 10, 10, true, 1, 19), // Black dragonhide
                    ImplingDrop(139, 4, 4, true, 1, 19), // Prayer potion(3)
                    ImplingDrop(5937, 4, 4, true, 1, 19), // Weapon poison(+)
                    ImplingDrop(6155, 3, 3, true, 1, 19), // Dagannoth hide
                ),
                tertiary = listOf(
                    ImplingDrop(2722, 1, 1, false, 1, 25), // Clue scroll (hard)
                ),
            ),
            // Dragon impling jar; main table total 1
            11256 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(9193, 10, 30, false, 1, 19), // Dragonstone bolt tips
                    ImplingDrop(9193, 36, 36, false, 1, 19), // Dragonstone bolt tips
                    ImplingDrop(4093, 1, 1, false, 1, 19), // Mystic robe bottom
                    ImplingDrop(1704, 3, 3, true, 1, 19), // Amulet of glory
                    ImplingDrop(1702, 2, 2, true, 1, 19), // Dragonstone amulet
                    ImplingDrop(11212, 100, 250, false, 1, 19), // Dragon arrow
                    ImplingDrop(9341, 10, 40, false, 1, 19), // Dragonstone bolts
                    ImplingDrop(1305, 1, 1, false, 1, 19), // Dragon longsword
                    ImplingDrop(5698, 3, 3, true, 1, 19), // Dragon dagger(p++)
                    ImplingDrop(11230, 100, 250, false, 1, 19), // Dragon dart
                    ImplingDrop(1615, 3, 3, true, 1, 19), // Dragonstone
                    ImplingDrop(11232, 100, 350, false, 1, 19), // Dragon dart tip
                    ImplingDrop(11237, 100, 350, false, 1, 19), // Dragon arrowtips
                    ImplingDrop(19582, 25, 35, false, 1, 19), // Dragon javelin tips
                    ImplingDrop(534, 100, 300, true, 1, 19), // Babydragon bones
                    ImplingDrop(536, 50, 100, true, 1, 19), // Dragon bones
                    ImplingDrop(5316, 1, 1, false, 1, 19), // Magic seed
                    ImplingDrop(5300, 6, 6, false, 1, 19), // Snapdragon seed
                    ImplingDrop(7218, 15, 15, true, 1, 19), // Summer pie
                ),
                tertiary = listOf(
                    ImplingDrop(12073, 1, 1, false, 1, 50), // Clue scroll (elite)
                ),
            ),
            // Crystal impling jar; main table total 1
            23768 to ImplingLootTable(
                preRoll = emptyList(),
                main = listOf(
                    ImplingDrop(1731, 5, 7, true, 1, 18), // Amulet of power
                    ImplingDrop(23661, 1, 1, false, 1, 18), // Crystal acorn
                    ImplingDrop(23962, 30, 40, false, 1, 18), // Crystal shard
                    ImplingDrop(1702, 1, 1, false, 1, 18), // Dragonstone amulet
                    ImplingDrop(1615, 2, 2, true, 1, 18), // Dragonstone
                    ImplingDrop(9191, 50, 125, false, 1, 18), // Ruby bolt tips
                    ImplingDrop(9194, 6, 10, false, 1, 18), // Onyx bolt tips
                    ImplingDrop(44, 150, 250, false, 1, 18), // Rune arrowtips
                    ImplingDrop(892, 400, 750, false, 1, 18), // Rune arrow
                    ImplingDrop(19580, 20, 60, false, 1, 18), // Rune javelin tips
                    ImplingDrop(824, 25, 75, false, 1, 18), // Rune dart tip
                    ImplingDrop(811, 50, 100, false, 1, 18), // Rune dart
                    ImplingDrop(11232, 10, 15, false, 1, 18), // Dragon dart tip
                    ImplingDrop(1215, 2, 2, true, 1, 18), // Dragon dagger
                    ImplingDrop(1333, 3, 6, true, 1, 18), // Rune scimitar
                    ImplingDrop(534, 75, 125, true, 1, 18), // Babydragon bones
                    ImplingDrop(5295, 3, 8, false, 1, 18), // Ranarr seed
                    ImplingDrop(5315, 1, 1, false, 1, 18), // Yew seed
                ),
                tertiary = listOf(
                    ImplingDrop(12073, 1, 1, false, 1, 50), // Clue scroll (elite)
                    ImplingDrop(23943, 1, 1, false, 1, 128), // Elven signet
                ),
            ),
        )
}
