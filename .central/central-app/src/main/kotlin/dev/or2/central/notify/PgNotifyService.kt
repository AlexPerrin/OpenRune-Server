package dev.or2.central.notify

import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource

class PgNotifyService(
    private val dataSource: DataSource,
    private val handlers: Map<String, PgNotifyHandler>,
) {
    private val log = LoggerFactory.getLogger(PgNotifyService::class.java)

    @Volatile private var stopped = true
    @Volatile private var listenConnection: Connection? = null
    private var thread: Thread? = null

    fun start() {
        if (!stopped) return
        stopped = false
        thread =
            Thread(
                {
                    try {
                        runListenLoop()
                    } catch (e: Exception) {
                        if (!stopped) log.error("PgNotify listen thread failed", e)
                    }
                },
                "central-pg-notify",
            ).apply { isDaemon = true }
        thread!!.start()
    }

    fun stop() {
        stopped = true
        thread?.interrupt()
        runCatching { listenConnection?.close() }
        listenConnection = null
        thread = null
    }

    private fun runListenLoop() {
        while (!stopped && !Thread.currentThread().isInterrupted) {
            try {
                listenOnce()
            } catch (e: Exception) {
                if (stopped) return
                log.warn("LISTEN connection lost, reconnecting: {}", e.message)
                Thread.sleep(500)
            }
        }
    }

    private fun listenOnce() {
        val conn = dataSource.connection.also { listenConnection = it }
        conn.autoCommit = true
        val pg = conn.unwrap(PGConnection::class.java)
        conn.createStatement().use { st ->
            for (channel in handlers.keys) {
                st.addBatch("LISTEN $channel")
            }
            st.executeBatch()
        }
        log.info("Listening for PostgreSQL NOTIFY on {} channel(s)", handlers.size)
        conn.createStatement().use { ping ->
            while (!stopped && !Thread.currentThread().isInterrupted) {
                ping.execute("SELECT 1")
                pg.notifications?.forEach { n ->
                    handlers[n.name]?.handle(n.parameter)
                }
                Thread.sleep(200)
            }
        }
    }
}
