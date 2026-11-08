package dev.or2.central.worldlink.protocol.social

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.FrameWriter
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame
import dev.or2.central.worldlink.protocol.utf8TruncatedTo

/**
 * Social world-link codec (friends, ignores, PM relay, sync).
 *
 * World → Central social frames are authorized by the authenticated world-link
 * connection (HELLO + world key) plus [characterId]; no per-player session token.
 */
object SocialPackets {
    data class CharacterPayload(
        val characterId: Int,
    )

    data class NameActionPayload(
        val characterId: Int,
        val targetName: String,
    )

    data class ChatFiltersPayload(
        val characterId: Int,
        val publicChat: Int,
        val privateChat: Int,
        val tradeChat: Int,
    )

    data class PmRelayPayload(
        val fromCharacterId: Int,
        val senderCrown: Int,
        val targetName: String,
        val senderDisplayName: String,
        val message: String,
    )

    fun encodeFriendAdd(
        characterId: Int,
        targetName: String,
    ): ByteArray = encodeNameAction(WorldOpcodes.OP_WORLD_FRIEND_ADD, characterId, targetName)

    fun encodeFriendDel(
        characterId: Int,
        targetName: String,
    ): ByteArray = encodeNameAction(WorldOpcodes.OP_WORLD_FRIEND_DEL, characterId, targetName)

    fun encodeIgnoreAdd(
        characterId: Int,
        targetName: String,
    ): ByteArray = encodeNameAction(WorldOpcodes.OP_WORLD_IGNORE_ADD, characterId, targetName)

    fun encodeIgnoreDel(
        characterId: Int,
        targetName: String,
    ): ByteArray = encodeNameAction(WorldOpcodes.OP_WORLD_IGNORE_DEL, characterId, targetName)

    fun encodeChatFilters(payload: ChatFiltersPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_CHAT_FILTERS) {
            writeInt(payload.characterId)
            writeByte(payload.publicChat)
            writeByte(payload.privateChat)
            writeByte(payload.tradeChat)
        }

