package dev.or2.central.worldlink.protocol.social

data class SocialSyncFriend(
    val displayName: String,
    val previousDisplayName: String?,
    val worldId: Int,
)

data class SocialSyncIgnore(
    val displayName: String,
    val previousDisplayName: String?,
)

data class SocialSyncSnapshot(
    val publicChat: Int,
    val privateChat: Int,
    val tradeChat: Int,
    val friends: List<SocialSyncFriend>,
    val ignores: List<SocialSyncIgnore>,
)

data class PrivateMessagePush(
    val senderWorldId: Int,
    val fromCharacterId: Int,
    val toCharacterId: Int,
    val senderCrown: Int,
    val senderDisplayName: String,
    val message: String,
)

data class FriendPresencePush(
    val ownerCharacterId: Int,
    val friendCharacterId: Int,
    val friendWorldId: Int,
    val friendDisplayName: String,
    val friendPreviousDisplayName: String?,
)
