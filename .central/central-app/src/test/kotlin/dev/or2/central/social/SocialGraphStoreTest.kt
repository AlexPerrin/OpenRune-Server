package dev.or2.central.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SocialGraphStoreTest {
    private fun graphWithAliceAndBob(): SocialGraphStore {
        val graph = SocialGraphStore()
        graph.putCharacterName(1, "Alice", null)
        graph.putCharacterName(2, "Bob", null)
        graph.loadFriendEdge(1, 2)
        return graph
    }

    @Test
    fun findCharacterByDisplayNameIgnoresCase() {
        val graph = SocialGraphStore()
        graph.putCharacterName(1, "Mark123", null)
        assertEquals(1, graph.findCharacterByDisplayName("mark123"))
        assertEquals(1, graph.findCharacterByDisplayName("MARK123"))
    }

    @Test
    fun friendAndReverseIndex() {
        val graph = graphWithAliceAndBob()
        assertTrue(graph.isFriend(1, 2))
        assertEquals(setOf(1), graph.reverseFriendOwners(2))
    }

    @Test
    fun privateChatFriendsOnlyBlocksNonFriends() {
        val graph = graphWithAliceAndBob()
        graph.loadFilters(2, ChatFiltersRow(0, 1, 0))
        assertEquals(0, graph.visibleFriendWorld(1, 2, 5))
        graph.loadFriendEdge(2, 1)
        assertEquals(5, graph.visibleFriendWorld(1, 2, 5))
    }

    @Test
    fun ignoreBlocksVisibility() {
        val graph = graphWithAliceAndBob()
        graph.loadIgnoreEdge(1, 2)
        assertEquals(0, graph.visibleFriendWorld(1, 2, 5))
    }

    @Test
    fun snapshotFriendsUsesOnlineLookup() {
        val graph = graphWithAliceAndBob()
        val friends = graph.snapshotFriends(1) { friendId -> if (friendId == 2) 7 else 0 }
        assertEquals(1, friends.size)
        assertEquals("Bob", friends[0].displayName)
        assertEquals(7, friends[0].worldId)
    }

    @Test
    fun friendPresenceRecipientsForOffline() {
        val graph = graphWithAliceAndBob()
        val online = OnlinePresenceIndex()
        online.put(1, 3, 10L)
        online.put(2, 5, 20L)
        val recipients = graph.friendPresenceRecipientsForOffline(2, 5, online)
        assertEquals(1, recipients.size)
        assertEquals(1, recipients[0].ownerCharacterId)
        assertEquals(0, recipients[0].visibleWorldId)
    }
}
