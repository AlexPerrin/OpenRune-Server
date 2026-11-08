package dev.or2.central.http

import dev.or2.central.db.repositories.WorldAuthRow
import dev.or2.central.util.constantTimeEquals
import dev.or2.central.util.sha256

class WorldKeyVerifier {
    fun verify(row: WorldAuthRow, providedKeyUtf8: ByteArray): Boolean {
        val perWorld = row.worldKeySha256
        if (perWorld == null || perWorld.isEmpty()) {
            return providedKeyUtf8.isEmpty()
        }
        return constantTimeEquals(sha256(providedKeyUtf8), perWorld)
    }
}
