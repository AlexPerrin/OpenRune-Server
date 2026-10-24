package org.rsmod.content.travel.fairyrings

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.righthand
import org.rsmod.api.table.FairyringRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.travel.fairyrings.FairyRingScript.Companion.FAIRYTALE_I
import org.rsmod.content.travel.fairyrings.FairyRingScript.Companion.FAIRYTALE_II
import org.rsmod.content.travel.fairyrings.FairyRingScript.Companion.NO_STAFF
import org.rsmod.content.travel.fairyrings.FairyRingScript.Companion.isDestination
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@ResourceLock("QuestRequirements")
class FairyRingScriptTest {
    private val previousPolicy = QuestRequirements.activePolicy()

    @AfterEach
    fun restorePolicy() {
        QuestRequirements.install(previousPolicy)
    }

    private val left = listOf('A', 'D', 'C', 'B')
    private val middle = listOf('I', 'L', 'K', 'J')
    private val right = listOf('P', 'S', 'R', 'Q')

    @Test
    fun `row ids are the dial positions of their code`() {
        val rows = FairyringRow.all()
        assertEquals(64, rows.size)
        for (row in rows) {
            val code = row.code.replace(" ", "")
            val id = left.indexOf(code[0]) * 100 + middle.indexOf(code[1]) * 10 + right.indexOf(code[2])
            assertEquals(id, row.id, code)
        }
        assertEquals((0 until 64).toSet(), rows.map { it.multilocState }.toSet())
    }

    @Test
    fun `the nine unused codes and the house ring (DIQ) have no fixed destination`() {
        val unused = FairyringRow.all().filterNot(::isDestination).map { it.code.replace(" ", "") }.toSet()
        assertEquals(setOf("AIP", "BIR", "BJQ", "CJP", "CJS", "CLQ", "DJQ", "DJS", "DKQ", "DIQ"), unused)
        assertTrue(isDestination(FairyringRow.all().single { it.code == FairyRingScript.ZANARIS_CODE }))
        assertFalse(FairyringRow.all().filter(::isDestination).any { it.destCoord.x < 1000 })
    }

    @Test
    fun GameTestState.`the dramen staff, the lunar staff and its unfinished stages open the dial`() {
        install(QuestRequirementMode.AssumeCompleted)
        for (staff in STAVES) {
            runGameTest(FairyRingScript::class) {
                val ring = placeRing()
                player.righthand = InvObj(staff)
                player.opLoc2(ring)
                advance(ticks = 2)
                assertModalOpen(FairyRingScript.DIAL_INTERFACE)
                assertMessageNotSent(NO_STAFF)
            }
        }
    }

    @Test
    fun GameTestState.`no staff is refused`() {
        install(QuestRequirementMode.AssumeCompleted)
        runGameTest(FairyRingScript::class) {
            val ring = placeRing()
            player.righthand = null
            player.opLoc2(ring)
            assertRefused()
        }
    }

    @Test
    fun GameTestState.`the elite Lumbridge and Draynor diary needs no staff`() {
        install(QuestRequirementMode.AssumeCompleted)
        runGameTest(FairyRingScript::class) {
            val ring = placeRing()
            player.righthand = null
            player.setVarBit("varbit.lumbridge_diary_elite_complete", 1)
            player.opLoc2(ring)
            advance(ticks = 2)
            assertModalOpen(FairyRingScript.DIAL_INTERFACE)
        }
    }

    @Test
    fun GameTestState.`respect-progress refuses players without the Fairytale quests`() {
        install(QuestRequirementMode.RespectProgress)
        runGameTest(FairyRingScript::class) {
            val ring = placeRing()
            player.righthand = InvObj("obj.dramen_staff")
            player.opLoc2(ring)
            assertRefused()
        }
    }

    @Test
    fun GameTestState.`the quest gate needs Fairytale I and Fairytale II`() {
        install(QuestRequirementMode.VirtualCompletions, setOf(FAIRYTALE_I))
        runGameTest(FairyRingScript::class) {
            player.righthand = InvObj("obj.dramen_staff")
            assertFalse(FairyRingScript.canUseNetwork(player))
            install(QuestRequirementMode.VirtualCompletions, setOf(FAIRYTALE_II))
            assertFalse(FairyRingScript.canUseNetwork(player))
            install(QuestRequirementMode.VirtualCompletions, setOf(FAIRYTALE_I, FAIRYTALE_II))
            assertTrue(FairyRingScript.canUseNetwork(player))
        }
    }

    /** Messages are captured per tick, so advance until the refusal (or give up after 3 ticks). */
    private fun GameTestScope.assertRefused() {
        var refused = false
        repeat(3) {
            if (!refused) {
                advance()
                refused = runCatching { assertMessageSent(NO_STAFF) }.isSuccess
            }
        }
        assertTrue(refused)
        assertModalNotOpen(FairyRingScript.DIAL_INTERFACE)
    }

    private fun install(mode: QuestRequirementMode, completions: Set<String> = emptySet()) {
        QuestRequirements.install(QuestRequirementPolicy(mode, virtualCompletions = completions))
    }

    private fun GameTestScope.placeRing() =
        placeMapLoc(RING_COORDS, FairyRingScript.HOME_HUB).also { player.teleport(RING_COORDS.translateX(-1)) }

    private companion object {
        val RING_COORDS = CoordGrid(0, 50, 50, 34, 31)
        val STAVES = FairyRingScript.STAVES
    }
}
