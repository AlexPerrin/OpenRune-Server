# Ground item despawn times (fix/ground-item-despawn-times)

Fix for upstream `main` (09/2026+): every item a player drops uses `constants.lootdrop_duration` (200 ticks) and
`ObjRepository.DEFAULT_REVEAL_DELAY` (100), whatever the item; and OBJ_ADD carries no timers, so the client's
despawn and reveal timers (RuneLite Ground Items "Despawn timer", the Clue Scroll Juggling hub plugin) show
nothing. Client blocker from task hub-ground-items-despawn. Repro: `::item 10180 1` (Clue scroll (easy)), drop it,
it is gone 120 s later (official: 61 min), and an untradeable drop turns public after 60 s.

## Upstream first
`gh pr list -R OpenRune/OpenRune-Server --state all --search` "despawn", "ground item", "lootdrop", "ObjAdd", and
`gh issue list ... --search despawn` (2026-10-20): nothing on drop timers or OBJ_ADD timers (only #246, pets).

## Behaviour
| Rule | Source | Before | After |
|---|---|---|---|
| Tradeable drop | Wiki, Drop: "Tradeable items appear to other players 60 seconds (one minute) after being dropped, and disappear after another 120 seconds"; RSProx below: reveal=100, despawn=300 | public at 100, gone at 200 | private for 100 ticks, despawns at 300 |
| Noted items | Cache: certs carry the base item's tradeability through `certlink`; RSProx: noted drops not recorded | as above | tradeability read from the uncerted type |
| Tradeable = which flag | Cache: trowel 676 `tradeable=false, stockMarket=false`, bronze axe `stockMarket=true`. At runtime `ItemServerType.tradeable` read true for the trowel (field default `true`; the codec never cleared it), so the trowel was dropped as tradeable on the first in-game run | - | the cache's `stockmarket` (Grand Exchange) flag |
| Untradeable drop | Wiki, Drop: "Untradeable items disappear after 180 seconds (three minutes)"; RSProx: despawn=300, neverturnpublic=true, no reveal | public at 100, gone at 200 | never public, despawns at 300 |
| Clue scrolls, challenge scrolls, Mimic | Wiki, Drop: "Clue scrolls disappear after 61 minutes"; Guide:Clue Juggling: "Dropped clue / challenge scrolls and Mimics despawn in exactly 61 minutes"; RSProx: despawn=6100, neverturnpublic=true (beginner and hard clues) | gone at 200 | never public, despawns at 6100 (61 min) |
| Which items are clue items | Cache names: "Clue scroll (beginner/easy/medium/hard/elite/master)", "Challenge scroll (medium/hard/elite)", "Mimic" (23184 `trail_mimic_casket`). No param or category covers exactly these: the tier categories (46/47/51/750) and tier params (347-350) also hold the step caskets, param 623 misses beginner/master/elite challenge and also hits X Marks the Spot and Song of the Elves quest scrolls | - | display name matches `(Clue|Challenge) scroll (<tier>)`, or is "Mimic". Leaves out 713 "Clue scroll" (`cert_display_tea`), the SotE "Clue scroll" items and "Clue scroll (special)" |
| OBJ_ADD timers | RSProx (all recordings): OBJ_ADD carries timeUntilPublic, timeUntilDespawn, ownership (0 none, 1 self, 2 other) and neverBecomesPublic; RuneLite GroundItemsPlugin builds its timers from `TileItem.getDespawnTime()/getVisibleTime()` | all four sent as 0/false | sent from the obj's timers (below) |
| Fresh owned drop | RSProx 1621 t31-33 (wool), 1718 t9584 (bronze axe), 3056 t950: `reveal=100, despawn=300, ownership=self, neverturnpublic=false`; untradeables 73 t6076 (trowel etc.), 1694, 91: `despawn=300, ownership=self, neverturnpublic=true`; clues 1702 t7239 (7254), 91 t14074 (23182): `despawn=6100, self, true` | - | same values to the owner |
| Re-send while private | RSProx 73 (seaweed 401 dropped, re-sent 51 ticks later): `reveal=49, despawn=249, ownership=self` | - | both timers counted down to the send tick |
| Reveal | RSProx: no packet to the owner at the reveal tick; others get the obj | (ObjReveal already skips the owner) | others get OBJ_ADD with the remaining despawn time, ownership=other |
| Re-send after reveal | RSProx 73 t5425 (1978): `despawn=72, ownership=other`, also when re-sent to the dropper (seaweed at +124: `despawn=175, ownership=other`) | - | public owned obj: ownership=other to everyone, remaining despawn |
| Server-spawned obj | RSProx 73 t0: `[obj_add] id=1573, count=1, neverturnpublic=false` (all 0) | 0 | unchanged: 0, none, false (map spawns have no timers); a timed server obj sends its despawn time |
| Despawn tick | RSProx 1621 wool +299, 91 kebab +299, 1718 bronze axe +299 (OBJ_DEL the tick before the 300th, counting the drop tick) | - | ObjRepository deletes at drop cycle + 300 (unchanged mechanism) |

## Caveats and guesses
- Not changed: NPC loot. RSProx: tradeable loot `reveal=100, despawn=200`, untradeable loot (and clues)
  `despawn=200, neverturnpublic=true`. Upstream already uses 200/100 for loot; with this change the packet carries
  those values, but untradeable NPC loot still turns public at 100 on our server (official: never). Its own fix.
- Not changed: the wilderness hook (`WildernessGroundItemHook`) keeps its own reveal and supply rules; an
  untradeable dropped in the wilderness turns public at once there. Out of scope.
- Not done: "Items dropped by an account with less than 20 hours of playtime follow the timers for dropped
  untradeable items" (wiki, Drop): no playtime counter on the server.
- Not done: untradeable timers "pause if logged out" and the items follow the player across worlds (wiki).
- Not done: items placed on tables (9 min) and random event rewards (3 h).
- Guess: a stackable drop merged into an existing private stack resets the stack's timers (upstream's existing
  merge behaviour, marked TODO in ObjRepository); the client gets OBJ_COUNT, which has no timer field, so its
  displayed timer keeps the first drop's time. No recording of a merged re-drop was found.
