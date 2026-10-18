package org.rsmod.api.net.rsprot.player

import io.mockk.mockk
import net.rsprot.protocol.api.Session
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.net.rsprot.RspClient
import org.rsmod.api.net.rsprot.player.PlayerReconnect.Companion.canReconnect
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

class PlayerReconnectTest {
    @Test
    fun `a reconnect matches the login whose seed it presents`() {
        val seed = intArrayOf(1, 2, 3, 4)
        assertTrue(PlayerReconnect.isSameLogin(seed, "tester", seed.copyOf(), "tester"))
        assertTrue(PlayerReconnect.isSameLogin(seed, "Tester", seed.copyOf(), "tester"))
        assertFalse(PlayerReconnect.isSameLogin(seed, "tester", intArrayOf(1, 2, 3, 5), "tester"))
    }

    @Test
    fun `the username must agree when both logins carry one`() {
        val seed = intArrayOf(9, 8, 7, 6)
        assertFalse(PlayerReconnect.isSameLogin(seed, "tester", seed.copyOf(), "other"))
        assertTrue(PlayerReconnect.isSameLogin(seed, "", seed.copyOf(), "tester"))
        assertTrue(PlayerReconnect.isSameLogin(seed, "tester", seed.copyOf(), ""))
    }

    @Test
    fun `a player whose logout is under way cannot be reconnected to`() {
        assertTrue(Player().canReconnect())
        assertFalse(Player().apply { loggingOut = true }.canReconnect())
        assertFalse(Player().apply { pendingLogout = true }.canReconnect())
        assertFalse(Player().apply { manualLogout = true }.canReconnect())
        assertFalse(Player().apply { forceDisconnect = true }.canReconnect())
    }

    @Test
    fun `closing the current session marks the player disconnected`() {
        val session = mockk<Session<Player>>()
        val player = Player()
        player.client = rspClient(session)
        PlayerReconnect.disconnectionHook(player, session).run()
        assertTrue(player.clientDisconnected.get())
    }

    @Test
    fun `the old session closing after a reconnect leaves the player connected`() {
        val oldSession = mockk<Session<Player>>()
        val newSession = mockk<Session<Player>>()
        val player = Player()
        player.client = rspClient(newSession)
        PlayerReconnect.disconnectionHook(player, oldSession).run()
        assertFalse(player.clientDisconnected.get())
    }

    @Suppress("UNCHECKED_CAST")
    private fun rspClient(session: Session<Player>): Client<Any, Any> =
        RspClient(session, mockk(relaxed = true)) as Client<Any, Any>
}
