package org.rsmod.content.quest.area

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * Every chat-head animation the content asks for must be a server mesanim; a missing one throws
 * "Error Loading MesAnim" mid-dialogue (the Lumbridge Doomsayer's `mesanim.scared` did).
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MesAnimTypesTest {
    @Test fun `every mesanim used by content resolves`() {
        val missing = USED.filter { name ->
            runCatching { ServerCacheManager.getMesAnim(name.asRSCM()) }.getOrNull() == null
        }
        assertTrue(missing.isEmpty(), "Missing server mesanims: $missing")
    }

    companion object {
        val USED =
            listOf(
                "angry", "bored", "confused", "drunk", "happy", "laugh", "mad_laugh", "neutral",
                "quiz", "sad", "scared", "shifty", "shocked", "short", "silent", "very_mad", "worried",
            ).map { "mesanim.$it" }

        @JvmStatic @BeforeAll fun cache() = QuestTalkFixture.setUpCache()

        @JvmStatic @AfterAll fun restore() = QuestTalkFixture.restore()
    }
}
