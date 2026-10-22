# QA: fix/stile-approach
- Branch: `fix/stile-approach`
- Commit: `513144d3dfcfcfd5613123bbe3457b952192c384`
## Upstream gate: **PASS** (sq33, 2026-10-21)
## Internal gate: N/A (dev-checked; sd27's check at 513144d3d is in the history below)

## Upstream gate at 513144d3d (sq33, 2026-10-21)
Three commits on upstream `main` 829b9477d (8a8c0ca7a, 8646e5ff0, 513144d3d), fork PR AlexPerrin/OpenRune-Server#21
(head 513144d3d). Spec `accuracy/stile-approach.md` and PR text `pr-texts/stile-approach.upstream.body` read from fork
docs 52233ff59 (the PR text equals PR #21's body). Every source below re-read by sq33 at the source, not from the spec.

### Build (CI at the exact commit): pass
- Fork `CI` run 39363600299, job "build & boot" 118435192812, head sha 513144d3d, success: `assemble test integration`
  ran `:content:generic:generic-locs:integration` (10 `@Test` in the module at the commit: `StileScriptTest` 6,
  `FenceCrossingScriptTest` 4), boot "Server ready in 15.938582831s"; the only error-like log line is a Kotlin
  language-version warning. `Formatting` run 39363603819 (spotless), success, same sha.
- `fix_gameval_conflicts.py --check` at the commit: "No gameval conflicts or unassigned IDs found."
- Branch server for the in-game check: current `upstream/main` e85278c9a ("Sync revision to 241") + 513144d3d, a clean
  merge (7e95f47d6 in `re/harness/qa/stile-approach-513144d3d-run`), `qa.sh gate-run` compile/cache/install all rc=0,
  "Server ready in 13.169377350s", no exception.
  Note for the environment, not the branch: the branch tree alone (cache tools `or2 = 3.0.4` from its base) no longer
  passes `:or-cache:freshCache` locally: the CDN now serves CS2 bundle 241.4, which the 3.0.4 compiler rejects (262
  SYNTAX_ERRORs in `speedrunning_init` / `cr_quests_ui_create_entry`); main's "241 Latest Support" (or2 3.0.7)
  compiles it. The branch does not touch cs2 or the cache tools. `qa.sh clone <slug> <commit> main` merges the push
  clone's local `main` branch, which was 27 commits behind; passing the sha of `origin/main` gives the real merge.

### Accuracy (independent re-read): pass
- Cache (osrs-mcp `cache_search`, SERVER, obj 12982 "Stile"): sizeX 1, sizeY 2, solid 2, op 1 "Climb-over". The
  footprint blocks walking, so the approach has to start outside it. Gamevals: `loc.qip_sheep_shearer_fullstyle` = 12982,
  `loc.fullstyle` = 993 (also `brain_farm_fullstyle` 22302, `death_fullstyle` 3730, which the branch does not list);
  `seq.human_walk_style` = 839.
- Wiki "Stile" (object 993): "Climb-over", no Agility requirement, no XP. Nothing on timing; the recording is the baseline.
- RSProx 1718 (revision 227), `re/ref/rsprox/txt/1718.txt`, player index 85, re-read at the tick markers:
  `[2327]` run (3195,3281) -> (3197,3280); `[2328]` `oploc1_v1 id=12982 coord=(3197,3276)` and run -> (3197,3278), the
  in-line tile outside the footprint; `[2329]` walk -> (3197,3277), the near footprint tile; `[2330]` walk -> (3197,3276),
  `sequence id=839 delay=30`, `exactmove to1=(3197,3277) delay1=30 to2=(3197,3276) delay2=94 angle=0`, `loc_merge
  id=12982 start=30 end=94 maxz=1`; `[2333]` walk -> (3197,3275). From arrival on the in-line tile (T): step on T+1,
  climb T+2, step off T+5. Matches the spec's Sources and Behaviour rows, and the PR text's steps 1-4.
- Sweep of all 40 transcripts for ops on the four `fullstyle` ids: only 1718 has one (this crossing); `loc_merge` on
  12982 appears 30 times in 1718 (other players), nowhere else. So the side/diagonal approach stays a guess, as the
  spec and PR say.
- LostCity `skill_agility/scripts/shortcuts.rs2` `[oploc1,fullstyle]` (line 198): side by `coordz(coord) >
  coordz(loc_coord)`, start on the near footprint tile, exactmove across, walk_coord beyond; reference only, as cited.
- Code against the rules (diff 829b9477d..513144d3d, 3 files): side by coordinate across the fence (`fromSouth`),
  `approach` = near footprint tile minus one step, `exit` = far tile plus one; walk to `approach` and `return` if not
  reached; `stepTo(start)` (`teleport(dest, TeleportType.Exempt)`), `delay(STEP_ON_TICKS = 1)`, `climbOver` (exactmove
  30/94 with `TeleportType.Exempt`, `seq.human_walk_style` delay 30, facing by direction), `delay(CLIMB_OVER_TICKS = 3)`,
  `stepTo(exit)`. On main `validateTeleport` returns true for `Exempt` before the wilderness check. `climbOver` is also
  used by `FenceCrossingScript` (`loc.gertrudefence`), as the PR says.
- Caveats in the PR cover every gap found: no `loc_merge` (engine has no API); the beside/diagonal approach is a guess
  (no recording); the climb's tile change goes out as a teleport (1718: a walk step) from the engine's `exactMove`; the
  op-tick vs next-tick start of the walk from beside the stile and the step-on on the op tick when already in line.

### Scope, format, PR text: pass
- One fix; the diff is only `StileScript.kt`, `ClimbOver.kt` and the new `StileScriptTest.kt` under
  `content/generic/generic-locs/.../stile`. No `Depends:` (none needed; #366 does not depend on it).
- No open upstream PR for the stile (`gh pr list -R OpenRune/OpenRune-Server --search stile`: only #366, the Sheep
  Shearer quest, whose author comment says the stile fix comes as its own PR; `--search fence`: #228, construction).
  sq32's "redundant: upstream #366 is open at this commit" stop was the observer matching the quest PR; it is not this
  change.
- Base 829b9477d is an ancestor of current `main` e85278c9a (11 commits behind); the merge is clean and none of them
  touches the stile files; the one `ProtectedAccess` change (f0c60cc54, run auto-enable) does not touch `teleport`,
  `exactMove` or `playerMoveWithMinDelay`.
- Commits: `fix: ...` subjects, authored Alex Perrin, co-author line. Upstream's PR template (since #369): Summary,
  Type of change, How it was tested, Checklist, Screenshots / notes all present; the Claude Code line at the end.
- Tests: `StileScriptTest` is a new file; the PR's Checklist justifies it ("the stile's side and tile geometry"), and
  the timing test pins the T+1 / T+5 ticks that the earlier gate failed on (AGENTS.md: tests for logic, not wiring).
  Acceptable.
- Claims checked against the commit: test names and count (6 tests, 10 in the module), the 1718 ticks, loc 12982 1x2 /
  blocks walk, seq 839 / exactmove 30/94, `TeleportType.Exempt`, Gertrude's fence sharing `climbOver`, the a-thoren
  #366 comment (2026-10-19T07:09Z), the four caveats. No false claim.

### In game (sq33, branch server = main e85278c9a + 513144d3d): pass
Desktop RuneLite through the desktop RSProx on the branch server (`qa.sh start`, port 43596, fresh accounts), account
`desktop`, desktop lease 8:55-9:17 PM EDT. The client was driven through the devtools MCP (`::tele` to each start tile,
`interact_object` 12982 "Climb-over", position polling); the evidence is our own RSProx recording of the session,
transcribed headlessly (`re/evidence/stile-approach/rsprox-own-session-sq33-20261021T211253-7b4d9da.txt`; per-tick
extract `rsprox-own-session-sq33-ticks.txt`; client-side traces `ingame-sq33-513144d3d.txt`). The pen stile
(3197,3276), ten crossings, ticks from the transcript (op tick, then the local player's moves):

| Start | Side | Transcript | Ends |
|---|---|---|---|
| (3197,3274) 2 S in line | S | 14 op + walk (3197,3275); 15 walk on (3197,3276); 16 climb; 19 walk off | (3197,3278) |
| (3197,3279) 1 N in line | N | 44 op + walk (3197,3278); 45 on; 46 climb; 49 off | (3197,3275) |
| (3196,3277) beside, west | N | 74 op; 75 run (3197,3278); 76 on; 77 climb; 80 off | (3197,3275) |
| (3198,3276) beside, east | S | 104 op; 105 run (3197,3275); 106 on; 107 climb; 110 off | (3197,3278) |
| (3198,3275) diagonal SE | S | 134 op + walk (3197,3275); 135 on; 136 climb; 139 off | (3197,3278) |
| (3196,3278) diagonal NW | N | 164 op + walk (3197,3278); 165 on; 166 climb; 169 off | (3197,3275) |
| (3194,3270) 4-6 tiles SW | S | 194 op, run; 196 walk (3197,3275); 197 on; 198 climb; 201 off | (3197,3278) |
| (3200,3279) 3 tiles NE | N | 224 op, run (3198,3277); 226 run (3197,3278); 227 on; 228 climb; 231 off | (3197,3275) |
| (3197,3278) on the in-line tile | N | 253 op + on; 254 climb; 257 off | (3197,3275) |
| (3197,3271) 5 S in line | S | 283 op, run; 284 run (3197,3275); 285 on; 286 climb; 289 off | (3197,3278) |

Every crossing: arrival on the in-line tile outside the footprint on the player's side (T), `[walk]` onto the near
footprint tile T+1, `[teleport]` to the far footprint tile with `[sequence] id=839 delay=30` and `[exactmove] delay1=30
delay2=94` (angle 1024 northward, 0 southward) T+2, `[walk]` onto the tile beyond T+5: the 1718 ticks (t2328 -> 2329
-> 2330 -> 2333). Never a climb from afar, off line or from the far side; the step on and step off go out as walk
steps, the climb's tile change as a teleport (the declared caveat). The beside-the-stile approach starts the tick
after the op; standing on the in-line tile, the step on is on the op tick (both as the PR's last caveat says). The
8a8c0ca7a failure (stuck beside the footprint) does not occur. No error message in the client; the server's runtime
log was not kept (the container is removed by `qa.sh stop`); its boot log had no exception.
Not covered: a second `fullstyle` stile (993, members-only areas; the account is non-members); the "can't reach the
in-line tile" path (no reachable setup at the pen). Both are code-only paths of the same geometry.

Clean-up: client logged out and stopped, the default proxy target restored, `qa-or-server`/`qa-or-http` removed,
desktop lease released 9:17 PM EDT. Incident during this gate (noted to the orchestrator at 8:49 PM): a denied lease
take was not caught and my chain stopped cq3's desktop client; undone within a minute, cq3 had to restart its client.

### Verdict: PASS
Ready for the upstream PR with the text in `pr-texts/stile-approach.upstream.body`.

---
## History: internal gate at 513144d3d (dev's check, sd27, 2026-10-19): PASS
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

## History: upstream gate at 8646e5ff0 (sq24, 2026-10-19): FAIL (void)
Run by sq24 on 2026-10-19 at 8646e5ff0 (two commits on upstream `main` 829b9477d, still the tip: 8a8c0ca7a and the
fix 8646e5ff0; fork PR AlexPerrin/OpenRune-Server#21). Spec `accuracy/stile-approach.md` and PR text
`pr-texts/stile-approach.upstream.body` read from sd23's docs commit 65cc94f05. The gate stopped at the first
failure, the accuracy check against RSProx; in game was not re-run.

### What must change
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

### Checked and fine (not verdicts on a new commit)
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

## History: internal gate at 8a8c0ca7a (sq24, 2026-10-19): FAIL (void)
One commit on upstream `main` 829b9477d (the tip), fork PR AlexPerrin/OpenRune-Server#21.
`StileScript.kt` +10/-5. Spec and PR text at fork docs e146db7cd. The upstream gate wasn't run: the internal gate
stopped at the first failure, in game.

### Internal gate: FAIL. What must change
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

### Evidence
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
