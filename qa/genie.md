# QA: acc/genie
- Branch: `acc/genie`
- Commit: `f076deed868860423f59ff176a0717eef9772d3b`
## Internal gate: **FAIL**
## Upstream gate: **PENDING**

Run by sq24 on 2026-10-19 in its own clone. Fork PR AlexPerrin/OpenRune-Server#18, which would replace
upstream draft #355's branch `content/genie` (only on the user's approval). The commit is two commits on
upstream `main` d2a0cd1b1: 3dd14bf6e (feature) and f076deed8 (timer ids). Spec `accuracy/genie.md` and PR
text `pr-texts/genie.{title,upstream.body}` at docs 4bdce71a9. The dev's run (`re/agents/sd23/wt/acc-genie.logs`)
was read as evidence only. The gate stopped at the first failure (in game, below). sd23 is fixing it, and a
new commit voids this record.

## Internal gate: FAIL. What must change
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

## Evidence (in game)
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

## Build-test: not completed
The `qa.sh build` / `split-build` runs of the commit tree were stopped (00:24, by the main session, after a
daemon death from memory pressure) and then killed by the WSL restart. The merge tree's cache, installDist and
boot passed (needed for the in-game check). `fix_gameval_conflicts.py --check` on main bcdb03292 +
f076deed8: "No gameval conflicts or unassigned IDs found". It runs again on the new commit.

## For the next run (found already; not verdicts)
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
