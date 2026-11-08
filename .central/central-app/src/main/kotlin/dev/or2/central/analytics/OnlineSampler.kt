package dev.or2.central.analytics

import dev.or2.central.db.repositories.OnlineSampleRepository
import dev.or2.central.db.repositories.SessionRepository

class OnlineSampler(
    private val sessionRepository: SessionRepository,
    private val onlineSampleRepository: OnlineSampleRepository,
) {
    fun sampleNow() {
        val timestamp = System.currentTimeMillis()
        val worldCounts = sessionRepository.countsByWorld()
        onlineSampleRepository.insertSnapshot(timestamp, worldCounts)
    }
}
