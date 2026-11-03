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
                val lost =
                    if (held(AIR_TALISMAN)) {
                        ""
                    } else {
                        " If I lose the <red>Strange Talisman</red>, I'll need to ask " +
                            "<red>Duke Horacio</red> for another."
                    }
                line(DUKE_RED + lost)
                return@questJournal
            }
            strike(DUKE)
            when {
                stage == STAGE_MET_SEDRIDOR ->
                    line(
                        "I delivered the <red>Strange Talisman</red> to <red>Sedridor</red> in " +
                            "the basement of the <red>Wizards' Tower</red>. I should see what he " +
                            "can tell me about it.",
                    )
                stage == STAGE_AGREED -> {
                    val lost =
                        if (held(RESEARCH_PACKAGE)) {
                            ""
                        } else {
                            " If I lose the <red>Package</red>, I'll need to ask " +
                                "<red>Sedridor</red> for another."
                        }
                    line(
                        "I delivered the <red>Strange Talisman</red> to <red>Sedridor</red> in " +
                            "the basement of the <red>Wizards' Tower</red>. He believes it might " +
                            "be key to discovering a <red>Teleportation Incantation</red> to the " +
                            "lost <red>Rune Essence Mine</red>. He asked me to help confirm this " +
                            "by delivering a <red>Package</red> to <red>Aubury</red>, an expert " +
                            "on <red>Runecrafting</red>. I can find him in his <red>Rune " +
                            "Shop</red> in south east <red>Varrock</red>." + lost,
                    )
                }
                else -> {
                    strike(SEDRIDOR)
                    when {
                        stage == STAGE_PACKAGE_GIVEN ->
                            line(
                                "I delivered the <red>Package</red> to <red>Aubury</red> at his " +
                                    "<red>Rune Shop</red> in south east <red>Varrock</red>. I " +
                                    "should see what he can tell me about the <red>Teleportation " +
                                    "Incantation</red>.",
                            )
                        access.player.rmNotesGiven ->
                            line(
                                "I delivered the <red>Package</red> to <red>Aubury</red> in " +
                                    "<red>Varrock</red>. He confirmed <red>Sedridor's</red> " +
                                    "suspicions and asked me to take some <red>Research " +
                                    "Notes</red> back to him, which I did. I should see what " +
                                    "<red>Sedridor</red> has learnt from them.",
                            )
                        else -> {
                            val lost =
                                if (held(RESEARCH_NOTES)) {
                                    ""
                                } else {
                                    " If I lose the <red>Research Notes</red>, I'll need to " +
                                        "ask <red>Aubury</red> for some more."
                                }
                            line(
                                "I delivered the <red>Package</red> to <red>Aubury</red> at his " +
                                    "<red>Rune Shop</red> in south east <red>Varrock</red>. He " +
                                    "confirmed <red>Sedridor's</red> suspicions and asked me to " +
                                    "take some <red>Research Notes</red> back to him. I can find " +
                                    "<red>Sedridor</red> in the basement of the <red>Wizards' " +
                                    "Tower</red>." + lost,
                            )
                        }
                    }
                }
            }
        }

    private fun QuestJournalBuilder.held(obj: String): Boolean =
        obj in access.inv || obj in access.bank

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(DUKE)
            line(SEDRIDOR)
            line(
                "I delivered the Package to Aubury in Varrock. He confirmed Sedridor's " +
                    "suspicions and asked me to take some Research Notes back to him. I did so, " +
                    "and Sedridor used them to discover the Teleportation Incantation to the " +
                    "lost Rune Essence Mine. As a thank you for my help, he granted me " +
                    "permission to use the Rune Essence Mine whenever I please.",
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

        private const val DUKE =
            "I spoke to Duke Horacio in Lumbridge Castle. He told me that he'd found a Strange " +
                "Talisman in the Castle which might be of use to the Order of Wizards at the " +
                "Wizards' Tower. He asked me to take it there and give it to a wizard called " +
                "Sedridor."
        private const val DUKE_RED =
            "I spoke to <red>Duke Horacio</red> in <red>Lumbridge Castle</red>. He told me that " +
                "he'd found a <red>Strange Talisman</red> in the <red>Castle</red> which might " +
                "be of use to the <red>Order of Wizards</red> at the <red>Wizards' Tower</red>. " +
                "He asked me to take it there and give it to a wizard called " +
                "<red>Sedridor</red>. I can find the <red>Wizards' Tower</red> south west of " +
                "<red>Lumbridge</red>, across the bridge from <red>Draynor Village</red>."
        private const val SEDRIDOR =
            "I delivered the Strange Talisman to Sedridor in the Wizards' Tower. He believed it " +
                "might be key to discovering a Teleportation Incantation to the lost Rune " +
                "Essence Mine. He asked me to help confirm this by delivering a Package to " +
                "Aubury, an expert on Runecrafting."
    }
}
