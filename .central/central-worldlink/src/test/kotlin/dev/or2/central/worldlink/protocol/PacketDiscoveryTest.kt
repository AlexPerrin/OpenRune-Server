package dev.or2.central.worldlink.protocol

import dev.or2.central.worldlink.protocol.discord.ServerDiscordIdSyncPacket
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldHelloPacket
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PacketDiscoveryTest {
    @Test
    fun discoversAnnotatedPacketClassesAutomatically() {
        val types = PacketDiscovery.all()
        assertTrue(types.contains(WorldHelloPacket::class))
        assertTrue(types.contains(ServerDiscordIdSyncPacket::class))
        assertTrue(types.size >= 25)
    }

    @Test
    fun discordPushOpcodeIsRegisteredForValidation() {
        assertNotNull(PacketCatalog.outbound[WorldOpcodes.OP_SERVER_DISCORD_ID_SYNC])
    }
}
