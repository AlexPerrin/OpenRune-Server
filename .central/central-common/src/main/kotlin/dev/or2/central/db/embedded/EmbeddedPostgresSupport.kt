package dev.or2.central.db.embedded

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.DriverManager
import java.util.concurrent.TimeUnit

/** Shared embedded PostgreSQL lifecycle: reuse a running same-PGDATA instance or stop stale processes. */
object EmbeddedPostgresSupport {
    data class Session(
        val jdbcUrl: String,
        val user: String = "postgres",
        val password: String = "",
        val port: Int,
        val dataDir: Path,
    )

    sealed interface StartPlan {
        data class Reuse(val session: Session) : StartPlan

        data object StartNew : StartPlan
    }

    fun planStart(dataDir: Path): StartPlan {
        val absolute = dataDir.toAbsolutePath().normalize()
        val info = readPostmasterInfo(absolute) ?: return StartPlan.StartNew

        val handle = ProcessHandle.of(info.pid)
        val alive = handle.isPresent && handle.get().isAlive

        if (!alive) {
            deletePidFile(absolute)
            return StartPlan.StartNew
        }

        if (!pathsEqualForOs(absolute, info.dataDir)) {
            return StartPlan.StartNew
        }

        if (canConnect(info.port)) {
            return StartPlan.Reuse(
                Session(
                    jdbcUrl = jdbcUrlForPort(info.port),
                    port = info.port,
                    dataDir = absolute,
                ),
            )
        }

        forceStop(absolute)
        return StartPlan.StartNew
    }

    /** After [AutoCloseable.close] on zonky, ensure the postmaster for [dataDir] is no longer running. */
    fun ensureStopped(dataDir: Path) {
        val absolute = dataDir.toAbsolutePath().normalize()
        val info = readPostmasterInfo(absolute) ?: return
        val handle = ProcessHandle.of(info.pid)
        if (handle.isPresent && handle.get().isAlive) {
            forceStop(absolute)
        } else {
            deletePidFile(absolute)
        }
    }

    fun forceStop(dataDir: Path) {
        val absolute = dataDir.toAbsolutePath().normalize()
        val info = readPostmasterInfo(absolute)
        if (info != null) {
            val handle = ProcessHandle.of(info.pid)
            if (handle.isPresent && handle.get().isAlive) {
                stopProcessTree(handle.get())
            }
        }
        deletePidFile(absolute)
        waitForPortRelease(info?.port ?: readConfiguredPort(absolute))
    }

    fun jdbcUrlForPort(port: Int): String = "jdbc:postgresql://127.0.0.1:$port/postgres"

    private data class PostmasterInfo(
        val pid: Long,
        val dataDir: Path,
        val port: Int,
    )

    private fun readPostmasterInfo(dataDir: Path): PostmasterInfo? {
        val pidFile = dataDir.resolve("postmaster.pid")
        if (!Files.isRegularFile(pidFile)) {
            return null
        }
        val lines =
            runCatching { Files.readAllLines(pidFile, StandardCharsets.UTF_8) }.getOrNull()
                ?: return null
        if (lines.size < 4) {
            deletePidFile(dataDir)
            return null
        }
        val pid = lines[0].trim().toLongOrNull() ?: run {
            deletePidFile(dataDir)
            return null
        }
        val listedDataDir =
            runCatching { Paths.get(lines[1].trim()).toAbsolutePath().normalize() }.getOrNull()
                ?: run {
                    deletePidFile(dataDir)
                    return null
                }
        val port = lines[3].trim().toIntOrNull() ?: readConfiguredPort(dataDir) ?: return null
        return PostmasterInfo(pid, listedDataDir, port)
    }

    private fun readConfiguredPort(dataDir: Path): Int? {
        val conf = dataDir.resolve("postgresql.conf")
        if (!Files.isRegularFile(conf)) {
            return null
        }
        return runCatching {
            Files.readAllLines(conf, StandardCharsets.UTF_8)
                .asSequence()
                .map { it.trim() }
                .filter { it.startsWith("port") && it.contains("=") }
                .mapNotNull { line ->
                    line.substringAfter("=").trim().substringBefore("#").trim().toIntOrNull()
                }
                .firstOrNull()
        }.getOrNull()
    }

    private fun canConnect(port: Int): Boolean =
        runCatching {
            DriverManager.getConnection(jdbcUrlForPort(port), "postgres", "").use { conn ->
                conn.isValid(2)
            }
        }.getOrDefault(false)

    private fun waitForPortRelease(port: Int?) {
        if (port == null) {
            return
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline && canConnect(port)) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    private fun stopProcessTree(root: ProcessHandle) {
        val descendants = root.descendants().toList()
        for (child in descendants) {
            runCatching { child.destroyForcibly() }
        }
        runCatching { root.destroyForcibly() }
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadlineNanos && root.isAlive) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    private fun deletePidFile(dataDir: Path) {
        runCatching { Files.deleteIfExists(dataDir.resolve("postmaster.pid")) }
    }

    private fun pathsEqualForOs(a: Path, b: Path): Boolean =
        a.normalize().toString().equals(b.normalize().toString(), ignoreCase = true)
}
