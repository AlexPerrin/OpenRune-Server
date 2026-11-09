package org.rsmod.api.net.rsprot.handlers

import dev.or2.central.worldlink.protocol.WorldOpcodes
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.social.FriendListAdd
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.net.central.CentralSocialResult
import org.rsmod.api.net.central.CentralSocialService
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.net.central.writeCentralFriendAdded
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.social.writeSocialMessage
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid

class FriendListAddHandler
@Inject
constructor(
    private val playerList: PlayerList,
    private val db: GameDbManager,
    private val social: CentralSocialService,
    private val serverConfig: ServerConfig,
) : MessageHandler<FriendListAdd> {
    override fun handle(player: Player, message: FriendListAdd) {
        val uid = player.uid
        val requestedName = message.name
        val characterId = player.characterId

        if (characterId <= 0) {
            player.writeSocialMessage("Social is not available right now.")
            return
        }

        db.request(
            request = {
                social.addFriend(
                    characterId = characterId,
                    name = requestedName,
                )
            },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request

                result.fold(
                    onOk = { socialResult ->
                        when (socialResult) {
                            CentralSocialResult.Ok -> {
                                current.mes(
                                    "Changes will take effect on your friends channel in the next 60 seconds.",
                                    ChatType.FriendNotification,
                                )
                                writeAddedFriend(uid, characterId, requestedName)
                            }

                            CentralSocialResult.Ignored -> Unit

                            is CentralSocialResult.Failed -> {
                                if (socialResult.reason == WorldOpcodes.SOCIAL_FAIL_USER_NOT_FOUND) {
                                    current.mes(
                                        "Unable to add friend - unknown player.",
                                        ChatType.FriendNotification,
                                    )
                                } else {
                                    current.writeSocialMessage(socialResult.message)
                                }
                            }
                        }
                    },
                    onErr = {
                        current.writeSocialMessage("Unable to add player right now.")
                    },
                )
            },
        )
    }

    private fun writeAddedFriend(uid: PlayerUid, characterId: Int, requestedName: String) {
        val key = nameKey(requestedName)
        db.request(
            request = { social.socialSnapshot(characterId = characterId) },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request

                result.fold(
                    onOk = { sync ->
                        when (sync) {
                            is OpenRuneCentralWorldLink.CentralSocialSnapshotResult.Ok -> {
                                val friend =
                                    sync.snapshot.friends.firstOrNull {
                                        nameKey(it.displayName) == key ||
                                            it.previousDisplayName?.let(::nameKey) == key
                                    }
                                if (friend != null) {
                                    current.writeCentralFriendAdded(friend, serverConfig)
                                }
                            }

                            is OpenRuneCentralWorldLink.CentralSocialSnapshotResult.Failed -> {
                                current.writeSocialMessage(sync.message)
                            }
                        }
                    },
                    onErr = {
                        current.writeSocialMessage("Unable to refresh social list right now.")
                    },
                )
            },
        )
    }

    private fun nameKey(name: String): String =
        name.trim().lowercase().replace('_', ' ').replace('-', ' ').replace(' ', ' ')
}
