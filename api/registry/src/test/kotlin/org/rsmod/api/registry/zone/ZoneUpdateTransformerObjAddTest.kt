package org.rsmod.api.registry.zone

import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.obj.ObjScope
import org.rsmod.map.CoordGrid

class ZoneUpdateTransformerObjAddTest {
    @Test
    fun `a private drop carries its public and despawn timers and self ownership`() {
        val obj = drop(ObjScope.Private, despawn = 300, reveal = 160)
        val add = ZoneUpdateTransformer.toPersistentObjAdd(obj, OWNER, cycle = 100) as ObjAdd
        assertEquals(60, add.timeUntilPublic)
        assertEquals(200, add.timeUntilDespawn)
        assertEquals(1, add.ownershipType)
        assertFalse(add.neverBecomesPublic)
    }

    @Test
    fun `a private drop that never turns public says so`() {
        val obj = drop(ObjScope.Private, despawn = 300, reveal = Obj.NEVER)
        val add = ZoneUpdateTransformer.toPersistentObjAdd(obj, OWNER, cycle = 100) as ObjAdd
        assertEquals(0, add.timeUntilPublic)
        assertEquals(200, add.timeUntilDespawn)
        assertTrue(add.neverBecomesPublic)
    }

    @Test
    fun `a public drop is the owner's own and another player's for everyone else`() {
        val obj = drop(ObjScope.Temp, despawn = 300, reveal = Obj.NEVER)
        val mine = ZoneUpdateTransformer.toPersistentObjAdd(obj, OWNER, cycle = 250) as ObjAdd
        val theirs = ZoneUpdateTransformer.toPersistentObjAdd(obj, OTHER, cycle = 250) as ObjAdd
        assertEquals(1, mine.ownershipType)
        assertEquals(2, theirs.ownershipType)
        assertEquals(50, theirs.timeUntilDespawn)
        assertEquals(0, theirs.timeUntilPublic)
        assertFalse(theirs.neverBecomesPublic)
    }

    @Test
    fun `a permanent spawn has no timers and no owner`() {
        val obj = Obj(TILE, ObjEntity(ITEM, 1, ObjScope.Perm.id), 0, Obj.NULL_OBSERVER_ID)
        val add = ZoneUpdateTransformer.toPersistentObjAdd(obj, OTHER, cycle = 1000) as ObjAdd
        assertEquals(0, add.timeUntilDespawn)
        assertEquals(0, add.timeUntilPublic)
        assertEquals(0, add.ownershipType)
    }

    @Test
    fun `timers never go negative`() {
        val obj = drop(ObjScope.Private, despawn = 300, reveal = 160)
        val add = ZoneUpdateTransformer.toPersistentObjAdd(obj, OWNER, cycle = 400) as ObjAdd
        assertEquals(0, add.timeUntilPublic)
        assertEquals(0, add.timeUntilDespawn)
    }

    private fun drop(scope: ObjScope, despawn: Int, reveal: Int): Obj =
        Obj(TILE, ObjEntity(ITEM, 1, scope.id), 0, OWNER, OWNER).apply {
            despawnCycle = despawn
            revealCycle = reveal
        }

    private companion object {
        const val ITEM = 2677
        const val OWNER = 11L
        const val OTHER = 22L
        val TILE = CoordGrid(3222, 3218)
    }
}
