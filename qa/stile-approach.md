# QA: fix/stile-approach
- Branch: `fix/stile-approach`
- Commit: `513144d3dfcfcfd5613123bbe3457b952192c384`
## Internal gate: **PASS** (dev's check, sd27, 2026-10-19)
## Upstream gate: **PENDING** (re-gate at 513144d3d; the FAIL at 8646e5ff0 is below)

## Internal gate at 513144d3d (dev's check; after the FAIL at 8a8c0ca7a, below)
Three commits on upstream `main` 829b9477d: 8a8c0ca7a, 8646e5ff0 (approach from the in-line tile outside the
footprint) and 513144d3d (`STEP_ON_TICKS = 1`, the climb the tick after the step on, sq24's upstream-gate finding).
Fork PR AlexPerrin/OpenRune-Server#21.
- **Build**: fork CI 39363600299 (CI) and 39363603819 (Formatting) green, head sha 513144d3d checked.
- **Accuracy** (`accuracy/stile-approach.md`, re-read at the source): loc 12982 1x2, solid 2 (osrs-mcp cache);
  1718 t2327-2333 re-read (in-line arrival on the op tick t2328, step on t2329, climb t2330, step off t2333).
- **In game** (desktop RuneLite through RSProx, branch server built by `qa.sh gate-run` at 513144d3d): 17 crossings
  of the pen stile from in line on both sides, beside the stile along the fence on both sides, diagonal on both
  sides, 4-6 tiles south-west, 3 tiles north-east, and standing on the in-line tile. Our own RSProx transcript
  (`re/evidence/fix-stile-approach/rsprox-own-session-ticks.txt`) shows every crossing walk to the in-line tile on
  the player's side first, then step on T+1, climb (seq 839, exactmove 30/94) T+2, step off T+5, the 1718 ticks;
  never a climb from afar or off line; each ends on the tile beyond the far footprint tile. The 8a8c0ca7a failure
  (walking onto the blocking footprint, no climb) does not occur. No server errors.
- **PR text** `pr-texts/stile-approach.upstream.body` checked against the commit (pr-text-review): no false claim.

## Upstream gate at 8646e5ff0: FAIL (sq24)
Run by sq24 on 2026-10-19 at 8646e5ff0 (two commits on upstream `main` 829b9477d, still the tip: 8a8c0ca7a and the
fix 8646e5ff0; fork PR AlexPerrin/OpenRune-Server#21). Spec `accuracy/stile-approach.md` and PR text
`pr-texts/stile-approach.upstream.body` read from sd23's docs commit 65cc94f05. The gate stopped at the first
failure, the accuracy check against RSProx; in game was not re-run.

## What must change
**The climb comes two ticks after the step onto the stile; in the recording it comes the next tick.** The spec
misreads 1718 by one tick. Re-read at the source (`re/ref/rsprox/txt/1718.txt`, player index 85):
- t2327: `[run] ... coord=(3195, 3281, 0)), newcoord=(3197, 3280, 0)`: the player is still 2 tiles north,
  not at the in-line tile (the spec says "t2327: the player arrives at (3197,3278)").
- t2328: `[oploc1_v1] id=12982, coord=(3197, 3276, 0)` and `[run] ... newcoord=(3197, 3278, 0)`: the player
  reaches the in-line tile on the op tick. There is no step onto the footprint on this tick (the spec says
  "t2328 (op): a one-tile move onto (3197,3277)").
- t2329: `[walk] ... coord=(3197, 3278, 0)), newcoord=(3197, 3277, 0)`: the step onto the near footprint tile.
- t2330: `[walk] ... newcoord=(3197, 3276, 0)`, `[sequence] id=839, delay=30`, `[exactmove] to1=(3197, 3277,
  0), delay1=30, to2=(3197, 3276, 0), delay2=94, angle=0`, `loc_merge` start 30 end 94: the climb, **one tick
  after the step on**.
- t2333: `[walk] ... newcoord=(3197, 3275, 0)`: the step off, 3 ticks after the climb.
So, from reaching the in-line tile (T): step on T+1, climb T+2, step off T+5.

The branch, measured server-side at 8646e5ff0 (a scratch integration test in the QA clone, not committed: the
pen stile `loc.qip_sheep_shearer_fullstyle`, player coords read every tick after `opLoc1`):
- from the in-line tile: step on T+1, climb T+3, step off T+6;
- from 3 tiles north: walks to a diagonal tile (t3), then the in-line tile (t4 = T), step on T+1, climb T+3,
  step off T+6.
`STEP_ON_TICKS = 2` in `StileScript.kt` is the cause; the crossing is one tick longer than the official one.

Fix:
1. Code (new commit): `STEP_ON_TICKS = 1`, and `StileScriptTest` with it (its 10-tick window still passes; an
   assertion on the tick of the climb would pin it).
2. Spec, Sources: t2327 / t2328 / t2330 as above (arrival on the op tick t2328, step on t2329, climb t2330,
   step off t2333); the Behaviour rows "Step on 1718 t2328 ... on the op tick" and "Climb ... 2 ticks after the
   step on" to the recorded ticks.
3. PR text: step 3 "Two ticks later the existing climb" -> "The next tick"; the data-sources line "a one-tile move
   onto the near footprint tile on the op tick, two ticks later seq 839" -> "the player reaches the in-line tile
   on the op tick, steps onto the near footprint tile the next tick, climbs (seq 839 ...) the tick after that and
   steps off three ticks later".

## Checked and fine (not verdicts on a new commit)
- **build-test**: fork CI run 39353940765 (`CI`: cache, assemble/test/integration, boot "Server ready in
  11.95s") and 39353942714 (`Formatting`) green at 8646e5ff0 (head sha checked).
  `fix_gameval_conflicts.py --check` clean.
- **Scope**: 3 files (`StileScript.kt`, `ClimbOver.kt`, `StileScriptTest.kt`); one fix. No open upstream PR
  (`gh pr list --search stile`: none). On current `main` 829b9477d. No `Depends:`.
- **Accuracy otherwise**: loc 12982 is 1x2 and its footprint blocks walking (seen in game at 8a8c0ca7a, below; the reason for the approach tile). The
  player's three tile changes in 1718 are `[walk]` moves, never `[teleport]`: the branch's non-jump teleports
  are a declared caveat, as are the missing `loc_merge` and the guess for side/diagonal approaches. No other
  crossing of a `fullstyle` stile is in the 40 recordings (only other players' `loc_merge` at the pen stile,
  with no movement lines), so the side/diagonal approach stays a guess. The step-off timing (3 ticks after the
  climb) and the exactmove/seq values match.
- **Template**: every section of upstream's PR template is present; commit subjects `fix: ...`.
- Note for the re-check: from 3 tiles north the route to the stile ends on a diagonal tile, and the script then
  walks one more tile to the in-line tile (one extra tick). That is within the declared approach guess.

---
## History: internal gate at 8a8c0ca7a (void)

Run by sq24 on 2026-10-19. One commit on upstream `main` 829b9477d (the tip), fork PR AlexPerrin/OpenRune-Server#21.
`StileScript.kt` +10/-5. Spec and PR text at fork docs e146db7cd. The upstream gate wasn't run: the internal gate
stopped at the first failure, in game.

## Internal gate: FAIL. What must change
**The Sheep Shearer pen stile can't be climbed at all on this branch.**
- The stile is `Stile` (loc 12982, 1x2) at 3197,3276, so its footprint is (3197,3276) and (3197,3277).
- From every start tile tried, on both sides, the player walks up to the stile and stops, without crossing.
  Even standing right next to it (3197,3275 south, 3197,3278 north), Climb-over does nothing.
- Cause, from the code: `climbStile()` picks a footprint tile as `start` and calls `playerMove(start)`. The
  footprint tiles are part of the loc, which blocks walking, so the move ends beside them, `coords != start`,
  and the new early `return` skips `climbOver`. On `main` the climb started from the player's own tile, so it
  happened, if from the wrong place: a-thoren's report.
- Port Sarim's stile on `main` (`loc.farming_style`), which this copies, may have a walkable footprint. The
  `fullstyle` stiles evidently don't.
- Fix: don't require standing on the footprint. Use the tile outside the footprint on the player's side, in
  line with the stile (`near` minus one step across the fence, or `far` plus one), as the start. Walk there
  (it is reachable), then `climbOver` across the 2-tile footprint to the tile beyond it on the other side.
  Alternatively, climb from wherever the interaction ends with the start and destination chosen by side.
  Whichever way: check it in game on the pen stile and one other `fullstyle` stile before handing over. The PR
  text's "the player walks onto the stile's tile on their side" has to change with it.

## Evidence
- **build-test**:
  - `qa.sh gate` on clone `qa/stile-8a8c0ca7a`, `origin/main` = 829b9477d.
  - Every step rc=0: compile, freshCache + mergePluginGamevals, buildCache, generateRsa, installDist,
    spotlessCheck, assemble.
  - `test` 764/0. `integration` 79/0 (`generic-locs` 4/4). Total 794 s.
  - `fix_gameval_conflicts.py --check` is clean.
  - Boot: "Server ready in 18.24s". No exception or error lines.
- **In game** (desktop lock 19:47-19:52, fresh account `sq24st`). `::tele` to each start tile, then
  Climb-over, then the position after 9 s:

| Start | End | Crossed? |
|---|---|---|
| 3196,3275 (south, diagonal) | 3197,3275 | no |
| 3198,3276 (beside, east) | 3198,3276 | no |
| 3197,3271 (south, 4 away) | 3197,3275 | no |
| 3194,3274 (south-west, far) | 3196,3276 | no |
| 3197,3275 (south, adjacent) | 3197,3275 | no |
| 3198,3278 (north, diagonal) | 3197,3278 | no |
| 3196,3277 (beside, west) | 3196,3277 | no |
| 3200,3279 (north-east, far) | 3198,3277 | no |
| 3197,3278 (north, adjacent) | 3197,3278 | no |
| 3197,3282 (north, 5 away) | 3197,3282 | no |

  There were no chat lines or errors. The other `fullstyle` stile was not tried after this failure.
  `main`'s behaviour wasn't re-run here; a-thoren's #366 report says that version climbs, from the wrong
  tile. Clean-up: desktop RuneLite back on the live target (logged in as `desktop`), qa-or-server/qa-or-http
  removed, lock freed 19:52.
- **Review notes for the re-check (not verdicts)**:
  - No upstream overlap (`gh pr list --search stile`: none open). The PR's comparison with Port Sarim's
    `climbStile` on `main` is accurate in code.
  - The template's "Builds and the server boots" can be ticked after a passing gate.
  - "Matches the OSRS wiki ... no deviation known" is ticked. Walking vs snapping to the start tile is a
    declared guess in the spec but not in the PR's notes; add it.
