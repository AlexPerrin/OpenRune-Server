package dev.or2.central

import dev.or2.central.config.CentralConfig
import dev.or2.central.embed.CentralEmbeddedServer

fun main() {
    val config = CentralConfig.load()
    val server = CentralEmbeddedServer(config)
    server.start()
    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop()
        },
    )
    Thread.currentThread().join()
}
