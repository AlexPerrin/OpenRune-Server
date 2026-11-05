package dev.or2.central.social

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SocialGraphLoader(
    private val repository: CentralSocialRepository,
) {
    fun loadInto(store: SocialGraphStore) {
        store.clear()
        for ((owner, friend) in repository.loadAllFriends()) {
            store.loadFriendEdge(owner, friend)
        }
        for ((owner, ignored) in repository.loadAllIgnores()) {
            store.loadIgnoreEdge(owner, ignored)
        }
        for ((characterId, filters) in repository.loadAllFilters()) {
            store.loadFilters(characterId, filters)
        }
        for (nameRow in repository.loadAllCharacterNames()) {
            store.loadCharacterName(nameRow)
        }
    }
}

class SocialGraphPersistence(
    private val repository: CentralSocialRepository,
) {
    private val log = LoggerFactory.getLogger(SocialGraphPersistence::class.java)
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "central-social-graph-persist").apply { isDaemon = true }
        }

    fun enqueueFriendAdd(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ) {
        executor.execute {
            runCatching {
                repository.persistFriendAdd(ownerCharacterId, friendCharacterId)
            }.onFailure {
                log.warn(
                    "Social persist friend add failed owner={} friend={}",
                    ownerCharacterId,
                    friendCharacterId,
                    it,
                )
            }
        }
    }

    fun enqueueFriendDelete(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ) {
        executor.execute {
            runCatching {
                repository.persistFriendDelete(ownerCharacterId, friendCharacterId)
            }.onFailure {
                log.warn(
                    "Social persist friend delete failed owner={} friend={}",
                    ownerCharacterId,
                    friendCharacterId,
                    it,
                )
            }
        }
    }

    fun enqueueIgnoreAdd(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ) {
        executor.execute {
            runCatching {
                repository.persistIgnoreAdd(ownerCharacterId, ignoredCharacterId)
            }.onFailure {
                log.warn(
                    "Social persist ignore add failed owner={} ignored={}",
                    ownerCharacterId,
                    ignoredCharacterId,
                    it,
                )
            }
        }
    }

    fun enqueueIgnoreDelete(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ) {
        executor.execute {
            runCatching {
                repository.persistIgnoreDelete(ownerCharacterId, ignoredCharacterId)
            }.onFailure {
                log.warn(
                    "Social persist ignore delete failed owner={} ignored={}",
                    ownerCharacterId,
                    ignoredCharacterId,
                    it,
                )
            }
        }
    }

    fun enqueuePrivateChatFilter(
        characterId: Int,
        privateChat: Int,
    ) {
        executor.execute {
            runCatching {
                repository.persistPrivateChatFilter(characterId, privateChat)
            }.onFailure {
                log.warn("Social persist private chat filter failed characterId={}", characterId, it)
            }
        }
    }

    fun flush() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
