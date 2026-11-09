package dev.or2.central.notify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgNotifyRegistrarTest {
    @Test
    fun duplicateChannelFails() {
        @PgNotifyChannel("test_channel")
        class HandlerA : PgNotifyHandler {
            override fun handle(payload: String?) = Unit
        }

        @PgNotifyChannel("test_channel")
        class HandlerB : PgNotifyHandler {
            override fun handle(payload: String?) = Unit
        }

        val error =
            assertFailsWith<IllegalStateException> {
                PgNotifyRegistrar.register(listOf(HandlerA(), HandlerB()))
            }
        assertEquals(true, error.message?.contains("test_channel") == true)
    }
}
