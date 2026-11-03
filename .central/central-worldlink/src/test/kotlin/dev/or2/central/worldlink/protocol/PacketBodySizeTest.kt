package dev.or2.central.worldlink.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class PacketBodySizeTest {
    @Test
    fun broadcastSizesFromFieldTypes() {
        val (min, max) =
            PacketBodySize.fromFields(
                arrayOf(
                    FieldKind.INT,
                    FieldKind.STRING_2048,
                    FieldKind.STRING_2048,
                    FieldKind.STRING_2048,
                ),
            )
        assertEquals(4 + 3 * 2, min)
        assertEquals(4 + 3 * (2 + 2048), max)
    }

    @Test
    fun loginSizesWithOptionalCharacterId() {
        val (min, max) =
            PacketBodySize.fromFields(
                arrayOf(
                    FieldKind.STRING_LOGIN_USERNAME,
                    FieldKind.STRING_LOGIN_PASSWORD,
                    FieldKind.INT_OPTIONAL,
                ),
            )
        assertEquals(4, min)
        assertEquals(
            2 + PacketLimits.LOGIN_USERNAME_MAX_UTF8 + 2 + PacketLimits.LOGIN_PASSWORD_MAX_UTF8 + 4,
            max,
        )
    }

    @Test
    fun fixedTokenIsExactWidth() {
        val (min, max) =
            PacketBodySize.fromFields(
                arrayOf(FieldKind.FIXED_TOKEN),
            )
        val expected = 2 + WorldOpcodes.TOKEN_BYTES
        assertEquals(expected, min)
        assertEquals(expected, max)
    }
}
