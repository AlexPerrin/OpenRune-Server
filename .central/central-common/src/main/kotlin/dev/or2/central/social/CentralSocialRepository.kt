package dev.or2.central.social

import dev.or2.sql.OpenRuneSql
import javax.sql.DataSource

data class SocialCharacterRow(
    val characterId: Int,
    val accountId: Long,
    val displayName: String,
)

data class ChatFiltersRow(
    val publicChat: Int,
    val privateChat: Int,
    val tradeChat: Int,
)

data class SocialFriendSnapshotRow(
    val displayName: String,
    val previousDisplayName: String?,
    val worldId: Int,
)

data class SocialIgnoreSnapshotRow(
    val displayName: String,
    val previousDisplayName: String?,
)

/** Persistence and bulk-load access for [SocialGraphStore]. Not used on the social hot path. */
class CentralSocialRepository(
    private val dataSource: DataSource,
) {
    data class CharacterNameRow(
        val characterId: Int,
        val displayName: String,
        val previousDisplayName: String?,
    )

    fun loadAllFriends(): List<Pair<Int, Int>> {
        val sql = OpenRuneSql.text("central/social/load_all_friends.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getInt("owner_character_id") to rs.getInt("friend_character_id"))
                        }
                    }
                }
            }
        }
    }

    fun loadAllIgnores(): List<Pair<Int, Int>> {
        val sql = OpenRuneSql.text("central/social/load_all_ignores.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getInt("owner_character_id") to rs.getInt("ignored_character_id"))
                        }
                    }
                }
            }
        }
    }

    fun loadAllFilters(): List<Pair<Int, ChatFiltersRow>> {
        val sql = OpenRuneSql.text("central/social/load_all_filters.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                rs.getInt("character_id") to
                                    ChatFiltersRow(
                                        publicChat = rs.getInt("public_chat"),
                                        privateChat = rs.getInt("private_chat"),
                                        tradeChat = rs.getInt("trade_chat"),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadAllCharacterNames(): List<CharacterNameRow> {
        val sql = OpenRuneSql.text("central/social/load_all_character_names.sql")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val display = clientDisplayName(rs.getString("display_name"))
                            if (display.isBlank()) {
                                continue
                            }
                            add(
                                CharacterNameRow(
                                    characterId = rs.getInt("id"),
                                    displayName = display,
                                    previousDisplayName =
                                        clientPreviousDisplayName(rs.getString("previous_display_name")),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun activeGameWorldForCharacter(
        characterId: Int,
        activeSinceMillis: Long,
    ): Int? {
        if (characterId <= 0) {
            return null
        }
        val sql = OpenRuneSql.text("game/character/characters_select_active_online_world.sql")
        val activeSince = java.sql.Timestamp(activeSinceMillis)
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.setTimestamp(2, activeSince)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return@use null
                    }
                    rs.getInt("online_central_world_id").takeIf { it > 0 }
                }
            }
        }
    }

    fun findCharacterByDisplayName(name: String): SocialCharacterRow? {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) {
            return null
        }
        val sql =
            """
            SELECT id, account_id, display_name
            FROM account_characters
            WHERE LOWER(display_name) = LOWER(?)
            LIMIT 1
            """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, cleaned)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    SocialCharacterRow(
                        characterId = rs.getInt("id"),
                        accountId = rs.getLong("account_id"),
                        displayName = clientDisplayName(rs.getString("display_name") ?: cleaned),
                    )
                }
            }
        }
    }

    fun persistFriendAdd(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ) {
        val sql =
            """
            INSERT INTO character_friends (owner_character_id, friend_character_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, ownerCharacterId)
                ps.setInt(2, friendCharacterId)
                ps.executeUpdate()
            }
        }
    }

    fun persistFriendDelete(
        ownerCharacterId: Int,
        friendCharacterId: Int,
    ) {
        val sql =
            """
            DELETE FROM character_friends
            WHERE owner_character_id = ? AND friend_character_id = ?
            """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, ownerCharacterId)
                ps.setInt(2, friendCharacterId)
                ps.executeUpdate()
            }
        }
    }

    fun persistIgnoreAdd(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ) {
        val sql =
            """
            INSERT INTO character_ignores (owner_character_id, ignored_character_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, ownerCharacterId)
                ps.setInt(2, ignoredCharacterId)
                ps.executeUpdate()
            }
        }
    }

    fun persistIgnoreDelete(
        ownerCharacterId: Int,
        ignoredCharacterId: Int,
    ) {
        val sql =
            """
            DELETE FROM character_ignores
            WHERE owner_character_id = ? AND ignored_character_id = ?
            """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, ownerCharacterId)
                ps.setInt(2, ignoredCharacterId)
                ps.executeUpdate()
            }
        }
    }

    fun persistPrivateChatFilter(
        characterId: Int,
        privateChat: Int,
    ) {
        val sql =
            """
            INSERT INTO character_chat_filters (character_id, private_chat)
            VALUES (?, ?)
            ON CONFLICT (character_id) DO UPDATE SET
                private_chat = EXCLUDED.private_chat
            """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, characterId)
                ps.setInt(2, privateChat.coerceIn(0, 2))
                ps.executeUpdate()
            }
        }
    }

    private fun clientDisplayName(raw: String?): String = raw?.trim().orEmpty()

    private fun clientPreviousDisplayName(raw: String?): String? = raw?.trim()?.takeIf { it.isNotBlank() }
}
