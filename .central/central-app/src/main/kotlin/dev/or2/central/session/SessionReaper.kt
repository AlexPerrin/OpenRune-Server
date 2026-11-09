package dev.or2.central.session

import dev.or2.central.db.repositories.SessionRepository
import dev.or2.central.http.WorldListCache
import dev.or2.central.social.SocialService
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SessionReaper(
    private val sessionRepository: SessionRepository,
    private val socialService: SocialService,
    private val worldListCache: WorldListCache?,
    private val ttlMillis: Long,
    private val scheduler: ScheduledExecutorService,
) {
    private val log = LoggerFactory.getLogger(SessionReaper::class.java)

    fun start() {
        scheduler.scheduleAtFixedRate(
            {
                try {
                    val cutoff = System.currentTimeMillis() - ttlMillis
                    val stale = sessionRepository.listStaleBefore(cutoff)
                    for (session in stale) {
                        val characterId = session.characterId ?: continue
                        socialService.onCharacterOffline(session.worldId, characterId)
                    }
                    val removed = sessionRepository.deleteStale(cutoff)
                    if (removed > 0) {
                        worldListCache?.rebuild()
                    }
                    socialService.pruneStalePresence()
                } catch (e: Exception) {
                    log.warn("Session reaping failed", e)
                }
            },
            1,
            1,
            TimeUnit.MINUTES,
        )
    }
}
