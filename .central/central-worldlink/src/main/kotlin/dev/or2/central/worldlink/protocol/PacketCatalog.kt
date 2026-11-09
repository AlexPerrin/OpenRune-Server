package dev.or2.central.worldlink.protocol

import dev.or2.central.auth.PasswordAuthConfig

/**
 * Registry of inbound/outbound packet shapes loaded from packet codec annotations.
 * Also validates frame sizes and a few structured payloads (login-fail script, UTF-8 chunk lists).
 */
object PacketCatalog {
    private val registry: PacketRegistrar.Registry = PacketRegistrar.registerAll(PacketDiscovery.all())

    val inbound: Map<Int, PacketDef> = registry.inbound
    val outbound: Map<Int, PacketDef> = registry.outbound

    fun inboundName(opcode: Int): String? = inbound[opcode]?.name

    fun isKnownInbound(opcode: Int): Boolean = opcode in inbound

    fun validateInboundBody(opcode: Int, bodyBytes: Int): String? =
        validateBody(inbound[opcode], bodyBytes, unknownReason = "unknown_opcode")

    fun validateGameToCentralBody(opcode: Int, bodyLen: Int): String? = validateInboundBody(opcode, bodyLen)

    fun validateCentralToGameBody(opcode: Int, bodyLen: Int): String? =
        validateBody(outbound[opcode], bodyLen, unknownReason = "unexpected_opcode")

    fun validateCentralToGameFrame(frame: ByteArray): String? {
        if (frame.isEmpty()) return "empty"
        val op = frame[0].toInt() and 0xFF
        val bodyLen = frame.size - 1
        val sizeError = validateCentralToGameBody(op, bodyLen)
        if (sizeError != null) return sizeError
        return frameValidators[op]?.invoke(frame)
    }

    fun describeFailure(reason: String): String = FailureDescriptions.text(reason)

    private fun validateBody(
        spec: PacketDef?,
        bodyBytes: Int,
        unknownReason: String,
    ): String? {
        if (spec == null) return unknownReason

        spec.allowedBodyBytes?.let { allowed ->
            if (bodyBytes !in allowed) return spec.sizeFailure("size")
            return null
        }

        if (bodyBytes < spec.minBodyBytes) return spec.sizeFailure("short")
        if (bodyBytes > spec.maxBodyBytes) return spec.sizeFailure("long")
        return null
    }

    private val frameValidators: Map<Int, (ByteArray) -> String?> =
        mapOf(
            WorldOpcodes.OP_LOGIN_FAIL to ::validateLoginFailFrame,
            WorldOpcodes.OP_SERVER_REBOOT to ::validateServerRebootFrame,
            WorldOpcodes.OP_SERVER_BROADCAST to ::validateServerBroadcastFrame,
            WorldOpcodes.OP_SERVER_DISPLAY_NAME_SYNC to ::validateServerDisplayNameSyncFrame,
            WorldOpcodes.OP_SERVER_DISCORD_ID_SYNC to ::validateServerDiscordIdSyncFrame,
        )

    private fun validateLoginFailFrame(frame: ByteArray): String? {
        val bodyLen = frame.size - 1
        if (bodyLen == 4) return null
        val buf = frame.copyOfRange(5, frame.size)
        var offset = 0
        repeat(3) {
            if (offset + 2 > buf.size) return "login_fail_script"
            val chunk = ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
            offset += 2
            if (chunk > WorldOpcodes.LOGIN_FAIL_SCRIPT_LINE_MAX_UTF8_BYTES) return "login_fail_script_line"
            if (offset + chunk > buf.size) return "login_fail_script"
            offset += chunk
        }
        return if (offset != buf.size) "login_fail_trailing" else null
    }

    private fun validateServerRebootFrame(frame: ByteArray): String? {
        val bodyLen = frame.size - 1
        val min = 1 + 4 + 8 + 2
        if (bodyLen < min) return "server_reboot_short"
        val msgLen = ((frame[1 + 1 + 4 + 8].toInt() and 0xFF) shl 8) or (frame[1 + 1 + 4 + 8 + 1].toInt() and 0xFF)
        if (msgLen > 2048) return "server_reboot_msg_len"
        if (min + msgLen != bodyLen) return "server_reboot_msg_mismatch"
        return null
    }

