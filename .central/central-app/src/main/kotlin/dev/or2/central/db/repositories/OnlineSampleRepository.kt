package dev.or2.central.db.repositories

import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

class OnlineSampleRepository(
    private val dataSource: DataSource,
) {
    fun insertSnapshot(sampledAtMillis: Long, countsByWorld: Map<Int, Int>) {
        if (countsByWorld.values.none { it > 0 }) return
        val sql = OpenRuneSql.text("central/online_samples/insert.sql")
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                countsByWorld.forEach { (worldId, count) ->
                    if (count <= 0) return@forEach
                    ps.setLong(1, sampledAtMillis)
                    ps.setInt(2, worldId)
                    ps.setInt(3, count)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }
}
