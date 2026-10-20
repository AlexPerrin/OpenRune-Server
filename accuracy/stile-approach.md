# Stile approach (fix/stile-approach)

Fix for upstream's shared `StileScript` (`content/generic/generic-locs`, every `loc.fullstyle` stile and the
Sheep Shearer pen's `loc.qip_sheep_shearer_fullstyle`), on main 829b9477d: 8a8c0ca7a, then 8646e5ff0 and 513144d3d (the climb one tick after the step on, sq24's upstream gate) after
the internal gate failed (qa/stile-approach.md: 8a8c0ca7a walked onto the footprint, which blocks walking, so it
never climbed). Reported by a-thoren on #366 (2026-10-19): "you can jump over the stile from too far away and the
code assumes you're right next to it, meaning you don't always end up in the sheep pen".

## Upstream first
`gh pr list -R OpenRune/OpenRune-Server --search stile` (2026-10-19): nothing open. On `main`, upstream's own Port
Sarim stile (`PortSarimLocScript.climbStile`) walks to a footprint tile first; `StileScript` did not move the
player at all before the climb.

## Sources
- Cache, loc 12982 `Stile` (the pen's, `qip_sheep_shearer_fullstyle`): sizeX 1, sizeY 2, solid 2 (blocks
  walking), shape 10. So the two footprint tiles, (3197,3276) and (3197,3277) at angle 0, can't be walked onto.
- RSProx recording 1718 (revision 227), ticks 2328-2333: the only crossing by the recording player (oploc1 12982 at
  (3197,3276), from the north):
  - t2328 (op tick, oploc1 at (3197,3276)): the player reaches (3197,3278), the tile outside the footprint, in
    line with the crossing.
  - t2329: a one-tile move onto (3197,3277), the near footprint tile (blocked, so a forced move).
  - t2330: seq 839 delay 30 and exactmove to1 (3197,3277) delay1 30, to2 (3197,3276) delay2 94, angle 0
    (south); the player's tile becomes (3197,3276); loc_merge 12982 start 30 end 94, maxz 1.
  - t2333: a one-tile move onto (3197,3275), the tile outside the footprint on the far side.
  Other players' crossings in the same recording show the same loc_merge (start 30, end 94).
- LostCity `skill_agility/scripts/shortcuts.rs2` `[oploc1,fullstyle]` (2004, reference only): the same shape
  (onto the near footprint tile, exactmove 30/94 across, then onto the tile beyond), side chosen by coordinate.

