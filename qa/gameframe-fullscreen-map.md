# QA: fix/57-gameframe-fullscreen-map
- Branch: `fix/57-gameframe-fullscreen-map`
- Commit: `d3a194b9e47b680569eb01ab4008551a054bdcce`
## Internal gate: **PASS**
## Upstream gate: **FAIL**

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

## Upstream gate details (FAIL)
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
