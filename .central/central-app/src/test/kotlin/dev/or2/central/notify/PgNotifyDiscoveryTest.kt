package dev.or2.central.notify

import dev.or2.central.notify.handlers.CharacterMuteNotifyHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PgNotifyDiscoveryTest {
    @Test
    fun discoversAnnotatedHandlers() {
        val types = PgNotifyDiscovery.all()
        assertTrue(types.contains(CharacterMuteNotifyHandler::class))
        assertTrue(types.size >= 7)
    }
}
