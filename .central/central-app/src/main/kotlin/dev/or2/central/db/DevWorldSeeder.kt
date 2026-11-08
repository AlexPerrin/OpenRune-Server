package dev.or2.central.db

import dev.or2.central.config.CentralConfig
import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

object DevWorldSeeder {
    fun seedIfEnabled(config: CentralConfig, dataSource: DataSource) {
        if (!config.devWorld.autoCreate) return
        val loginMessage = config.loginWelcomeMessage()
        val worldActivity = config.serverName
        dataSource.connection.use { conn ->
            conn.prepareStatement(OpenRuneSql.text("central/seed/dev_realm.sql")).use { ps ->
                ps.setString(1, loginMessage)
                ps.executeUpdate()
            }
            conn.prepareStatement(OpenRuneSql.text("central/seed/dev_world.sql")).use { ps ->
                ps.setString(1, worldActivity)
                ps.executeUpdate()
            }
        }
    }
}
