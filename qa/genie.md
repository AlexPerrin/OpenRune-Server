# QA: acc/genie
- Branch: `acc/genie`
- Commit: `7a2316ea9119f7a7a031df3028c6880f2db5825f`
## Internal gate: **PASS**
## Upstream gate: **FAIL**

Re-run by sq24 on 2026-10-19 at 7a2316ea9, one squashed commit (`feat: Genie random event`) on upstream `main`
731fd392a. Fork PR AlexPerrin/OpenRune-Server#18. Spec and PR text at fork docs d0ef91bd9. The verdicts at
f076deed8 below are void.

## Upstream gate at 7a2316ea9: FAIL. What must change
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

## Internal gate at 7a2316ea9: PASS
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

## Earlier run at f076deed8 (void: superseded by 7a2316ea9)

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
