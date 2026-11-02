package org.rsmod.content.interfaces.clans

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.net.clan.ClanAccessChanged
import org.rsmod.api.net.clan.ClanFounderAccept
import org.rsmod.api.net.clan.ClanRank
import org.rsmod.api.net.clan.Clans
import org.rsmod.api.net.clan.runeDay
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.GameMessage
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.advanced.onOpPlayer5
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpPlayerT
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val FOUNDERS = 5
private const val FOUNDER_SLOTS = 5
private const val FOUNDER_PLAYTIME = 25 * 60 * 100
private const val CLAN_TAB = 1
private const val RECRUIT_OP = 5
private const val RECRUIT = "Recruit"
private const val RECRUITMENT = 2951
private const val MAX_MEMBERS = 500
private const val LIST_PLAYERS = "clientscript.[clientscript,clan_create_side_list_players]"
private const val UPDATE_BUTTON_TEXT = "clientscript.[clientscript,script4447]"

private const val GIVE = "I'm in a clan. Can you give me anything?"
private const val OFFER =
    "I've got cloaks that let you show your clan's colours, and vexilla to help you recruit more people into " +
        "your clan"
private const val FORM = "I'd like to form a clan."
private const val JOIN_FIRST =
    "You can either form your own clan with your friends, or apply to an already existing clan."

private enum class Topic {
    WhoAreYou,
    WhatDoYouDo,
    WhyHere,
    ClanHub,
    WhatIsAClan,
    Join,
    Offer,
    Coffer,
    Hall,
    Give,
    Form,
    Goodbye,
}

private class Founding(val leader: Player) {
    var name: String = ""
    val founders: MutableList<Player> = mutableListOf(leader)
}

