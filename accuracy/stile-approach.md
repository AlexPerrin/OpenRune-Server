# Stile approach (fix/stile-approach)

Fix for upstream's shared `StileScript` (`content/generic/generic-locs`, every `loc.fullstyle` stile and the
Sheep Shearer pen's `loc.qip_sheep_shearer_fullstyle`), commit 8a8c0ca7a on main 829b9477d. Reported by
a-thoren on #366 (2026-10-19): "you can jump over the stile from too far away and the code assumes you're right
next to it, meaning you don't always end up in the sheep pen".

## Upstream first
`gh pr list -R OpenRune/OpenRune-Server --search stile` (2026-10-19): nothing open. On `main`, upstream's own Port
Sarim stile (`PortSarimLocScript.climbStile`) already walks to its start tile first; `StileScript` did not.

## Behaviour
| Mechanic | Source | Before | After |
|---|---|---|---|
| Start tile | A stile spans two tiles, one either side of the fence; the player climbs from the one on their side (Port Sarim stile on main; per LostCity `skill_agility/scripts/shortcuts.rs2` `[oploc1,fullstyle]`, 2004: side chosen by coordinate, player put on the start tile) | climbed from wherever the interaction ended (a diagonal tile, or beside the stile along the fence) | the footprint tile on the player's side, by coordinate across the fence |
| Approach | Port Sarim stile: `playerMove(start)` | none | walks onto the start tile; if it can't get there, nothing happens |
| Climb | unchanged | `climbOver(start, dest)`, 3 ticks, `human_walk_style` | the same, from the start tile to the other footprint tile |

Guess: walking (Port Sarim's way) rather than LostCity's snap to the start tile. Either way the player lands on
the far side.

## Tests
None (simple loc handler, AGENTS.md). In game: the Sheep Shearer pen stile from a diagonal tile and from beside it
along the fence, on both sides.

## PR split
One fix, no dependencies. #366 (Sheep Shearer) doesn't depend on it: the bug is on `main` without the quest.
