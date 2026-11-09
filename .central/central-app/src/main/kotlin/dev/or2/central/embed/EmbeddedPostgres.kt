package dev.or2.central.embed

import dev.or2.central.config.CentralConfig
import dev.or2.central.db.embedded.EmbeddedPostgresSupport
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.slf4j.LoggerFactory
import java.net.BindException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

object EmbeddedPostgres {
    private val log = LoggerFactory.getLogger(EmbeddedPostgres::class.java)

    @Volatile
    private var embedded: EmbeddedPostgres? = null

    @Volatile
    private var reused: EmbeddedPostgresSupport.Session? = null

    @Volatile
    private var activeDataDir: Path? = null

    fun resolveConfig(config: CentralConfig): Pair<CentralConfig, Boolean> {
        if (config.jdbc?.url?.isNotBlank() == true || config.database.host.isNotBlank()) {
            return config to false
        }
        synchronized(this) {
            jdbcSession()?.let { session ->
                return config.withEmbeddedJdbc(session.jdbcUrl) to true
            }
            val dataDir = embeddedPgdataPath().also { Files.createDirectories(it) }
            activeDataDir = dataDir
            when (val plan = EmbeddedPostgresSupport.planStart(dataDir)) {
                is EmbeddedPostgresSupport.StartPlan.Reuse -> {
                    reused = plan.session
                    return config.withEmbeddedJdbc(plan.session.jdbcUrl) to true
                }
                EmbeddedPostgresSupport.StartPlan.StartNew -> {
                    val instance = startNewEmbedded(dataDir)
                    embedded = instance
                    return config.withEmbeddedJdbc(instance.getJdbcUrl("postgres", "postgres")) to true
                }
            }
        }
    }

    fun stop() {
        synchronized(this) {
            val owned = embedded
            embedded = null
            reused = null
            val dataDir = activeDataDir
            activeDataDir = null
            runCatching { owned?.close() }
            if (dataDir != null) {
                EmbeddedPostgresSupport.ensureStopped(dataDir)
            }
        }
    }

    private fun jdbcSession(): EmbeddedPostgresSupport.Session? {
        embedded?.let {
            return EmbeddedPostgresSupport.Session(
                jdbcUrl = it.getJdbcUrl("postgres", "postgres"),
                port = -1,
                dataDir = activeDataDir ?: embeddedPgdataPath(),
            )
        }
        return reused
    }

    private fun startNewEmbedded(dataDir: Path): EmbeddedPostgres {
        try {
            return EmbeddedPostgres.builder()
                .setDataDirectory(dataDir.toFile())
                .setCleanDataDirectory(false)
                .setPGStartupWait(Duration.ofSeconds(30))
                .start()
        } catch (t: Throwable) {
            if (!isBindException(t)) {
                throw t
            }
            log.warn("Embedded PostgreSQL port in use; stopping stale instance and retrying (PGDATA={})", dataDir)
            EmbeddedPostgresSupport.forceStop(dataDir)
            return EmbeddedPostgres.builder()
                .setDataDirectory(dataDir.toFile())
                .setCleanDataDirectory(false)
                .setPGStartupWait(Duration.ofSeconds(30))
                .start()
        }
    }

    private fun isBindException(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            if (current is BindException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun CentralConfig.withEmbeddedJdbc(url: String): CentralConfig =
        copy(
            jdbc = dev.or2.central.config.JdbcConfig(url = url),
            database = database.copy(user = "postgres", password = ""),
        )

    private fun embeddedPgdataPath(): Path {
        val env = System.getenv("OPENRUNE_EMBEDDED_PGDATA")?.trim()?.takeIf { it.isNotEmpty() }
        return Path.of(env ?: ".data/pgdata").toAbsolutePath().normalize()
    }
}
