package org.rsmod.content.quest.area.portsarim.piratestreasure

import dev.openrune.types.MesAnimType
import org.rsmod.api.player.dialogue.Dialogue

/** Wydin's job offer (Pirate's Treasure), from his talk options and from his back-room door. */
suspend fun Dialogue.wydinJobOffer(question: String) {
    chatPlayer(quiz, question)
    wydin(
        neutral,
        "Well, you're keen, I'll give you that. Okay, I'll give you a go. Have you got your own " +
            "white apron?",
    )
    if (PiratesTreasureState.hasWhiteApron(player)) {
        chatPlayer(happy, "Yes, I have one right here.")
        wydin(
            happy,
            "Wow - you are well prepared! You're hired. Go through to the back and tidy up for me, " +
                "please.",
        )
        PiratesTreasureState.hireAtWydin(player)
        if (!PiratesTreasureState.wearsWhiteApron(player)) {
            wydin(neutral, "You need to put your white apron on first though.")
        }
        return
    }
    chatPlayer(sad, "No, I haven't.")
    wydin(
        neutral,
        "Well, you can't work here unless you have a white apron. Health and safety regulations, " +
            "you understand.",
    )
    chatPlayer(quiz, "Where can I get one of those?")
    wydin(
        neutral,
        "Well, I get all of mine over at the clothing shop in Varrock. They sell them cheap there.",
    )
    wydin(
        neutral,
        "Oh, and I'm sure that I've seen a spare one over in Gerrant's fish store somewhere. It's " +
            "the little place just north of here.",
    )
}

/** Wydin's talk once he has hired the player; returns `true` when the player wants to buy. */
suspend fun Dialogue.wydinEmployeeTalk(): Boolean {
    wydin(quiz, "Is it nice and tidy round the back now?")
    val topic =
        choice4(
            "Yes, can I work out front now?",
            1,
            "Yes, are you going to pay me yet?",
            2,
            "No, it's a complete mess",
            3,
            "Can I buy something please?",
            4,
        )
    when (topic) {
        1 -> {
            chatPlayer(quiz, "Yes, can I work out front now?")
            wydin(neutral, "No, I'm the one who works here.")
        }
        2 -> {
            chatPlayer(quiz, "Yes, are you going to pay me yet?")
            wydin(neutral, "Umm... No, not yet.")
        }
        3 -> {
            chatPlayer(sad, "No, it's a complete mess.")
            wydin(neutral, "Ah well, it'll give you something to do, won't it.")
        }
        else -> {
            chatPlayer(quiz, "Can I buy something please?")
            wydin(happy, "Yes, of course.")
            return true
        }
    }
    return false
}

suspend fun Dialogue.wydin(mesanim: MesAnimType, text: String) {
    chatNpcSpecific("Wydin", "npc.wydin", mesanim, text)
}
