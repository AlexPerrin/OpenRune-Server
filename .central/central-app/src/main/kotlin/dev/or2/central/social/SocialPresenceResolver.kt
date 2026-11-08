package dev.or2.central.social

import dev.or2.central.db.repositories.SessionRepository

class SocialPresenceResolver(
    private val sessionRepository: SessionRepository,
    private val repository: CentralSocialRepository,
    private val onlineIndex: OnlinePresenceIndex,
    private val sessionTtlMillis: Long,
    internal val sessionWorldForCharacter: ((Int, Long) -> Int?)? = null,
) {
    fun worldForFriendSnapshot(
        characterId: Int,
        onStaleEvict: (worldId: Int, characterId: Int) -> Unit,
    ): Int = resolveWithGameDbCheck(characterId, onStaleEvict) ?: 0

    fun worldForPmDelivery(characterId: Int): Int? {
        if (characterId <= 0) {
            return null
        }
        val activeSince = activeSinceMillis()
        sessionWorld(characterId, activeSince)?.let { return it }
        return onlineIndex.worldFor(characterId)
    }

    fun activeCharacterIds(): Set<Int> =
        sessionRepository
            .listActiveOnlineCharacters(activeSinceMillis())
            .map { it.characterId }
            .toSet()

    fun activeCharacterIdsForWorld(worldId: Int): Set<Int> =
        sessionRepository
            .listActiveOnlineCharactersByWorld(worldId, activeSinceMillis())
            .map { it.characterId }
            .toSet()

    fun activeSinceMillis(): Long = System.currentTimeMillis() - sessionTtlMillis.coerceAtLeast(1L)

    private fun sessionWorld(
        characterId: Int,
        activeSince: Long,
    ): Int? {
        sessionWorldForCharacter?.let { return it(characterId, activeSince) }
        return sessionRepository.activeWorldForCharacter(characterId, activeSince)
    }

    private fun resolveWithGameDbCheck(
        characterId: Int,
        onStaleEvict: (worldId: Int, characterId: Int) -> Unit,
    ): Int? {
        if (characterId <= 0) {
            return null
        }
        val activeSince = activeSinceMillis()
        val sessionWorld =
            sessionWorld(characterId, activeSince) ?: run {
                evictIfIndexed(characterId, onStaleEvict)
                return null
            }
        val gameWorld = repository.activeGameWorldForCharacter(characterId, activeSince)
        if (gameWorld == null || gameWorld != sessionWorld) {
            evictIfIndexed(characterId, onStaleEvict)
            return null
        }
        return sessionWorld
    }

    private fun evictIfIndexed(
        characterId: Int,
        onStaleEvict: (worldId: Int, characterId: Int) -> Unit,
    ) {
        val entry = onlineIndex.get(characterId) ?: return
        onStaleEvict(entry.worldId, characterId)
    }
}
