# QA: acc/genie
- Branch: `acc/genie`
- Commit: `3ccb86bd85c6d96e1e067761d0bb72d64a7d47fd`
## Internal gate: N/A (dev-checked)
## Upstream gate: **FAIL**

sq30 ran this on 2026-10-20 at 3ccb86bd8, which is five commits on upstream `main` 829b9477d. I re-fetched `main` and it is still the tip. The commits are 068aabee2, e9c7bcac3, sd23's 9a222bd53 and a44a3daa0, and sd30's 3ccb86bd8. Fork PR #18. The spec is `accuracy/genie.md` and the PR text `pr-texts/genie.upstream.body`, both at fork docs 9eb87c281. The gate fails on accuracy, and two of the PR text's claims change with it. The verdicts at e9c7bcac3 and earlier, below, are void and kept as history.

Evidence is in `re/evidence/gate-acc-genie/` (main checkout):
- `sq30-session-3ccb86bd8.txt`: the RSProx transcript of my desktop2 session on the branch server.
- `sq30-session-genie-lines.txt`: the Genie's packets from that transcript.
- `runA-*.json`, `runB-timeline.json`: devtools timelines.
- `branch-server.log`

## What must change (new commit on `acc/genie`)

1. **He leaves at the wrong time after Talk-to.** The recordings show him leaving on his next call tick (the add's 10-tick grid). The branch leaves exactly 10 ticks after Talk-to (`HAND_OVER_TICKS`).
   - **Why the spec's reading falls short.** Rule 15 and sq24's fix 6 read "10 ticks after Talk-to" from 3056 alone. There the Talk-to fell on a call tick: add t4000, Talk-to t4030 (that tick's call still fires), delete t4040. That recording can't tell "Talk-to + 10" apart from "the next call tick".
   - **What 1718 adds** (`re/ref/rsprox/txt/1718.txt`). Its two Genies that were neither ignored nor left to time out leave off the Talk-to + 10 pattern but on the grid:
     - G1: add t1058 (line 29553), Genie face cleared t1062 (29708), delete t1068 (29900) = add + 10. There was no call at all.
     - G3: add t7140 (135751), call t7150, face cleared t7156 (136079), delete t7160 (136208) = add + 20.
     - In 3056 the face is cleared on Talk-to + 1 (t4031, line 40559). Read that way, the Talk-to for G1 was t1061 and he left 7 ticks later; for G3 it was t7155 and he left 5 ticks later.
     - Even without that inference, a delete at add + 10 can't be Talk-to + 10, because the earliest possible Talk-to is add + 1.
     - The ignored G2 never clears its face (t1177-1280).
     - All four recorded departures are on the add's grid: +10, +20, +40, and +103 after "Maybe later." at +100.
   - **Branch in game** (my transcript): add t794, call t804, Talk-to t808 (opnpc1, line 13903), delete t818 (line 14111). That is Talk-to + 10. The recordings put it at t814, the next call tick.
   - **Fix:** don't restart the timer at Talk-to (drop `HAND_OVER_TICKS` and `leaveCycle`). On the call timer's next fire after the hand-over, leave with the smoke and sound 1930, and send no call or wave. Keep "Have a nice day" for a Talk-to in between. Update the test, and spec rule 15 ("leaves on his next call tick, 1-10 ticks after Talk-to; 1718 G1/G3, 3056").
   - In the PR text, replace "he leaves 10 ticks after Talk-to whatever the dialogue is doing" with the new rule. If the code isn't changed, say it as a caveat and drop the claim that it comes from the recordings.
2. **The Rub message gives the wrong XP.** On this world the message says 10 times the level, but the player gets that times the world's XP rate.
   - **In game:** Attack level 1 Rub gives the messagebox "Your wish has been granted!<br>You have been awarded 10 Attack XP!" (transcript t874, if 229:3, synth 2655). The XP that arrives is **+1500** (devtools xp history tick 1471: xpGained 1500, the world rate x150). The lamp went from 2 to 1.
   - **Cause:** `rub()` prints `statBase * 10` but calls `statAdvance(stat, xp.toDouble())`, which applies the rate.
   - **Official:** the recording (3056 t4038) shows 990 Construction XP at level 99. The message and the XP are the same number, with no multiplier.
   - **Fix, either one:**
     - give the unscaled XP (`statAdvance` with the rate turned off, if the API allows it), so the message stays true;
     - or print the XP actually added.
   - Update the PR text's lamp bullet and caveat C7 to match. Today they say the rate applies, while the message says otherwise.
3. **PR text, "How it was tested".** "These still need an in-game run on this commit; our QA gate does it before the PR is marked ready" is now out of date. On the next commit, give the gate's in-game result instead: the run below, redone at the fix.

## Checked and passing at 3ccb86bd8
**sq24's six divergences.** I compared each against the recordings (re-read at the lines cited: 3056.txt lines 40212-40700, 1718.txt G1/G2/G3) and against my session's transcript on the branch server. All but the leave timing are fixed:

| sq24 item | Recording | Branch in game (my transcript tick / line) | Verdict |
|---|---|---|---|
| Call interval | calls every 10 ticks from the add (3056 t4010/4020/4030, 1718 G2 t1187...1267) | ignored Genie: add t913, calls t923, 933, ... 1003 (lines 16040-17428), 10 ticks apart | fixed |
| Wave | seq 863 with the greeting and every call | seq 863 at t794, 804, 913, 923, ... 1003, 1040; none with "Maybe later." | fixed |
| Smoke and sounds | arrival: map_anim 86 h124 x2 + sound_area 2301 and 1930 r7; departure: map_anim 86 + 1930 | arrival t794/913/1040: one map_anim 86 h124 + 2301 + 1930 r7 at the add tile; departure t818/1016/1046: map_anim 86 + 1930 at his tile | fixed (one puff instead of two, and on the add tick: already in the PR's caveats) |
| Reward message | "Your reward is: <col=ff0000>1</col> x <col=ff0000>Lamp</col>." the Talk-to tick | same text, t808, the opnpc1 tick (line 13906); lamp +1 the same tick | fixed |
| Rub message box | if 229 "Your wish has been granted!<br>You have been awarded 990 Construction XP!" + synth 2655 | if 229 "...awarded 10 Attack XP!" (skill capitalised) + synth 2655, t874 | fixed in form; the XP number is wrong (item 2) |
| Leave timing | see item 1 | Talk-to + 10 | **not matching** (item 1) |

**sd30's departure (3ccb86bd8).** This matches 1718 G2 exactly:
- "Maybe later." at t1013 = add + 100, in place of the 10th call, with no seq (line 17582).
- Delete at t1016 = add + 103, with map_anim 86 and sound 1930 at his tile (lines 17613-17623).
- The code says it on the 10th timer fire (`calls + 1 >= LIFETIME_TICKS / CALL_INTERVAL`) and leaves after `IGNORED_LEAVE_TICKS` = 3. A Talk-to in those 3 ticks goes through `handOver`, which refuses (`ignored`), so the player gets "Have a nice day" and no lamp. That case is listed as a guess.

**Also in game.** Fresh account `sq30g1` on the branch server (my containers on ports 43598/8085), desktop2 under its lock from 01:58 to 02:31. A run between 02:03 and 02:12 is not counted: another agent's commands reached my client through a shared helper script, and the orchestrator confirmed it.
- Greeting: "Greetings, Master sq30g1!" on the add tick.
- Following: teleported 6 tiles away at t1550 (client tick), he was beside the player again at the next call.
- Second Talk-to after the lamp: "Have a nice day, Master." (t816), and no second lamp.
- `::randomevent` with a Genie out: "Your Genie is already here."
- Dismiss: delete with smoke and sound 1930 the same tick (t1046).
- Lamp chooser: interface 240, "Choose the stat you wish to be advanced!".
- The server log has no exception or error lines except my own malformed `::tele` (an IllegalArgumentException for level 50).
- Not re-run (unchanged code paths, checked by sq24 at 068aabee2): full inventory, interrupted dialogue, another player's lines.

**Accuracy, other rules.** I re-read "Random events", "Genie" and "Transcript:Genie" through osrs-mcp `wiki_page` on 2026-10-20. Every quoted rule in spec rows 1-8, 10, 12-14, 16-18, 21-24 and 27 is still there. The cache has NPC 326 with ops Talk-to/Dismiss, and the gamevals have seq 863 `emote_wave` and spotanim 86 `smokepuff`. Synth 2301 has no cache name, which is why it gets the module name `macro_genie_appear`. The caveats C1-C7 in the PR cover the remaining gaps from the spec. Not in the spec, minor: the official Genie stops facing the player one tick after Talk-to; I didn't check this on the branch.

**Build.**
- Fork CI at exact head 3ccb86bd85c6d96e1e067761d0bb72d64a7d47fd: `CI` run 39375088118 is success. It ran freshCache, buildCache, generateRsa and `assemble test integration`, and booted to "Server ready in 14.6s".
- `Formatting` run 39375089748: success.
- The branch server's own build (`qa.sh gate-run` on 829b9477d + 3ccb86bd8, identical tree) booted to "Server ready in 10.42s".
- `fix_gameval_conflicts.py --check`: "No gameval conflicts or unassigned IDs found."

**Scope.** 8 files, all in `content/events/random-events`, based on current `main`, no `Depends:`.
- Commit subjects are `feat:`/`fix:` with Co-Authored-By.
- `gh pr list -R OpenRune/OpenRune-Server --search genie` and `"random event"` both show only #355 (ours, draft).
- The PR text has all the template's sections (Summary, Type of change, How it was tested, Checklist, Screenshots / notes). Its other claims are true of the commit:
  - 9 tests, as listed;
  - ids 65534/65533/65501/63886-63892, and synth 2301;
  - the checklist ticks and notes;
  - the recordings cited.

---
## History (void)

### Run by sq24 at e9c7bcac3 (void; was `## Upstream gate: FAIL` at commit e9c7bcac3e5fb1d17ef2d9747bbbea5c00b52793)

Run by sq24 on 2026-10-19 at e9c7bcac3: two commits on upstream `main` 829b9477d (re-fetched, still the tip):
068aabee2 (`feat: Genie random event`, checked below) and e9c7bcac3 (`feat: keep the random event zones in a
server-only dbtable`). Fork PR AlexPerrin/OpenRune-Server#18 (head e9c7bcac3). Spec and PR text at fork docs
aa3080530. The verdicts at 068aabee2 and earlier below are void. The gate stopped at the first failure, the
RSProx comparison (accuracy); the in-game re-check was not run (the code has to change anyway; the desktop
lock was held by sq25 with a grant pending for sd24).

## Upstream gate at e9c7bcac3: FAIL. What must change

### The e9c7bcac3 change itself: passes
- The five dungeon areas (no events), Ferox Enclave (events allowed) and the Grand Exchange box (centre
  3165,3490,0, radius 13, no events) are rows of server-only `dbtable.random_event_zones` (columns area,
  allowed, centre, radius) in a new `random-events/pack` (`RandomEventsPluginPack`), the same pattern as
  upstream's `ShootingStarsTable`/`StrongholdFloorsTable` packs. `RandomEventLocations.isDisallowed` takes the
  first zone containing the tile and returns `!allowed`, else the bank check. The old order (GE, then the
  dungeons, then Ferox) gave the same results: none of the rows overlap.
- Ids by `fix_gameval_conflicts.py`: dbtable 65501, dbrows 63886-63892; `--check` on the branch: "No gameval
  conflicts or unassigned IDs found."
- Build of the branch server tree (`qa.sh gate-run`, main c2492318d + e9c7bcac3 = the e9c7bcac3 tree):
  cache, compile and installDist rc=0; `api/generated` gets `RandomEventZonesRow` (git-ignored, generated, as
  upstream's other tables); the dbtable and the seven dbrows are in the built gamevals. Boot: "Server ready in
  13.22s", no exception or error lines. The zone rows are only read by the 5-minute schedule check, which
  can't be reached in game in a session (1-2 h due time, pushed forward again on login); the integration
  tests' bank case runs through `isDisallowed` and so loads the table.
- Nit (not blocking): `row.allowed ?: false` on the generated non-null `Boolean`.
- CI at the exact commit (dispatched by sq24; no run existed): `CI` run 39348038658
  (https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39348038658), head e9c7bcac3, **success**
  (freshCache, buildCache, generateRsa, `assemble test integration`, boot to ready); `Formatting` run
  39348041267, head e9c7bcac3, **success**.

### RSProx comparison: FAIL (gameplay-visible divergences, all primary-sourced)
Recordings (RSProx database, transcripts in `re/ref/rsprox/txt/`, local only; facts only, no names):
- Content search NPC 326 (`npc.macro_geni`): 87 recordings. Already transcribed: **1718** (rev 227.1,
  Leagues V) shows three other players' Genies from spawn to leave (t1058-1068, t1177-1280, t7140-7160).
- TEXT "Please take this lamp": 16 recordings. Downloaded and transcribed **3056** (rev 231, base game,
  sha256 e81902a7...b8f0): the recording player's own Genie, t4000-4041, Talk-to, lamp, Rub. (3031 from the
  "Have a nice day" search turned out to be another NPC's line; no recording of the Genie's "Have a nice day"
  was found.)

Matches: the greeting and the three call lines (text, random pick), "Maybe later." when ignored, the
lifetime (ignored: "Maybe later." 100 ticks after the spawn, 1718 t1177 -> t1277), the Talk-to line and its
chathead (chathap, `happy`), the lamp (obj 2528) given at Talk-to before the dialogue is continued, the
xpreward interface (240) title "Choose the stat you wish to be advanced!", 10 x level XP (990 at level 99,
3056 t4038).

Must be fixed (new commit on `acc/genie`):
1. **Call interval: 10 ticks, not 20.** Every recorded Genie calls every 10 ticks: 1718 t1178 greeting,
   calls at t1187, 1197, 1207, ... 1267, "Maybe later." t1277; 3056 greeting t4001, calls t4010, 4020, 4030.
   The branch's `CALL_INTERVAL` is 20 (12 s, from the pre-2014 behaviour). Fix: `CALL_INTERVAL = 10`.
2. **Wave on the greeting and every call.** Each greeting and call plays npc seq 863 (`emote_wave`) the same
   tick as the overhead text (1718, 3056). The branch plays no animation. Fix: `anim` `seq.emote_wave` with
   each `say`.
3. **Smoke puff and sounds on arrival and departure.** Arrival: `map_anim` 86 (`smokepuff`, height 124) and
   `sound_area` 2301 and 1930 (range 7) at the Genie's tile (1718 t1059/1178/7141, 3056 t4001). Departure
   (ignored, after the hand-over, or otherwise): `map_anim` 86 and `sound_area` 1930 at his tile (1718
   t1069/7161 and after "Maybe later." t1280, 3056 t4041). The branch has none.
4. **The reward message at Talk-to.** With the lamp, the same tick: game message "Your reward is:
   <col=ff0000>1</col> x <col=ff0000>Lamp</col>." (3056 t4030). The branch sends none, and its Caveats call
   the wording undocumented.
5. **Rub result.** Official: messagebox (if 229) "Your wish has been granted!<br>You have been awarded 990
   Construction XP!" and synth 2655 (3056 t4038). The branch sends the game message "Your wish has been
   granted!" only. Fix: the mesbox with "You have been awarded <xp> <Skill> XP!" (and the sound).
6. **Departure after the hand-over: 10 ticks after Talk-to, not when the dialogue closes.** 3056: Talk-to
   t4030, dialogue continued t4033, the Genie stays until t4040 (deleted, smoke at t4041), while the player
   rubs the lamp. The branch removes him as soon as the dialogue closes (`leave(npc)` after `startDialogue`),
   or at 20 ticks. Fix: `HAND_OVER_TICKS = 10` and don't leave on dialogue close (keep the interrupted-dialogue
   safety: the lamp is already given at Talk-to). This also changes the "Talked to again before he goes"
   window the PR text describes.

Caveats to add or update (PR text):
- "Maybe later." is followed by the departure 3 ticks later (1718 t1277 -> t1280); the branch leaves the same
  tick.
- The arrival tile: the Genie is added on a tile next to the player (diagonal in two of the four spawns) and
  in one case steps one tile to stand orthogonally beside them the next tick (1718 t1059). The greeting comes
  the tick after the add. The branch greets on the spawn tick, on the first free of its eight offsets.
- One recorded greeting has no title ("Greetings, <name>!", 1718 t7141); the condition is unknown. The branch
  always says Master/Mistress.
- The lamp XP is not multiplied by anything officially (990 at 99); the branch's `statAdvance` applies the
  world's XP rate (already declared).
- "Have a nice day, Master/Mistress." is in no recording found; it stays sourced from the transcript.
- The no-event zones, the schedule (1-2 h, 5-minute check, offline extension) and the transformed/busy rules
  can't be seen in a recording; they stay sourced from the wiki's Spawn mechanics (re-read through osrs-mcp
  `wiki_page` "Random events", "Genie", "Transcript:Genie" this run: every quoted rule is still there).

### PR text (must change with the code)
- "Every 12 seconds he follows them ... calls them": 10 ticks (6 s) after fix 1, with the recordings as the
  source; drop the "Guess: the call every 12 seconds" caveat.
- "the Genie leaves when it closes or 12 seconds (20 ticks) after Talk-to": update to fix 6.
- The Caveats' "no chat message when the lamp is given (... the wording is not on the wiki)" and the "Your wish
  has been granted!" line: replace by the recorded messages (fixes 4, 5).
- The rsprox checklist box ("we have no dumps of the official game"): tick it, citing recordings 1718 and
  3056 (and add `docs/quirks.md`/notes as before).
- "How it was tested", In game: "on the previous commit; the code differs only in the repeat-Talk-to line and
  the late-completion guard" is not true of e9c7bcac3: the in-game runs were on 7a2316ea9 and 068aabee2 (sq24
  records), and e9c7bcac3 also moves the zones into the dbtable. Restate after the new commit's in-game run.
- The spec `accuracy/genie.md`: add the RSProx section (rows 9, 10, 12, 13, 18 and new rows for the wave,
  the smoke/sounds and the departure delay) and drop "osrs-mcp server was down".

Not checked because the gate stopped: the in-game re-check at e9c7bcac3, `pr-text-review` beyond the claims
above, CONTRIBUTING/template (unchanged sections from 068aabee2, which passed). Scope and overlap pass: 8
files, all in `content/events/random-events` (module, pack, test, gamevals); `gh pr list --search genie` /
`"random event"`: only #355 (ours, draft).


Re-run by sq24 on 2026-10-19 at 068aabee2, one commit (`feat: Genie random event`) on upstream `main` 829b9477d,
which was re-fetched and is still the tip. Fork PR AlexPerrin/OpenRune-Server#18. Spec and PR text (upstream's
template, #369) at fork docs 0c19c7bb3. The verdicts at 7a2316ea9 and f076deed8 below are void.

## Upstream re-check at 068aabee2, texts at c38c52ff4: FAIL (PR text only)
sd23's text fix: the "game data in the cache" box is unticked with a note, "Builds and the server boots" is
ticked, and the counts are 764 + 87. The ticks and counts are right now. One claim in the new note is not:
- **Wrong claim:** "I found no upstream pattern for a list of area ids or a radius in the cache (no dbtable/pack
  config for area lists)." Upstream `main` has one: `or-cache/src/main/kotlin/dev/openrune/impl/Music.kt`
  defines the server-only `dbtable.music_classic` with `column("area", 0, VarType.AREA)`, one area per row.
  Coordinates live in dbtables too: `VarType.COORDGRID` in `ShootingStarsTable`, `StrongholdFloorsTable` and
  `InstanceSettingsTable`. A radius is an ordinary `VarType.INT` column.
