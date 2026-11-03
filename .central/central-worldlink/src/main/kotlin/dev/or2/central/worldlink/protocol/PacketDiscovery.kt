package dev.or2.central.worldlink.protocol

import kotlin.reflect.KClass
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

/**
 * Discovers [@WorldPacketIncoming] / [@WorldPacketOutgoing] classes under [scanned subpackages][PacketDiscovery].
 * Add a new packet under `protocol/packets/...` or `protocol/discord/`.
 */
object PacketDiscovery {
    private const val ROOT_PACKAGE = "dev.or2.central.worldlink.protocol"

    /** Subpackages scanned for [@WorldPacketIncoming] / [@WorldPacketOutgoing]. */
    private val scannedSubpackages =
        listOf(
            "$ROOT_PACKAGE.packets",
            "$ROOT_PACKAGE.discord",
        )

    private val discovered: List<KClass<*>> by lazy { scan() }

    fun all(): List<KClass<*>> = discovered

    private fun scan(): List<KClass<*>> {
        val reflections =
            Reflections(
                ConfigurationBuilder()
                    .forPackage(ROOT_PACKAGE)
                    .addScanners(Scanners.TypesAnnotated),
            )

        val types =
            (
                reflections.getTypesAnnotatedWith(WorldPacketIncoming::class.java) +
                    reflections.getTypesAnnotatedWith(WorldPacketOutgoing::class.java)
            )
                .map { it.kotlin }
                .filter { klass ->
                    val pkg = klass.java.packageName
                    scannedSubpackages.any { root -> pkg == root || pkg.startsWith("$root.") }
                }
                .sortedBy { it.qualifiedName }

        require(types.isNotEmpty()) {
            "No @WorldPacketIncoming/@WorldPacketOutgoing classes found under $scannedSubpackages"
        }

        return types
    }
}
