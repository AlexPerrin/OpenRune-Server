package org.rsmod.api.net.rsprot

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

internal class TokenLoginVerifier(
    private val issuer: String,
    private val audience: String,
    publicKey: String,
) {
    private val key: PublicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getMimeDecoder().decode(publicKey.replace(PEM_ARMOR, ""))))

    fun accountName(token: String): String? {
        val parts = token.split('.')
        if (parts.size != 3) {
            return null
        }
        return try {
            val header = mapper.readTree(decoder.decode(parts[0]))
            if (header.path("alg").asText() != "RS256") {
                return null
            }
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(key)
            signature.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            if (!signature.verify(decoder.decode(parts[2]))) {
                return null
            }
            val claims = mapper.readTree(decoder.decode(parts[1]))
            val aud = claims.path("aud")
            val exp = claims.path("exp")
            when {
                claims.path("iss").asText() != issuer -> null
                aud.none { it.asText() == audience } && aud.asText() != audience -> null
                !exp.canConvertToLong() || exp.asLong() <= Instant.now().epochSecond -> null
                else -> claims.path("sub").asText().takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        private val PEM_ARMOR = Regex("-----(BEGIN|END) PUBLIC KEY-----")
        private val decoder = Base64.getUrlDecoder()
        private val mapper = ObjectMapper()
    }
}
