package dev.or2.central.logs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed interface BaseCentralLog {
    val worldId: Int
    val occurredAtEpochMillis: Long
    val characterId: Int
    val accountId: Long
}

interface TypedLog {
    val type: String
}

@Serializable
public data class ActivityLogItemLine(
    val type: String,
    val index: Int,
    val itemId: String,
    val quantity: Int,
)

public interface ItemLineProducer {
    public fun itemLines(): List<ActivityLogItemLine>
}

@Serializable
public sealed class CentralActivityLog :
    BaseCentralLog,
    TypedLog {

    @Serializable
    @SerialName("login")
    public data class Login(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
    ) : CentralActivityLog() {
        @Transient
        override val type: String = "login"

        init {
            require(characterId > 0)
        }
    }

    @Serializable
    @SerialName("logout")
    public data class Logout(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        val sessionId: Long? = null,
    ) : CentralActivityLog() {
        @Transient
        override val type: String = "logout"
    }

    @Serializable
    @SerialName("chat")
    public data class Chat(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        val message: String,
    ) : CentralActivityLog() {
        @Transient
        override val type: String = "chat"
    }

    @Serializable
    @SerialName("trade")
    public data class Trade(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        val initiatorCharacterId: Int,
        val receiverCharacterId: Int,
        val initiatorItems: List<TradedItem>,
        val receiverItems: List<TradedItem>,
    ) : CentralActivityLog(), ItemLineProducer {

        @Transient
        override val type: String = "trade"

        override fun itemLines(): List<ActivityLogItemLine> =
            buildList {
                initiatorItems.forEachIndexed { i, t ->
                    add(ActivityLogItemLine("trade_initiated", i, t.id, t.quantity))
                }
                receiverItems.forEachIndexed { i, t ->
                    add(ActivityLogItemLine("trade_receiving", i, t.id, t.quantity))
                }
            }
    }

    @Serializable
    @SerialName("button_click")
    public data class ButtonClick(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        val interfaceId: String,
        val componentId: String,
    ) : CentralActivityLog() {
        @Transient
        override val type: String = "button_click"
    }

    /**
     * Base for item logs (auto item-line generation).
     */
    @Serializable
    public sealed class ItemLog : CentralActivityLog(), ItemLineProducer {

        public abstract val itemId: String
        public abstract val quantity: Int

        protected abstract val itemLineType: String

        final override val type: String
            get() = itemLineType

        final override fun itemLines(): List<ActivityLogItemLine> =
            listOf(ActivityLogItemLine(itemLineType, 0, itemId, quantity))
    }

    @Serializable
    @SerialName("pickup_item")
    public data class PickupItem(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        override val itemId: String,
        override val quantity: Int,
        val tileX: Int? = null,
        val tileZ: Int? = null,
    ) : ItemLog() {
        override val itemLineType: String = "pickup_item"
    }

    @Serializable
    @SerialName("dropped_item")
    public data class DroppedItem(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        override val itemId: String,
        override val quantity: Int,
        val tileX: Int? = null,
        val tileZ: Int? = null,
    ) : ItemLog() {
        override val itemLineType: String = "dropped_item"
    }

    @Serializable
    @SerialName("destroy_item")
    public data class DestroyItem(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        override val itemId: String,
        override val quantity: Int,
        val tileX: Int? = null,
        val tileZ: Int? = null,
    ) : ItemLog() {
        override val itemLineType: String = "destroy_item"
    }

    @Serializable
    @SerialName("command")
    public data class Command(
        override val worldId: Int,
        override val occurredAtEpochMillis: Long,
        override val characterId: Int,
        override val accountId: Long,
        val command: String,
        val args: List<String> = emptyList(),
    ) : CentralActivityLog() {
        @Transient
        override val type: String = "command"
    }
}

@Serializable
public data class TradedItem(
    val id: String,
    val quantity: Int,
)