    fun encodeSocialSync(characterId: Int): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_SOCIAL_SYNC) {
            writeInt(characterId)
        }

    fun encodePmRelay(payload: PmRelayPayload): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_PM_RELAY) {
            writeInt(payload.fromCharacterId)
            writeByte(payload.senderCrown)
            writeUtf8Social(payload.targetName)
            writeUtf8Social(payload.senderDisplayName)
            writeUtf8Social(payload.message, SocialLimits.PM_MESSAGE_MAX_UTF8)
        }

    fun encodeSocialOk(): ByteArray = byteArrayOf(WorldOpcodes.OP_WORLD_SOCIAL_OK.toByte())

    fun encodeSocialFail(reason: Int): ByteArray =
        byteArrayOf(
            WorldOpcodes.OP_WORLD_SOCIAL_FAIL.toByte(),
            reason.toByte(),
        )

    fun encodeSocialSyncFail(reason: Int): ByteArray =
        byteArrayOf(
            WorldOpcodes.OP_WORLD_SOCIAL_SYNC_FAIL.toByte(),
            reason.toByte(),
        )

    fun encodeSocialSyncOk(snapshot: SocialSyncSnapshot): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_SOCIAL_SYNC_OK) {
            writeByte(snapshot.publicChat)
            writeByte(snapshot.privateChat)
            writeByte(snapshot.tradeChat)
            writeShort(snapshot.friends.size)
            for (friend in snapshot.friends) {
                writeUtf8Social(friend.displayName)
                writeUtf8Social(friend.previousDisplayName ?: "")
                writeInt(friend.worldId)
            }
            writeShort(snapshot.ignores.size)
            for (ignore in snapshot.ignores) {
                writeUtf8Social(ignore.displayName)
                writeUtf8Social(ignore.previousDisplayName ?: "")
            }
        }

    fun encodeServerPrivateMessage(push: PrivateMessagePush): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_PRIVATE_MESSAGE) {
            writeInt(push.senderWorldId)
            writeInt(push.fromCharacterId)
            writeInt(push.toCharacterId)
            writeByte(push.senderCrown)
            writeUtf8Social(push.senderDisplayName)
            writeUtf8Social(push.message, SocialLimits.PM_MESSAGE_MAX_UTF8)
        }

    fun encodeServerFriendPresence(push: FriendPresencePush): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_FRIEND_PRESENCE) {
            writeInt(push.ownerCharacterId)
            writeInt(push.friendCharacterId)
            writeInt(push.friendWorldId)
            writeUtf8Social(push.friendDisplayName)
            writeUtf8Social(push.friendPreviousDisplayName ?: "")
        }

    fun decodeNameAction(input: FrameReader): NameActionPayload {
        val characterId = input.readInt()
        val targetName = input.readUtf8LenPrefixed().trim()
        input.requireFullyConsumed()
        return NameActionPayload(characterId, targetName)
    }

    fun decodeChatFilters(input: FrameReader): ChatFiltersPayload {
        val characterId = input.readInt()
        val publicChat = input.readUnsignedByte()
        val privateChat = input.readUnsignedByte()
        val tradeChat = input.readUnsignedByte()
        input.requireFullyConsumed()
        return ChatFiltersPayload(characterId, publicChat, privateChat, tradeChat)
    }

    fun decodeSocialSync(input: FrameReader): CharacterPayload {
        val characterId = input.readInt()
        input.requireFullyConsumed()
        return CharacterPayload(characterId)
    }

    fun decodePmRelay(input: FrameReader): PmRelayPayload {
        val fromCharacterId = input.readInt()
        val senderCrown = input.readUnsignedByte()
        val targetName = input.readUtf8LenPrefixed().trim()
        val senderDisplayName = input.readUtf8LenPrefixed().trim()
        val message = input.readUtf8LenPrefixed().trim()
        input.requireFullyConsumed()
        return PmRelayPayload(fromCharacterId, senderCrown, targetName, senderDisplayName, message)
    }

    fun decodeSocialSyncOk(input: FrameReader): SocialSyncSnapshot {
        val publicChat = input.readUnsignedByte()
        val privateChat = input.readUnsignedByte()
        val tradeChat = input.readUnsignedByte()
        val friendCount = input.readUnsignedShort()
        val friends = ArrayList<SocialSyncFriend>(friendCount)
        repeat(friendCount) {
            friends +=
                SocialSyncFriend(
                    displayName = input.readUtf8LenPrefixed(),
                    previousDisplayName = input.readUtf8LenPrefixed().ifBlank { null },
                    worldId = input.readInt(),
                )
        }
        val ignoreCount = input.readUnsignedShort()
        val ignores = ArrayList<SocialSyncIgnore>(ignoreCount)
        repeat(ignoreCount) {
            ignores +=
                SocialSyncIgnore(
                    displayName = input.readUtf8LenPrefixed(),
                    previousDisplayName = input.readUtf8LenPrefixed().ifBlank { null },
                )
        }
        input.requireFullyConsumed()
        return SocialSyncSnapshot(publicChat, privateChat, tradeChat, friends, ignores)
    }

    fun decodeServerPrivateMessage(input: FrameReader): PrivateMessagePush {
        val senderWorldId = input.readInt()
        val fromCharacterId = input.readInt()
        val toCharacterId = input.readInt()
        val senderCrown = input.readUnsignedByte()
        val senderDisplayName = input.readUtf8LenPrefixed()
        val message = input.readUtf8LenPrefixed()
        input.requireFullyConsumed()
        return PrivateMessagePush(
            senderWorldId,
            fromCharacterId,
            toCharacterId,
            senderCrown,
            senderDisplayName,
            message,
        )
    }

    fun decodeServerFriendPresence(input: FrameReader): FriendPresencePush {
        val ownerCharacterId = input.readInt()
        val friendCharacterId = input.readInt()
        val friendWorldId = input.readInt()
        val friendDisplayName = input.readUtf8LenPrefixed()
        val friendPreviousDisplayName = input.readUtf8LenPrefixed().ifBlank { null }
        input.requireFullyConsumed()
        return FriendPresencePush(
            ownerCharacterId,
            friendCharacterId,
            friendWorldId,
            friendDisplayName,
            friendPreviousDisplayName,
        )
    }

    private fun encodeNameAction(
        opcode: Int,
        characterId: Int,
        targetName: String,
    ): ByteArray =
        outboundFrame(opcode) {
            writeInt(characterId)
            writeUtf8Social(targetName)
        }

    private fun FrameWriter.writeUtf8Social(
        value: String,
        maxBytes: Int = SocialLimits.NAME_MAX_UTF8,
    ) {
        val utf8 = utf8TruncatedTo(value, maxBytes)
        writeShort(utf8.size)
        writeBytes(utf8)
    }
}

/** Game → Central social frames (alongside [dev.or2.central.worldlink.protocol.GameToCentralPackets]). */
object GameToCentralSocialPackets {
    fun friendAdd(characterId: Int, targetName: String): ByteArray =
        SocialPackets.encodeFriendAdd(characterId, targetName)

    fun friendDel(characterId: Int, targetName: String): ByteArray =
        SocialPackets.encodeFriendDel(characterId, targetName)

    fun ignoreAdd(characterId: Int, targetName: String): ByteArray =
        SocialPackets.encodeIgnoreAdd(characterId, targetName)

    fun ignoreDel(characterId: Int, targetName: String): ByteArray =
        SocialPackets.encodeIgnoreDel(characterId, targetName)

    fun chatFilters(
        characterId: Int,
        publicChat: Int,
        privateChat: Int,
        tradeChat: Int,
    ): ByteArray =
        SocialPackets.encodeChatFilters(
            SocialPackets.ChatFiltersPayload(characterId, publicChat, privateChat, tradeChat),
        )

    fun socialSync(characterId: Int): ByteArray = SocialPackets.encodeSocialSync(characterId)

    fun pmRelay(payload: SocialPackets.PmRelayPayload): ByteArray = SocialPackets.encodePmRelay(payload)
}
