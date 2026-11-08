package dev.or2.central.db

import org.slf4j.Logger

object PostgresStartupLog {
    fun logMigrations(logger: Logger, jdbcUrl: String, user: String, migrationsApplied: Int) {
        val (host, port, database) = parsePostgresJdbc(jdbcUrl)
        logger.info(
            "PostgreSQL: {}@{}:{}/{} — Flyway applied {} migration(s)",
            user.ifBlank { "postgres" },
            host,
            port,
            database,
            migrationsApplied,
        )
    }

    internal fun parsePostgresJdbc(jdbcUrl: String): Triple<String, Int, String> {
        val withoutPrefix = jdbcUrl.removePrefix("jdbc:postgresql://")
        val pathPart = withoutPrefix.substringBefore('?')
        val hostPort = pathPart.substringBefore('/')
        val database = pathPart.substringAfter('/', "postgres")
        val host = hostPort.substringBefore(':').ifBlank { "localhost" }
        val port = hostPort.substringAfter(':', "5432").toIntOrNull() ?: 5432
        return Triple(host, port, database)
    }
}
