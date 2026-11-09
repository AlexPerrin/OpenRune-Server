package dev.or2.login.model

sealed class OpenRuneLoginOutcome {

    abstract val code: String

    data class Ok(
        val slot: Int,
        val staffModLevel: Int,
        val playerMod: Boolean,
        val member: Boolean,
        val accountHash: Long,
        val userId: Long,
        val userHash: Long,
        val authenticatorKind: String,
    ) : OpenRuneLoginOutcome() {
        override val code: String = "ok"
    }

    sealed class Denied(override val code: String) : OpenRuneLoginOutcome() {

        data object InvalidCredentials : Denied("invalid_credentials")
        data object Banned : Denied("banned")
        data object Locked : Denied("locked")
        data object ServerFull : Denied("server_full")
        data object Duplicate : Denied("duplicate")
        data object Timeout : Denied("timeout")
        data object UpdateInProgress : Denied("update_in_progress")
        data object LoginServerOffline : Denied("login_server_offline")
        data object LoginServerNoReply : Denied("login_server_no_reply")
        data object LoginServerLoadError : Denied("login_server_load_error")
        data object UnknownReplyFromLoginServer : Denied("unknown_reply_from_login_server")
        data object InvalidAuthenticatorCode : Denied("invalid_authenticator_code")
        data object AuthenticatorRequired : Denied("authenticator_required")
        data object InvalidSave : Denied("invalid_save")
        data object ConnectFail : Denied("connect_fail")

        data class DisallowedByScript(
            val line1: String,
            val line2: String,
            val line3: String,
        ) : Denied("disallowed_by_script")

        data class Unmapped(
            val rsprotKind: String,
        ) : Denied("unmapped")
    }

    companion object {
        private const val CODE = "code"
        private const val SLOT = "slot"
        private const val STAFF_MOD_LEVEL = "staff_mod_level"
        private const val PLAYER_MOD = "player_mod"
        private const val MEMBER = "member"
        private const val ACCOUNT_HASH = "account_hash"
        private const val USER_ID = "user_id"
        private const val USER_HASH = "user_hash"
        private const val AUTHENTICATOR = "authenticator"
        private const val RS_PROT_KIND = "rsprot_kind"
    }
}