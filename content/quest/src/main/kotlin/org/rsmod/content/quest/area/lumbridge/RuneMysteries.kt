package org.rsmod.content.quest.area.lumbridge

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class RuneMysteriesQuest : QuestScript(
    "quest_runemysteries", "varp.runemysteries", rewards {
    extra("Rune Essence Mine Access") },
    ItemRewardDisplay(AIR_TALISMAN)
) {

    override fun ScriptContext.init() {
    }

    override fun subTitle(): String =
        "talking to <col=800000>Duke Horacio</col> in <col=800000>Lumbridge Castle</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(access.player)
            val talismanGiven = access.player.rmTalismanGiven
            if (stage < STAGE_MET_SEDRIDOR || !talismanGiven) {
                line(DUKE.format(RED_TALISMAN, RED_SEDRIDOR))
                line("I can find the Wizards' Tower south west of Lumbridge, across the bridge from Draynor Village.")
                if (!held(AIR_TALISMAN)) {
                    line("If I lose the Strange Talisman, I'll need to ask Duke Horacio for another.")
                }
                return@questJournal
            }
            strike(DUKE.format("Strange Talisman", "Sedridor"))
            when {
                stage == STAGE_MET_SEDRIDOR ->
                    line(
                        "I delivered the Strange Talisman to Sedridor in the basement of the Wizards' Tower. " +
                            "I should see what he can tell me about it.",
                    )
                stage == STAGE_AGREED -> {
                    line(
                        "I delivered the Strange Talisman to Sedridor in the basement of the Wizards' Tower. " +
                            "He believes it might be key to discovering a Teleportation Incantation to the lost " +
                            "Rune Essence Mine. He asked me to help confirm this by delivering a " +
                            "<red>Package</red> to Aubury, an expert on Runecrafting. I can find him in his Rune " +
                            "Shop in south east Varrock.",
                    )
                    if (!held(RESEARCH_PACKAGE)) {
                        line("If I lose the Package, I'll need to ask Sedridor for another.")
                    }
                }
                else -> {
                    strike(SEDRIDOR)
                    when {
                        stage == STAGE_PACKAGE_GIVEN ->
                            line(
                                "I delivered the Package to Aubury at his Rune Shop in south east Varrock. I " +
                                    "should see what he can tell me about the Teleportation Incantation.",
                            )
                        access.player.rmNotesGiven ->
                            line(
                                "I delivered the Package to Aubury in Varrock. He confirmed Sedridor's " +
                                    "suspicions and asked me to take some Research Notes back to him, which I " +
                                    "did. I should see what Sedridor has learnt from them.",
                            )
                        else -> {
                            line(
                                "I delivered the Package to Aubury at his Rune Shop in south east Varrock. He " +
                                    "confirmed Sedridor's suspicions and asked me to take some <red>Research " +
                                    "Notes</red> back to him. I can find Sedridor in the basement of the " +
                                    "Wizards' Tower.",
                            )
                            if (!held(RESEARCH_NOTES)) {
                                line("If I lose the Research Notes, I'll need to ask Aubury for some more.")
                            }
                        }
                    }
                }
            }
        }

    private fun QuestJournalBuilder.held(obj: String): Boolean = obj in access.inv || obj in access.bank

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(DUKE.format("Strange Talisman", "Sedridor"))
            line(SEDRIDOR)
            line(
                "I delivered the Package to Aubury in Varrock. He confirmed Sedridor's suspicions " +
                    "and asked me to take some Research Notes back to him. I did so, and Sedridor " +
                    "used them to discover the Teleportation Incantation to the lost Rune Essence " +
                    "Mine. As a thank you for my help, he granted me permission to use the Rune " +
                    "Essence Mine whenever I please.",
            )
        }

    companion object {

        const val STAGE_TALISMAN = 1
        const val STAGE_MET_SEDRIDOR = 2
        const val STAGE_AGREED = 3
        const val STAGE_PACKAGE_GIVEN = 4
        const val STAGE_PACKAGE_READ = 5
        const val STAGE_COMPLETE = 6

        const val AIR_TALISMAN = "obj.air_talisman"
        const val RESEARCH_PACKAGE = "obj.research_package"
        const val RESEARCH_NOTES = "obj.research_notes"

        private const val RED_TALISMAN = "<red>Strange Talisman</red>"
        private const val RED_SEDRIDOR = "<red>Sedridor</red>"
        private const val DUKE =
            "I spoke to Duke Horacio in Lumbridge Castle. He told me that he'd found a %s in the Castle " +
                "which might be of use to the Order of Wizards at the Wizards' Tower. He asked me to take it " +
                "there and give it to a wizard called %s."
        private const val SEDRIDOR =
            "I delivered the Strange Talisman to Sedridor in the Wizards' Tower. He believed it might be key " +
                "to discovering a Teleportation Incantation to the lost Rune Essence Mine. He asked me to help " +
                "confirm this by delivering a Package to Aubury, an expert on Runecrafting."
    }
}
