# QA: fix/stile-approach
- Branch: `fix/stile-approach`
- Commit: `8a8c0ca7ab070a84b1857b84883424af9d9ac451`
## Internal gate: **FAIL**
## Upstream gate: **PENDING**

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
