package org.rsmod.api.net.rsprot

import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenLoginVerifierTest {
    private val verifier = TokenLoginVerifier(SECRET)

    @Test
    fun `a token the issuer signed names its account`() {
        assertEquals("tester", verifier.accountName(token()))
        assertEquals("a.b", verifier.accountName(token(account = "a.b")))
    }

    @Test
    fun `an android token ends at its string terminator`() {
        assertEquals("tester", verifier.accountName(token() + "\u0000"))
        assertNull(verifier.accountName(token(secret = OTHER_SECRET) + "\u0000" + token()))
    }

    @Test
    fun `a token signed by another secret is refused`() {
        assertNull(verifier.accountName(token(secret = OTHER_SECRET)))
    }

    @Test
    fun `an expired token is refused`() {
        assertNull(verifier.accountName(token(exp = Instant.now().epochSecond - 1)))
    }

    @Test
    fun `a malformed token is refused`() {
        assertNull(verifier.accountName("not-a-token"))
        assertNull(verifier.accountName(token(account = "")))
        assertNull(verifier.accountName(token().substringBeforeLast('.') + ".!!!!"))
        assertNull(verifier.accountName(token().replace("tester", "admin")))
    }

    private fun token(
        account: String = "tester",
        exp: Long = Instant.now().epochSecond + 300,
        secret: String = SECRET,
    ): String {
        val body = "$account.$exp"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"))
        return "$body.${Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.toByteArray()))}"
    }

    private companion object {
        private val SECRET = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        private val OTHER_SECRET = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
    }
}