- **Fix (text):** drop the "no upstream pattern" sentence. Say the lists could move to a server-only dbtable
  (an `AREA` column as in `music_classic`; a `COORDGRID` + `INT` row for the Grand Exchange box) and offer to
  do it. Or move them, which is a code change and a re-gate.
Everything else in the text is unchanged from the run below and passes.

## Upstream gate at 068aabee2: FAIL. What must change (PR text only)
1. **The checklist's "game data in the cache, not Kotlin maps" is ticked, but it isn't true of the commit.**
   - `RandomEventLocations` holds the no-event places as Kotlin data: `NO_EVENT_AREAS` (five area symbols),
     `BANKS_WITH_EVENTS` (Ferox), and the Grand Exchange box (`GRAND_EXCHANGE_CENTRE` 3165,3490 and radius 13).
   - Upstream AGENTS.md, "Game data lives in the cache", names "location lists" as data that belongs in a
     dbtable or enum.
   - Fix: untick that box with a one-line note (the five areas, the Ferox exception and the GE box are in
     `RandomEventLocations`; happy to move them to a dbtable). Or move them to the cache, which is a code
     change and a re-gate.
2. **Make the test results match this commit**: "Builds and the server boots" can be ticked now. The full
   test counts on this commit are 764 + 87 (the PR's 726 + 87 were the previous commit's, before #333, #315
   and #334 added tests).

Everything else in the upstream gate passes:
- **The 7a2316ea9 FAIL items are done**:
  - rebased on 829b9477d;
  - the template's sections (Summary, Type of change, How it was tested, Checklist, Screenshots / notes);
  - the completed-event "Have a nice day" is implemented and stated;
  - the in-game check is described.
- **Checklist, the other boxes**:
  - the attrs box is unticked, with the persistent attribute explained;
  - the quirks.md box is unticked, with the deviations in the notes;
  - the rsprox box is unticked (no dumps);
  - "Tests only cover complex logic and anything that moves items" is true: the schedule plus the lamp
    hand-over;
  - "No unrelated changes" is true: 5 files, all in `content/events/random-events`.
- **Overlap**: `gh pr list --search "random event"` shows only #355, ours.
- `fix_gameval_conflicts.py --check` is clean (timers 65534/65533).
- **Caveats**: complete against the f076deed8 and 7a2316ea9 lists. The no-event-zone case of "Have a nice day"
  stays a declared gap.
- **pr-text-review**:
  - The behaviour claims are true in game (below), including the new "Talked to again before he goes, he says
    'Have a nice day, Master/Mistress.'".
  - The in-game list says what was checked on the previous commit and what on this one, and that is accurate.

## Internal gate at 068aabee2: PASS
- **build-test**:
  - `qa.sh gate` on clone `qa/genie-068aabee2`, `origin/main` = 829b9477d.
  - Every step rc=0: compile, freshCache + mergePluginGamevals, buildCache, generateRsa, installDist,
    spotlessCheck, assemble.
  - `test` 764/0. `integration` 87/0, `GenieRandomEventTest` 8/8. Total 616 s.
  - Gamevals check is clean.
  - Boot: "Server ready in 19.74s". No exception or error lines over the session.
- **The two changes**:
  - `talk()` answers "Have a nice day, ${title}." when `handOver` refuses (already handed over).
  - After the dialogue it untracks the player's Genie only if it is the npc talked to (`genies[player]?.npc
    === npc`). That closes the late-completion orphan case from the 7a2316ea9 record.
- **In game** (desktop lock 19:19-19:23, fresh account `sq24g3`):

| Check | Result |
|---|---|
| Talk-to | lamp line; lamp in the inventory (1) |
| Walk away (dialogue closed), Talk-to again at +5 s | "Have a nice day, Master." (npc chat); no second lamp (still 1) |
| After that | Genie gone at +11 s, with the "Have a nice day" line still open |
| Continue, new summon, Talk-to, leave the line open 15 s | Genie gone while the line is open |
| Continue that line, then `::randomevent` | a new Genie is out and tracked: Dismiss removes him. Lamps: 2 over the two hand-overs, one each. |

  The late-completion guard itself can't be reached in game (commands aren't processed while the line is
  open, and the schedule is 1-2 h). It was checked by reading the code. Clean-up: desktop RuneLite back on the
  live target (logged in as `desktop`), qa-or-server/qa-or-http removed, lock freed 19:23.

## Earlier run at 7a2316ea9 (void: superseded by 068aabee2)

Re-run by sq24 on 2026-10-19 at 7a2316ea9, one squashed commit (`feat: Genie random event`) on upstream `main`
731fd392a. Fork PR AlexPerrin/OpenRune-Server#18. Spec and PR text at fork docs d0ef91bd9. The verdicts at
f076deed8 below are void.

### Upstream gate at 7a2316ea9: FAIL (void). What had to change
1. **Base and PR template.** Upstream `main` moved during the gate: 731fd392a -> 829b9477d (#369 PR
   template, #348 idle logout, #333 The Knight's Sword, #315 The Ides of Milk and Brutus, #334 Doric's Quest).
   - **#369 adds `.github/pull_request_template.md`.** Its sections are Summary, Type of change, How it was
     tested, Checklist, Screenshots / notes for reviewers. The PR text still uses CONTRIBUTING's old layout
     (Problem/Change/Tests/Release note/Checklist), so put it in the template's layout.
   - Tick the template's checklist honestly:
     - "State in varbits/varps, not attrs": unticked, with the existing attr note.
     - "New content checked against rsprox dumps": unticked; no captures were used.
     - "Deviations noted in `docs/quirks.md`": the deviations are in the PR's notes. Either say so or add them
       to `docs/quirks.md`.
   - The merge is clean: `git merge-tree` of 829b9477d and 7a2316ea9 shows no conflict, and
     `fix_gameval_conflicts.py --check` on the merge finds none. #333/#315/#334 change
     `content/quest/manager` (`chooseLampSkill` gained a defaulted `minLevel` parameter). Rebase onto the
     current `main`; the next gate builds the new commit.
2. **Caveat gap: the completed-event line.** Transcript:Genie has "If the player completed the event or is
   in a zone where the random event isn't allowed: Genie: Have a nice day, [Master/Mistress]." The branch
   covers neither case:
   - In game, a second Talk-to on the handed-over Genie (in the 12 s before he leaves) gives nothing at all.
   - The Caveats name the line only for no-event zones.
   - Add the completed-event case to the "Not implemented" caveat, or answer it with that line, which is a
     code change.
3. **PR text, How it was tested:** replace "Nothing was checked in game from this exact commit." with this
   run's in-game check (below).

Not blocking, for sd23. **`talk()` can still orphan a later Genie.** After an interrupted hand-over:
- The hand-over timer removes the Genie and lets the player have a new one.
- The old `talk()` coroutine may still finish later and run `genies.remove(player)`. If a new Genie was out by
  then, that untracks him, and he is orphaned as at f076deed8.
- In game, `::randomevent` typed during the open dialogue is not processed. The 5-minute schedule check is the
  only way in: an event due while the old dialogue is still open, 1-2 h after the last spawn.
- Fix: remove only the Genie that was talked to, e.g. `genies.remove(player, genies[player]?.takeIf { it.npc
  === npc })`, or compare `npc` before removing and leaving.

Passes:
- **Scope**: 5 files, all in `content/events/random-events`; no docs files on the code branch.
- **Overlap**: `gh pr list --search genie` / `"random event"`: only #355, ours.
- **Ids**: timers 65534/65533 (fix_gameval_conflicts), clean on the branch and on the merge with 829b9477d.
- **AGENTS.md**: the doc comments are gone. Two one-line constraint comments remain (Make-X as weak queues; the
  lamp-first order). 8 integration tests, on the schedule and the lamp hand-over, which moves an item.
- **Accuracy and caveats**:
  - Every item from the f076deed8 record is in: every event is a Genie; only Ferox excepted from the banks that
    allow events; Abyssal Nexus, Crash Site Cavern, Neypotzli, Ape Atoll; leaving after a minute cited from the
    wiki; the XP rate; fork PR #18.
  - The interval (1-2 h), the 5-minute check and the 10-70 min offline extension match the wiki's Spawn
    mechanics.
  - The lamp-before-line order is declared as a guess, with LostCity (MIT) as a 2004 reference only.
- **pr-text-review**: the claims checked in game below are true. The test list matches the 8 tests.

### Internal gate at 7a2316ea9: PASS (void)
- **build-test**:
  - `qa.sh gate` on clone `qa/genie-7a2316ea9`: one container, the shared remote build cache for compiles,
    tests never cached, `origin/main` = 731fd392a for the spotless ratchet.
  - Every step rc=0: compile, freshCache + mergePluginGamevals, buildCache, generateRsa, installDist,
    spotlessCheck, assemble.
  - `test` 726 tests, 0 failures/errors. `integration` 87 tests, 0 failures/errors, `GenieRandomEventTest`
    8/8. Total 614 s. Logs in `genie-7a2316ea9.logs/`.
  - `fix_gameval_conflicts.py --check` clean.
  - Boot of the tree (main 731fd392a + the commit): "Server ready in 12.18s". No exception or error lines in
    the log over the session.
- **accuracy-check**: the hand-over now gives the lamp at Talk-to, before the line. The Genie stays tracked
  (`handedOver`) and leaves when the dialogue closes, or 20 ticks after Talk-to. A second Talk-to returns
  early. Wiki facts as re-read at f076deed8; the order is a declared guess.
- **In game** (desktop lock 18:52-18:57, fresh account `sq24g2`):

| Check | Result |
|---|---|
| Talk-to | lamp in the inventory as the line opens (count 0 -> 1) |
| Talk-to, then walk away (dialogue closed) | one lamp; Genie gone within 12 s of Talk-to |
| Talk-to, walk away, Talk-to again within 12 s | second Talk-to: no dialogue, no lamp (lamps 1 -> 2 over the two summons, one each); Genie gone ~12 s after the first Talk-to |
| Talk-to, dialogue left open | Genie gone 11 s after Talk-to while the line is still shown; continuing afterwards: no error, no second lamp |
| `::randomevent` with a Genie out | "Your Genie is already here."; still one Genie |
| Dismiss | Genie gone |
| Full inventory (28), Talk-to | lamp on the floor under the player (3221,3216), exactly one |
| `::randomevent` typed while the line is open | not processed (no Genie), so the orphan edge case above can't be reached through it |

  Not repeated (code unchanged since f076deed8, checked then): summon beside, following, leaving after 60 s,
  Rub (10 x level x XP rate). Clean-up: desktop RuneLite back on the live target (logged in as `desktop`),
  qa-or-server/qa-or-http removed, lock freed 18:57.

### Earlier run at f076deed8 (void: superseded by 7a2316ea9)

Run by sq24 on 2026-10-19 in its own clone. Fork PR AlexPerrin/OpenRune-Server#18, which would replace
upstream draft #355's branch `content/genie` (only on the user's approval). The commit is two commits on
upstream `main` d2a0cd1b1: 3dd14bf6e (feature) and f076deed8 (timer ids). Spec `accuracy/genie.md` and PR
text `pr-texts/genie.{title,upstream.body}` at docs 4bdce71a9. The dev's run (`re/agents/sd23/wt/acc-genie.logs`)
was read as evidence only. The gate stopped at the first failure (in game, below). sd23 is fixing it, and a
new commit voids this record.

### Internal gate at f076deed8: FAIL (void). What had to change
**An interrupted Talk-to orphans the Genie and loses the lamp.** `talk()` calls `genies.remove(player)`
before `startDialogue { chatNpc(...) }`, and does `leave(npc)` and `invAddOrDrop(LAMP)` only after the
dialogue. When the dialogue doesn't finish (in game: another dialogue replaced it), the code after it never
runs:
- the player gets no lamp;
- the Genie is no longer in `genies`, so `tick()` never moves, calls or removes it, and it was added with
  `npcRepo.add(npc, Int.MAX_VALUE)`, so it stays in the world until restart;
- the owner's Talk-to now gets the other-player line ("Sorry, I'm trying to talk to someone else, but she
  seems to be ignoring me.", "she" because the owner lookup returns null), and Dismiss gives "He isn't
  interested in you.". The player can't get rid of it;
- `::randomevent` and the schedule then spawn a second Genie beside it, because the player is not in
  `genies` any more.

Fix: keep the Genie tracked until the dialogue has finished (remove it from `genies`, `leave` it and give the
lamp only after `chatNpc` returns), or give the lamp and remove him in a `finally`. Either way, an
interrupted Talk-to has to leave him calling and leaving at his normal time, or finish the reward. Add an
integration test for the interrupted dialogue (AGENTS.md: item-moving flows need a no-loss test).

### Evidence (in game)
Branch server `qa-or-server` (upstream main bcdb03292 + f076deed8, `qa.sh runbuild` then `start`, "Server
ready in 12.83s"), desktop RuneLite under the desktop lock (00:26-00:38), fresh account `sq24ge`
(non-members; the Genie is F2P).

| Check | Result |
|---|---|
| `::randomevent` | Genie (npc 326) on the adjacent tile, interacting with the player, ops Talk-to/Dismiss. Overhead text isn't exposed to the devtools bridge, so it wasn't checked. |
| Ignored | gone between 59 s and 62 s after the summon (matches the wiki intro: "disappears after a minute") |
| Follow | after `::right 6` (7 tiles away), beside the player again within 20 s (one call interval) |
| Talk-to, completed | "Ah, so you are there, Master sq24ge. I'm so glad you summoned me. Please take this lamp and make your wish."; then Genie gone, lamp (2528) in the inventory, no chat line |
| Talk-to, full inventory (28 logs) | lamp on the floor under the player (3215,3217) |
| Rub (Attack, level 1) | interface 240, "Choose the stat you wish to be advanced!"; **+1,500 Attack XP**: 10 x level 1 x this world's `baseXpRate` 150. `statAdvance` applies the player's XP rate by default, the same as upstream's X Marks the Spot lamp. |
| Dismiss (own) | Genie gone |
| **Talk-to, interrupted** | Talk-to, then a click that opened another dialogue (the skill guide) over the Genie's. No lamp. The Genie stayed 8+ minutes (from 00:31 to the end of the session, beyond his 60 s lifetime), wandering, no calls or follow. The owner's Talk-to gave the other-player line, Dismiss gave "He isn't interested in you.", and `::randomevent` spawned a second Genie beside him. **FAIL** |
| Server log | no exception or error lines |

### Build-test: not completed
The `qa.sh build` / `split-build` runs of the commit tree were stopped (00:24, by the main session, after a
daemon death from memory pressure) and then killed by the WSL restart. The merge tree's cache, installDist and
boot passed (needed for the in-game check). `fix_gameval_conflicts.py --check` on main bcdb03292 +
f076deed8: "No gameval conflicts or unassigned IDs found". It runs again on the new commit.

### For the next run (found already; not verdicts)
Accuracy, re-read 2026-10-19 (osrs-mcp `wiki_page` Random events, Genie, Lamp, Transcript:Genie; the
wiki's full lists via the page source):
- **The interval is OSRS-accurate** (the user's question): "the game will pick a point in time between 1
  and 2 hours in the future relative to the player's last random event", on a real-time clock that runs
  offline. "A check is made every 5 minutes of in-game time". Offline: "extended by 10 to 70 minutes and
  continue counting down". All are in Random events, Spawn mechanics, each citing Mod Ash. The branch
  matches all three. The spec's row 3 says the 5-minute check is "not documented"; the wiki documents it.
- The lamp gives 10 x level (Lamp, Genie), matching the branch, but in game the world's XP rate multiplies it
  (x150 here). Say so in the PR text (Change, the lamp bullet), or use rate 1.0 if upstream wants lamps
  unscaled. Upstream's own quest lamp scales, so following it is defensible.
- Caveats to add:
  - **Every random event is a Genie here.** In OSRS each event is chosen with equal weight from all the
    events the player is eligible for (Spawn mechanics), so a Genie comes far less often than every 1-2 h.
  - **Banks that allow random events:** the wiki's "Bank locations with random events" lists Ferox Enclave,
    Giants' Foundry, the Hosidius charcoal furnaces, Myths' Guild, Rogues' Den, Trouble Brewing, the
    Sunbleak Island, Wyrmscraig and Charred Island bank chests, and Odovacar in Tarn's Lair. Only Ferox is
    excepted; the others count as no-event banks here.
  - The "Not covered" list should also name Abyssal Nexus, Crash Site Cavern, Neypotzli and Ape Atoll
    (greegree), from the wiki's "most bosses and combat areas" list.
- The PR text calls leaving after a minute a guess ("the current timings are not documented"). The
  Random events intro says an ignored event "disappears after a minute". Cite it; only the 12 s call
  interval is undocumented now.
- The PR text's first line links fork PR #10 (`content/genie`). The fork PR for this branch is #18. Right
  once `acc/genie` is pushed onto `content/genie`; make sure it is.
- Commit 3dd14bf6e's message says "Timers random_event (433) and random_event_genie (434)". At the tip they
  are 65534/65533. Fix it if the commits aren't squashed.
- AGENTS.md (upstream main): several comments restate the name ("5 minutes of play.", "Real-time clock in
  epoch milliseconds; replaceable for tests.", "The 5-minute check. Returns the outcome so tests can see why
  nothing spawned.", the `NEXT_EVENT_AT` and `FULL_INV_DROP_TICKS` KDocs). The attr for the due time is
  declared in the PR text as a review risk.
- Upstream main moved d2a0cd1b1 -> bcdb03292 (#359, #361, #312 Slayer). None of it touches what the genie
  uses (NpcRepository, the timers, `chooseLampSkill`, the area gamevals, AreaChecker). #312 changes
  `GameTestScope`, so run `integration` on main + the branch as well. Base the new commit on the current main.
- Upstream overlap: `gh pr list --search genie` / `"random event"` showed only #355 (ours).
