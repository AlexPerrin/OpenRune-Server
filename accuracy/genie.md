# Genie random event (acc/genie, upstream PR #355)

Spec for the audited rewrite on `acc/genie` (068aabee2, then e9c7bcac3 moving the zones into `dbtable.random_event_zones`, on main 829b9477d; 068aabee2 is the feature, the timer ids, the AGENTS.md trim, the hand-over fix, the repeat-Talk-to line and the late-completion guard, squashed).
No earlier audit exists for this feature; this one was written 2026-10-18 by sd23. The osrs-mcp
server was down, so the wiki was read directly (oldschool.runescape.wiki, `?action=raw` and page fetches).

Sources: OSRS Wiki "Random events" (Spawn mechanics, Locations without random events, Changes, Trivia),
"Genie" (intro, Changes, Trivia), "Transcript:Genie", "Lamp". The Spawn mechanics section quotes Mod Ash
for each rule.

## Upstream first
- `gh pr list -R OpenRune/OpenRune-Server --search genie` / `"random event"` (2026-10-18): only #355 (ours,
  draft). `main` (c2492318d) has no random events module. No review comments on #355.

## Mechanics
| # | Mechanic | Official behaviour (source) | Branch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Interval | "a point in time between 1 and 2 hours in the future relative to the player's last random event", on a real-time clock that runs offline (Random events, Spawn mechanics) | `MIN/MAX_EVENT_DELAY_MS` 60-120 min, epoch-ms due time in persistent attr `random_event_next_at` | matches |
| 2 | Offline | "If the player is offline when they are due ..., the timer will be extended by 10 to 70 minutes" (Spawn mechanics) | on login, push the due time forward by 10-70 min until it is in the future | matches |
| 3 | Check cadence | "A check is made every 5 minutes of in-game time" (Spawn mechanics) | soft timer every 500 ticks (5 min of play) | matches |
| 4 | Ineligible location | due in a no-event location: the event is missed and a new 1-2 h is set (Spawn mechanics) | missed and re-rolled | matches |
| 5 | No-event locations | banks with a booth, in front of most bank chests, Grand Exchange stone ground, God Wars, Ancient Cavern, Ruins of Tapoyauik, Charred Dungeon, most bosses/minigames, ... (Locations without random events) | booth within 7 / chest within 2 tiles (not Ferox), a 27x27 GE box, cache areas for the listed dungeons, instances | approximated (caveat; rest of the list not covered) |
| 6 | Transformed | "Random events will not spawn if the player character is using an altered model" (Spawn mechanics); Mod Ash: the genie deletes itself when it notices the transformation | missed and re-rolled; an active Genie leaves | matches (miss-vs-delay is a guess, caveat) |
| 7 | Busy | an open interface or a repeated Make-X action can delay a spawn until free (Spawn mechanics) | held while an interface is open or a weak queue is pending; retried each tick | matches (weak queue = Make-X, 1-tick retry: caveat) |
| 8 | One at a time | no second event NPC while one is trying to get the player's attention (Spawn mechanics) | refused while a Genie is out | matches |
| 9 | Greeting | overhead "Greetings, [Master/Mistress] [player name]!" on spawning (Transcript:Genie, Overhead dialogue) | `say()` on spawn, facing the player | matches |
| 10 | Calls | random of "Hello, ...?", "Ehem... ...?", "I'm waiting for you, ...!" while waiting (Transcript) | every 12 s (`CALL_INTERVAL`), follows the player | matches (12 s from Genie, pre-update behaviour; caveat) |
| 11 | Banks | "The Genie is now able to enter banks." (Genie, Changes 1 Nov 2023); follows into banks | follows everywhere | matches |
| 12 | Ignored | overhead "Maybe later." (Transcript); an ignored event "disappears after a minute" (Random events intro) | leaves after 100 ticks (60 s) with "Maybe later." | matches |
| 13 | Talk-to | "Ah, so you are there, [Master/Mistress] [player name]. I'm so glad you summoned me. Please take this lamp and make your wish." then a lamp (Transcript, Standard dialogue) | that line, lamp given, Genie removed | matches |
| 14 | Other player's Talk-to | "Sorry, I'm trying to talk to someone else, but [he/she] seems to be ignoring me." (Transcript) | same, owner's pronoun | matches |
| 15 | Master/Mistress | by the player's gender (Genie, Changes 29 May 2024; Trivia) | body type A = Master | matches |
| 16 | Full inventory | rewards are dropped and stay up to three hours (Random events, Trivia, Mod Ash) | `invAddOrDrop` with a 3 h duration | matches |
| 17 | Lamp XP | "ten times their skill level of choice" (Lamp); "10 times the current level" (Genie) | `statBase(stat) * 10` | matches (base vs boosted level unsourced, caveat) |
| 18 | Rub / give messages | not on the wiki | "Your wish has been granted!"; no message on receipt | unsourced (caveat) |
| 19 | Other player's Dismiss | not documented | "He isn't interested in you." | unsourced (caveat) |
| 20 | Completed / no-event-zone talk | "Have a nice day, [Master/Mistress]." (Transcript) | completed: a repeat Talk-to before he leaves gets the line; no-event zone: not implemented (the Genie gives the lamp anywhere) | completed matches; zone case scope gap (caveat) |
| 21 | Underwater Genie | possible underwater at Mogre Camp / off Fossil Island (Genie, Trivia) | not implemented | scope gap (caveat) |

## Divergences
None in what is implemented. The approximations and guesses are rows 5, 6, 7, 10, 17, 18, 19 and are
listed in the PR's Caveats.

## Scope notes (sq24, 2026-10-19)
- Every random event is a Genie here (OSRS picks among eligible events with equal weight).
- Of the wiki's banks with random events only Ferox is excepted; also not covered: Abyssal Nexus, Crash Site
  Cavern, Neypotzli, Ape Atoll in greegree form.
- Lamp XP goes through `statAdvance`, so the world's XP rate multiplies it (as upstream's quest lamp).

## Review risks (upstream AGENTS.md)
- The due time is a persistent `AttributeKey<Number>` (epoch milliseconds). AGENTS.md prefers varps for
  basic persistent state; epoch milliseconds do not fit a 32-bit varp. A varp holding epoch minutes would.
  Left as is; the PR text says why an attribute is used.

## Tests
`GenieRandomEventTest` (8, integration; AGENTS.md: state logic and item flows only): due time 1-2 h ahead,
persisted, not restarted by a relog; offline 10-70 min extension; a due event spawns and re-rolls; bank miss;
transformed miss and leave; interface delay; Make-X delay; an interrupted hand-over gives exactly one lamp and
the Genie leaves (fix for sq24's in-game finding: an interrupted Talk-to gave no lamp and left an untracked
Genie in the world). The lamp-first order and a departure independent of the dialogue are a guess, per LostCity
`macro events/scripts/general/macro_event_genie.rs2` (2004: lamp added before the line, departure queued).

## Ids
Timers `random_event` 65534 and `random_event_genie` 65533, written as -1 and assigned by
`fix_gameval_conflicts.py` (timers have no OSRS max), one step below cannon's `mcannon_fire` 65535.

## PR split
One change (`acc/genie`, force-pushed onto #355's branch). No dependencies. Other random events can build
on the schedule later as their own PRs.
