package dev.or2.central.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object DataSourceFactory {
    fun create(config: CentralConfig): HikariDataSource {
        val hikari =
            HikariConfig().apply {
                jdbcUrl = config.resolvedJdbcUrl()
                username = config.resolvedDbUser()
                password = config.resolvedDbPassword()
                maximumPoolSize = config.database.poolSize.coerceIn(1, 128)
                poolName = "central-pool"
                isAutoCommit = true
            }
        return HikariDataSource(hikari)
    }
}
