package dev.or2.central.db.repositories

import dev.or2.central.social.OnlineCharacter
import dev.or2.sql.OpenRuneSql
import dev.or2.central.util.toHex
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

data class SessionRow(
    val id: Long,
    val accountId: Long,
    val worldId: Int,
    val characterId: Int?,
    val tokenHash: ByteArray,
    val createdAt: Long,
    val lastSeenAt: Long,
)

class SessionRepository(
    private val dataSource: DataSource,
) {
    private val tokenCache = ConcurrentHashMap<String, SessionRow>()

    fun findByTokenHash(tokenHash: ByteArray): SessionRow? {
        val key = tokenHash.toHex()
        tokenCache[key]?.let { return it }
        val sql = OpenRuneSql.text("central/session/find_by_token_hash.sql")
        val row =
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setBytes(1, tokenHash)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@use null
                        readRow(rs)
                    }
                }
            }
        if (row != null) {
            tokenCache[key] = row
        }
        return row
    }

    fun findByCharacterAndWorld(
        characterId: Int,
        worldId: Int,
    ): SessionRow? {
        if (characterId <= 0) {
            return null
        }
        val sql = OpenRuneSql.text("central/session/find_by_character_and_world.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.setInt(2, characterId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    readRow(rs)
                }
            }
        }
    }

    fun touchById(sessionId: Long, nowMillis: Long) {
        val sql = OpenRuneSql.text("central/session/touch_by_id.sql")
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, nowMillis)
                ps.setLong(2, sessionId)
                ps.executeUpdate()
            }
        }
        invalidateCache()
    }

    fun bindCharacterId(
        sessionId: Long,
        characterId: Int,
    ): Boolean {
        if (characterId <= 0) {
            return false
        }
        val sql = OpenRuneSql.text("central/session/bind_character_id.sql")
        val updated =
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, characterId)
                    ps.setLong(2, sessionId)
                    ps.executeUpdate()
                }
            }
        if (updated > 0) {
            invalidateCache()
            return true
        }
        return false
    }

    fun deleteById(sessionId: Long) {
        val sql = OpenRuneSql.text("central/session/delete_by_id.sql")
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, sessionId)
                ps.executeUpdate()
            }
        }
        invalidateCache()
    }

    fun listByWorldId(worldId: Int): List<SessionRow> {
        val sql = OpenRuneSql.text("central/session/list_by_world_id.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(readRow(rs))
                        }
                    }
                }
            }
        }
    }

    fun deleteByWorldId(worldId: Int): Int {
        val sql = OpenRuneSql.text("central/session/delete_by_world_id.sql")
        val count =
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, worldId)
                    ps.executeUpdate()
                }
            }
        if (count > 0) invalidateCache()
        return count
    }

    fun listAllOnlineCharacters(): List<OnlineCharacter> {
        val sql = OpenRuneSql.text("central/session/list_online_characters.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val characterId = rs.getObject("character_id")?.let { (it as Number).toInt() } ?: continue
                            if (characterId <= 0) {
                                continue
                            }
                            add(
                                OnlineCharacter(
                                    characterId = characterId,
                                    worldId = rs.getInt("world_id"),
                                    accountId = rs.getLong("account_id"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun listActiveOnlineCharacters(activeSinceMillis: Long): List<OnlineCharacter> {
        val sql = OpenRuneSql.text("central/session/list_active_online_characters.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, activeSinceMillis)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val characterId = rs.getObject("character_id")?.let { (it as Number).toInt() } ?: continue
                            if (characterId <= 0) {
                                continue
                            }
                            add(
                                OnlineCharacter(
                                    characterId = characterId,
                                    worldId = rs.getInt("world_id"),
                                    accountId = rs.getLong("account_id"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun activeWorldForCharacter(
        characterId: Int,
        activeSinceMillis: Long,
    ): Int? {
        if (characterId <= 0) {
            return null
        }
        val sql = OpenRuneSql.text("central/session/active_world_for_character.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.setLong(2, activeSinceMillis)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return@use null
                    }
                    rs.getInt("world_id").takeIf { it > 0 }
                }
            }
        }
    }

    fun listActiveOnlineCharactersByWorld(
        worldId: Int,
        activeSinceMillis: Long,
    ): List<OnlineCharacter> {
        if (worldId <= 0) {
            return emptyList()
        }
        val sql = OpenRuneSql.text("central/session/list_active_online_characters_by_world.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.setLong(2, activeSinceMillis)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val characterId = rs.getObject("character_id")?.let { (it as Number).toInt() } ?: continue
                            if (characterId <= 0) {
                                continue
                            }
                            add(
                                OnlineCharacter(
                                    characterId = characterId,
                                    worldId = rs.getInt("world_id"),
                                    accountId = rs.getLong("account_id"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun listStaleBefore(beforeMillis: Long): List<SessionRow> {
        val sql = OpenRuneSql.text("central/session/list_stale.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, beforeMillis)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(readRow(rs))
                        }
                    }
                }
            }
        }
    }

    fun listByAccountId(accountId: Long): List<SessionRow> {
        val sql = OpenRuneSql.text("central/session/list_by_account_id.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, accountId)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(readRow(rs))
                        }
                    }
                }
            }
        }
    }

    fun deleteStale(beforeMillis: Long): Int {
        val sql = OpenRuneSql.text("central/session/delete_stale.sql")
        val count =
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setLong(1, beforeMillis)
                    ps.executeUpdate()
                }
            }
        if (count > 0) invalidateCache()
        return count
    }

    fun deleteAllForAccount(accountId: Long): Int {
        val sql = OpenRuneSql.text("central/session/delete_by_account_id.sql")
        val count =
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setLong(1, accountId)
                    ps.executeUpdate()
                }
            }
        if (count > 0) invalidateCache()
        return count
    }

    fun findDistinctWorldIdsByAccount(accountId: Long): List<Int> {
        val sql = OpenRuneSql.text("central/session/select_distinct_world_ids_by_account.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, accountId)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getInt("world_id"))
                        }
                    }
                }
            }
        }
    }

    fun totalOnline(): Int {
        val sql = OpenRuneSql.text("central/session/count_all.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    require(rs.next())
                    rs.getInt(1)
                }
            }
        }
    }

    fun countsByWorld(): Map<Int, Int> {
        val sql = OpenRuneSql.text("central/session/counts_by_world.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(rs.getInt("world_id"), rs.getInt("c"))
                        }
                    }
                }
            }
        }
    }

    fun invalidateCache() {
        tokenCache.clear()
    }

    private fun readRow(rs: ResultSet): SessionRow =
        SessionRow(
            id = rs.getLong("id"),
            accountId = rs.getLong("account_id"),
            worldId = rs.getInt("world_id"),
            characterId = rs.getObject("character_id")?.let { (it as Number).toInt() },
            tokenHash = rs.getBytes("token_hash"),
            createdAt = rs.getLong("created_at"),
            lastSeenAt = rs.getLong("last_seen_at"),
        )
}
