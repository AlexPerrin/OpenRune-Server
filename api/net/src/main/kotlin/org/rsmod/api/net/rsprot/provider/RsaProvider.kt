package org.rsmod.api.net.rsprot.provider

import java.io.BufferedReader
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Security
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Properties
import net.rsprot.crypto.rsa.RsaKeyPair
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader

object RsaProvider {
    fun from(file: Path): RsaKeyPair {
        val properties = file.resolveSibling(SHARED_KEY_FILE)
        if (Files.isRegularFile(properties)) return fromProperties(properties)
        return Files.newBufferedReader(file).toKeyPair()
    }

    /**
     * osrs-plugins-reverse: our own test server's key (`server/data/rsa.properties`, hex
     * `modulus` and `private_exponent`), so app builds made for that server log in here too.
     */
    private fun fromProperties(file: Path): RsaKeyPair {
        val properties = Properties().apply { Files.newBufferedReader(file).use(::load) }
        fun hex(key: String) = BigInteger(checkNotNull(properties.getProperty(key)) { "$key missing in $file" }, 16)
        return RsaKeyPair(hex("private_exponent"), hex("modulus"))
    }

    private const val SHARED_KEY_FILE = "rsa.properties"

    private fun BufferedReader.toKeyPair(): RsaKeyPair =
        PemReader(this).use { reader ->
            val pem = reader.readPemObject()
            val keySpec = PKCS8EncodedKeySpec(pem.content)
            Security.addProvider(BouncyCastleProvider())
            val factory = KeyFactory.getInstance("RSA", "BC")
            val privateKey = factory.generatePrivate(keySpec) as RSAPrivateKey
            val exponent = privateKey.privateExponent
            val modulus = privateKey.modulus
            RsaKeyPair(exponent, modulus)
        }
}
