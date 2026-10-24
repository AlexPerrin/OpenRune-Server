package org.rsmod.content.skills.hunter

import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.skills.hunter.CrystalImplingScript.Companion.halfHourSlot
import org.rsmod.content.skills.hunter.CrystalImplingScript.Companion.isVisibleTime

@Execution(ExecutionMode.SAME_THREAD)
class CrystalImplingScriptTest {
    @Test
    fun `a new half hour starts three minutes past`() {
        assertEquals(halfHourSlot(LocalTime.of(10, 3)), halfHourSlot(LocalTime.of(10, 32)))
        assertNotEquals(halfHourSlot(LocalTime.of(10, 32)), halfHourSlot(LocalTime.of(10, 33)))
        assertFalse(isVisibleTime(LocalTime.of(10, 31)))
        assertTrue(isVisibleTime(LocalTime.of(10, 33)))
    }

    @Test
    fun GameTestState.`one crystal impling at a time, on a wiki spot`() =
        runInjectedGameTest(CrystalImplingScript::class, null, CrystalImplingScript::class) {
            script ->
            for (spot in CrystalImplingScript.SPOTS) allocZoneCollision(spot)
            val first = checkNotNull(script.spawn())
            assertTrue(first.coords in CrystalImplingScript.SPOTS)
            val second = checkNotNull(script.spawn())
            assertEquals(second, script.current())
            // The first game tick also spawns one (server start), replacing the second.
            advance(ticks = 1)
            assertFalse(first.isSlotAssigned)
            assertFalse(second.isSlotAssigned)
            assertTrue(checkNotNull(script.current()).coords in CrystalImplingScript.SPOTS)
        }
}
