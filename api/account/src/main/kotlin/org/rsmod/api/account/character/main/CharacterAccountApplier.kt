package org.rsmod.api.account.character.main

import jakarta.inject.Inject
import java.time.LocalDateTime
import org.rsmod.api.account.character.CharacterAccountLoginSegment
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public class CharacterAccountApplier @Inject constructor() :
    CharacterDataStage.Applier<CharacterAccountLoginSegment> {
    override fun apply(player: Player, data: CharacterAccountLoginSegment) {
        val d = data.wrapped
        val c = d.characterData
        player.accountId = d.accountId
        player.characterId = c.characterId

        val accountHash = (d.accountId.toLong() shl 32) or c.characterId.toLong()
        val userHash = d.accountName.hashCode().toLong()
        player.userId = c.characterId.toLong()
        player.accountHash = accountHash
        player.userHash = userHash

        val uuid = c.characterId.toLong()
        player.uuid = uuid
        player.observerUUID = uuid

        player.trustedDevices = d.trustedDevices.toMutableList()
        player.twoFactorAuth = d.twoFactorAuth
        player.lastKnownDevice = d.trustedDevices.maxByOrNull { it.verifiedAt }?.deviceId
        // Test server: every account is a member, so members ops (e.g. cannon Set-up) show.
        player.members = true
        player.username = d.accountName
        player.displayName = c.displayName ?: ""
        player.previousDisplayName = c.previousDisplayName ?: ""
        player.displayNameChangedAtMillis = c.displayNameChangedAtMillis
        player.discordId = d.discordId
        // Test server: a character that never logged in starts in Lumbridge as in OSRS, not at the
        // database default (3200, 3200).
        player.coords =
            if (c.lastLogin == null) NEW_CHARACTER_COORDS else CoordGrid(c.coordX, c.coordZ, c.coordLevel)
        player.createdAt = c.createdAt
        player.runEnergy = c.runEnergy
        // Test server: OSRS XP rates (1x) whatever the realm's or the account's stored rate (world
        // 255's realm gives new accounts 150x), so XP-based client plugins see real values.
        player.xpRate = 1.0
        player.lastLogin = LocalDateTime.now()
        player.vars.backing.putAll(c.varps)
        if (c.attrs.isNotEmpty()) {
            player.attr.putAllFromPersistence(c.attrs)
        }
        player.modLevel = d.rights
    }

    private companion object {
        val NEW_CHARACTER_COORDS = CoordGrid(3222, 3218, 0)
    }
}
