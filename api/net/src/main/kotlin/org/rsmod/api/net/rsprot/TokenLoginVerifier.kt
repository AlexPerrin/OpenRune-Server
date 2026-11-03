package org.rsmod.api.net.rsprot

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class TokenLoginVerifier(secret: String) {
    private val key = SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256")

    fun accountName(credential: String): String? {
        val token = credential.substringBefore('\u0000')
        val macStart = token.lastIndexOf('.')
        val expiryStart = token.lastIndexOf('.', macStart - 1)
        if (expiryStart <= 0) {
            return null
        }
        val expiry = token.substring(expiryStart + 1, macStart).toLongOrNull() ?: return null
        val signature =
            try {
                Base64.getUrlDecoder().decode(token.substring(macStart + 1))
            } catch (_: IllegalArgumentException) {
                return null
            }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val expected = mac.doFinal(token.substring(0, macStart).toByteArray(Charsets.UTF_8))
        if (!MessageDigest.isEqual(expected, signature) || expiry <= Instant.now().epochSecond) {
            return null
        }
        return token.substring(0, expiryStart)
    }
}
