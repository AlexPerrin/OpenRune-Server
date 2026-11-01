package dev.openrune.codec.osrs.impl

import dev.openrune.definition.type.ItemType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemServerCodecTest {
    @Test
    fun `a server config entry with tradeable false makes the item untradeable`() {
        val base = ItemType(id = ID, stockMarket = false)
        val overlay = ItemServerType(ID).apply { tradeable = false }
        val item = ItemServerType(ID)

        with(ItemServerCodec(REV, mapOf(ID to base), mapOf(ID to overlay))) { item.createData() }

        assertFalse(item.tradeable)
        assertFalse(item.stockmarket)
    }

    @Test
    fun `the overlay keeps the cache's grand exchange flag`() {
        val base = ItemType(id = ID, stockMarket = true)
        val overlay = ItemServerType(ID)
        val item = ItemServerType(ID)

        with(ItemServerCodec(REV, mapOf(ID to base), mapOf(ID to overlay))) { item.createData() }

        assertTrue(item.tradeable)
        assertTrue(item.stockmarket)
    }

    @Test
    fun `an overlay does not put a non grand exchange item on the exchange`() {
        val base = ItemType(id = ID, stockMarket = false)
        val overlay = ItemServerType(ID)
        val item = ItemServerType(ID)

        with(ItemServerCodec(REV, mapOf(ID to base), mapOf(ID to overlay))) { item.createData() }

        assertFalse(item.stockmarket)
    }

    private companion object {
        const val ID = 1
        const val REV = 241
    }
}
