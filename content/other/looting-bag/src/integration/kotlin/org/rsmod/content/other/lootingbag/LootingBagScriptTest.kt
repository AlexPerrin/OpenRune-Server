package org.rsmod.content.other.lootingbag

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InvScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.other.lootingbag.LootingBagScript.Companion.BAG
import org.rsmod.content.other.lootingbag.LootingBagScript.Companion.BAG_INV
import org.rsmod.content.other.lootingbag.LootingBagScript.Companion.BAG_OPEN
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class LootingBagScriptTest {
    @Test
    fun GameTestState.`the bag inventory is a saved one`() = runBasicGameTest {
        val type = checkNotNull(ServerCacheManager.getInventory(BAG_INV.asRSCM()))
        assertEquals(InvScope.Perm, type.scope)
    }

    @Test
    fun GameTestState.`storing moves exactly the requested items into the bag`() =
        runInjectedGameTest(LootingBagScript::class, null, LootingBagScript::class) { script ->
            player.inv[0] = InvObj(BAG, 1)
            player.inv[1] = InvObj("obj.coins", 500)
            player.inv[2] = InvObj("obj.bronze_dagger", 1)
            player.inv[3] = InvObj("obj.bronze_dagger", 1)
            player.inv[4] = InvObj("obj.bronze_dagger", 1)
            var stored = false
            with(script) { player.withProtectedAccess { stored = store("obj.coins", 200) } }
            assertTrue(stored)
            with(script) { player.withProtectedAccess { store("obj.bronze_dagger", 5) } }
            val bag = player.invMap.getOrPut(BAG_INV)
            assertEquals(300, player.inv.count("obj.coins"))
            assertEquals(200, bag.count("obj.coins"))
            assertEquals(0, player.inv.count("obj.bronze_dagger"))
            assertEquals(3, bag.count("obj.bronze_dagger"))
            assertEquals(1, player.inv.count(BAG))
        }

    @Test
    fun GameTestState.`the bag and untradeables stay out`() =
        runInjectedGameTest(LootingBagScript::class, null, LootingBagScript::class) { script ->
            player.inv[0] = InvObj(BAG, 1)
            player.inv[1] = InvObj("obj.coal_bag", 1)
            var bagStored = true
            var keyStored = true
            with(script) { player.withProtectedAccess { bagStored = store(BAG, 1) } }
            with(script) { player.withProtectedAccess { keyStored = store("obj.coal_bag", 1) } }
            assertFalse(bagStored)
            assertFalse(keyStored)
            assertEquals(1, player.inv.count(BAG))
            assertEquals(1, player.inv.count("obj.coal_bag"))
            assertEquals(0, player.invMap.getOrPut(BAG_INV).occupiedSpace())
        }

    @Test
    fun GameTestState.`a full bag refuses and keeps the item`() =
        runInjectedGameTest(LootingBagScript::class, null, LootingBagScript::class) { script ->
            val bag = player.invMap.getOrPut(BAG_INV)
            for (slot in 0 until bag.size) bag[slot] = InvObj("obj.bronze_sword", 1)
            player.inv[0] = InvObj("obj.bronze_dagger", 1)
            var stored = true
            with(script) { player.withProtectedAccess { stored = store("obj.bronze_dagger", 1) } }
            assertFalse(stored)
            assertEquals(1, player.inv.count("obj.bronze_dagger"))
            assertEquals(0, bag.count("obj.bronze_dagger"))
        }

    @Test
    fun GameTestState.`an open bag takes tradeable pickups only in the wilderness`() =
        runGameTest(LootingBagScript::class) {
            val dagger = ServerCacheManager.getItem("obj.bronze_dagger".asRSCM())!!
            val coalBag = ServerCacheManager.getItem("obj.coal_bag".asRSCM())!!
            val bag = player.invMap.getOrPut(BAG_INV)
            player.teleport(WILDERNESS)
            player.inv[0] = InvObj(BAG, 1)
            assertNull(player.openBagFor(dagger))
            player.inv[0] = InvObj(BAG_OPEN, 1)
            assertSame(bag, player.openBagFor(dagger))
            assertNull(player.openBagFor(coalBag))
            player.teleport(LUMBRIDGE)
            assertNull(player.openBagFor(dagger))
        }

    private companion object {
        val WILDERNESS = CoordGrid(3100, 3600)
        val LUMBRIDGE = CoordGrid(3222, 3218)
    }
}
