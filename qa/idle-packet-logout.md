# QA: fix/18-idle-packet-logout
- Branch: `fix/18-idle-packet-logout`
- Commit: `cfe4a706f45ce87b8123d9dd6713b717657101f3`
## Internal gate: **PASS**
## Upstream gate: **PASS**

Run by sq25 (companion to sd24) on 2026-10-19. The commit is not pushed (the fork branch backs open upstream
PR #348, whose head is still b484a4039); fetched from sd24's clone `re/agents/sd24/or` (worktree w18). Two
commits on 1be5dfb5f: b484a4039 (the handler, as on #348) and cfe4a706f (review on #348: `gameplay.idle-logout`
toggle, `IdleHandlerTest` dropped). Text `pr-texts/idle-packet-logout.*` at docs 8f358b8b9 (unchanged at
fe39bad9a). Clone `re/agents/sq25/or-18`, gate on upstream `main` bcdb03292 + the branch (merge 5b2f88bf8,
`origin/main` = bcdb03292 for the ratchet).

## Internal gate details (PASS)
### build-test
One container (`eclipse-temurin:21`, 4 GB, Gradle home `sq25-gradle`, `--no-build-cache
--no-configuration-cache`, `--max-workers=2`, test/integration 1 worker, in-process Kotlin, cache steps
`-Xmx2600m`), one `./gradlew --no-daemon` per step, `game.yml` from `game.example.yml`. Run before the WSL
restart; the step logs were in /tmp and were lost with it, the results below were read from them at the time
("main" accepted this run, 2026-10-19).

| Step | Result | Time |
|---|---|---|
| `clean` + or-cache classpath compile | pass | 103s |
| `:or-cache:freshCache :or-cache:mergePluginGamevals` | pass | 65s |
| `:or-cache:buildCache` | pass | 35s |
| `generateRsa` | pass | 20s |
| `:server:app:installDist -x test` | pass | 552s |
| `spotlessCheck` | pass | 26s |
| `assemble` | pass | 75s |
| `test` | pass, 708 tests, 0 failures (no `IdleHandlerTest`) | 281s |
| `integration` | pass, 79 tests, 0 failures | 185s |
| boot | "Server ready in 19.96s" (and 8.9s / 10.3s on the in-game boots), no Exception/ERROR line; config line shows `idleLogout=true` | |
| `fix_gameval_conflicts.py --check` | "No gameval conflicts or unassigned IDs found." | |

`main` has not touched `IdleHandler.kt`, `MessageConsumerProvider.kt`, `ServerConfig.kt` or `game.example.yml`
since 1be5dfb5f, up to today's 731fd392a.

### accuracy-check
- OSRS Wiki "Idle", Logout timer (osrs-mcp `wiki_page`, 2026-10-19): "a hidden timer that will automatically log
  out any player who has been idle for 5 minutes (if out of combat)"; game clients can extend it to 30 minutes
  (client side). rsprot `Idle` (osrs-241-model sources): sent after 15,000 client cycles (20 ms) without mouse
  or keyboard input, five minutes. The handler routes IDLE to `manualLogout`; `PlayerLogoutProcess
  .processManualLogout` refuses while `isLogoutBlocked()` (combat) with the logout button's message, as the PR
  caveat says. Matches: the "if out of combat" rule holds through the same check.
- Not covered (and not claimed): the 6-hour forced logout and its warnings (wiki, same section); a separate
  feature.

### In-game (desktop RuneLite, fresh account `desktop` on the branch server, sq25-or-server 43597 + sq25-or-http
8084, under the desktop lock)
- `idle-logout: true` (default): logged in 01:44:40, no input; the client was on LOGIN_SCREEN at the 01:50:43
  poll (15 s polls, still LOGGED_IN at 01:50:28): logged out after ~5.5-6 minutes idle.
- `idle-logout: false` in the clone's `game.yml` (boot log `idleLogout=false`): logged in 01:51:50, no input;
  still LOGGED_IN at 01:59:42 (468 s of polling, ~8 minutes idle).
- Combat-blocked IDLE not tried (caveat in the PR).
- `game.yml` restored to `true`; containers removed.

### reverse
n/a: the protocol claim (IDLE after 15,000 cycles) is rsprot's documentation, checked in its sources, and the
in-game run shows the client sending it.

## Upstream gate, first run: FAIL (text fixed at docs b678f7eb7; see "Upstream re-run" at the end)
Passes: scope (one fix: handler, listener registration, the config key and its `game.example.yml` entry);
no other upstream PR for idle logout (`gh pr list --search idle`: only #348); `--check` clean; commit subjects
`fix:`, authored as Alex Perrin with the Co-Authored-By line; no KDoc or explanatory code comments (the
`game.example.yml` comment documents the key, as the file does for every key); no test, as asked.

**The maintainer's comments are answered exactly:** "No Test is needed for this tbh" (on IdleHandlerTest):
the test is deleted in cfe4a706f. "Can we have a server wide toggle to turn this off in server config default
it to on tho": `GameplayConfig.idleLogout` (`gameplay.idle-logout` in `game.yml`) defaults to `true`, and the
handler returns early when it is false (checked in game above).

**Branch as it is (not rebased): fine.** It is 18 commits behind `main` (731fd392a), merges with no conflict,
and `main` has touched none of its files; the gate ran on the merge. Rebasing would only rewrite the commits
under review; no need unless upstream asks.

Fails on the PR text (`pr-texts/idle-packet-logout.upstream.body`), text-only, no new commit needed:
1. **No "Data sources" section** (CONTRIBUTING requires it). Add one, e.g.: OSRS Wiki (by the wiki's
   contributors, CC BY-NC-SA 3.0): Idle, "Logout timer" (5 idle minutes, out of combat); rsprot's `Idle`
   message doc (15,000 client cycles).
2. **Stale results line.** "On this branch merged onto current `main` (c2492318d ...)": `main` is no longer
   c2492318d. Name the base of the run, e.g. "merged onto `main` bcdb03292 (no shared files with `main` up to
   731fd392a)", with this record's results (test 708, integration 79, boot pass).
3. Optional: under Tests, the manual check from this record (default on: logged out after ~5-6 idle minutes;
   `idle-logout: false`: still logged in after 8).

Re-check after the text change: pr-text-review only (same commit).

## Upstream re-run: PASS (text at docs b678f7eb7, same commit)
pr-text-review of `pr-texts/idle-packet-logout.upstream.body` and `.title` at b678f7eb7 (sd24's fix for the two
items above; code unchanged at cfe4a706f), 2026-10-19:
- Data sources present: OSRS Wiki "Idle", Logout timer (5 idle minutes, out of combat; matches my read of the page)
  and rsprot's `Idle` doc (15,000 client cycles; matches its sources), with the wiki's licence line.
- Results line: "gate run on upstream `main` bcdb03292 + this branch" is true of this record; "`main` touches none
  of this branch's files up to 731fd392a" confirmed: `git diff --name-only 1be5dfb5f 731fd392a --` the branch's 4
  files is empty. The step list (no build or configuration cache, test 708/0, integration 79/0, boot) matches
  the internal gate table.
- Tests: the in-game lines (default logged out after about 5-6 idle minutes; `false` with `idleLogout=false` in
  the boot log, still logged in after 8) match the In-game section; "IDLE while logout is blocked by combat was
  not tried" is true.
- Unchanged claims re-checked: handler and toggle as in the diff; combat-blocked message from
  `processManualLogout`; release note and checklist true. Every section present; subject `fix:`.
- Caveats complete against the accuracy check (the combat-blocked case; the 6-hour logout is not claimed).
