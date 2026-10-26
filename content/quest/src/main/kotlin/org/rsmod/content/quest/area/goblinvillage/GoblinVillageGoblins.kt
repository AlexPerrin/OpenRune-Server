package org.rsmod.content.quest.area.goblinvillage

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GoblinVillageGoblins @Inject constructor(private val aiPlayerInteractions: AiPlayerInteractions) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (id in SOLDIERS) {
            onOpNpc1("npc.goblin_red_soldier_$id") { talk(it.npc, red = true) }
            onOpNpc1("npc.goblin_green_soldier_$id") { talk(it.npc, red = false) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc, red: Boolean) {
        val questDone = vars["varbit.gobdip_main"] >= QUEST_COMPLETE
        startDialogue(npc) {
            if (questDone) afterQuest(npc, red) else armourBest(red)
        }
    }

    private suspend fun Dialogue.armourBest(red: Boolean) {
        val colour = if (red) "red" else "green"
        chatNpc(angry, if (red) "Red armour best!" else "Green armour best!")
        when (choice2("Why is $colour best?", 1, "Err, okay.", 2)) {
            1 -> {
                chatPlayer(quiz, "Why is $colour best?")
                if (red) {
                    chatNpc(neutral, "Cos General Bentnoze says so, and he bigger than me.")
                } else {
                    chatNpc(neutral, "I forgot now, but General Wartface says it is. So it must be.")
                }
            }
            else -> chatPlayer(confused, "Err, okay.")
        }
    }

    private suspend fun Dialogue.afterQuest(npc: Npc, red: Boolean) {
        when (access.random.of(maxExclusive = 5)) {
            0 -> attack(npc, "I kill you human!")
            1 -> attack(npc, "Go away smelly human!")
            2 -> {
                chatNpc(quiz, "What you doing here?")
                when (
                    choice2("I'm here to kill all you goblins!", 1, "I'm just looking around.", 2)
                ) {
                    1 -> {
                        chatPlayer(angry, "I'm here to kill all you goblins!")
                        attack(npc, "I kill you!")
                    }
                    else -> {
                        chatPlayer(neutral, "I'm just looking around.")
                        chatNpc(neutral, "Me not sure that allowed. You have to check with generals.")
                    }
                }
            }
            3 -> newCentury(npc)
            else -> brownBest(red)
        }
    }

    private suspend fun Dialogue.newCentury(npc: Npc) {
        chatNpc(happy, "Happy goblin new century!")
        when (choice2("Happy new century!", 1, "What is the goblin new century?", 2)) {
            1 -> {
                chatPlayer(happy, "Happy new century!")
                return
            }
            else -> chatPlayer(quiz, "What is the goblin new century?")
        }
        chatNpc(
            neutral,
            "Goblin century mark year of big battle on Plain of Mud. That when Big High War God " +
                "give us commandments.",
        )
        when (
            choice4(
                "Who is the Big High War God?",
                1,
                "What are the goblin commandments?",
                2,
                "Where is the Plain of Mud?",
                3,
                "I need to go.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Who is the Big High War God?")
                chatNpc(
                    neutral,
                    "Big High War God take goblins and make them strong! Without him, we small " +
                        "and weak and stupid. But thanks to Big High War God, we most powerful " +
                        "race in Gielinor!",
                )
                chatPlayer(neutral, "Umm... you're clearly not the most powerful race in Gielinor.")
                attack(npc, "Not to doubt word of Big High War God! I kill you!")
            }
            2 -> {
                chatPlayer(quiz, "What are the goblin commandments?")
                chatNpc(
                    neutral,
                    "Slay enemies of Big High War God! Not show mercy! Not run from battle! Not " +
                        "doubt word of Big High War God!",
                )
                attack(
                    npc,
                    "Without commandments we not know right from wrong. We be like other races " +
                        "who not know war is good!",
                )
            }
            3 -> {
                chatPlayer(quiz, "Where is the Plain of Mud?")
                attack(npc, "That goblin secret! No human ever find Plain of Mud!")
            }
            else -> chatPlayer(neutral, "I need to go.")
        }
    }

    private suspend fun Dialogue.brownBest(red: Boolean) {
        val wearing = if (red) "red" else "green"
        chatNpc(happy, "Brown armour best!")
        when (
            choice3("Why is brown best?", 1, "But you're still wearing $wearing!", 2, "Err, okay.", 3)
        ) {
            1 -> {
                chatPlayer(quiz, "Why is brown best?")
                chatNpc(
                    neutral,
                    "General Wartface and General Bentnoze both say it is. And normally they " +
                        "never agree!",
                )
            }
            2 -> {
                chatPlayer(quiz, "But you're still wearing $wearing armour!")
                if (red) {
                    chatNpc(neutral, "It not red, it just red-ish brown!")
                } else {
                    chatNpc(neutral, "Um... my brown armour getting wash.")
                }
            }
            else -> chatPlayer(confused, "Err, okay.")
        }
    }

    private suspend fun Dialogue.attack(npc: Npc, text: String) {
        chatNpc(angry, text)
        npc.opPlayer2(player, aiPlayerInteractions)
    }

    private companion object {
        private const val QUEST_COMPLETE = 6
        private val SOLDIERS = 2..8
    }
}
