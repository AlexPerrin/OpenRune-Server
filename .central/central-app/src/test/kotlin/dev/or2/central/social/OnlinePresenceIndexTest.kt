package dev.or2.central.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnlinePresenceIndexTest {
    @Test
    fun putAndWorldFor() {
        val index = OnlinePresenceIndex()
        index.put(10, 1, 100L)
        assertEquals(1, index.worldFor(10))
        assertTrue(index.isOnline(10, 1))
        assertFalse(index.isOnline(10, 2))
    }

    @Test
    fun removeClearsEntry() {
        val index = OnlinePresenceIndex()
        index.put(10, 1, 100L)
        val removed = index.remove(10)
        assertEquals(10, removed?.characterId)
        assertNull(index.worldFor(10))
    }

    @Test
    fun removeAllOnWorld() {
        val index = OnlinePresenceIndex()
        index.put(10, 1, 100L)
        index.put(11, 1, 101L)
        index.put(12, 2, 102L)
        val removed = index.removeAllOnWorld(1)
        assertEquals(2, removed.size)
        assertNull(index.worldFor(10))
        assertEquals(2, index.worldFor(12))
    }

    @Test
    fun hydrateReplacesState() {
        val index = OnlinePresenceIndex()
        index.put(1, 1, 1L)
        index.hydrate(listOf(OnlineCharacter(2, 3, 4L)))
        assertNull(index.worldFor(1))
        assertEquals(3, index.worldFor(2))
    }
}
