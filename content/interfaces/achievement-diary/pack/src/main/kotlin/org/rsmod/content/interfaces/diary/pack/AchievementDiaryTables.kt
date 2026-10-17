package org.rsmod.content.interfaces.diary.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Achievement diary journals (the journal scroll opened from the Achievement Diaries side tab).
 * [AchievementDiariesTable] has one row per area: its index in enum 595 (the side tab's order), the
 * diary's name (journal title, chat messages) and the heading line of its journal.
 * [AchievementDiaryTasksTable] has the tasks in the OSRS Wiki's order per tier (0 easy .. 3 elite),
 * worded as the in-game journal writes them (rsprox capture of every journal).
 */
object AchievementDiariesTable {
    const val AREA = 0
    const val HEADING = 1
    const val NAME = 2

    const val LUMBRIDGE = 6
    const val VARROCK = 8

    private val DIARIES =
        listOf(
            Diary("lumbridge", LUMBRIDGE, "Lumbridge & Draynor", "Lumbridge & Draynor Tasks"),
            Diary("varrock", VARROCK, "Varrock", "Varrock Tasks"),
        )

    fun diaries() =
        dbTable("dbtable.achievement_diaries", serverOnly = true) {
            column("area", AREA, VarType.INT)
            column("heading", HEADING, VarType.STRING)
            column("name", NAME, VarType.STRING)

            for ((key, area, name, heading) in DIARIES) {
                row("dbrow.achievement_diary_$key") {
                    column(AREA, area)
                    column(HEADING, heading)
                    column(NAME, name)
                }
            }
        }

    private data class Diary(val key: String, val area: Int, val name: String, val heading: String)
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
                "Squeeze through an obstacle pipe in Edgeville dungeon.",
            ),
            listOf(
                "Create a super combat potion in Varrock west bank.",
                "Use Lunar magic to make 20 mahogany planks at the Lumberyard.",
                "Bake a summer pie in the Cooking Guild.",
                "Smith and fletch ten rune darts within Varrock.",
                "Craft 100 or more earth runes simultaneously from Essence without the use of Extracts.",
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