## Behaviour
| Mechanic | Source | Before (main) | After |
|---|---|---|---|
| Side | coordinate across the fence (1718; LostCity) | none: climbed from the player's tile toward the far footprint tile, so from the north it moved one tile without crossing | south/west of the near footprint tile = near side, else far side |
| Approach | 1718 t2328: in line, outside the footprint | the interaction's end tile (any adjacent or diagonal tile) | walks to the tile outside the footprint on the player's side, in line with the crossing; if it can't get there, nothing happens |
| Step on | 1718 t2329, the tick after arriving | none | one-tile forced move onto the near footprint tile once the player is on the in-line tile |
| Climb | 1718 t2330 | `climbOver`: seq `human_walk_style` delay 30, exactmove 30/94, facing the crossing | the same, 1 tick after the step on (matches 839/30/94/angle and t2329 -> t2330) |
| Step off | 1718 t2333 | none (ended on a footprint tile) | one-tile forced move onto the tile beyond, 3 ticks after the climb |
| Teleport rules | an agility move, not a teleport | the exactmove went through the Standard teleport check | the forced moves and the exactmove are exempt (also Gertrude's fence, which shares `climbOver`) |

## Caveats and guesses
- No `loc_merge` packet: the engine has no loc-merge API; the player model isn't merged with the stile during
  the climb (visual only).
- Guess: how the official game routes a player who clicks from beside the stile along the fence or from a
  diagonal tile; 1718 only shows an in-line approach. We walk them to the in-line tile first.
- The forced one-tile moves are `teleport(Exempt)` calls; player info sends them as walk steps, as in 1718 (our
  transcript). The climb's tile change goes out as a teleport (1718: walk), from `exactMove` on main.

## Tests
`StileScriptTest` (integration, 6): the climb 1 tick after the step on and the step off 3 ticks after the climb; from the south, from the north, from beside the stile along the fence, from
4 tiles away, and a north-facing stile crossed east to west; each ends on the tile beyond the far footprint
tile. In game: the pen stile from both sides, in line, beside and diagonal.

## PR split
One fix, no dependencies. #366 (Sheep Shearer) doesn't depend on it: the bug is on `main` without the quest.

## RSProx comparison
- Official: 1718 (revision 227), player index 85, re-read at the source by sd27 (2026-10-19): t2327 run to
  (3197,3280); t2328 `oploc1_v1 id=12982 (3197,3276)` and run to (3197,3278), the in-line tile; t2329 walk onto
  (3197,3277); t2330 walk to (3197,3276), seq 839 delay 30, exactmove to1 (3197,3277) delay1 30, to2 (3197,3276)
  delay2 94, angle 0, loc_merge 12982 start 30 end 94; t2333 walk to (3197,3275). From arriving on the in-line tile
  (T): step on T+1, climb T+2, step off T+5. No other `fullstyle` crossing by a recording player in the 40
  recordings (sq24's search).
- Ours: the desktop session against the branch server at 513144d3d, recorded by the desktop RSProx and transcribed
  (`re/evidence/fix-stile-approach/rsprox-own-session-ticks.txt`; 17 crossings). Every crossing:
  arrival on the in-line tile T, walk onto the near footprint tile T+1, seq 839 delay 30 + exactmove 30/94 to the
  far footprint tile T+2 (angle 1024 northward, 0 southward), walk onto the tile beyond T+5. Same as 1718.
- Differences: no `loc_merge` (caveat); the climb's own tile change goes out in player info as a teleport
  (1718: a walk step), from the engine's `exactMove`, which `climbOver` already used on main; the step on and step
  off go out as walk steps, as in 1718.

## Check (sd27, 513144d3d)
- Fork CI 39363600299 (CI: build, test, integration, boot) and Formatting 39363603819 green at 513144d3d. sd23's dev
  run: `generic-locs` spotlessCheck and integration 10/0 (StileScriptTest 6/6; the timing test fails with the
  old 2-tick step on).
- In game (desktop RuneLite through RSProx, branch server on 43598, `qa.sh gate-run` + `start`), the pen stile
  (3197,3276), ticks from our own transcript:
  | Start tile | Side | Path (tick: tile) | Ends |
  |---|---|---|---|
  | (3197,3274) 2 S, in line | S | 73 walk (3197,3275); 74 on; 75 climb; 78 off | (3197,3278) |
  | (3197,3279) 1 N, in line | N | 739 walk (3197,3278); 740 on; 741 climb; 744 off | (3197,3275) |
  | (3196,3277) beside, N, west | N | 233 op; 234 to (3197,3278); 235 on; 236 climb; 239 off | (3197,3275) |
  | (3198,3276) beside, S, east | S | 264 op; 265 to (3197,3275); 266 on; 267 climb; 270 off | (3197,3278) |
  | (3198,3275) diagonal SE | S | 560 walk (3197,3275); 561 on; 562 climb; 565 off | (3197,3278) |
  | (3196,3278) diagonal NW | N | 579 walk (3197,3278); 580 on; 581 climb; 584 off | (3197,3275) |
  | (3194,3270) 4-6 tiles SW | S | 619-621 to (3197,3275); 622 on; 623 climb; 626 off | (3197,3278) |
  | (3200,3279) 3 tiles NE | N | 802 to (3198,3277); 804 to (3197,3278); 805 on; 806 climb; 809 off | (3197,3275) |
  | (3197,3278), already on the in-line tile | N | 303 on; 304 climb; 307 off | (3197,3275) |
  Never a climb from anywhere but the near footprint tile after the in-line tile; no server errors.
- Seen in game, not in any recording (guesses): from beside the stile along the fence the move to the in-line tile
  starts the tick after the op (one idle tick at 803 for the NE route); a player already standing on the in-line
  tile steps on on the op tick.
- Earlier: 8a8c0ca7a walked onto the blocking footprint and never climbed (qa/stile-approach.md); 8646e5ff0
  climbed 2 ticks after the step on (sq24's upstream gate FAIL), fixed by 513144d3d (`STEP_ON_TICKS = 1`).
