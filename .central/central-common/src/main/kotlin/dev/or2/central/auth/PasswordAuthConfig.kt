package dev.or2.central.auth

data class PasswordAuthConfig(
    val passwordHasher: String = "bcrypt",
    val bcryptCost: Int = 12,
    val argon2Iterations: Int = 20,
    val argon2MemoryKib: Int = 65536,
) {
    fun toHasher(): PasswordHasher =
        CompositePasswordHasher(
            defaultAlgorithm = passwordHasher,
            bcryptCost = bcryptCost,
            argon2Iterations = argon2Iterations,
            argon2MemoryKib = argon2MemoryKib,
        )

    companion object {
        val DEFAULT: PasswordAuthConfig = PasswordAuthConfig()

        const val WIRE_BYTES: Int = 4

        private const val ALG_BCRYPT: Int = 0
        private const val ALG_ARGON2: Int = 1

        fun encodeWire(config: PasswordAuthConfig): ByteArray {
            val algorithmId =
                when (config.passwordHasher.lowercase()) {
                    "bcrypt" -> ALG_BCRYPT
                    else -> ALG_ARGON2
                }
            val costByte =
                when (algorithmId) {
                    ALG_BCRYPT -> config.bcryptCost.coerceIn(4, 31)
                    else -> config.argon2Iterations.coerceIn(1, 100)
                }
            val memoryKib =
                when (algorithmId) {
                    ALG_BCRYPT -> 0
                    else -> config.argon2MemoryKib.coerceIn(8, 524_288)
                }
            return byteArrayOf(
                algorithmId.toByte(),
                costByte.toByte(),
                (memoryKib ushr 8).toByte(),
                memoryKib.toByte(),
            )
        }

        fun decodeWire(body: ByteArray, offset: Int = 0): PasswordAuthConfig {
            require(body.size - offset >= WIRE_BYTES) { "password auth wire block too short" }
            val algorithmId = body[offset].toInt() and 0xFF
            val costByte = body[offset + 1].toInt() and 0xFF
            val memoryKib = ((body[offset + 2].toInt() and 0xFF) shl 8) or (body[offset + 3].toInt() and 0xFF)
            return when (algorithmId) {
                ALG_BCRYPT ->
                    PasswordAuthConfig(
                        passwordHasher = "bcrypt",
                        bcryptCost = costByte.coerceIn(4, 31),
                    )
                else ->
                    PasswordAuthConfig(
                        passwordHasher = "argon2",
                        argon2Iterations = costByte.coerceIn(1, 100),
                        argon2MemoryKib = memoryKib.coerceIn(8, 524_288),
                    )
            }
        }
    }
}
