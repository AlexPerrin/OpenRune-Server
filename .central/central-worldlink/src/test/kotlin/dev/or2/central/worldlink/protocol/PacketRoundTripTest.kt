package dev.or2.central.worldlink.protocol

import dev.or2.central.account.Rights
import dev.or2.central.worldlink.protocol.packets.incoming.impl.WorldHelloPacket
import dev.or2.central.auth.PasswordAuthConfig
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.HelloAckPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.HelloRejectPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.LoginFailPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.LoginOkPacket
import dev.or2.central.worldlink.protocol.packets.outgoing.impl.WorldSocialFailPacket
import dev.or2.central.worldlink.protocol.social.SocialPackets
import dev.or2.central.worldlink.protocol.social.SocialSyncFriend
import dev.or2.central.worldlink.protocol.social.SocialSyncIgnore
import dev.or2.central.worldlink.protocol.social.SocialSyncSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketRoundTripTest {
    @Test
    fun helloAckRoundTrip() {
        val config = PasswordAuthConfig(passwordHasher = "bcrypt", bcryptCost = 12)
        val frame = HelloAckPacket.encode(config)
        val reader = readInboundFrame(frame)
        assertEquals(WorldOpcodes.OP_HELLO_ACK, reader.opcode)
        val decoded = HelloAckPacket.decodeAuthConfig(reader)
        assertEquals(config.passwordHasher, decoded.passwordHasher)
        assertEquals(config.bcryptCost, decoded.bcryptCost)
    }

    @Test
    fun helloAckLegacyEmptyBody() {
        val frame = byteArrayOf(WorldOpcodes.OP_HELLO_ACK.toByte())
        val decoded = HelloAckPacket.decodeAuthConfig(frame)
        assertEquals(PasswordAuthConfig.DEFAULT, decoded)
    }

    @Test
    fun helloRejectRoundTrip() {
        val frame = HelloRejectPacket.encode(HelloRejectPacket.Payload(WorldOpcodes.HELLO_REASON_BAD_KEY))
        val reader = readInboundFrame(frame)
        assertEquals(WorldOpcodes.OP_HELLO_REJECT, reader.opcode)
        assertEquals(WorldOpcodes.HELLO_REASON_BAD_KEY, reader.readUnsignedByte())
    }

    @Test
    fun loginFailWithScriptLines() {
        val frame =
            LoginFailPacket.encode(
                LoginFailPacket.Payload(
                    code = WorldOpcodes.LOGIN_FAIL_WORLD_ACCESS,
                    clientProtocolVersion = 7,
                    scriptLines = Triple("line1", "line2", "line3"),
                ),
            )
        val reader = readInboundFrame(frame)
        assertEquals(WorldOpcodes.OP_LOGIN_FAIL, reader.opcode)
        assertEquals(WorldOpcodes.LOGIN_FAIL_WORLD_ACCESS, reader.readInt())
        assertEquals("line1", reader.readUtf8LenPrefixed())
        assertEquals("line2", reader.readUtf8LenPrefixed())
        assertEquals("line3", reader.readUtf8LenPrefixed())
    }

    @Test
    fun loginOkRoundTrip() {
        val token = ByteArray(WorldOpcodes.TOKEN_BYTES) { it.toByte() }
        val frame =
            LoginOkPacket.encode(
                LoginOkPacket.Payload(
                    token = token,
                    accountId = 42L,
                    rights = Rights.ADMINISTRATOR,
                    realmDevMode = false,
                    clientProtocolVersion = 7,
                ),
            )
        val reader = readInboundFrame(frame)
        assertEquals(WorldOpcodes.OP_LOGIN_OK, reader.opcode)
        assertEquals(WorldOpcodes.TOKEN_BYTES, reader.readUnsignedShort())
        reader.readFully(WorldOpcodes.TOKEN_BYTES)
        assertEquals(42L, reader.readLong())
        assertEquals("ADMINISTRATOR", reader.readUtf8LenPrefixed())
        assertEquals(0, reader.trailingUnreadBytes())
    }

    @Test
    fun worldHelloDecodeBuildsPayload() {
        val writer = FrameWriter(WorldOpcodes.OP_WORLD_HELLO)
        writer.writeInt(WorldOpcodes.MAGIC)
        writer.writeShort(7)
        writer.writeInt(1)
        val key = byteArrayOf(1, 2, 3)
        writer.writeShort(key.size)
        writer.writeBytes(key)
        val payload = WorldHelloPacket.decode(readInboundFrame(writer.build()))
        assertEquals(WorldOpcodes.MAGIC, payload.magic)
        assertEquals(7, payload.version)
        assertEquals(1, payload.worldId)
        assertTrue(key.contentEquals(payload.key))
    }

    @Test
    fun socialSyncOkRoundTrip() {
        val snapshot =
            SocialSyncSnapshot(
                publicChat = 0,
                privateChat = 1,
                tradeChat = 2,
                friends =
                    listOf(
                        SocialSyncFriend("Alice", "alice", 255),
                        SocialSyncFriend("Bob", null, 0),
                    ),
                ignores = listOf(SocialSyncIgnore("Troll", "troll")),
            )
        val frame = SocialPackets.encodeSocialSyncOk(snapshot)
        val reader = readInboundFrame(frame)
        assertEquals(WorldOpcodes.OP_WORLD_SOCIAL_SYNC_OK, reader.opcode)
        val decoded = SocialPackets.decodeSocialSyncOk(reader)
        assertEquals(snapshot.publicChat, decoded.publicChat)
        assertEquals(snapshot.friends.size, decoded.friends.size)
        assertEquals("Alice", decoded.friends[0].displayName)
        assertEquals(255, decoded.friends[0].worldId)
    }

    @Test
    fun socialSyncInboundRoundTrip() {
        val frame = SocialPackets.encodeSocialSync(characterId = 6)
        val reader = readInboundFrame(frame)
        assertEquals(WorldOpcodes.OP_WORLD_SOCIAL_SYNC, reader.opcode)
        val decoded = SocialPackets.decodeSocialSync(reader)
        assertEquals(6, decoded.characterId)
    }
}
