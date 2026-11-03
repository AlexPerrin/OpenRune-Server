package dev.or2.central.embed

import dev.or2.central.config.CentralConfig

/**
 * Compatibility wrapper for game-server embedded Central (OpenRune-Server same-instance mode).
 */
class OpenRuneCentralEmbeddedServer(
    private val httpPort: Int,
    private val runtime: CentralConfig,
) {
    private val delegate = CentralEmbeddedServer(runtime.withHttpPort(httpPort))

    fun start() = delegate.start()

    fun stop() = delegate.stop()
}

private fun CentralConfig.withHttpPort(port: Int): CentralConfig = copy(http = http.copy(port = port))
