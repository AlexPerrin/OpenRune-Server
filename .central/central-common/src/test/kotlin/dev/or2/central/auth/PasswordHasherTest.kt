package dev.or2.central.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test
    fun argon2RoundTrip() {
        val hasher = CompositePasswordHasher("argon2", 12)
        val stored = hasher.hash("my-secret")
        assertTrue(stored.startsWith("\$argon2"))
        assertTrue(hasher.verify(stored, "my-secret"))
        assertFalse(hasher.verify(stored, "wrong"))
    }

    @Test
    fun bcryptRoundTrip() {
        val hasher = CompositePasswordHasher("bcrypt", 10)
        val stored = hasher.hash("my-secret")
        assertTrue(stored.startsWith("\$2"))
        assertTrue(hasher.verify(stored, "my-secret"))
        assertFalse(hasher.verify(stored, "wrong"))
    }

    @Test
    fun compositeAutoDetectsStoredPrefix() {
        val hasher = CompositePasswordHasher("argon2", 12)
        val argonStored = hasher.hash("pw")
        val bcryptHasher = CompositePasswordHasher("bcrypt", 10)
        val bcryptStored = bcryptHasher.hash("pw")
        assertTrue(hasher.verify(argonStored, "pw"))
        assertTrue(hasher.verify(bcryptStored, "pw"))
        assertFalse(hasher.verify(argonStored, "wrong"))
    }

    @Test
    fun bcryptDefaultShouldUpgradeArgon2Hashes() {
        val bcryptHasher = CompositePasswordHasher("bcrypt", 12)
        val argonHasher = CompositePasswordHasher("argon2", 12)
        val argonHash = argonHasher.hash("pw")
        assertTrue(bcryptHasher.shouldUpgrade(argonHash))
    }

    @Test
    fun passwordAuthWireRoundTrip() {
        val config =
            PasswordAuthConfig(
                passwordHasher = "argon2",
                argon2Iterations = 24,
                argon2MemoryKib = 32768,
            )
        val wire = PasswordAuthConfig.encodeWire(config)
        assertEquals(PasswordAuthConfig.WIRE_BYTES, wire.size)
        val decoded = PasswordAuthConfig.decodeWire(wire)
        assertEquals("argon2", decoded.passwordHasher)
        assertEquals(24, decoded.argon2Iterations)
        assertEquals(32768, decoded.argon2MemoryKib)
    }
}
