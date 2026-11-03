package dev.or2.central.social

import dev.or2.central.social.CentralSocialRepository.CharacterNameRow
import java.util.concurrent.ConcurrentHashMap

data class CharacterProfile(
    val displayName: String,
    val previousDisplayName: String?,
)

data class FriendPresenceRecipient(
    val ownerCharacterId: Int,
    val ownerWorldId: Int,
    val friendCharacterId: Int,
    val friendDisplayName: String,
    val friendPreviousDisplayName: String?,
    val visibleWorldId: Int,
)

class SocialGraphStore {
    private val friends = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val ignores = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val filters = ConcurrentHashMap<Int, ChatFiltersRow>()
    private val reverseFriends = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val namesByCharacterId = ConcurrentHashMap<Int, CharacterProfile>()
    private val characterIdByLowerName = ConcurrentHashMap<String, Int>()

    fun clear() {
        friends.clear()
        ignores.clear()
        filters.clear()
        reverseFriends.clear()
        namesByCharacterId.clear()
        characterIdByLowerName.clear()
    }

    fun loadFriendEdge(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ) {
        friends.computeIfAbsent(ownerCharacterId) { ConcurrentHashMap.newKeySet() }.add(friendCharacterId)
        reverseFriends.computeIfAbsent(friendCharacterId) { ConcurrentHashMap.newKeySet() }.add(ownerCharacterId)
    }

    fun loadIgnoreEdge(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ) {
        ignores.computeIfAbsent(ownerCharacterId) { ConcurrentHashMap.newKeySet() }.add(ignoredCharacterId)
    }

    fun loadFilters(
        characterId: Int,
        row: ChatFiltersRow,
    ) {
        filters[characterId] = row
    }

    fun loadCharacterName(row: CharacterNameRow) {
        putCharacterName(row.characterId, row.displayName, row.previousDisplayName)
    }

    fun putCharacterName(
        characterId: Int,
        displayName: String,
        previousDisplayName: String?,
    ) {
        val cleaned = displayName.trim()
        if (characterId <= 0 || cleaned.isBlank()) {
            return
        }
        namesByCharacterId[characterId]?.displayName?.lowercase()?.let { old ->
            if (characterIdByLowerName[old] == characterId) {
                characterIdByLowerName.remove(old)
            }
        }
        namesByCharacterId[characterId] =
            CharacterProfile(
                displayName = cleaned,
                previousDisplayName = previousDisplayName?.trim()?.takeIf { it.isNotBlank() },
            )
        characterIdByLowerName[cleaned.lowercase()] = characterId
    }

    fun findCharacterByDisplayName(name: String): Int? {
        val key = name.trim().lowercase()
        if (key.isEmpty()) {
            return null
        }
        return characterIdByLowerName[key]
    }

    fun profile(characterId: Int): CharacterProfile? = namesByCharacterId[characterId]

    fun isFriend(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ): Boolean = friends[ownerCharacterId]?.contains(friendCharacterId) == true

    fun isIgnored(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ): Boolean = ignores[ownerCharacterId]?.contains(ignoredCharacterId) == true

    fun chatFilters(characterId: Int): ChatFiltersRow = filters[characterId] ?: ChatFiltersRow(0, 0, 0)

    fun addFriend(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ): Boolean {
        val added =
            friends.computeIfAbsent(ownerCharacterId) { ConcurrentHashMap.newKeySet() }.add(friendCharacterId)
        if (added) {
            reverseFriends.computeIfAbsent(friendCharacterId) { ConcurrentHashMap.newKeySet() }.add(ownerCharacterId)
        }
        return added
    }

    fun deleteFriend(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ) {
        friends[ownerCharacterId]?.remove(friendCharacterId)
        reverseFriends[friendCharacterId]?.remove(ownerCharacterId)
    }

    fun addIgnore(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ): Boolean =
        ignores.computeIfAbsent(ownerCharacterId) { ConcurrentHashMap.newKeySet() }.add(ignoredCharacterId)

    fun deleteIgnore(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ) {
        ignores[ownerCharacterId]?.remove(ignoredCharacterId)
    }

    fun setPrivateChatFilter(
        characterId: Int,
        privateChat: Int,
    ) {
        val current = chatFilters(characterId)
        filters[characterId] = current.copy(privateChat = privateChat.coerceIn(0, 2))
    }

    fun friendIds(ownerCharacterId: Int): Set<Int> = friends[ownerCharacterId]?.toSet().orEmpty()

    fun ignoreIds(ownerCharacterId: Int): Set<Int> = ignores[ownerCharacterId]?.toSet().orEmpty()

    fun reverseFriendOwners(friendCharacterId: Int): Set<Int> =
        reverseFriends[friendCharacterId]?.toSet().orEmpty()

