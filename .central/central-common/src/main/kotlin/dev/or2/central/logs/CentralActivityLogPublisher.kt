package dev.or2.central.logs

import java.util.UUID

fun interface CentralActivityLogPublisher {
    fun publish(log: CentralActivityLog): UUID

    companion object {
        /** Sentinel returned by [None]; no row is written. */
        val NilLogUuid: UUID = UUID(0L, 0L)

        val None: CentralActivityLogPublisher = CentralActivityLogPublisher { NilLogUuid }
    }
}

class DefaultCentralActivityLogPublisher(
    private val repository: CentralActivityLogRepository,
) : CentralActivityLogPublisher {
    override fun publish(log: CentralActivityLog): UUID = repository.insert(log)
}
