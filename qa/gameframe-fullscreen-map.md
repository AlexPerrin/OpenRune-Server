# QA: fix/57-gameframe-fullscreen-map
- Branch: `fix/57-gameframe-fullscreen-map`
- Commit: `e551ba7cea4cc638ff3c6ca6923fc45f2f519d93`
## Upstream gate: **FAIL**
## Internal gate: N/A (dev-checked)

Run by sq25 on 2026-10-19 at the merge commit e551ba7ce (upstream `main` 829b9477d merged into d3a194b9e; backs
upstream PR #362). The commit is not on GitHub: fetched read-only from sd24's clone (`re/agents/sd24/or`, worktree
w57) into `re/agents/sq25/or`, gated in a fresh `git clone --shared` `re/agents/sq25/r-57` (`origin/main` =
829b9477d). Text `pr-texts/gameframe-fullscreen-map.{title,upstream.body,comment}` at docs aa3080530. The
earlier PASS at d3a194b9e (kept below) is voided by this commit.

## Failing item: accuracy, the RSProx comparison finds an unlisted divergence

Recording 3204 (rsprox.net, revision 232.1; found by INTERFACE 165 and VARBIT 4607 searches, transcribed
locally) switches between `toplevel_osrs_stretch` (161) / `toplevel_pre_eoc` (164) and `toplevel_display` (165)
eight times (Oculus orb and the login welcome screen; it never opens the world map 595). On none of these
switches does the official server send `resizable_stone_arrangement` (4607): the frame goes 161 -> 165 -> 161
(and 164 -> 165 -> 164) with 4607 unchanged. 4607 changes once in the recording, 0 -> 1, sent just before the
161 -> 164 `if_opentop` after the player picked the layout in the display settings (tick 5916-5917).

