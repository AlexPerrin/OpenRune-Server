package dev.or2.central.db.repositories

import dev.or2.sql.OpenRuneSql
import java.util.UUID
import javax.sql.DataSource

enum class PunishmentLoginBlock {
    LOCKED,
    BANNED,
}

class PunishmentService(
    private val dataSource: DataSource,
) {
    fun characterBelongsToAccount(characterId: Int, accountId: Long): Boolean {
        val sql = OpenRuneSql.text("game/punishment/verify_character_for_account.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.setLong(2, accountId)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }
    }

    fun blockingLoginSanction(accountId: Long, loginCharacterId: Int?): PunishmentLoginBlock? {
        val sql =
            if (loginCharacterId != null && loginCharacterId > 0) {
                OpenRuneSql.text("game/punishment/select_active_affecting_character.sql")
            } else {
                OpenRuneSql.text("game/punishment/select_active_for_account.sql")
            }
        val kinds =
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    if (loginCharacterId != null && loginCharacterId > 0) {
                        ps.setInt(1, loginCharacterId)
                        ps.setInt(2, loginCharacterId)
                    } else {
                        ps.setLong(1, accountId)
                    }
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                rs.getString("kind")?.let(::add)
                            }
                        }
                    }
                }
            }
        if (kinds.isEmpty()) return null
        return when {
            "locked" in kinds -> PunishmentLoginBlock.LOCKED
            kinds.any { it == "ban" || it == "temp_ban" } -> PunishmentLoginBlock.BANNED
            else -> null
        }
    }

    fun attachCentralActivityLogs(
        punishmentId: Long,
        logUuids: Collection<UUID>,
    ): Int {
        if (logUuids.isEmpty()) return 0
        val sql = OpenRuneSql.text("game/punishment/insert_attached_log.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                var inserted = 0
                logUuids.distinct().forEach { uuid ->
                    ps.setLong(1, punishmentId)
                    ps.setObject(2, uuid)
                    ps.setObject(3, uuid)
                    inserted += ps.executeUpdate()
                }
                inserted
            }
        }
    }

    fun listAttachedCentralActivityLogUuids(punishmentId: Long): List<UUID> {
        val sql = OpenRuneSql.text("game/punishment/select_attached_log_uuids.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, punishmentId)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            rs.getObject("log_uuid", UUID::class.java)?.let(::add)
                        }
                    }
                }
            }
        }
    }

    fun detachCentralActivityLog(
        punishmentId: Long,
        logUuid: UUID,
    ): Boolean {
        val sql = OpenRuneSql.text("game/punishment/delete_attached_central_activity_log.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, punishmentId)
                ps.setObject(2, logUuid)
                ps.executeUpdate() > 0
            }
        }
    }
}
