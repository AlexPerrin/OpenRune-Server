package dev.or2.central.worldlink.protocol

import dev.or2.central.auth.PasswordAuthConfig
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldHelloPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketCatalogTest {
    @Test
    fun worldHelloSizesFromAnnotation() {
        val spec = PacketCatalog.inbound[WorldOpcodes.OP_WORLD_HELLO]!!
        assertEquals(12, spec.minBodyBytes)
        assertEquals(12 + PacketLimits.WORLD_KEY_MAX_BYTES, spec.maxBodyBytes)
    }

    @Test
    fun loginSizesFromAnnotation() {
        val spec = PacketCatalog.inbound[WorldOpcodes.OP_LOGIN]!!
        assertEquals(4, spec.minBodyBytes)
        assertEquals(
            2 + PacketLimits.LOGIN_USERNAME_MAX_UTF8 + 2 + PacketLimits.LOGIN_PASSWORD_MAX_UTF8 + 4,
            spec.maxBodyBytes,
        )
    }

    @Test
    fun heartbeatTokenIsFixedWidth() {
        val spec = PacketCatalog.inbound[WorldOpcodes.OP_HEARTBEAT]!!
        val expected = 2 + WorldOpcodes.TOKEN_BYTES
        assertEquals(expected, spec.minBodyBytes)
        assertEquals(expected, spec.maxBodyBytes)
    }

    @Test
    fun validateAcceptsHelloWithinBounds() {
        assertNull(PacketCatalog.validateInboundBody(WorldOpcodes.OP_WORLD_HELLO, 12))
        assertNull(PacketCatalog.validateInboundBody(WorldOpcodes.OP_WORLD_HELLO, 12 + PacketLimits.WORLD_KEY_MAX_BYTES))
        assertEquals("world_hello_short", PacketCatalog.validateInboundBody(WorldOpcodes.OP_WORLD_HELLO, 11))
        assertEquals("world_hello_long", PacketCatalog.validateInboundBody(WorldOpcodes.OP_WORLD_HELLO, 13 + PacketLimits.WORLD_KEY_MAX_BYTES))
    }

    @Test
    fun pushSubscribeIsEmptyBody() {
        val spec = PacketCatalog.inbound[WorldOpcodes.OP_PUSH_SUBSCRIBE]!!
        assertEquals(0, spec.minBodyBytes)
        assertEquals(0, spec.maxBodyBytes)
    }

    @Test
    fun socialSyncOkSizesFromAnnotation() {
        val spec = PacketCatalog.outbound[WorldOpcodes.OP_WORLD_SOCIAL_SYNC_OK]!!
        assertEquals(7, spec.minBodyBytes)
        assertTrue(spec.maxBodyBytes > 40_000)
    }

    @Test
    fun serverPrivateMessageSizesFromAnnotation() {
        val spec = PacketCatalog.outbound[WorldOpcodes.OP_SERVER_PRIVATE_MESSAGE]!!
        assertEquals(17, spec.minBodyBytes)
        assertTrue(spec.maxBodyBytes > 1000)
    }

    @Test
    fun helloAckAllowsLegacyOrAuthWire() {
        assertNull(PacketCatalog.validateCentralToGameBody(WorldOpcodes.OP_HELLO_ACK, 0))
        assertNull(PacketCatalog.validateCentralToGameBody(WorldOpcodes.OP_HELLO_ACK, PasswordAuthConfig.WIRE_BYTES))
        assertEquals("hello_ack_size", PacketCatalog.validateCentralToGameBody(WorldOpcodes.OP_HELLO_ACK, 1))
    }

    @Test
    fun discoversWorldHelloPacket() {
        assertEquals("WORLD_HELLO", PacketCatalog.inbound[WorldOpcodes.OP_WORLD_HELLO]?.name)
        assertEquals(WorldHelloPacket::class.simpleName, PacketCatalog.inbound[WorldOpcodes.OP_WORLD_HELLO]?.sourceClass)
    }
}