    private fun validateServerBroadcastFrame(frame: ByteArray): String? =
        validateUtf8Chunks(frame, 1 + 4, 3, 2048, "server_broadcast")

    private fun validateServerDisplayNameSyncFrame(frame: ByteArray): String? =
        validateUtf8Chunks(frame, 1 + 8 + 4, 2, 96, "server_display_name_sync")

    private fun validateServerDiscordIdSyncFrame(frame: ByteArray): String? =
        validateUtf8Chunks(frame, 1 + 8, 1, 96, "server_discord_id_sync")

    private fun validateUtf8Chunks(
        frame: ByteArray,
        start: Int,
        chunks: Int,
        maxChunk: Int,
        prefix: String,
    ): String? {
        var offset = start
        val end = frame.size
        repeat(chunks) {
            if (offset + 2 > end) return "${prefix}_truncated"
            val chunk = ((frame[offset].toInt() and 0xFF) shl 8) or (frame[offset + 1].toInt() and 0xFF)
            offset += 2
            if (chunk > maxChunk) return "${prefix}_chunk_len"
            if (offset + chunk > end) return "${prefix}_chunk_data"
            offset += chunk
        }
        return if (offset != end) "${prefix}_trailing" else null
    }

    private object FailureDescriptions {
        private val static =
            mapOf(
                "empty" to "frame was empty (expected at least an opcode byte)",
                "unknown_opcode" to "opcode is not valid for game-to-Central frames",
                "unexpected_opcode" to "opcode is not defined for this direction in the protocol",
                "login_fail_script" to "LOGIN_FAIL optional script trailer is truncated or malformed",
                "login_fail_script_line" to "LOGIN_FAIL script line exceeds max UTF-8 length",
                "login_fail_trailing" to "LOGIN_FAIL has unexpected trailing bytes after the script lines",
                "server_reboot_msg_len" to "SERVER_REBOOT message UTF-8 block length is invalid",
                "server_reboot_msg_mismatch" to "SERVER_REBOOT declared message length does not match frame size",
                "server_broadcast_truncated" to "SERVER_BROADCAST is truncated before a length-prefixed string",
                "server_broadcast_chunk_len" to "SERVER_BROADCAST string length exceeds maximum UTF-8 size",
                "server_broadcast_chunk_data" to "SERVER_BROADCAST string data is truncated",
                "server_broadcast_trailing" to "SERVER_BROADCAST has unexpected trailing bytes",
                "server_display_name_sync_truncated" to "SERVER_DISPLAY_NAME_SYNC is truncated before a length-prefixed string",
                "server_display_name_sync_chunk_len" to "SERVER_DISPLAY_NAME_SYNC string length exceeds maximum UTF-8 size",
                "server_display_name_sync_chunk_data" to "SERVER_DISPLAY_NAME_SYNC string data is truncated",
                "server_display_name_sync_trailing" to "SERVER_DISPLAY_NAME_SYNC has unexpected trailing bytes",
            )

        fun text(reason: String): String {
            static[reason]?.let { return it }
            if (reason.endsWith("_short")) {
                val packet = reason.removeSuffix("_short").uppercase()
                return "$packet body is shorter than the minimum for this opcode"
            }
            if (reason.endsWith("_long")) {
                val packet = reason.removeSuffix("_long").uppercase()
                return "$packet body is longer than the maximum for this opcode"
            }
            if (reason.endsWith("_size")) {
                val packet = reason.removeSuffix("_size").uppercase()
                if (packet == "HELLO_ACK") {
                    return "HELLO_ACK body must be empty (legacy) or ${PasswordAuthConfig.WIRE_BYTES} bytes (password auth config)"
                }
                return "$packet body length is not valid for this opcode"
            }
            return "validation code: $reason"
        }
    }
}