- Guess: scroll boxes keep the 3-minute untradeable timer (wiki, Scroll box). Step caskets ("Casket (tier)") and the
  elite puzzle box: the wiki doesn't say; they get the untradeable timer.
- Found on the way, not fixed: `ObjRegistry.singleCopy` and `ZoneUpdateTransformer.copy` drop `ownerId`;
  `ItemServerType.tradeable` reads true at runtime for untradeables (other users: death drops, shops); items tradeable between
  players but not on the GE get the untradeable timers here.
- Not covered: GIM-owned items (ownership 3); no group storage of observer ids in the protocol code.
- Leagues recording 91 shows one area with `despawn=18000` drops (y ~6437); a league rule, ignored.

## RSProx comparison
Local transcripts `re/ref/rsprox/txt/` (25 recordings), all `obj_add`/`obj_del` lines (19,222 / 7,662), the
transcriber prints `reveal`/`despawn`/`ownership` only when non-zero. Drops: if_button7 on 149:0 then OBJ_ADD on the
player's tile. Recordings 73, 91, 1619, 1621, 1694, 1702, 1718, 3056 (rev 225-231). Each rule above names its
recording and tick. Checked at the source by sd28: 1702 lines 121495/121804 (7254: NPC drop `despawn=200`, re-drop
`despawn=6100, self, true`), 1621 line 4343 (1737 `reveal=100, despawn=300, self, false`), 73 lines 37692/38428
(401 drop, then `reveal=49, despawn=249`), 73 line 5425 (`ownership=other`). Matches: all rules in the table; no
divergence in what this change implements. Not in any recording: easy/medium/elite/master clue drops, challenge
scroll and Mimic drops, noted drops, GIM items, another player's drop in its private window.

## Tests
- `ObjAddTimersTest` (api/registry, unit): fresh tradeable 100/300/self/false; untradeable 0/300/self/true; re-send
  at +51 gives 49/249; reveal gives 0/200/other; re-send after reveal 176/other; server obj 0/0/none/false.
- `PlayerDropTimersTest` (api/player, unit): tradeable 300/100, untradeable 300/never, each clue and challenge
  scroll tier and the Mimic 6100/never, and the look-alike names ("Clue scroll", "Clue scroll (special)", "Scroll
  box (easy)") stay on the untradeable timers. Noted items and the tick-by-tick despawn are checked in game.
- In game (desktop RuneLite through RSProx, branch server at 9a93f61ef; `re/evidence/ground-item-despawn-times/`):
  OBJ_ADD for bronze axe and noted bronze axe `reveal=100, despawn=300, ownership=self, neverturnpublic=false` at
  tick 184, OBJ_DEL at 483 (+299, as official); Clue scroll (easy) `despawn=6100, self, true`, still on the ground
  after the axes went. The trowel came out tradeable (`reveal=100`), which found the `tradeable` flag problem above;
  8bc126d49 uses `stockmarket`, checked by the unit test, not re-run in game (desktop needed by other agents).
- Seen, not changed: OBJ_ADD opflags are `OpFlags.ALL_SHOWN` (-1), official 31 (0b11111); upstream's existing value.

## PR split
One change: the drop timers and the OBJ_ADD fields that report them (without the second the first can't be seen in
the client). If upstream asks, it splits at the commit boundary: "fix: send ground item timers in OBJ_ADD" (engine,
any obj with a duration) and "fix: OSRS despawn times for dropped items" (HeldInteractions); neither needs the other
to build. No dependencies.
