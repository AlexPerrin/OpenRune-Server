package org.rsmod.content.travel.jewellery

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.table.JewelleryTeleportDestsRow
import org.rsmod.api.table.JewelleryTeleportItemsRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class JewelleryTeleportScriptTest {
    @Test
    fun GameTestState.`a use swaps the necklace for one charge less`() =
        runInjectedGameTest(JewelleryTeleportScript::class, null, JewelleryTeleportScript::class) { script ->
            prepare()
            player.inv[0] = obj("obj.necklace_of_minigames_8")
            assertTrue(use(script, "dbrow.jewellery_teleport_games_necklace_8", inventory = true))
            assertEquals(0, player.inv.count("obj.necklace_of_minigames_8"))
            assertEquals(1, player.inv.count("obj.necklace_of_minigames_7"))
            val sent = client.mapOf(MessageGame::message)
            assertTrue("You rub the necklace..." in sent, sent.toString())
            assertTrue(sent.any { "Your games necklace has seven uses left." in it }, sent.toString())
        }

    @Test
    fun GameTestState.`the last ring charge crumbles, the last glory charge leaves the amulet`() =
        runInjectedGameTest(JewelleryTeleportScript::class, null, JewelleryTeleportScript::class) { script ->
            prepare()
            player.inv[0] = obj("obj.ring_of_dueling_1")
            assertTrue(use(script, "dbrow.jewellery_teleport_ring_of_dueling_1", inventory = true))
            assertEquals(0, player.inv.count("obj.ring_of_dueling_1"))
            assertEquals(0, player.inv.occupiedSpace())
            player.worn[NECK_SLOT] = obj("obj.amulet_of_glory_1")
            assertTrue(use(script, "dbrow.jewellery_teleport_amulet_of_glory_1", inventory = false))
            assertEquals(0, player.worn.count("obj.amulet_of_glory_1"))
            assertEquals(1, player.worn.count("obj.amulet_of_glory"))
            val sent = client.mapOf(MessageGame::message)
            assertTrue(sent.any { "Your ring of dueling crumbles to dust." in it }, sent.toString())
            assertTrue(sent.any { "You use your amulet's last charge." in it }, sent.toString())
        }

    @Test
    fun GameTestState.`no item, no charge and no teleport`() =
        runInjectedGameTest(JewelleryTeleportScript::class, null, JewelleryTeleportScript::class) { script ->
            prepare()
            assertFalse(use(script, "dbrow.jewellery_teleport_games_necklace_3", inventory = true))
            assertEquals(0, player.inv.occupiedSpace())
        }

    @Test
    fun GameTestState.`glory, skills necklace and combat bracelet charge lines`() =
        runInjectedGameTest(JewelleryTeleportScript::class, null, JewelleryTeleportScript::class) { script ->
            prepare()
            player.inv[0] = obj("obj.amulet_of_glory_4")
            player.inv[1] = obj("obj.jewl_necklace_of_skills_1")
            player.inv[2] = obj("obj.jewl_bracelet_of_combat_1")
            assertTrue(use(script, "dbrow.jewellery_teleport_amulet_of_glory_4", inventory = true))
            assertTrue(use(script, "dbrow.jewellery_teleport_skills_necklace_1", inventory = true))
            assertTrue(use(script, "dbrow.jewellery_teleport_combat_bracelet_1", inventory = true))
            assertEquals("obj.jewl_necklace_of_skills".asRSCM(RSCMType.OBJ), player.inv[1]?.id)
            assertEquals("obj.jewl_bracelet_of_combat".asRSCM(RSCMType.OBJ), player.inv[2]?.id)
            val sent = client.mapOf(MessageGame::message)
            assertTrue(sent.any { "Your amulet has three charges left." in it }, sent.toString())
            assertTrue(sent.any { "You use your skills necklace's last charge." in it }, sent.toString())
            assertTrue(sent.any { "You use your combat bracelet's last charge." in it }, sent.toString())
        }

    @Test
    fun GameTestState.`farming guild lands inside with 45 farming, corp in the lair`() =
        runInjectedGameTest(JewelleryTeleportScript::class, null, JewelleryTeleportScript::class) { script ->
            val guild = JewelleryTeleportDestsRow.getRow("dbrow.jewellery_teleport_dest_skills_farming_guild")
            assertEquals(CoordGrid(1248, 3719, 0), script.landing(guild, 44))
            assertEquals(CoordGrid(1248, 3725, 0), script.landing(guild, 45))
            val corp = JewelleryTeleportDestsRow.getRow("dbrow.jewellery_teleport_dest_games_corporeal_beast")
            assertEquals(CoordGrid(2964, 4382, 2), script.landing(corp, 99))
            val burthorpe = JewelleryTeleportDestsRow.getRow("dbrow.jewellery_teleport_dest_games_burthorpe")
            assertEquals(CoordGrid(2899, 3553, 0), script.landing(burthorpe, 99))
        }

    private fun GameTestScope.use(script: JewelleryTeleportScript, row: String, inventory: Boolean): Boolean {
        var used = false
        val item = JewelleryTeleportItemsRow.getRow(row)
        with(script) {
            player.withProtectedAccess { used = useCharge(item, if (inventory) player.inv else player.worn) }
        }
        return used
    }

    private fun obj(name: String) = InvObj(name.asRSCM(RSCMType.OBJ), 1)

    private fun GameTestScope.prepare() {
        val center = CoordGrid(0, 50, 50, 20, 20)
        allocZoneCollision(center)
        player.teleport(center)
        player.clearInv()
    }

    private companion object {
        const val NECK_SLOT = 2
    }
}
