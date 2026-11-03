package dev.or2.central.worldlink.protocol.social

/** Social protocol limits (not wire layout — sizes come from [dev.or2.central.worldlink.protocol.FieldKind]). */
object SocialLimits {
    const val NAME_MAX_UTF8: Int = 96

    const val PRIVATE_MESSAGE_MAX_CHARS: Int = 255

    const val PM_MESSAGE_MAX_UTF8: Int = PRIVATE_MESSAGE_MAX_CHARS * 4

    /** Max friends / ignores encoded in a single SOCIAL_SYNC_OK (OSRS-style cap). */
    const val MAX_LIST_ENTRIES: Int = 200
}
