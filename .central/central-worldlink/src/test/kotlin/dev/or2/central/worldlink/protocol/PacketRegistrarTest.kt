package dev.or2.central.worldlink.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PacketRegistrarTest {
    @Test
    fun duplicateOpcodeFailsOnRegistration() {
        @WorldPacketIncoming(
            opcode = WorldOpcodes.OP_WORLD_HELLO,
            name = "DUPLICATE_A",
        )
        class DuplicateA

        @WorldPacketIncoming(
            opcode = WorldOpcodes.OP_WORLD_HELLO,
            name = "DUPLICATE_B",
        )
        class DuplicateB

        val error =
            assertFailsWith<IllegalStateException> {
                PacketRegistrar.registerAll(listOf(DuplicateA::class, DuplicateB::class))
            }
        assertEquals(true, error.message?.contains("0x1") == true)
        assertEquals(true, error.message?.contains("DUPLICATE_A") == true)
        assertEquals(true, error.message?.contains("DUPLICATE_B") == true)
    }

    @Test
    fun sameOpcodeDifferentDirectionIsAllowed() {
        @WorldPacketIncoming(
            opcode = WorldOpcodes.OP_LOGIN,
            name = "LOGIN_IN",
            fields = [
                FieldKind.STRING_LOGIN_USERNAME,
                FieldKind.STRING_LOGIN_PASSWORD,
            ],
        )
        class LoginIn

        @WorldPacketOutgoing(
            opcode = WorldOpcodes.OP_LOGIN_OK,
            name = "LOGIN_OUT",
            fields = [FieldKind.LONG],
        )
        class LoginOut

        val registry = PacketRegistrar.registerAll(listOf(LoginIn::class, LoginOut::class))
        assertEquals(1, registry.inbound.size)
        assertEquals(1, registry.outbound.size)
    }
}
