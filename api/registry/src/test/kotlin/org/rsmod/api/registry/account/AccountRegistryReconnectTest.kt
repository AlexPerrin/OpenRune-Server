package org.rsmod.api.registry.account

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccountRegistryReconnectTest {
    private val registry = AccountRegistry(mockk(), mockk(), mockk())

    @Test
    fun `queued reconnects run once on the next login cycle`() {
        var runs = 0
        registry.queueReconnect { runs++ }
        registry.queueReconnect { runs++ }
        assertEquals(0, runs)
        registry.handleLogins()
        assertEquals(2, runs)
        registry.handleLogins()
        assertEquals(2, runs)
    }

    @Test
    fun `reconnects are not limited by the logins per cycle`() {
        var runs = 0
        repeat(3) { registry.queueReconnect { runs++ } }
        registry.handleLogins(count = 0)
        assertEquals(3, runs)
    }
}
