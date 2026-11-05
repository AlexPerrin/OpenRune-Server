package dev.or2.central.notify

import dev.or2.central.db.repositories.SessionRepository
import dev.or2.central.http.WorldListCache
import dev.or2.central.social.SocialService
import dev.or2.central.worldlink.WorldConnectionRegistry

class NotifyBroadcaster(
    private val registry: WorldConnectionRegistry,
    private val sessionRepository: SessionRepository,
    private val socialService: SocialService,
    private val worldListCache: WorldListCache?,
) {
    fun worldsForAccount(accountId: Long): List<Int> = sessionRepository.findDistinctWorldIdsByAccount(accountId)

    fun revokeSessionsForAccount(accountId: Long) {
        val sessions = sessionRepository.listByAccountId(accountId)
        for (session in sessions) {
            val characterId = session.characterId ?: continue
            socialService.onCharacterOffline(session.worldId, characterId)
        }
        sessionRepository.deleteAllForAccount(accountId)
        worldListCache?.rebuild()
    }

    fun push(worldIds: List<Int>, frame: ByteArray, broadcastAll: Boolean = false) {
        val targets =
            when {
                worldIds.isNotEmpty() -> worldIds
                broadcastAll -> registry.registeredWorldIds()
                else -> emptyList()
            }
        registry.broadcast(frame, targets)
    }
}
