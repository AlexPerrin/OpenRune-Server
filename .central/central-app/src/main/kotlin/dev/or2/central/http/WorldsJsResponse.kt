package dev.or2.central.http

import kotlinx.serialization.Serializable

@Serializable
data class WorldsJsResponse(
    val worlds: List<WorldsJsWorld>,
)

@Serializable
data class WorldsJsWorld(
    val id: Int,
    val types: List<String>,
    val address: String,
    val activity: String,
    val location: Int,
    val players: Int,
)
