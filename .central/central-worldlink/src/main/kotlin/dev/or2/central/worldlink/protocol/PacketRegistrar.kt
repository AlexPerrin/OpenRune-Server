package dev.or2.central.worldlink.protocol

import kotlin.reflect.KClass

object PacketRegistrar {
    data class Registry(
        val inbound: Map<Int, PacketDef>,
        val outbound: Map<Int, PacketDef>,
    )

    fun registerAll(types: List<KClass<*>>): Registry {
        val inbound = linkedMapOf<Int, PacketDef>()
        val outbound = linkedMapOf<Int, PacketDef>()

        for (type in types) {
            val def = parsePacketDefinition(type)
            val target =
                when (def.direction) {
                    PacketDirection.INBOUND -> inbound
                    PacketDirection.OUTBOUND -> outbound
                }
            val previous = target.put(def.opcode, def)
            if (previous != null) {
                error(
                    "Duplicate ${def.direction.name.lowercase()} packet opcode " +
                        "0x${def.opcode.toString(16)}: " +
                        "'${previous.name}' (${previous.sourceClass}) vs " +
                        "'${def.name}' (${def.sourceClass})",
                )
            }
            when (def.direction) {
                PacketDirection.INBOUND -> PacketRegistry.registerInbound(def.opcode, def.name)
                PacketDirection.OUTBOUND -> PacketRegistry.registerOutbound(def.opcode, def.name)
            }
        }

        return Registry(inbound, outbound)
    }

    private fun parsePacketDefinition(type: KClass<*>): PacketDef {
        val incoming = type.annotations.filterIsInstance<WorldPacketIncoming>().singleOrNull()
        val outgoing = type.annotations.filterIsInstance<WorldPacketOutgoing>().singleOrNull()

        val (opcode, name, direction, fields, allowedBodyBytes) =
            when {
                incoming != null && outgoing != null ->
                    error("${type.qualifiedName} has both @WorldPacketIncoming and @WorldPacketOutgoing")
                incoming != null ->
                    Quint(
                        incoming.opcode,
                        incoming.name,
                        PacketDirection.INBOUND,
                        incoming.fields,
                        incoming.allowedBodyBytes,
                    )
                outgoing != null ->
                    Quint(
                        outgoing.opcode,
                        outgoing.name,
                        PacketDirection.OUTBOUND,
                        outgoing.fields,
                        outgoing.allowedBodyBytes,
                    )
                else ->
                    error("${type.qualifiedName} is missing @WorldPacketIncoming or @WorldPacketOutgoing")
            }

        val (minBody, maxBody) = PacketBodySize.fromFields(fields)
        val allowed = allowedBodyBytes.takeIf { it.isNotEmpty() }

        return PacketDef(
            opcode = opcode,
            name = name,
            direction = direction,
            minBodyBytes = minBody,
            maxBodyBytes = maxBody,
            fields = fields.toList(),
            allowedBodyBytes = allowed,
            sourceClass = type.simpleName ?: type.qualifiedName.orEmpty(),
        )
    }

    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