    fun visibleFriendWorld(
        viewerOwnerId: Int,
        friendCharacterId: Int,
        friendOnlineWorldId: Int,
    ): Int {
        if (friendOnlineWorldId <= 0) {
            return 0
        }
        if (isIgnored(viewerOwnerId, friendCharacterId) || isIgnored(friendCharacterId, viewerOwnerId)) {
            return 0
        }
        val privateChat = chatFilters(friendCharacterId).privateChat
        return when {
            privateChat >= 2 -> 0
            privateChat == 1 && !isFriend(friendCharacterId, viewerOwnerId) -> 0
            else -> friendOnlineWorldId
        }
    }

    fun snapshotFriends(
        ownerCharacterId: Int,
        onlineWorldFor: (Int) -> Int,
    ): List<SocialFriendSnapshotRow> {
        val out = mutableListOf<SocialFriendSnapshotRow>()
        for (friendId in friendIds(ownerCharacterId)) {
            val profile = profile(friendId) ?: continue
            val display = profile.displayName
            if (display.isBlank()) {
                continue
            }
            val rawWorld = onlineWorldFor(friendId)
            val visibleWorld = visibleFriendWorld(ownerCharacterId, friendId, rawWorld)
            out +=
                SocialFriendSnapshotRow(
                    displayName = display,
                    previousDisplayName = profile.previousDisplayName,
                    worldId = visibleWorld,
                )
        }
        return out.sortedBy { it.displayName.lowercase() }
    }

    fun snapshotIgnores(ownerCharacterId: Int): List<SocialIgnoreSnapshotRow> {
        val out = mutableListOf<SocialIgnoreSnapshotRow>()
        for (ignoredId in ignoreIds(ownerCharacterId)) {
            val profile = profile(ignoredId) ?: continue
            val display = profile.displayName
            if (display.isBlank()) {
                continue
            }
            out +=
                SocialIgnoreSnapshotRow(
                    displayName = display,
                    previousDisplayName = profile.previousDisplayName,
                )
        }
        return out.sortedBy { it.displayName.lowercase() }
    }

    fun friendPresenceRecipients(
        friendCharacterId: Int,
        friendOnlineWorldId: Int,
        onlineIndex: OnlinePresenceIndex,
    ): List<FriendPresenceRecipient> {
        val profile = profile(friendCharacterId) ?: return emptyList()
        val out = mutableListOf<FriendPresenceRecipient>()
        for (ownerId in reverseFriendOwners(friendCharacterId)) {
            val ownerWorld = onlineIndex.worldFor(ownerId) ?: continue
            val visibleWorld =
                visibleFriendWorld(
                    viewerOwnerId = ownerId,
                    friendCharacterId = friendCharacterId,
                    friendOnlineWorldId = friendOnlineWorldId,
                )
            if (visibleWorld <= 0) {
                continue
            }
            out +=
                FriendPresenceRecipient(
                    ownerCharacterId = ownerId,
                    ownerWorldId = ownerWorld,
                    friendCharacterId = friendCharacterId,
                    friendDisplayName = profile.displayName,
                    friendPreviousDisplayName = profile.previousDisplayName,
                    visibleWorldId = visibleWorld,
                )
        }
        return out
    }

    fun friendPresenceRecipientsForOffline(
        friendCharacterId: Int,
        lastOnlineWorldId: Int,
        onlineIndex: OnlinePresenceIndex,
    ): List<FriendPresenceRecipient> {
        if (lastOnlineWorldId <= 0) {
            return emptyList()
        }
        val profile = profile(friendCharacterId) ?: return emptyList()
        val out = mutableListOf<FriendPresenceRecipient>()
        for (ownerId in reverseFriendOwners(friendCharacterId)) {
            val ownerWorld = onlineIndex.worldFor(ownerId) ?: continue
            val wasVisible =
                visibleFriendWorld(
                    viewerOwnerId = ownerId,
                    friendCharacterId = friendCharacterId,
                    friendOnlineWorldId = lastOnlineWorldId,
                )
            if (wasVisible <= 0) {
                continue
            }
            out +=
                FriendPresenceRecipient(
                    ownerCharacterId = ownerId,
                    ownerWorldId = ownerWorld,
                    friendCharacterId = friendCharacterId,
                    friendDisplayName = profile.displayName,
                    friendPreviousDisplayName = profile.previousDisplayName,
                    visibleWorldId = 0,
                )
        }
        return out
    }

    fun visiblePresenceRecipients(
        friendCharacterId: Int,
        friendOnlineWorldId: Int,
        onlineIndex: OnlinePresenceIndex,
    ): Map<Int, FriendPresenceRecipient> =
        friendPresenceRecipients(friendCharacterId, friendOnlineWorldId, onlineIndex)
            .associateBy { it.ownerCharacterId }
}
