package org.rsmod.api.net.rsprot

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class TokenLoginVerifier(secret: String) {
    private val key = SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256")

    fun verify(token: String): TokenLogin {
        val macStart = token.lastIndexOf('.')
        val expiryStart = token.lastIndexOf('.', macStart - 1)
        val account = if (expiryStart >= 0) token.substring(0, expiryStart) else null
        fun refuse(refusal: TokenLogin.Refusal) = TokenLogin(token.length, account, refusal)
        if (expiryStart <= 0) {
            return refuse(TokenLogin.Refusal.Shape)
        }
        val expiry =
            token.substring(expiryStart + 1, macStart).toLongOrNull()
                ?: return refuse(TokenLogin.Refusal.Shape)
        val signature =
            try {
                Base64.getUrlDecoder().decode(token.substring(macStart + 1))
            } catch (_: IllegalArgumentException) {
                return refuse(TokenLogin.Refusal.Shape)
            }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val expected = mac.doFinal(token.substring(0, macStart).toByteArray(Charsets.UTF_8))
        if (!MessageDigest.isEqual(expected, signature)) {
            return refuse(TokenLogin.Refusal.Mac)
        }
        if (expiry <= Instant.now().epochSecond) {
            return refuse(TokenLogin.Refusal.Expiry)
        }
        return TokenLogin(token.length, account, null)
    }
}

internal class TokenLogin(val length: Int, val account: String?, val refusal: Refusal?) {
    enum class Refusal {
        Shape,
        Mac,
        Expiry,
    }

    fun refused(cause: Any): String =
        "Token login refused: $cause (length=$length, account=${account?.let { "'$it'" } ?: "none"})"
}
