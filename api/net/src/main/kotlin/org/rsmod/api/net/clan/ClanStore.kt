package org.rsmod.api.net.clan

import java.sql.Statement
import org.rsmod.api.db.DatabaseConnection

internal object ClanStore {
    private val CREATE_TABLES =
        listOf(
            "CREATE TABLE IF NOT EXISTS clans (" +
                "id SERIAL PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "name_key TEXT NOT NULL UNIQUE, " +
                "created_minute INT NOT NULL, " +
                "resign_at BIGINT NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS clan_members (" +
                "character_id INT PRIMARY KEY, " +
                "clan_id INT NOT NULL REFERENCES clans(id) ON DELETE CASCADE, " +
                "name TEXT NOT NULL, " +
                "rank INT NOT NULL, " +
                "join_runeday INT NOT NULL, " +
                "joined_at BIGINT NOT NULL, " +
                "last_seen INT NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS clan_settings (" +
                "clan_id INT NOT NULL REFERENCES clans(id) ON DELETE CASCADE, " +
                "setting INT NOT NULL, " +
                "int_value INT NOT NULL DEFAULT 0, " +
                "string_value TEXT, " +
                "PRIMARY KEY (clan_id, setting))",
        )

    fun createTables(connection: DatabaseConnection) {
        for (sql in CREATE_TABLES) {
            connection.prepareStatement(sql).use { it.executeUpdate() }
        }
    }

    fun clanIdOf(connection: DatabaseConnection, characterId: Int): Int? =
        connection.prepareStatement("SELECT clan_id FROM clan_members WHERE character_id = ?").use {
            it.setInt(1, characterId)
            it.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    fun nameTaken(connection: DatabaseConnection, name: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM clans WHERE name_key = ?").use {
            it.setString(1, nameKey(name))
            it.executeQuery().use { rs -> rs.next() }
        }

    fun load(connection: DatabaseConnection, clanId: Int): Clan? {
        val clan =
            connection
                .prepareStatement("SELECT name, created_minute, resign_at FROM clans WHERE id = ?")
                .use {
                    it.setInt(1, clanId)
                    it.executeQuery().use { rs ->
                        if (!rs.next()) {
                            return null
                        }
                        Clan(clanId, rs.getString(1), rs.getInt(2), rs.getLong(3))
                    }
                }
        connection
            .prepareStatement(
                "SELECT character_id, name, rank, join_runeday, joined_at, last_seen FROM clan_members " +
                    "WHERE clan_id = ? ORDER BY joined_at, character_id"
            )
            .use {
                it.setInt(1, clanId)
                it.executeQuery().use { rs ->
                    while (rs.next()) {
                        clan.members +=
                            ClanMember(
                                characterId = rs.getInt(1),
                                name = rs.getString(2),
                                rank = rs.getInt(3),
                                joinRuneDay = rs.getInt(4),
                                joinedAt = rs.getLong(5),
                                lastSeenDay = rs.getInt(6),
                            )
                    }
                }
            }
        connection
            .prepareStatement(
                "SELECT setting, int_value, string_value FROM clan_settings WHERE clan_id = ?"
            )
            .use {
                it.setInt(1, clanId)
                it.executeQuery().use { rs ->
                    while (rs.next()) {
                        val string = rs.getString(3)
                        if (string != null) {
                            clan.stringSettings[rs.getInt(1)] = string
                        } else {
                            clan.intSettings[rs.getInt(1)] = rs.getInt(2)
                        }
                    }
                }
            }
        return clan
    }

    fun create(connection: DatabaseConnection, name: String, createdMinute: Int): Int? {
        val sql =
            "INSERT INTO clans (name, name_key, created_minute) VALUES (?, ?, ?) " +
                "ON CONFLICT (name_key) DO NOTHING"
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use {
            it.setString(1, name)
            it.setString(2, nameKey(name))
            it.setInt(3, createdMinute)
            if (it.executeUpdate() == 0) {
                return null
            }
            it.generatedKeys.use { keys -> if (keys.next()) keys.getInt(1) else null }
        }
    }

    fun saveMember(connection: DatabaseConnection, clanId: Int, member: ClanMember) {
        val sql =
            "INSERT INTO clan_members (character_id, clan_id, name, rank, join_runeday, joined_at, last_seen) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (character_id) DO UPDATE SET " +
                "clan_id = EXCLUDED.clan_id, name = EXCLUDED.name, rank = EXCLUDED.rank, " +
                "join_runeday = EXCLUDED.join_runeday, joined_at = EXCLUDED.joined_at, last_seen = EXCLUDED.last_seen"
        connection.prepareStatement(sql).use {
            it.setInt(1, member.characterId)
            it.setInt(2, clanId)
            it.setString(3, member.name)
            it.setInt(4, member.rank)
            it.setInt(5, member.joinRuneDay)
            it.setLong(6, member.joinedAt)
            it.setInt(7, member.lastSeenDay)
            it.executeUpdate()
        }
    }

    fun deleteMember(connection: DatabaseConnection, characterId: Int) {
        connection.prepareStatement("DELETE FROM clan_members WHERE character_id = ?").use {
            it.setInt(1, characterId)
            it.executeUpdate()
        }
    }

    fun deleteClan(connection: DatabaseConnection, clanId: Int) {
        connection.prepareStatement("DELETE FROM clans WHERE id = ?").use {
            it.setInt(1, clanId)
            it.executeUpdate()
        }
    }

    fun saveClan(connection: DatabaseConnection, clan: Clan) {
        connection
            .prepareStatement("UPDATE clans SET name = ?, name_key = ?, resign_at = ? WHERE id = ?")
            .use {
                it.setString(1, clan.name)
                it.setString(2, nameKey(clan.name))
                it.setLong(3, clan.resignAt)
                it.setInt(4, clan.id)
                it.executeUpdate()
            }
    }

    fun saveSetting(connection: DatabaseConnection, clanId: Int, setting: Int, int: Int, string: String?) {
        val sql =
            "INSERT INTO clan_settings (clan_id, setting, int_value, string_value) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (clan_id, setting) DO UPDATE SET " +
                "int_value = EXCLUDED.int_value, string_value = EXCLUDED.string_value"
        connection.prepareStatement(sql).use {
            it.setInt(1, clanId)
            it.setInt(2, setting)
            it.setInt(3, int)
            it.setString(4, string)
            it.executeUpdate()
        }
    }
}
