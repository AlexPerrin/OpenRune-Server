package dev.or2.central.db.repositories

import dev.or2.sql.OpenRuneSql
import java.sql.Types
import javax.sql.DataSource

class WorldOperationRepository(
    private val dataSource: DataSource,
) {
    fun shouldBlockLoginForScheduledReboot(worldId: Int, nowEpochMs: Long): Boolean =
        dataSource.connection.use { conn ->
            val sql = OpenRuneSql.text("game/worldops/select_active_reboots_for_login.sql")
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, worldId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val rebootAt = rs.getTimestamp("reboot_at").time
                        val createdAt = rs.getTimestamp("created_at").time
                        val noticeSec = ((rebootAt - createdAt) / 1000L).coerceAtLeast(60L)
                        val leadMs = RebootLoginBlockPolicy.leadBlockSeconds(noticeSec) * 1000L
                        val remaining = rebootAt - nowEpochMs
                        if (remaining in 1..leadMs) {
                            return@use true
                        }
                    }
                    false
                }
            }
        }
}

object RebootLoginBlockPolicy {
    fun leadBlockSeconds(noticeSeconds: Long): Long = (noticeSeconds / 10L).coerceIn(30L, 300L)
}
