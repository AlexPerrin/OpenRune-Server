package dev.openrune.pack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StockClientScriptsTest {
    private val stock = (0..10196).toSet()

    @Test
    fun `fresh cache ends the stock range at its last group`() {
        assertEquals(10196, StockClientScripts.maxId(stock, setOf(10197, 10198)))
    }

    @Test
    fun `an earlier build's custom scripts right after the stock range are not stock`() {
        val custom = (10197..10209).toSet()
        assertEquals(10196, StockClientScripts.maxId(stock + custom, custom))
    }

    @Test
    fun `a stale custom block far above the stock range is not stock`() {
        val known = stock + setOf(45550, 45553)
        assertEquals(10196, StockClientScripts.maxId(known, setOf(10197, 10198)))
    }

    @Test
    fun `custom ids inside the stock range leave the stock maximum where it is`() {
        assertEquals(10196, StockClientScripts.maxId(stock, setOf(9973, 9974)))
    }

    @Test
    fun `custom ids written over stock scripts in the middle of the range do not lower it`() {
        assertEquals(9972, StockClientScripts.maxId((0..9972).toSet(), (9950..9960).toSet()))
    }

    @Test
    fun `small gaps in the known ids are bridged`() {
        val withGaps = stock - setOf(0, 5000, 5001, 10190)
        assertEquals(10196, StockClientScripts.maxId(withGaps, setOf(10197)))
    }

    @Test
    fun `nothing known gives no maximum`() {
        assertNull(StockClientScripts.maxId(emptySet(), setOf(10197)))
    }
}
