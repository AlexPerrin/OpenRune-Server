package dev.or2.central.account

import java.time.LocalDateTime

public data class TwoFactorAuthData(
    public val twoFactorSecret: String? = null,
    public val twoFactorRecoveryCodes: String? = null,
    public val twoFactorConfirmedAt: LocalDateTime? = null,
) {
    public val twoFactorConfirmed: Boolean
        get() = !twoFactorSecret.isNullOrBlank() && twoFactorConfirmedAt != null
}