The branch, like `main`, writes 4607 = 1 (the 165 row's flag) when the fullscreen map opens, and with this PR
puts the old value back on close/logout. In game on this commit (below) 4607 goes 0 -> 1 -> 0 around the map.
Official evidence for the same top level says 4607 is not touched at all. The PR text says "While the map is
open the flag still changes as before" and has no caveat for it, and its rsprox note says "we have no rsprox
dumps of the official game", which is no longer true.

**Required change, one of:**
- (preferred, code) don't write `resizable_stone_arrangement` when moving into or out of a frame that is not
  `isGameplay` (the official behaviour in 3204). The stone choice then survives the map, fixed-frame closes and
  logout by itself, and the saved-state varbit may become unnecessary: the dev/orchestrator decide, since it was
  the maintainer's review request. Re-gate at the new commit.
- (text only) keep the code, and in the PR text: list the divergence in the notes/caveats ("the official server
  doesn't send `resizable_stone_arrangement` on switches to `toplevel_display`: RSProx recording 3204, Oculus
  orb; this PR still sets it while the map is open and restores it"), and replace "we have no rsprox dumps of the
  official game" with what was compared (3204: toplevel 165 switches; no recording opens the world map 595).
  Re-check: pr-text-review only.

## Checks that passed at e551ba7ce

### build-test (local gate; the commit isn't on GitHub, so no fork CI)
`openrune/upstream/qa/qa.sh gate` with `GATE_VOL=sq25-gradle`, logs `re/agents/sq25/r-57.logs/`:
1a-compile 17s, 1-cache 72s, 2-buildcache 36s, 2b-rsa 20s, 7-install 27s, 3-spotless 18s, 4-assemble 80s,
5-test 105s (764 tests, 0 failures), 6-integration 132s (79 tests, 0 failures), all rc=0. Boot (container
sq25-or-server, port 43597): "Server ready in 12.6s", no Exception/ERROR line in the session.
`fix_gameval_conflicts.py --check` on the tree: "No gameval conflicts or unassigned IDs found."

### Scope
Net diff against `main` 829b9477d: 3 files, +38/-1 (GameframeScript.kt, the gameframe `gamevals.toml`, the new
`gameframe_vars.toml`); `isFallbackCandidate`/`FULLSCREEN_MAP_TOPLEVEL` are gone and #315's `isGameplay` is used
throughout, as the merge message and the text say. On current `main`. Only #362 touches the gameframe
(`gh pr list --search gameframe`); #370 and #371 (updated today) add no varp 65471 / varbit 65447.

### In game (desktop RuneLite, fresh account `sq25gf`, branch server on 43597, desktop lock 20:29-20:41)
4607 read with `get_var`, 65447 with `::getvarbit gameframe_stone_before_fullscreen`, top level from
`list_interfaces`.
- 161, `::varbit resizable_stone_arrangement 0`: 4607 = 0, 65447 = 0. Orb "Fullscreen World Map": 165, 4607 = 1,
  65447 = 1. Close (595:38): 161, 4607 = 0, 65447 = 0.
- Same with 4607 = 1 first: open -> 165, 4607 = 1, 65447 = 2; close -> 161, 4607 = 1, 65447 = 0.
- 4607 = 0, map open, logout (182 logout CC_OP) and login: 161, 4607 = 0, 65447 = 0.
- `::varbit gameframe_toplevel 165`, logout and login: 161 (`gameframe_toplevel` = 161), 4607 = 0.
- Not tried: closing the map to a fixed frame (as the text says).

### PR text and comment (pr-text-review), apart from the failing item
- Title `fix:`; every template section present; data sources; release note; Claude Code line.
- Problem, change, ids 65471/65447, Temp/Never scope, `--check`, and "the gameframe module has no tests": true
  of the commit.
- "In game on the branch before the merge": true, and now also checked on the merge (above); worth saying so
  when the text is next edited.
- The drafted `.comment` is true of e551ba7ce (merge commit, no rebase; `isFallbackCandidate()` and
  `FULLSCREEN_MAP_TOPLEVEL` dropped; the restore tests `isGameplay`; build cache, compile, spotlessCheck, boot,
  `--check`). It should change with whichever fix above is chosen (and can add test 764/0, integration 79/0).

---

## Previous gate at d3a194b9e (voided by e551ba7ce; kept as history)

Its header was: # QA: fix/57-gameframe-fullscreen-map / - Branch: `fix/57-gameframe-fullscreen-map` / - Commit: `d3a194b9e47b680569eb01ab4008551a054bdcce` / ## Internal gate: PASS / ## Upstream gate: PASS

Run by sq25 (companion to sd24) on 2026-10-19. The commit is not pushed (the fork branch backs open upstream
PR #362, head still a01ebb28b); fetched from sd24's clone `re/agents/sd24/or` (worktree w57). Three commits on
1be5dfb5f: a01ebb28b (the fix as on #362), 11d0cf135 (review on #362: the pre-fullscreen stone arrangement moves
from an attribute to the server varbit `varbit.gameframe_stone_before_fullscreen` on `varp.gameframe_state`),
d3a194b9e (ids moved to 65471 / 65447, off content/sheep-shearer's 65472 / 65449). Text
`pr-texts/gameframe-fullscreen-map.*` at docs 8f358b8b9 (unchanged at fe39bad9a).

The first gate attempt (uncached, `re/agents/sq25/or-57`) died in the WSL restart; this is the re-run: fresh
`git clone --shared` `re/agents/sq25/g-57`, tree dc0f85c79 = upstream `main` 731fd392a + d3a194b9e (clean merge),
`origin/main` = 731fd392a for the ratchet.

## Internal gate details (PASS)
### build-test
`openrune/upstream/qa/qa.sh gate` (the build-test skill's gate: one priority ticket, one container, remote
build cache for compiles, tests never cached; `GATE_VOL=sq25-gradle`). Logs `re/agents/sq25/g-57.logs/`.

| Step | Result | Time |
|---|---|---|
| `clean :or-cache:qaCacheToolsClasspath` | pass | 19s |
| `:or-cache:freshCache :or-cache:mergePluginGamevals` | pass | 63s |
| `:or-cache:buildCache` (packs `gameframe_vars.toml`) | pass | 30s |
| `generateRsa` | pass | 13s |
| `:server:app:installDist` | pass | 498s |
| `spotlessCheck` | pass | 30s |
| `assemble` | pass | 70s |
| `test` | pass, 726 tests, 0 failures | 254s |
| `integration` | pass, 79 tests, 0 failures | 163s |
| boot (`sq25-or-server`, 43597) | "Server ready in 12.07s", no Exception/ERROR line | |

`fix_gameval_conflicts.py --check` ("No gameval conflicts or unassigned IDs found.", with this tree's
`max-ids.toml`) on: the branch alone (d3a194b9e), the merge with `main` 731fd392a, and the branch merged into
fork `integration` 5cc473397 (clean merge). A table-aware scan of every open upstream PR's gamevals (heads
fetched 2026-10-19) finds no other new varp 65471 or varbit 65447 (#231 adds a *varp* 65447,
`farming_patch_07`: a different table, no clash). `main` has touched no gameframe or worldmap file since
1be5dfb5f.

### accuracy-check
No OSRS mechanic is claimed beyond the frames themselves: the fullscreen world map frame
(`toplevel_display`, 165) is never a login frame, and the classic/modern stone choice
(`resizable_stone_arrangement`, varbit 4607) survives the map. The frame flags come from or-cache's
`GameframeTable` (osrs_stretch 161: resizable, stone false; pre_eoc 164: resizable, stone true; display 165:
resizable, stone true). No divergence found.

### In-game (desktop RuneLite, fresh account `desktop` on the branch server, desktop lock)
Values: client varbit 4607 (`get_var`), toplevel from `list_interfaces`, the server-only varbit 65447 through
`::getvarbit gameframe_stone_before_fullscreen` (the client can't read it: transmit Never).
- Start on 161; `::varbit resizable_stone_arrangement 0` -> 4607 = 0, 65447 = 0.
- Orb "Fullscreen World Map" -> top 165, 4607 = 1 (the map frame's flag, as before), 65447 = 1 (classic saved).
- Close (worldmap:close) -> top 161, 4607 = 0 restored, 65447 = 0.
- Open again (4607 = 1, 65447 = 1), then log out with the map open (logout:logout CC_OP) and log in -> top 161,
  4607 = 0 (restored at logout), 65447 = 0. This is the logout-with-the-map-open path the PR says was not tried.
- `::varbit gameframe_toplevel 165` (saved frame = the map), 4607 = 1, log out and in -> top 164 (pre-EoC,
  matching the stone flag), `gameframe_toplevel` = 164: not the map frame.
- Not tried: closing the map back to a *fixed* frame (switching the client to fixed mode through the settings
  dropdown wasn't driven). The code path is the same `restoreStoneArrangement()` as the close and logout paths
  above.
- Seen on the way, pre-existing and not this branch: a new account logs in on 161 with 4607 = 1, because the
  settings pack's `account_default_resizable_stone_arrangement` (value 1) is applied after the login frame is
  picked. Upstream behaviour, unchanged here.

### reverse
n/a.

## Upstream gate, first run: FAIL (text fixed at docs b678f7eb7; see "Upstream re-run" at the end)
Passes: scope (one fix; gameframe script, its gamevals and a pack varp/varbit; nothing unrelated); no other
upstream PR on the gameframe (`gh pr list --search gameframe`: only #362); `--check` clean as above; `fix:`
subjects, authored as Alex Perrin, Co-Authored-By lines; nothing made public for a test, no test added (interface
wiring, as AGENTS.md says). One new comment, `// The fullscreen world map has pre-EoC's flags but is never a
frame to fall back to.`, is the one-line "game quirk" note AGENTS.md allows and was in a01ebb28b when the
maintainer reviewed it; acceptable.

**The maintainer's comment is answered exactly:** "Make this a server sided varbit please :)" (on the saved
stone state): 11d0cf135 replaces the attribute with `varbit.gameframe_stone_before_fullscreen` on
`varp.gameframe_state` (scope Temp, transmit Never) in the gameframe pack; in game the server holds it and the
client never sees it.

**Branch as it is (not rebased): fine.** 18 commits behind `main` 731fd392a, clean merge, no shared files,
the gate ran on the merge. Ids were assigned against the current max ids and still pass `--check` on today's
`main`. No rebase needed unless upstream asks.

Fails on the PR text (`pr-texts/gameframe-fullscreen-map.upstream.body`), text-only, no new commit needed:
1. **No "Data sources" section** (CONTRIBUTING requires it). Add one, e.g. "None external: the frames' flags
   come from or-cache's `GameframeTable` rows."
2. **The results paragraph is stale and under-claims the checklist.** It says "merged onto current `main`
   (c2492318d ...)", `buildCache` "run with the earlier ids 65472 / 65449", and lists no `test`/`integration`
   run while the checklist ticks them. Replace with this record's run: `main` 731fd392a + d3a194b9e, buildCache,
   spotlessCheck, assemble, test (726), integration (79), boot pass; `--check` clean on the branch, on the merge
   with `main` and with our other open PRs' ids.
3. **The in-game lines are out of date.** "Not re-checked in game on this branch" and "The logout-with-the-map-
   open path was not tried" are no longer true: replace them with the checks above (open/close restores 4607,
   logout with the map open restores it, a saved map frame logs in to 164). Keep "closing to a fixed frame not
   tried in game" as the remaining gap.

Re-check after the text change: pr-text-review only (same commit).

## Upstream re-run: PASS (text at docs b678f7eb7, same commit)
pr-text-review of `pr-texts/gameframe-fullscreen-map.upstream.body` and `.title` at b678f7eb7 (sd24's fix for the
three items above; code unchanged at d3a194b9e), 2026-10-19:
- Data sources present ("None external", the flags from or-cache's `GameframeTable` rows 161/164/165): true.
- Results paragraph: `main` 731fd392a + this branch, clean merge; "`main` touches no gameframe or world map file"
  confirmed (`git diff --name-only 1be5dfb5f 731fd392a` over the branch's 3 files and `content/interfaces/worldmap`
  is empty); buildCache packs `gameframe_vars.toml`, test 726/0, integration 79/0, boot; `--check` clean on the
  branch, the merge with `main`, and with our other open PRs' ids (fork `integration` and the open-PR scan
  above). It no longer claims "no build cache" (this run used the remote build cache for compiles; tests
  uncached). Matches the internal gate table.
- In-game lines match the In-game section (161 with 4607 = 0 -> map 165, 4607 = 1, 65447 = 1 -> close 161,
  0, 0; logout with the map open -> 161, 4607 = 0; saved 165 with 4607 = 1 -> 164). The 4607 = 0 start state was
  set with `::varbit` (a new account starts at 1, upstream's account default); the text states the state, not
  how it was reached, which is fine.
- Caveats now list the untried fixed-frame close, the only gap left; the rows-order note is true.
- Unchanged claims (problem, change, ids, review answer, release note, checklist) re-checked against the diff.
  Every section present; subject `fix:`.
