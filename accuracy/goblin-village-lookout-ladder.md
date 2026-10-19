# Goblin Village lookout ladder (fix/goblin-village-lookout-ladder)

Split out of upstream draft #367 ("Goblin Diplomacy quest; Goblin Village lookout ladder"): the ladder is a fix of
its own, independent of the quest.

## Problem
The lookout by Goblin Village's south gate has a ladder in three locs: `goblin_ladder_bottom` (16450, Climb-up,
level 0), `goblin_ladder_middle` (16557, no ops) and `goblin_ladder_top` (16556, Climb-down, level 2). On upstream
`main` 829b9477d none of them has a handler: they have no content group in
`.data/raw-cache/server/loc/loc.toml` (so `LadderScript` / `DungeonLadderScript` don't bind them), and #328's
`GoblinCaveLadders` only covers the cave ladders, so Climb-up does nothing. A generic
one-level ladder would also be wrong here: the top is on level 2.

## Sources
- OSRS Wiki, Goblin Diplomacy (Walkthrough): a crate "Up a ladder near the gate to the goblin village"; "You can
  go up the only ladder in the goblin village, up there will be some crates." (re-read 2026-10-19).
- Quest Helper `GoblinDiplomacy` (BSD): `goUpLadder` on 16450 at (2954, 3497, 0), the `upstairs` zone on plane 2,
  `goDownLadder` on 16556.
- The cache: loc ops of 16450 / 16556 / 16557 (2026-10-18 audit, openrune/upstream/accuracy/goblin-diplomacy.md
  row 29).

## Behaviour
| # | Mechanic | Official (source) | Branch | Verdict |
|---|---|---|---|---|
| 1 | Climb-up on the bottom ladder | to the lookout top, plane 2 (Quest Helper zone) | `climbInPlace(2)`: arrive delay, `human_reachforladder`, 1 tick, telejump to the same x/z on level 2 | matches |
| 2 | Climb-down on the top ladder | back to level 0 (Quest Helper) | `climbInPlace(0)` | matches |
| 3 | Middle section | no ops (cache) | not bound | matches |
| 4 | Landing tile | not documented; OSRS ladders keep the player's tile in most places | the player's own tile | guess (caveat) |

## Tests
None (a simple op handler; AGENTS.md). The same handler (from #367's version) runs on our live server; an in-game run from this branch needs the desktop lock.

## PR split
One commit off upstream `main`, no `Depends:`. Goblin Diplomacy (content/goblin-diplomacy) uses it for its third
crate but doesn't depend on it.
