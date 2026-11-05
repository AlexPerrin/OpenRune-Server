package dev.or2.central.worldlink.protocol.social

object PmTraceLog {
    const val STAGE_GAME_SEND = "game-send"
    const val STAGE_GAME_CENTRAL_OK = "game-central-ok"
    const val STAGE_GAME_CENTRAL_FAIL = "game-central-fail"
    const val STAGE_CENTRAL_RECV = "central-recv"
    const val STAGE_CENTRAL_PUSH = "central-push"
    const val STAGE_CENTRAL_PUSH_FAIL = "central-push-fail"
    const val STAGE_GAME_INBOUND = "game-inbound"
    const val STAGE_GAME_DEFERRED = "game-deferred"
    const val STAGE_GAME_PACKET = "game-packet"

    fun format(
        stage: String,
        fromCharacterId: Int,
        toCharacterId: Int? = null,
        targetName: String? = null,
        messageLen: Int,
        extra: String = "",
    ): String =
        buildString {
            append("PM_TRACE stage=").append(stage)
            append(" from=").append(fromCharacterId)
            toCharacterId?.let { append(" to=").append(it) }
            targetName?.let { append(" target=").append(it) }
            append(" len=").append(messageLen)
            if (extra.isNotBlank()) {
                append(' ').append(extra.trim())
            }
        }

    fun formatPacket(
        ownerLabel: String,
        ownerCharacterId: Int,
        push: PrivateMessagePush,
        worldMessageCounter: Int,
    ): String =
        buildString {
            append("PM_TRACE stage=").append(STAGE_GAME_PACKET)
            append(" owner=").append(ownerLabel).append("(charId=").append(ownerCharacterId).append(')')
            append(" sender=").append(push.senderDisplayName).append("(charId=").append(push.fromCharacterId).append(')')
            append(" receiverCharId=").append(push.toCharacterId)
            append(" worldId=").append(push.senderWorldId)
            append(" counter=").append(worldMessageCounter)
            append(" message=\"").append(push.message).append('"')
        }
}
