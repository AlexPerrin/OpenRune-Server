# Adding activity logs

Activity logs are JSON rows in Central’s `activity_logs` table. The game server writes most of them; Central can publish some via `CentralActivityLogPublisher`.

## How it works

```
Game event  →  CentralActivityLogWriter  →  activity_logs (Postgres)
                      ↑
              same DB as Central (embedded or jdbc-url in game.yml)
```

Each log has:

- `log_type` — string discriminator (`login`, `command`, `dropped_item`, …)
- `occurred_at` — epoch millis (UTC)
- `account_id`, `character_id`, `world_id`
- `payload` — JSON body (the sealed class fields minus the common ones)

Item-related logs can also get rows in `activity_log_items` when the type implements `ItemLineProducer`.

## Adding a new log type

### 1. Define the type in `central-common`

Add a class under `central-common/.../logs/CentralActivityLog.kt`:

```kotlin
@Serializable
@SerialName("my_event")   // stored as log_type
data class MyEvent(
    override val worldId: Int,
    override val occurredAtEpochMillis: Long,
    override val characterId: Int,
    override val accountId: Long,
    val detail: String,
) : CentralActivityLog() {
    @Transient
    override val type: String = "my_event"
}
```

Rules:

- Extend `CentralActivityLog` (or `ItemLog` if it’s a single item + quantity).
- Use `@SerialName` for the JSON/polyglot name; keep `type` in sync.
- Put shared fields on `BaseCentralLog` (`worldId`, `occurredAtEpochMillis`, `characterId`, `accountId`).

For items, extend `ItemLog` — `itemLines()` is generated for `activity_log_items`.

No migration needed for new types; `payload` is JSONB.

### 2. Write it from the game server

In `OpenRune-Server`, add a method on `CentralActivityLogWriter` (or call an existing helper):

```kotlin
public fun logMyEvent(player: Player, detail: String) {
    val repo = repository ?: return
    val worldId = config.world
    val now = System.currentTimeMillis()
    val charId = player.characterId
    val accountId = player.accountId.toLong()
    executor.execute {
        try {
            repo.insert(
                CentralActivityLog.MyEvent(
                    worldId = worldId,
                    occurredAtEpochMillis = now,
                    characterId = charId,
                    accountId = accountId,
                    detail = detail,
                ),
            )
        } catch (e: Exception) {
            logger.warn(e) { "Central activity log my_event failed characterId=$charId" }
        }
    }
}
```

Patterns to copy:

| Event | Writer method |
|-------|----------------|
| Login / logout | `logPlayerLogin`, `logPlayerLogout` |
| Command | `logCommand` |
| Drop / destroy item | `logItemDrop`, `logItemDestroy` |

Call your method from the right place (handler, script, or `NetworkScript` event).

`CentralActivityLogWriter.start()` runs on game startup when Central JDBC is configured (`central.postgres` in `game.yml`). If logs never appear, check the startup line: `Central activity log JDBC enabled`.

### 3. Publish from Central (optional)

If the event happens inside Central itself, inject `CentralActivityLogPublisher` and call `publish(log)`. That uses the same `insert` path as the game writer.

### 4. Query logs

Use `CentralActivityLogRepository` in Central:

- `findByLogUuid(uuid)`
- `listByTypeAndTimeRange(type, from, to, limit)`
- `listByCharacter(characterId, limit)`
- `listActivityLogIdsForItem(itemId, limit)` — item index table

Attach logs to punishments via `PunishmentService.attachCentralActivityLogs`.

## Checklist

1. New `CentralActivityLog` subclass in `central-common`
2. `publishToMavenLocal` (publishes `central-common`, `central-worldlink`, and `openrune-central`) so the game server sees the type
3. Writer method + call site in OpenRune-Server
4. Confirm `central.postgres` points at the same DB as Central
5. Trigger the event and query: `SELECT log_type, payload FROM activity_logs ORDER BY id DESC LIMIT 5`

## Existing log types

| `log_type` | Class |
|------------|--------|
| `login` | `Login` |
| `logout` | `Logout` |
| `chat` | `Chat` |
| `command` | `Command` |
| `trade` | `Trade` |
| `button_click` | `ButtonClick` |
| `pickup_item` | `PickupItem` |
| `dropped_item` | `DroppedItem` |
| `destroy_item` | `DestroyItem` |
