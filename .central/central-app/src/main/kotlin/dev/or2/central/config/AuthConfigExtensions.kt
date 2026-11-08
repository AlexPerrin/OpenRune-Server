package dev.or2.central.config

import dev.or2.central.auth.PasswordAuthConfig

fun AuthConfig.toPasswordAuthConfig(): PasswordAuthConfig =
    PasswordAuthConfig(
        passwordHasher = passwordHasher,
        bcryptCost = bcryptCost,
        argon2Iterations = argon2Iterations,
        argon2MemoryKib = argon2MemoryKib,
    )
