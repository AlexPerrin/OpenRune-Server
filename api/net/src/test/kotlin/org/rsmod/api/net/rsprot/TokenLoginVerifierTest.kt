package org.rsmod.api.net.rsprot

import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenLoginVerifierTest {
    private val verifier = TokenLoginVerifier(SECRET)

    @Test
    fun `a token the issuer signed names its account`() {
        assertAccepted("tester", token())
        assertAccepted("a.b", token(account = "a.b"))
    }

    @Test
    fun `a token signed by another secret is refused for its mac`() {
        assertRefused(TokenLogin.Refusal.Mac, "tester", token(secret = OTHER_SECRET))
        assertRefused(TokenLogin.Refusal.Mac, "admin", token().replace("tester", "admin"))
    }

    @Test
    fun `an expired token is refused for its expiry`() {
        assertRefused(TokenLogin.Refusal.Expiry, "tester", token(exp = Instant.now().epochSecond - 1))
    }

    @Test
    fun `a malformed token is refused for its shape`() {
        assertRefused(TokenLogin.Refusal.Shape, null, "not-a-token")
        assertRefused(TokenLogin.Refusal.Shape, null, "a.b")
        assertRefused(TokenLogin.Refusal.Shape, "", token(account = ""))
        assertRefused(TokenLogin.Refusal.Shape, "tester", "tester.soon.AAAA")
        assertRefused(TokenLogin.Refusal.Shape, "tester", token().substringBeforeLast('.') + ".!!!!")
    }

    @Test
    fun `a refusal line names its cause, length and account but not the mac`() {
        val token = token(secret = OTHER_SECRET)
        val login = verifier.verify(token)
        val mac = token.substringAfterLast('.')
        assertEquals("Token login refused: mac (length=${token.length}, account='tester')", login.refused("mac"))
        assertEquals(
            "Token login refused: no account (length=${token.length}, account='tester')",
            login.refused("no account"),
        )
        assertEquals(
            "Token login refused: Central Banned (length=${token.length}, account='tester')",
            login.refused("Central ${LoginResponse.Banned}"),
        )
        assertFalse(login.refused("mac").contains(mac))
        assertEquals("Token login refused: shape (length=11, account=none)", verifier.verify("not-a-token").refused("shape"))
    }

    private fun assertAccepted(account: String, token: String) {
        val login = verifier.verify(token)
        assertNull(login.refusal)
        assertEquals(account, login.account)
    }

    private fun assertRefused(refusal: TokenLogin.Refusal, account: String?, token: String) {
        val login = verifier.verify(token)
        assertEquals(refusal, login.refusal)
        assertEquals(account, login.account)
        assertEquals(token.length, login.length)
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
