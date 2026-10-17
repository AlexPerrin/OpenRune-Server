package org.rsmod.content.interfaces.diary.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Achievement diary journals (the journal scroll opened from the Achievement Diaries side tab).
 * [AchievementDiariesTable] has one row per area: its index in enum 595 (the side tab's order) and
 * the heading line of its journal. [AchievementDiaryTasksTable] has the tasks in the OSRS Wiki's
 * order per tier (0 easy .. 3 elite). Task wording follows the in-game journal where RuneLite's
 * Diary Requirements plugin quotes it, otherwise the wiki's task tables.
 */
object AchievementDiariesTable {
    const val AREA = 0
    const val HEADING = 1

    const val LUMBRIDGE = 6
    const val VARROCK = 8
    const val ARDOUGNE = 1
    const val DESERT = 5
    const val FALADOR = 2
    const val FREMENNIK = 3
    const val KANDARIN = 4
    const val KARAMJA = 0
    const val MORYTANIA = 7
    const val WESTERN = 10
    const val WILDERNESS = 9
    const val KOUREND = 11

    private val DIARIES =
        listOf(
            Triple("lumbridge", LUMBRIDGE, "Lumbridge & Draynor Tasks"),
            Triple("varrock", VARROCK, "Varrock Tasks"),
            Triple("ardougne", ARDOUGNE, "Ardougne Area Tasks"),
            Triple("desert", DESERT, "Desert Tasks"),
            Triple("falador", FALADOR, "Falador Area Tasks"),
            Triple("fremennik", FREMENNIK, "Fremennik Tasks"),
            Triple("kandarin", KANDARIN, "Kandarin Tasks"),
            Triple("karamja", KARAMJA, "Karamja Area Tasks"),
            Triple("morytania", MORYTANIA, "Morytania Tasks"),
            Triple("western", WESTERN, "Western Area Tasks"),
            Triple("wilderness", WILDERNESS, "Wilderness Area Tasks"),
            Triple("kourend", KOUREND, "Kourend & Kebos Tasks"),
        )

    fun diaries() =
        dbTable("dbtable.achievement_diaries", serverOnly = true) {
            column("area", AREA, VarType.INT)
            column("heading", HEADING, VarType.STRING)

            for ((key, area, heading) in DIARIES) {
                row("dbrow.achievement_diary_$key") {
                    column(AREA, area)
                    column(HEADING, heading)
                }
            }
        }
}

object AchievementDiaryTasksTable {
    const val AREA = 0
    const val TIER = 1
    const val TEXT = 2

    private val LUMBRIDGE =
        listOf(
            listOf(
                "Complete a lap of the Draynor Village agility course.",
                "Slay a Cave bug beneath Lumbridge Swamp.",
                "Have Sedridor teleport you to the Essence Mine.",
                "Craft some water runes from Essence.",
                "Learn your age from Hans in Lumbridge.",
                "Pickpocket a man or woman in Lumbridge.",
                "Chop and burn some oak logs in Lumbridge.",
                "Kill a Zombie in Draynor Sewers.",
                "Catch some Anchovies in Al Kharid.",
                "Bake some Bread on the Lumbridge kitchen range.",
                "Mine some Iron ore at the Al Kharid mine.",
                "Enter the H.A.M. Hideout.",
            ),
            listOf(
                "Complete a lap of the Al Kharid agility course.",
                "Grapple across the River Lum.",
                "Purchase an upgraded device from Ava.",
                "Travel to the Wizards' Tower by Fairy ring.",
                "Cast the teleport to Lumbridge spell.",
                "Catch some Salmon in Lumbridge.",
                "Craft a coif in the Lumbridge cow pen.",
                "Chop some willow logs in Draynor Village.",
                "Pickpocket Martin the Master Gardener.",
                "Get a slayer task from Chaeldar.",
                "Catch an Essence or Eclectic impling in Puro-Puro.",
                "Craft some Lava runes at the fire altar in Al Kharid.",
            ),
            listOf(
                "Cast Bones to Peaches in Al Kharid palace.",
                "Squeeze past the jutting wall on your way to the cosmic altar.",
                "Craft 56 Cosmic runes simultaneously from Essence without the use of Extracts.",
                "Travel from Lumbridge to Edgeville on a Waka Canoe.",
                "Collect at least 100 Tears of Guthix in one visit.",
                "Take the train from Dorgesh-Kaan to Keldagrim.",
                "Purchase some Barrows gloves from the Lumbridge bank chest.",
                "Pick some Belladonna from the farming patch at Draynor Manor.",
                "Light your mining helmet in the Lumbridge castle basement.",
                "Recharge your prayer at the Emir's Arena with Smite activated.",
                "Craft, string and enchant an Amulet of Power in Lumbridge.",
            ),
            listOf(
                "Steal from a Dorgesh-Kaan rich chest.",
                "Grapple across a pylon on the Dorgesh-Kaan Agility Course.",
                "Chop some magic logs at the Mage Training Arena.",
                "Smith an Adamant platebody down Draynor sewer.",
                "Craft 140 or more Water runes simultaneously from Essence without the use of Extracts.",
                "Perform the Quest cape emote in the Wise Old Man's house.",
            ),
        )

    private val VARROCK =
        listOf(
            listOf(
                "Browse Thessalia's store.",
                "Have Aubury teleport you to the Essence mine.",
                "Mine some Iron in the south east mining patch near Varrock.",
                "Make a normal plank at the Sawmill.",
                "Enter the second level of the Stronghold of Security.",
                "Jump over the fence south of Varrock.",
                "Chop down a dying tree in the Lumber Yard.",
                "Buy a newspaper.",
                "Give a dog a bone!",
                "Spin a bowl on the pottery wheel and fire it in the oven in Barb Village.",
                "Speak to Haig Halen after obtaining at least 50 Kudos.",
                "Craft some Earth runes from Essence.",
                "Catch some trout in the River Lum at Barbarian Village.",
                "Steal from the Tea stall in Varrock.",
            ),
            listOf(
                "Have the Apothecary in Varrock make you a strength potion.",
                "Enter the Champions' Guild.",
                "Select a colour for your kitten.",
                "Use the spirit tree north of Varrock.",
                "Perform the 4 emotes from the Stronghold of Security.",
                "Enter the Tolna dungeon after completing A Soul's Bane.",
                "Teleport to the digsite using a Digsite pendant.",
                "Cast the teleport to Varrock spell.",
                "Get a Slayer task from Vannaka.",
                "Make 20 Mahogany Planks in one go.",
                "Pick a White tree fruit.",
                "Use the balloon to travel from Varrock.",
                "Complete a lap of the Varrock Agility course.",
            ),
            listOf(
                "Trade furs with the Fancy Dress Seller for a spottier cape and equip it.",
                "Speak to Orlando Smith when you have achieved 153 Kudos.",
                "Make a Waka Canoe near Edgeville.",
                "Teleport to Paddewwa.",
                "Teleport to Barbarian Village with a skull sceptre.",
                "Chop some yew logs in Varrock and burn them at the top of the Varrock church.",
                "Have the Varrock estate agent decorate your house with Fancy Stone.",
                "Collect at least 2 yew roots from the Tree patch in Varrock Palace.",
                "Pray at the altar in Varrock palace with Smite active.",
                "Squeeze through the obstacle pipe in Edgeville dungeon.",
            ),
            listOf(
                "Create a super combat potion in Varrock west bank.",
                "Use Lunar magic to make 20 mahogany planks at the Lumberyard.",
                "Bake a summer pie in the Cooking Guild.",
                "Smith and fletch ten rune darts within Varrock.",
                "Craft 100 or more earth runes simultaneously from Essence without the use of Extracts.",
            ),
        )

    private val ARDOUGNE =
        listOf(
            listOf(
                "Have Wizard Cromperty teleport you to the Rune Essence mine.",
                "Steal a cake from the Ardougne market stalls.",
                "Sell Silk to Silk trader in Ardougne for 60 coins each.",
                "Use the altar in East Ardougne's church.",
                "Go out fishing on the Fishing Trawler",
                "Enter the Combat Training Camp north of W. Ardougne.",
                "Have Tindel Marchant identify a Rusted Sword for you.",
                "Use the Ardougne Lever to teleport to the Wilderness.",
                "View Aleck's Hunter Emporium in Yanille.",
                "Check what pets you have Insured with Probita in Ardougne.",
            ),
            listOf(
                "Enter the Unicorn pen in Ardougne zoo using Fairy rings.",
                "Grapple over Yanille's south wall.",
                "Harvest some strawberries from the Ardougne farming patch.",
                "Cast the Ardougne Teleport spell.",
                "Travel to Castlewars by Hot Air Balloon.",
                "Claim buckets of sand from Bert in Yanille.",
                "Catch any fish on the Fishing Platform.",
                "Pickpocket the master farmer north of Ardougne.",
                "Collect some Nightshade from the Skavid Caves.",
                "Kill a swordchick in the Tower of Life.",
                "Equip Iban's upgraded staff or upgrade an Iban staff.",
                "Visit the Island East of the Necromancer's tower.",
            ),
            listOf(
                "Recharge some Jewellery at the Totem in the Legends Guild.",
                "Enter the Magic Guild.",
                "Steal from a chest in Ardougne Castle.",
                "Have a zookeeper put you in Ardougne Zoo's monkey cage.",
                "Teleport to the Watchtower.",
                "Catch a Red Salamander.",
                "Check the health of a Palm tree near tree gnome village.",
                "Pick some Poison Ivy berries from the patch south of Ardougne.",
                "Smith a Mithril platebody near Ardougne.",
                "Enter your POH from Yanille.",
                "Smith a Dragon sq shield in West Ardougne.",
                "Craft some Death runes from Essence.",
            ),
            listOf(
                "Catch a Manta ray in the Fishing Trawler and cook it in Port Khazard.",
                "Picklock the door to the basement of Yanille Agility Dungeon.",
                "Pickpocket a Hero.",
                "Make a rune crossbow yourself from scratch within Witchaven or Yanille.",
                "Imbue a Salve amulet at Nightmare Zone, or equip a Salve amulet that was imbued there.",
                "Pick some Torstol from the patch north of Ardougne.",
                "Complete a lap of Ardougne's rooftop agility course.",
                "Cast Ice Barrage on another player within Castle Wars.",
            ),
        )

    private val DESERT =
        listOf(
            listOf(
                "Catch a Golden Warbler.",
                "Mine 5 clay in the north-eastern desert.",
                "Enter the Kalphite Hive.",
                "Enter the Desert with a set of Desert robes equipped.",
                "Kill a Vulture",
                "Have the Nardah Herbalist clean a Herb for you.",
                "Collect 5 Potato Cactus from the Kalphite Hive.",
                "Sell some artefacts to Simon Templeton.",
                "Open the Sarcophagus in the first room of Pyramid Plunder.",
                "Cut a desert cactus open to fill a waterskin.",
                "Travel from the Shantay Pass to Pollnivneach by Magic Carpet.",
            ),
            listOf(
                "Climb to the summit of the Agility Pyramid.",
                "Slay a desert lizard.",
                "Catch an Orange Salamander.",
                "Steal a feather from the Desert Phoenix.",
                "Travel to Uzer via Magic Carpet.",
                "Travel to the Desert via Eagle.",
                "Pray at the Elidinis statuette in Nardah.",
                "Create a combat potion in the desert.",
                "Teleport to Enakhra's Temple with the Camulet.",
                "Visit the Genie.",
                "Teleport to Pollnivneach with a redirected teleport to house tablet.",
                "Chop some Teak logs near Uzer.",
            ),
            listOf(
                "Knock out and pickpocket a Menaphite Thug.",
                "Mine some Granite.",
                "Refill your waterskins in the Desert using Lunar magic.",
                "Kill the Kalphite Queen.",
                "Complete a lap of the Pollnivneach agility course.",
                "Slay a Dust Devil in the desert cave with a Slayer helmet equipped.",
                "Activate Ancient Magicks at the altar in the Jaldraocht Pyramid.",
                "Defeat a Locust Rider with Keris.",
                "Burn some yew logs on the Nardah Mayor's balcony.",
                "Create a Mithril Platebody in Nardah.",
            ),
            listOf(
                "Bake a wild pie at the Nardah Clay Oven.",
                "Cast Ice Barrage against a foe in the Desert.",
                "Fletch some Dragon darts at the Bedabin Camp.",
                "Speak to the KQ head in your POH.",
                "Steal from the Grand Gold Chest in the final room of Pyramid Plunder.",
                "Restore at least 85 Prayer points when praying at the Altar in Sophanem.",
            ),
        )

    private val FALADOR =
        listOf(
            listOf(
                "Find out what your family crest is from Sir Renitee.",
                "Climb over the western Falador wall.",
                "Browse Sarah's farm shop.",
                "Get a Haircut or a Shave from the Falador Hairdresser.",
                "Fill a bucket from the pump north of Falador west bank.",
                "Kill a duck in Falador Park.",
                "Make a mind tiara.",
                "Take the boat to Entrana.",
                "Repair a broken strut in the Motherlode Mine.",
                "Claim a security book from the Security guard at Port Sarim jail.",
                "Smith some Blurite Limbs on Doric's Anvil.",
            ),
            listOf(
                "Light a Bullseye lantern at the Chemist's in Rimmington.",
                "Telegrab some Wine of Zamorak at the Chaos Temple by the Wilderness.",
                "Unlock the Crystal chest in Taverley.",
                "Place a Scarecrow in the Falador farming patch.",
                "Kill a Mogre at Mudskipper Point.",
                "Visit the Port Sarim Rat Pits.",
                "Grapple up and then jump off the north Falador wall.",
                "Pickpocket a Falador guard.",
                "Pray at the Altar of Guthix in Taverley whilst wearing full Initiate.",
                "Mine some Gold ore at the Crafting Guild.",
                "Squeeze through the crevice in the Dwarven mines.",
                "Chop and burn some Willow logs in Taverley",
                "Craft a fruit basket on the Falador Farm loom.",
                "Teleport to Falador.",
            ),
            listOf(
                "Craft 140 Mind runes simultaneously from Essence without the use of Extracts.",
                "Change your family crest to the Saradomin symbol.",
                "Kill the Giant Mole beneath Falador Park.",
                "Kill a Skeletal Wyvern in the Asgarnia Ice Dungeon.",
                "Complete a lap of the Falador rooftop agility course.",
                "Enter the mining guild wearing a Prospector helmet.",
                "Kill the Blue Dragon under the Heroes' Guild.",
                "Crack a wall safe within Rogues Den.",
                "Recharge your prayer in the Port Sarim church while wearing full Proselyte.",
                "Enter the Warriors' Guild.",
                "Equip a dwarven helmet within the dwarven mines.",
            ),
            listOf(
                "Craft 252 Air Runes simultaneously from Essence without the use of Extracts.",
                "Purchase a White 2h Sword from Sir Vyvin.",
                "Find at least 3 magic roots at once when digging up your magic tree in Falador.",
                "Perform a skillcape or quest cape emote at the top of Falador Castle.",
                "Jump over the strange floor in Taverley dungeon.",
                "Mix a Saradomin brew in Falador east bank.",
            ),
        )

    private val FREMENNIK =
        listOf(
            listOf(
                "Catch a Cerulean twitch.",
                "Change your boots at Yrsa's Shoe Store.",
                "Kill 5 Rock crabs.",
                "Craft a tiara from scratch in Rellekka.",
                "Browse the Stonemasons shop.",
                "Collect 5 Snape grass on Waterbirth Island.",
                "Steal from the Keldagrim crafting or baker's stall.",
                "Fill a bucket with water at the Rellekka well.",
                "Enter the Troll Stronghold.",
                "Chop and burn some oak logs in the Fremennik Province.",
            ),
            listOf(
                "Slay a Brine rat.",
                "Travel to the Snowy Hunter Area via Eagle.",
                "Mine some coal in Rellekka.",
                "Steal from the Rellekka Fish stalls.",
                "Travel to Miscellania by Fairy ring.",
                "Catch a Snowy knight.",
                "Pick up your Pet Rock from your POH Menagerie.",
                "Visit the Lighthouse from Waterbirth island.",
                "Mine some gold at the Arzinian mine.",
            ),
            listOf(
                "Teleport to Trollheim.",
                "Catch a Sabre-toothed Kyatt.",
                "Mix a super defence potion in the Fremennik province.",
                "Steal from the Keldagrim Gem Stall.",
                "Craft a Fremennik shield on Neitiznot.",
                "Mine 5 Adamantite ores on Jatizso.",
                "Obtain 100% support from your kingdom subjects.",
                "Teleport to Waterbirth Island.",
                "Obtain the Blast Furnace Foreman's permission to use the Blast Furnace for free.",
            ),
            listOf(
                "Kill each of the Dagannoth Kings.",
                "Craft 56 astral runes simultaneously from Essence without the use of Extracts.",
                "Create a dragonstone amulet in the Neitiznot furnace.",
                "Complete a lap of the Rellekka agility course.",
                "Kill the generals of Armadyl, Bandos, Saradomin and Zamorak in the God Wars Dungeon.",
                "Slay a Spiritual mage within the Godwars Dungeon.",
            ),
        )

    private val KANDARIN =
        listOf(
            listOf(
                "Catch a Mackerel at Catherby.",
                "Buy a candle from the Chandler in Catherby.",
                "Collect five Flax from the Seers' flax fields.",
                "Play the Organ in Seers' church.",
                "Plant some Jute seeds in the patch north of McGrubor's Wood.",
                "Have Galahad make you a cup of tea.",
                "Defeat one of each elemental in the workshop.",
                "Get a pet fish from Harry in Catherby.",
                "Buy a Stew from the Seers' pub.",
                "Speak to Sherlock.",
                "Cross the Coal truck log shortcut.",
            ),
            listOf(
                "Complete a lap of the Barbarian agility course.",
                "Create a Super Antipoison potion from scratch in the Seers/Catherby Area.",
                "Enter the Ranging guild.",
                "Use the grapple shortcut to get from the water obelisk to Catherby shore.",
                "Catch and cook a Bass in Catherby.",
                "Teleport to Camelot.",
                "String a Maple shortbow in Seers' Village bank.",
                "Pick some Limpwurt root from the farming patch in Catherby.",
                "Create a Mind helmet.",
                "Kill a Fire Giant inside Baxtorian Waterfall.",
                "Complete a wave of Barbarian Assault.",
                "Steal from the chest in Hemenster.",
                "Travel to McGrubor's Wood by Fairy Ring.",
                "Mine some coal near the coal trucks.",
            ),
            listOf(
                "Catch a Leaping Sturgeon.",
                "Complete a lap of the Seers' Village agility course.",
                "Create a Yew Longbow from scratch around Seers' Village.",
                "Enter the Seers' Village courthouse with piety turned on.",
                "Charge a Water Orb.",
                "Burn some Maple logs with a bow in Seers' Village.",
                "Kill a Shadow Hound in the Shadow dungeon.",
                "Kill a Mithril Dragon.",
                "Purchase and equip a granite body from Barbarian Assault.",
                "Have the Seers' estate agent decorate your house with Fancy Stone.",
                "Smith an Adamant spear at Otto's Grotto.",
            ),
            listOf(
                "Read the Blackboard at Barbarian Assault after reaching level 5 in every role.",
                "Pick some Dwarf weed from the herb patch at Catherby.",
                "Fish and Cook 5 Sharks in Catherby using the Cooking gauntlets.",
                "Mix a Stamina Mix on top of the Seers' Village bank.",
                "Smith a Rune Hasta at Otto's Grotto.",
                "Construct a Pyre ship from Magic Logs.(Requires Chewed Bones.)",
                "Teleport to Catherby.",
            ),
        )

    private val KARAMJA =
        listOf(
            listOf(
                "Pick 5 bananas from the plantation located east of the volcano.",
                "Use the rope swing to travel to the small island north-west of Karamja, where the moss giants are.",
                "Mine some gold from the rocks on the north-west peninsula of Karamja.",
                "Travel to Port Sarim via the dock, east of Musa Point.",
                "Travel to Ardougne via the port near Brimhaven.",
                "Explore Cairn Island to the west of Karamja.",
                "Use the Fishing spots north of the banana plantation.",
                "Collect 5 seaweed from anywhere on Karamja.",
                "Attempt the TzHaar Fight Pits or Fight Cave.",
                "Kill a jogre in the Pothole dungeon.",
            ),
            listOf(
                "Claim a ticket from the Agility Arena in Brimhaven.",
                "Discover hidden wall in the dungeon below the volcano.",
                "Visit the Isle of Crandor via the dungeon below the volcano.",
                "Use Vigroy and Hajedy's cart service.",
                "Earn 100% favour in the village of Tai Bwo Wannai.",
                "Cook a spider on a stick.",
                "Charter the Lady of the Waves from Cairn Isle to Port Khazard.",
                "Cut a log from a teak tree.",
                "Cut a log from a mahogany tree.",
                "Catch a karambwan.",
                "Exchange gems for a machete.",
                "Use the gnome glider to travel to Karamja.",
                "Grow a healthy fruit tree in the patch near Brimhaven.",
                "Trap a horned graahk.",
                "Chop the vines to gain deeper access to Brimhaven Dungeon.",
                "Cross the lava using the stepping stones within Brimhaven Dungeon.",
                "Climb the stairs within Brimhaven Dungeon.",
                "Charter a ship from the shipyard in the far east of Karamja.",
                "Mine a red topaz from a gem rock.",
            ),
            listOf(
                "Become the Champion of the Fight Pits.",
                "Kill a Ket-Zek in the Fight Caves.",
                "Eat an oomlie wrap.",
                "Craft some nature runes from Essence.",
                "Cook a karambwan thoroughly.",
                "Kill a deathwing in the dungeon under the Kharazi Jungle.",
                "Use the crossbow shortcut south of the volcano.",
                "Collect 5 palm leaves.",
                "Be assigned a Slayer task by the Slayer Master in Shilo Village.",
                "Kill a metal dragon in Brimhaven Dungeon.",
            ),
            listOf(
                "Craft 56 Nature runes simultaneously from Essence without the use of Extracts.",
                "Equip a Fire Cape or Infernal Cape in Mor Ul Rek.",
                "Check the health of a palm tree in Brimhaven.",
                "Create an antivenom potion whilst standing in the horse shoe mine.",
                "Check the health of your Calquat tree patch.",
            ),
        )

    private val MORYTANIA =
        listOf(
            listOf(
                "Craft any Snelm from scratch in Morytania.",
                "Cook a thin Snail on the Port Phasmatys range.",
                "Get a slayer task from the Slayer Master in Canifis.",
                "Kill a Banshee in the Slayer Tower.",
                "Have Sbott in Canifis tan something for you.",
                "Enter Mort Myre Swamp.",
                "Kill a Ghoul.",
                "Place a Scarecrow in the Morytania flower patch.",
                "Offer some bonemeal at the Ectofuntus.",
                "Kill a werewolf in its human form using the Wolfbane Dagger.",
                "Restore your prayer points at the nature altar.",
            ),
            listOf(
                "Catch a swamp lizard.",
                "Complete a lap of the Canifis agility course.",
                "Obtain some Bark from a Hollow tree.",
                "Travel to Dragontooth Isle.",
                "Kill a Terror Dog.",
                "Complete a game of trouble brewing.",
                "Board the Swampy boat at the Hollows.",
                "Make a batch of cannonballs at the Port Phasmatys furnace.",
                "Kill a Fever Spider on Braindeath Island.",
                "Use an ectophial to return to Port Phasmatys.",
                "Mix a Guthix Balance potion while in Morytania.",
            ),
            listOf(
                "Enter the Kharyrll portal in your POH.",
                "Climb the advanced spike chain within Slayer Tower.",
                "Harvest some Watermelon from the Allotment patch on Harmony Island.",
                "Chop and burn some mahogany logs on Mos Le'Harmless.",
                "Complete a temple trek with a hard companion.",
                "Kill a Cave Horror.",
                "Harvest some Bittercap Mushrooms from the patch in Canifis.",
                "Pray at the Altar of Nature with Piety activated.",
                "Use the shortcut to get to the bridge over the Salve.",
                "Mine some Mithril ore in the Abandoned Mine.",
            ),
            listOf(
                "Catch a shark in Burgh de Rott with your bare hands.",
                "Cremate any Shade remains on a Magic or Redwood pyre.",
                "Fertilize the Morytania herb patch using Lunar Magic.",
                "Craft a Black dragonhide body in Canifis bank.",
                "Kill an Abyssal demon in the Slayer Tower.",
                "Loot the Barrows chest while wearing any complete barrows set.",
            ),
        )

    private val WESTERN =
        listOf(
            listOf(
                "Catch a Copper Longtail.",
                "Complete a novice game of Pest Control.",
                "Mine some Iron Ore near Piscatoris.",
                "Complete a lap of the Gnome agility course.",
                "Score a goal in a Gnomeball match.",
                "Claim any Chompy bird hat from Rantz.",
                "Teleport to Pest Control using the Minigame Teleport.",
                "Collect a swamp toad at the Gnome Stronghold.",
                "Have Brimstail teleport you to the Essence Mine.",
                "Fletch an Oak Shortbow in the Gnome Stronghold.",
                "Kill a Terrorbird in the Terrorbird enclosure.",
            ),
            listOf(
                "Take the agility shortcut from the Grand Tree to Otto's Grotto.",
                "Travel to the Gnome Stronghold by Spirit Tree.",
                "Trap a Spined Larupia.",
                "Fish some Bass on Ape Atoll.",
                "Chop and burn some teak logs on Ape Atoll.",
                "Complete an intermediate game of Pest Control.",
                "Travel to the Feldip Hills by Gnome Glider.",
                "Claim a Chompy bird hat from Rantz after registering at least 125 kills.",
                "Travel from Eagles' Peak to the Feldip Hills by Eagle.",
                "Make a Chocolate Bomb at the Grand Tree.",
                "Complete a delivery for the Gnome Restaurant.",
                "Turn your small crystal seed into a Crystal saw.",
                "Mine some Gold ore underneath the Grand Tree.",
            ),
            listOf(
                "Kill an Elf with a Crystal bow.",
                "Catch and cook a Monkfish in Piscatoris.",
                "Complete a Veteran game of Pest Control.",
                "Catch a Dashing Kebbit.",
                "Complete a lap of the Ape Atoll agility course.",
                "Chop and burn some Mahogany logs on Ape Atoll.",
                "Mine some Adamantite ore in Tirannwn.",
                "Check the health of your Palm tree in Lletya.",
                "Claim a Chompy bird hat from Rantz after registering at least 300 kills.",
                "Build an Isafdar painting in your POH Quest hall.",
                "Kill Zulrah.",
                "Teleport to Ape Atoll.",
                "Pickpocket a Gnome.",
            ),
            listOf(
                "Fletch a Magic Longbow in Tirannwn.",
                "Kill the Thermonuclear Smoke devil (Does not require task).",
                "Have Prissy Scilla protect your Magic tree.",
                "Use the Elven overpass advanced cliffside shortcut.",
                "Equip any complete void set.",
                "Claim a Chompy bird hat from Rantz after registering at least 1000 kills.",
                "Pickpocket an Elf.",
            ),
        )

    private val WILDERNESS =
        listOf(
            listOf(
                "Cast Low Alchemy at the Fountain of Rune.",
                "Enter the Wilderness from the Ardougne or Edgeville lever.",
                "Pray at the Chaos Altar in the Western Wilderness.",
                "Enter the Chaos Runecrafting temple.",
                "Kill a Mammoth in the Wilderness.",
                "Kill an Earth Warrior in the Wilderness beneath Edgeville.",
                "Restore some prayer points at the demonic ruins.",
                "Enter the King Black Dragon's lair.",
                "Collect 5 Red spiders' eggs from the Wilderness",
                "Mine some Iron ore in the Wilderness.",
                "Have the Mage of Zamorak teleport you to the Abyss.",
                "Equip any team cape in the Wilderness.",
            ),
            listOf(
                "Mine some Mithril ore in the wilderness.",
                "Chop some yew logs from a fallen Ent.",
                "Enter the Wilderness Godwars Dungeon.",
                "Complete a lap of the Wilderness Agility course.",
                "Kill a Green Dragon.",
                "Kill an Ankou in the Wilderness.",
                "Charge an Earth Orb.",
                "Kill a Bloodveld in the Wilderness Godwars Dungeon.",
                "Talk to the Emblem Trader in Edgeville about emblems. (He's just north of the bank)",
                "Smith a Golden helmet in the Resource Area.",
                "Open the Muddy Chest in the lava maze.",
            ),
            listOf(
                "Cast one of the 3 God spells against another player in the Wilderness.",
                "Charge an Air Orb.",
                "Catch a Black Salamander in the Wilderness.",
                "Smith an Adamant scimitar in the Resource Area.",
                "Kill a Lava Dragon.",
                "Kill the Chaos Elemental.",
                "Kill the Crazy Arc., Chaos Fanatic & Scorpia.",
                "Take the agility shortcut from Trollheim into the Wilderness.",
                "Kill a Spiritual warrior in the Wilderness Godwars Dungeon.",
                "Fish some Raw Lava Eel in the Wilderness.",
            ),
            listOf(
                "Kill Callisto, Venenatis & Vet'ion.",
                "Teleport to Ghorrock.",
                "Fish and Cook a Dark Crab in the Resource Area.",
                "Smith a rune scimitar from scratch in the Resource Area.",
                "Steal from the Rogues' chest.",
                "Slay a spiritual mage inside the wilderness Godwars Dungeon.",
                "Cut and burn some magic logs in the Resource Area.",
            ),
        )

    private val KOUREND =
        listOf(
            listOf(
                "Mine some Iron at the Mount Karuulm mine.",
                "Kill a Sandcrab.",
                "Hand in a book at the Arceuus Library.",
                "Steal from a Hosidius Food Stall.",
                "Browse the Warrens General Store.",
                "Take a boat to Land's End.",
                "Pray at the Altar in Kourend Castle",
                "Dig up some Saltpetre.",
                "Enter your Player Owned House from Hosidius.",
                "Do a lap of either tier of the Shayzien Agility Course.",
                "Create a Strength potion in the Lovakengj Pub.",
                "Fish a Trout from the River Molch.",
            ),
            listOf(
                "Travel to the Fairy Ring south of Mount Karuulm.",
                "Kill a Lizardman.",
                "Use Kharedst's memoirs to teleport to all five cities in Great Kourend.",
                "Mine some Volcanic sulphur.",
                "Enter the Farming Guild.",
                "Switch to the Necromancy Spellbook at Tyss.",
                "Repair a Piscarilius crane.",
                "Deliver some intelligence to Captain Ginea.",
                "Catch a Bluegill on Molch Island.",
                "Use the boulder leap in the Arceuus essence mine.",
                "Subdue the Wintertodt.",
                "Catch a Chinchompa in the Kourend Woodland.",
                "Chop some Mahogany logs north of the Farming Guild.",
            ),
            listOf(
                "Enter the Woodcutting Guild.",
                "Smelt an Adamantite bar in The Forsaken Tower.",
                "Kill a Lizardman Shaman in the Lizardman Temple.",
                "Mine some Lovakite.",
                "Plant some Logavano seeds at the Tithe Farm.",
                "Kill a Zombie in the Shayzien Crypts.",
                "Teleport to Xeric's Heart using Xeric's Talisman.",
                "Deliver an artefact to Captain Khaled.",
                "Kill a Wyrm in the Karuulm Slayer Dungeon.",
                "Cast Monster Examine on a Troll south of Mount Quidamortem.",
            ),
            listOf(
                "Craft one or more Blood runes from Dark essence fragments.",
                "Chop some Redwood logs.",
                "Defeat Skotizo in the Catacombs of Kourend.",
                "Catch an Anglerfish and cook it whilst in Great Kourend.",
                "Kill a Hydra in the Karuulm Slayer Dungeon.",
                "Create an Ape Atoll teleport tablet.",
                "Complete a Raid in the Chambers of Xeric.",
                "Create your own Battlestaff from scratch within the Farming Guild.",
            ),
        )

    fun tasks() =
        dbTable("dbtable.achievement_diary_tasks", serverOnly = true) {
            column("area", AREA, VarType.INT)
            column("tier", TIER, VarType.INT)
            column("text", TEXT, VarType.STRING)

            val areas =
                listOf(
                    Triple("lumbridge", AchievementDiariesTable.LUMBRIDGE, LUMBRIDGE),
                    Triple("varrock", AchievementDiariesTable.VARROCK, VARROCK),
                    Triple("ardougne", AchievementDiariesTable.ARDOUGNE, ARDOUGNE),
                    Triple("desert", AchievementDiariesTable.DESERT, DESERT),
                    Triple("falador", AchievementDiariesTable.FALADOR, FALADOR),
                    Triple("fremennik", AchievementDiariesTable.FREMENNIK, FREMENNIK),
                    Triple("kandarin", AchievementDiariesTable.KANDARIN, KANDARIN),
                    Triple("karamja", AchievementDiariesTable.KARAMJA, KARAMJA),
                    Triple("morytania", AchievementDiariesTable.MORYTANIA, MORYTANIA),
                    Triple("western", AchievementDiariesTable.WESTERN, WESTERN),
                    Triple("wilderness", AchievementDiariesTable.WILDERNESS, WILDERNESS),
                    Triple("kourend", AchievementDiariesTable.KOUREND, KOUREND),
                )
            for ((key, area, tiers) in areas) {
                tiers.forEachIndexed { tier, tasks ->
                    tasks.forEachIndexed { index, text ->
                        row("dbrow.achievement_diary_${key}_${TIER_NAMES[tier]}_${index + 1}") {
                            column(AREA, area)
                            column(TIER, tier)
                            column(TEXT, text)
                        }
                    }
                }
            }
        }

    private val TIER_NAMES = listOf("easy", "medium", "hard", "elite")
}
