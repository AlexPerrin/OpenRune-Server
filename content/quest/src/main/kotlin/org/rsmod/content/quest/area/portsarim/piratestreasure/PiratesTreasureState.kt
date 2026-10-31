package org.rsmod.content.quest.area.portsarim.piratestreasure

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Player

/**
 * Pirate's Treasure state shared with the Port Sarim scripts (customs officer, Wydin). The quest
 * stage is varp 71 (`varp.hunt`); the rum smuggling sub-state lives in server-only varbits.
 */
object PiratesTreasureState {
    const val QUEST_KEY = "quest_piratestreasure"
    const val NOT_STARTED = 0
    const val FETCH_RUM = 1
    const val RECEIVED_KEY = 2
    const val READ_NOTE = 3
    const val COMPLETE = 4

    const val EMPLOYED_PLANTATION = 1
    const val EMPLOYED_WYDIN = 2

    const val RUM_NONE = 0
    const val RUM_IN_CRATE = 1
    const val RUM_SHIPPED = 2

    const val CRATE_CAPACITY = 10

    const val KARAMJA_RUM = "obj.karamja_rum"
    const val WHITE_APRON = "obj.white_apron"

    private val quest: Quest
        get() = checkNotNull(Quest.get(QUEST_KEY)) { "Pirate's Treasure is not registered." }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    var Player.employment by intVarBit("varbit.pirates_treasure_employed")
    var Player.crateBananas by intVarBit("varbit.pirates_treasure_crate_bananas")
    var Player.crateRum by intVarBit("varbit.pirates_treasure_crate_rum")
    var Player.gardenerMet by intVarBit("varbit.pirates_treasure_gardener_met")

    fun employedAtPlantation(player: Player): Boolean =
        player.employment and EMPLOYED_PLANTATION != 0

    fun employedAtWydin(player: Player): Boolean = player.employment and EMPLOYED_WYDIN != 0

    fun hireAtWydin(player: Player) {
        player.employment = player.employment or EMPLOYED_WYDIN
    }

    fun carriesRum(player: Player): Boolean = player.inv.count(KARAMJA_RUM) > 0

    fun wearsWhiteApron(player: Player): Boolean = player.worn.count(WHITE_APRON) > 0

    fun hasWhiteApron(player: Player): Boolean =
        player.inv.count(WHITE_APRON) > 0 || wearsWhiteApron(player)
}