class ClanFoundingScript @Inject constructor(private val clans: Clans, private val eventBus: EventBus) :
    PluginScript() {
    private val foundings = HashMap<Player, Founding>()
    private val inHub = HashSet<Player>()

    override fun ScriptContext.startup() {
        onOpNpc1("npc.clan_clerk") { startDialogue(it.npc) { clerk() } }
        onIfOpen("interface.clans_creation_sidepanel") {
            for (button in listOf("name", "cancel", "confirm")) {
                player.ifSetEvents("component.clans_creation_sidepanel:$button", -1..-1, IfEvent.Op1)
            }
            player.ifSetEvents("component.clans_creation_sidepanel:invite", -1..-1, IfEvent.TgtPlayer)
            player.ifSetEvents("component.clans_creation_sidepanel:contents", 0 until FOUNDER_SLOTS, IfEvent.Op1)
            foundings[player]?.let { drawFounders(it) }
        }
        onIfOverlayButton("component.clans_creation_sidepanel:name") { setName() }
        onIfOverlayButton("component.clans_creation_sidepanel:cancel") { leaveFounding(player) }
        onIfOverlayButton("component.clans_creation_sidepanel:confirm") { confirm() }
        onIfOverlayButton("component.clans_creation_sidepanel:contents") { removeFounder(it.comsub) }
        onOpPlayerT("component.clans_creation_sidepanel:invite") { invite(it.target) }
        onArea("area.clan_hub") {
            inHub += player
            refreshRecruitOp(player)
        }
        onAreaExit("area.clan_hub") {
            inHub -= player
            refreshRecruitOp(player)
        }
        onWearposChange { refreshRecruitOp(player) }
        onPlayerQueueWithArgs<Player>("queue.clan_recruit_invite") { offerToJoin(it.args) }
        onEvent<ClanAccessChanged> {
            clans.clanOf(player)?.let { player.recolourClanItems(it) }
            refreshRecruitOp(player)
        }
        onOpPlayer5 {
            when {
                player in foundings && player in inHub -> invite(it.target)
                canRecruit(player) -> recruit(it.target)
            }
        }
        onEvent<ClanFounderAccept> { accept(player, leader) }
        onEvent<SessionStateEvent.Logout> {
            inHub -= player
            leaveFounding(player)
        }
        onEvent<SessionStateEvent.Delete> {
            inHub -= player
            leaveFounding(player)
        }
    }

    private suspend fun Dialogue.clerk() {
        chatNpc(happy, "Good day.")
        var topic = topMenu()
        while (true) {
            topic =
                when (topic) {
                    Topic.WhoAreYou -> {
                        chatPlayer(quiz, "Who are you?")
                        chatNpc(
                            neutral,
                            "My name is Clerk. I work on gathering people together to form what we call clans.",
                        )
                        choice4(
                            "What do you do here?",
                            Topic.WhatDoYouDo,
                            "What is a clan?",
                            Topic.WhatIsAClan,
                            GIVE,
                            Topic.Give,
                            "Goodbye.",
                            Topic.Goodbye,
                        )
                    }
                    Topic.WhatDoYouDo -> {
                        chatPlayer(quiz, "What do you do here?")
                        chatNpc(
                            neutral,
                            "My mission is to administer clans; I help form them and can tell you anything you " +
                                "need to know about them.",
                        )
                        choice5(
                            "Sure, but why are you here specifically?",
                            Topic.WhyHere,
                            "Who are you?",
                            Topic.WhoAreYou,
                            "What is a clan?",
                            Topic.WhatIsAClan,
                            GIVE,
                            Topic.Give,
                            "Goodbye.",
                            Topic.Goodbye,
                        )
                    }
                    Topic.WhyHere -> {
                        chatPlayer(quiz, "Sure, but why are you here specifically?")
                        chatNpc(
                            neutral,
                            "Why does anyone go anywhere? As a gatherer of people, I go where people gather. The " +
                                "Grand Exchange is known to be a place where people come together. What better " +
                                "place to bring them even closer?",
                        )
                        chatPlayer(neutral, "I suppose this usually is a crowded place.")
                        chatNpc(
                            neutral,
                            "Very much so. I was lucky to find this charming corner to set up the clan hub.",
                        )
                        hubMenu()
                    }
                    Topic.ClanHub -> {
                        chatPlayer(quiz, "What's the clan hub?")
                        chatNpc(
                            neutral,
                            "The clan hub is what I call this area, my little corner of the Grand Exchange. This " +
                                "is where every clan is born, as well as the best place to look for new recruits.",
                        )
                        chatNpc(neutral, "If you're in a clan you can also access your Clan Hall from here.")
                        hubMenu()
                    }
                    Topic.WhatIsAClan -> {
                        chatPlayer(quiz, "What is a clan?")
                        chatNpc(
                            neutral,
                            "Ah, clans. Where do I begin? Clans are groups of people gathered together for " +
                                "numerous reasons. Usually they have shared interests, like activities they enjoy " +
                                "together.",
                        )
                        chatNpc(
                            neutral,
                            "These activities could include improving certain skills, battling the same " +
                                "monsters, or even battling other clans!",
                        )
                        chatNpc(
                            neutral,
                            "Other clans form together because they just enjoy the company of other like-minded " +
                                "people. The clan community can be a great resource if you should ever need help " +
                                "with anything.",
                        )
                        clanMenu()
                    }
                    Topic.Join -> {
                        chatPlayer(quiz, "How do I join a clan?")
                        chatNpc(neutral, JOIN_FIRST)
                        chatNpc(
                            neutral,
                            "Should you choose to form your own clan you will need four friends to join you, and " +
                                "it is recommended that you have spent a good amount of time in this world first. " +
                                "Leadership is not for everyone.",
                        )
                        chatNpc(
                            neutral,
                            "However, fret not. There are plenty of interesting and exciting clans out there! " +
                                "Check out the clan recruitment board to see which clans are currently accepting " +
                                "applications.",
                        )
                        chatNpc(neutral, JOIN_FIRST)
                        clanMenu()
                    }
                    Topic.Offer -> {
                        chatPlayer(quiz, "What can a clan offer me?")
                        chatNpc(
                            neutral,
                            "Clans offer you the ability to have an official gathering of friends or like-minded " +
                                "individuals in a hierarchical structure.",
                        )
                        chatNpc(
                            neutral,
                            "In other words, you and your friends get an exclusive chat channel and a Clan Hall, " +
                                "as well as the ability to assign ranks to members.",
                        )
                        chatNpc(
                            neutral,
                            "Ranks can be assigned from custom pre-sets, which then determine what permissions " +
                                "that person has.",
                        )
                        chatNpc(
                            neutral,
                            "For example: One person may have the rank to kick players from the chat, while " +
                                "another has the rank to access the Clan Coffer.",
                        )
                        clanMenu()
                    }
                    Topic.Coffer -> {
                        chatPlayer(quiz, "What is the Clan Coffer?")
                        chatNpc(
                            neutral,
                            "The Clan Coffer is a shared coin store, used by members of the clan. Highly ranked " +
                                "members can assign who has permission to deposit and withdraw coins.",
                        )
                        clanMenu()
                    }
                    Topic.Hall -> {
                        chatPlayer(quiz, "What is a Clan Hall?")
                        chatNpc(
                            neutral,
                            "A Clan Hall is a private area only accessible for the people with the permission to " +
                                "enter. High ranked members of the clan can restrict it to only allow certain " +
                                "ranks in, or open it up to even allow guests.",
                        )
                        chatPlayer(quiz, "Why would I need a Clan Hall? Can't I get a house instead?")
                        chatNpc(
                            neutral,
                            "The beauty of it is that you can have both. You can keep your house for private " +
                                "guests and your own personal use, but when you wish to see larger groups then the " +
                                "Clan Hall may be more fitting.",
                        )
                        topMenu()
                    }
                    Topic.Give -> {
                        chatPlayer(quiz, GIVE)
                        give() ?: return
                    }
                    Topic.Form -> {
                        chatPlayer(neutral, FORM)
                        formClan()
                        return
                    }
                    Topic.Goodbye -> {
                        chatPlayer(neutral, "Goodbye.")
                        chatNpc(neutral, "Goodbye. Come back any time.")
                        return
                    }
                }
        }
    }

    private suspend fun Dialogue.give(): Topic? {
        if (clans.clanOf(player) == null) {
            chatNpc(neutral, "You'll need to be in a clan before I can give you anything.")
            return topMenu()
        }
        if (player.inv.isFull()) {
            chatNpc(neutral, "$OFFER, but you don't have space to take any.")
            chatPlayer(neutral, "I suppose I'd better free up some space.")
            chatNpc(neutral, "Yes, you should! Now, did you want to ask me anything else while you're here?")
            return topMenu()
        }
        chatNpc(happy, "Certainly. $OFFER.")
        while (true) {
            val kind = choice2("Clan cloak", ClanItem.Cloak, "Clan vexillum", ClanItem.Vexillum)
            val clan = clans.clanOf(player) ?: return null
            access.takeClanItem(kind, clan)
            if (player.inv.isFull()) {
                chatPlayer(happy, "Thanks!")
                return null
            }
        }
    }

    private suspend fun Dialogue.topMenu(): Topic =
        if (clans.clanOf(player) != null) {
            choice4(
                "Who are you?",
                Topic.WhoAreYou,
                "What is a clan?",
                Topic.WhatIsAClan,
                GIVE,
                Topic.Give,
                "Goodbye.",
                Topic.Goodbye,
            )
        } else {
            choice5(
                "Who are you?",
                Topic.WhoAreYou,
                "What is a clan?",
                Topic.WhatIsAClan,
                GIVE,
                Topic.Give,
                FORM,
                Topic.Form,
                "Goodbye.",
                Topic.Goodbye,
            )
        }

    private suspend fun Dialogue.hubMenu(): Topic =
        choice4(
            "What's the clan hub?",
            Topic.ClanHub,
            "What is a clan?",
            Topic.WhatIsAClan,
            GIVE,
            Topic.Give,
            "Goodbye.",
            Topic.Goodbye,
        )

    private suspend fun Dialogue.clanMenu(): Topic =
        choice5(
            "How do I join a clan?",
            Topic.Join,
            "What can a clan offer me?",
            Topic.Offer,
            "What is the Clan Coffer?",
            Topic.Coffer,
            "What is a Clan Hall?",
            Topic.Hall,
            "Goodbye.",
            Topic.Goodbye,
        )

    private suspend fun Dialogue.formClan() {
        if (player in foundings || foundings.values.any { player in it.founders }) {
            chatNpc(neutral, "You are already forming a clan. Use the side panel to finish it.")
            openPanel(player)
            return
        }
        if (player.vars["varp.playtime"] < FOUNDER_PLAYTIME) {
            chatNpc(
                neutral,
                "Leadership is not for everyone. Come back when you have spent at least 25 hours in this " +
                    "world.",
            )
            return
        }
        if (player.vars["varbit.clan_founded_runeday"] == runeDay() and 0x7FFF) {
            chatNpc(neutral, "You have already formed a clan today. Come back tomorrow.")
            return
        }
        chatNpc(
            neutral,
            "Very well. Choose a name for your clan and invite four other players to found it with you.",
        )
        val founding = Founding(player)
        foundings[player] = founding
        VarPlayerIntMapSetter.set(player, "varbit.clan_i_am_founder", 1)
        openPanel(player)
    }

    private fun openPanel(player: Player) {
        clans.setFounding(player, true)
        VarPlayerIntMapSetter.set(player, "varbit.side_channels_tab_selected", CLAN_TAB)
        player.ifOpenOverlay("interface.clans_creation_sidepanel", "component.side_channels:contents", eventBus)
    }

    private fun canRecruit(player: Player): Boolean {
        val clan = clans.clanOf(player) ?: return false
        return ClanItem.Vexillum.objs.any { player.righthand.isType(it) } &&
            clan.rankOf(player) >= clan.permissionRank(RECRUITMENT)
    }

    private fun refreshRecruitOp(player: Player) {
        val vexillum = canRecruit(player)
        if (vexillum || player in inHub && clans.clanOf(player) == null) {
            MiscOutput.setPlayerOp(player, RECRUIT_OP, RECRUIT, priority = vexillum)
        } else {
            MiscOutput.clearPlayerOp(player, RECRUIT_OP, RECRUIT)
        }
    }

    private fun ProtectedAccess.recruit(target: Player) {
        val clan = clans.clanOf(player) ?: return
        when {
            target == player -> return
            target.vars["varbit.option_acceptaid"] == 0 -> mes("${target.displayName} is not accepting aid.")
            clan.members.size >= MAX_MEMBERS -> mes("Your clan is full.")
            else ->
                clans.hasClan(target) { inClan ->
                    if (inClan) {
                        player.mes("${target.displayName} is already in a clan.")
                        return@hasClan
                    }
                    clans.inviteMember(target, player)
                    player.mes("Sending clan invitation to ${target.displayName}...")
                    target.weakQueue("queue.clan_recruit_invite", 1, player)
                }
        }
    }

    private suspend fun ProtectedAccess.offerToJoin(recruiter: Player) {
        val clan = clans.clanOf(recruiter) ?: return
        val join = choice2("Yes.", true, "No.", false, title = "Join ${clan.name}?")
        if (join) acceptRecruit(player, recruiter) else clans.takeMemberInvite(player, recruiter)
    }

    private fun acceptRecruit(invitee: Player, recruiter: Player) {
        if (!clans.takeMemberInvite(invitee, recruiter) || !canRecruit(recruiter)) {
            return
        }
        val clan = clans.clanOf(recruiter) ?: return
        clans.hasClan(invitee) { inClan ->
            if (inClan || clans.clanOf(recruiter) !== clan) {
                return@hasClan
            }
            if (!clans.addMember(clan, invitee, ClanRank.MEMBER)) {
                recruiter.mes("Your clan is full.")
                return@hasClan
            }
            clans.systemMessage(clan, "${invitee.displayName} has been invited into the clan by ${recruiter.displayName}.")
        }
    }

    private fun foundingOf(player: Player): Founding? =
        foundings[player] ?: foundings.values.firstOrNull { player in it.founders }

    private suspend fun ProtectedAccess.setName() {
        val founding = foundings[player] ?: return
        val name = stringDialog("Enter a name for your clan:").trim()
        if (name.isEmpty()) {
            return
        }
        if (!validClanName(name)) {
            mes("That name is not allowed.")
            return
        }
        clans.nameTaken(name) { taken ->
            if (taken) {
                player.mes("That clan name is already taken.")
                return@nameTaken
            }
            founding.name = name
            founding.founders.forEach {
                it.runClientScript(
                    UPDATE_BUTTON_TEXT.asRSCM(RSCMType.CLIENTSCRIPT),
                    "component.clans_creation_sidepanel:universe".asRSCM(RSCMType.COMPONENT),
                    "component.clans_creation_sidepanel:name".asRSCM(RSCMType.COMPONENT),
                    "enum.clan_create_buttons".asRSCM(RSCMType.ENUM),
                    name,
                )
            }
        }
    }

    private fun ProtectedAccess.invite(target: Player) {
        val founding = foundings[player] ?: return
        when {
            target == player || target in founding.founders -> return
            founding.founders.size >= FOUNDERS -> mes("Your clan already has five founders.")
            foundingOf(target) != null -> mes("${target.displayName} is already forming a clan.")
            target.vars["varp.playtime"] < FOUNDER_PLAYTIME ->
                mes("${target.displayName} has not spent enough time in the game to found a clan.")
            else ->
                clans.hasClan(target) { inClan ->
                    if (inClan) {
                        player.mes("${target.displayName} is already in a clan.")
                        return@hasClan
                    }
                    clans.inviteFounder(target, player)
                    player.mes("Sending clan invitation to ${target.displayName}...")
                    GameMessage.requestMes(
                        target,
                        "wishes to found a clan with you.",
                        player.displayName,
                        ChatType.ClanCreationInvitation,
                    )
                }
        }
    }

    private fun accept(invitee: Player, leader: Player) {
        val founding = foundings[leader] ?: return
        if (founding.founders.size >= FOUNDERS || foundingOf(invitee) != null) {
            return
        }
        founding.founders += invitee
        openPanel(invitee)
        drawFounders(founding)
    }

    private fun drawFounders(founding: Founding) {
        val names = (0 until FOUNDER_SLOTS).map { founding.founders.getOrNull(it)?.displayName.orEmpty() }
        founding.founders.forEach {
            it.runClientScript(
                LIST_PLAYERS.asRSCM(RSCMType.CLIENTSCRIPT),
                "component.clans_creation_sidepanel:contents".asRSCM(RSCMType.COMPONENT),
                names[0],
                names[1],
                names[2],
                names[3],
                names[4],
                it.displayName,
            )
        }
    }

    private fun ProtectedAccess.removeFounder(slot: Int) {
        val founding = foundings[player] ?: return
        val target = founding.founders.getOrNull(slot) ?: return
        if (target != player) {
            leaveFounding(target)
        }
    }

    private fun leaveFounding(player: Player) {
        val led = foundings.remove(player)
        if (led != null) {
            VarPlayerIntMapSetter.set(player, "varbit.clan_i_am_founder", 0)
            led.founders.forEach { closePanel(it) }
            led.founders.filter { it != player }.forEach { it.mes("${player.displayName} has cancelled the clan.") }
            return
        }
        val founding = foundingOf(player) ?: return
        founding.founders -= player
        closePanel(player)
        founding.leader.mes("${player.displayName} has left your clan's founders.")
        drawFounders(founding)
    }

    private fun closePanel(player: Player) {
        clans.setFounding(player, false)
        if (player.ui.containsOverlay("interface.clans_creation_sidepanel")) {
            player.ifOpenOverlay("interface.clans_sidepanel", "component.side_channels:contents", eventBus)
        }
    }

    private fun ProtectedAccess.confirm() {
        val founding = foundings[player] ?: return
        if (founding.name.isEmpty()) {
            mes("You must choose a name for your clan first.")
            return
        }
        if (founding.founders.size < FOUNDERS) {
            mes("You need five founders to form a clan.")
            return
        }
        if (founding.founders.any { !clans.isLoggedIn(it) || clans.clanOf(it) != null }) {
            mes("All five founders must be online and not in a clan.")
            return
        }
        foundings.remove(player)
        val founders = founding.founders.toList()
        clans.create(founding.name, founders) { clan ->
            founders.forEach { closePanel(it) }
            VarPlayerIntMapSetter.set(player, "varbit.clan_i_am_founder", 0)
            if (clan == null) {
                player.mes("That clan name is already taken.")
                return@create
            }
            VarPlayerIntMapSetter.set(player, "varbit.clan_founded_runeday", runeDay() and 0x7FFF)
            founders.forEach { it.mes("You have founded ${clan.name}.") }
        }
    }
}
