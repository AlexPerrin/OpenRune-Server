package dev.or2.central.notify

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

object PgNotifyDiscovery {
    private const val HANDLER_PACKAGE = "dev.or2.central.notify.handlers"

    private val discovered: List<KClass<out PgNotifyHandler>> by lazy { scan() }

    fun all(): List<KClass<out PgNotifyHandler>> = discovered

    private fun scan(): List<KClass<out PgNotifyHandler>> {
        val reflections =
            Reflections(
                ConfigurationBuilder()
                    .forPackage(HANDLER_PACKAGE)
                    .addScanners(Scanners.TypesAnnotated),
            )

        val types =
            reflections
                .getTypesAnnotatedWith(PgNotifyChannel::class.java)
                .map { it.kotlin }
                .filterIsInstance<KClass<out PgNotifyHandler>>()
                .filter { klass ->
                    val pkg = klass.java.packageName
                    pkg == HANDLER_PACKAGE || pkg.startsWith("$HANDLER_PACKAGE.")
                }
                .sortedBy { it.qualifiedName }

        require(types.isNotEmpty()) {
            "No @PgNotifyChannel handlers found under $HANDLER_PACKAGE"
        }

        return types
    }
}

object PgNotifyRegistrar {
    fun register(handlers: List<PgNotifyHandler>): Map<String, PgNotifyHandler> {
        val map = linkedMapOf<String, PgNotifyHandler>()
        for (handler in handlers) {
            val channel =
                handler::class.findAnnotation<PgNotifyChannel>()?.channel
                    ?: error("${handler::class.qualifiedName} is missing @PgNotifyChannel")
            val previous = map.put(channel, handler)
            if (previous != null) {
                error(
                    "Duplicate NOTIFY channel '$channel': " +
                        "${previous::class.simpleName} vs ${handler::class.simpleName}",
                )
            }
        }
        return map
    }
}
