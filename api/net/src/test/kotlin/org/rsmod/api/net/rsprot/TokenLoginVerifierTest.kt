package org.rsmod.api.net.rsprot

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenLoginVerifierTest {
    private val issuerKey = rsaKeyPair()
    private val verifier =
        TokenLoginVerifier(ISSUER, AUDIENCE, Base64.getEncoder().encodeToString(issuerKey.public.encoded))

    @Test
    fun `a token the issuer signed for this audience names its account`() {
        assertEquals("tester", verifier.accountName(token()))
        assertEquals("tester", verifier.accountName(token(aud = """["other","$AUDIENCE"]""")))
    }

    @Test
    fun `a token signed by another key is refused`() {
        assertNull(verifier.accountName(token(key = rsaKeyPair())))
    }

    @Test
    fun `a token of another issuer or audience is refused`() {
        assertNull(verifier.accountName(token(iss = "https://other.example")))
        assertNull(verifier.accountName(token(aud = "\"other\"")))
    }

    @Test
    fun `an expired token is refused`() {
        assertNull(verifier.accountName(token(exp = Instant.now().epochSecond - 1)))
    }

    @Test
    fun `a malformed token is refused`() {
        assertNull(verifier.accountName("not-a-token"))
        assertNull(verifier.accountName(token(alg = "none")))
        assertNull(verifier.accountName(token(sub = "")))
        assertNull(verifier.accountName(token().dropLast(4) + "!!!!"))
    }

    private fun token(
        key: KeyPair = issuerKey,
        alg: String = "RS256",
        iss: String = ISSUER,
        aud: String = "\"$AUDIENCE\"",
        exp: Long = Instant.now().epochSecond + 300,
        sub: String = "tester",
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"$alg","typ":"JWT"}""".toByteArray())
        val claims =
            encoder.encodeToString("""{"iss":"$iss","aud":$aud,"exp":$exp,"sub":"$sub"}""".toByteArray())
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(key.private)
        signature.update("$header.$claims".toByteArray())
        return "$header.$claims.${encoder.encodeToString(signature.sign())}"
    }

    private companion object {
        private const val ISSUER = "https://auth.example"
        private const val AUDIENCE = "openrune"

        private fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
