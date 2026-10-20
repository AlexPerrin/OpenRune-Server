# Genie random event (acc/genie, upstream PR #355)

Baseline for the upstream gate on `acc/genie` at **3ccb86bd8** (fork PR #18), on upstream `main` 829b9477d:
- 068aabee2 `feat: Genie random event` (the feature, squashed);
- e9c7bcac3 `feat: keep the random event zones in a server-only dbtable`;
- 9a222bd53 and a44a3daa0: sd23's fixes for sq24's RSProx FAIL (qa/genie.md);
- 3ccb86bd8 (sd30): "Maybe later." is shown, and he leaves 3 ticks after it.

Rewritten 2026-10-20 by sd30 with accuracy-check. sd23's spec of 2026-10-18 is the base. Every source below was
re-read: the wiki through osrs-mcp `wiki_page` ("Random events", "Genie", "Transcript:Genie") and the raw wiki
text for "Random events" sections 7-9. The RSProx recordings were read at the packet level by sd30 (3056)
and checked against sq24's tick notes (1718).

Sources:
- OSRS Wiki "Random events": intro, Spawn mechanics (quotes Mod Ash for each rule), Locations without random
  events, Bank locations with random events, Trivia.
- OSRS Wiki "Genie": intro, Behaviour before 2014, Changes, Trivia; "Transcript:Genie"; "Lamp".
- RSProx recordings of the official game (references/rsprox.md), local only. Ids and packet facts only.
- Cache/gamevals: npc 326 `macro_geni`, obj 2528 `macro_genilamp`, seq 863 `emote_wave`, spotanim 86
  `smokepuff`, synth 1930 `smokepuff`, synth 2301 (no cache name; `macro_genie_appear` in the module's
  gamevals.toml), synth 2655 `found_gem`, interfaces 240 `xpreward`, 229 `messagebox`, 231 `chat_left`.

## Upstream first
`gh pr list -R OpenRune/OpenRune-Server --search genie` / `"random event"` (2026-10-20): only #355 (ours,
draft). `main` 829b9477d has no random events module.

## RSProx recordings
- Search NPC 326: 87 recordings (`re/ref/rsprox/search/npc_326.json`). TEXT "Please take this lamp": 16
  (`text_Please_take_this_lamp.json`).
- **3056** (rev 231.1, base game): the recording player's own Genie, t4000-4041. The full event: add,
  greeting, 3 calls, Talk-to, lamp, Rub, departure.
- **1718** (rev 227.1, Leagues V): three other players' Genies from add to delete, two of them ignored to the
  end ("Maybe later.").
- No recording found of "Have a nice day", the other-player lines, Dismiss, or a full inventory.

What 3056 shows (ticks relative to the add, t4000 = +0):

| Tick | Packets |
|---|---|
| +0 | npc add 326 beside the player ((3491,3231), directly south of the player at (3491,3232)) |
| +1 | seq 863, face player, say "Greetings, Master <name>!"; map_anim 86 h124 + sound_area 2301 r7; map_anim 86 h124 + sound_area 1930 r7, all at the add tile |
| +10, +20, +30 | seq 863 + say, one of the three call lines (random) |
| +30 | opnpc1 (Talk-to), same tick: game message "Your reward is: <col=ff0000>1</col> x <col=ff0000>Lamp</col>.", chat_left with npchead 326, anim chathap3 (569), the transcript line over 3 lines; the call of that tick is still sent |
| +31 | Genie's face cleared |
| +35 | lamp Rub: xpreward (240) opens, title "Choose the stat you wish to be advanced!" |
| +38 | choice made: messagebox (229) "Your wish has been granted!<br>You have been awarded 990 Construction XP!", synth 2655 |
| +40 | npc delete (10 ticks after Talk-to, the player was still in the messagebox) |
| +41 | map_anim 86 h124 + sound_area 1930 r7 at his tile |

1718 adds: the arrival seq/smoke/sounds are the same in all three; the greeting comes the tick after the add,
and in two of them (seen by sd30) the Genie steps one tile that tick to stand orthogonally beside the player; calls on the add's
10-tick grid; "Maybe later." in place of the 10th call (add +100, no seq), delete 3 ticks later with the
smoke and sound 1930. One greeting has no title ("Greetings, <name>!"; condition unknown).

## Mechanics
Verdicts are for 3ccb86bd8. "Recorded" = seen in a recording above.

| # | Mechanic | Official behaviour (source) | Branch | Verdict |
|---|---|---|---|---|
| 1 | Interval | "a point in time between 1 and 2 hours in the future relative to the player's last random event", real-time clock that runs offline (Random events, Spawn mechanics) | `MIN/MAX_EVENT_DELAY_MS` 60-120 min, epoch-ms due time in persistent attr `random_event_next_at` | matches |
| 2 | Offline | "the timer will be extended by 10 to 70 minutes and continue counting down" (Spawn mechanics) | on login, +10-70 min until in the future | matches |
| 3 | Check cadence | "A check is made every 5 minutes of in-game time" (Spawn mechanics) | soft timer every 500 ticks | matches (counting from login: guess) |
| 4 | Missed in a no-event place | "likely that they will miss out ... a new spawn moment being chosen one to two hours down the line" (Spawn mechanics) | missed and re-rolled | matches |
| 5 | No-event zones | list in "Locations without random events"; banks with a booth and in front of most bank chests; exceptions in "Bank locations with random events" | `RandomEventLocations.isDisallowed`: instances; then the first row of server-only `dbtable.random_event_zones` containing the tile (God Wars Dungeon, Ancient Cavern, Waterbirth Island Dungeon, Ruins of Tapoyauik, Charred Dungeon: no events; Ferox Enclave: allowed; Grand Exchange: centre 3165,3490,0 radius 13, no events); else a bank booth within 7 or a bank chest within 2 tiles | approximated (caveat C1) |
| 6 | Transformed | "will not spawn if the player character is using an altered model" (Spawn mechanics); Mod Ash (cited there): the genie deletes itself when it notices | missed and re-rolled; an active Genie leaves | matches (missed vs delayed: guess) |
| 7 | Busy | "an interface open or ... a repeated skilling action (through the "Make-X" option)": delayed until free (Spawn mechanics) | held while a modal is open or a weak queue is pending; retried every tick | matches (weak queue = Make-X, 1-tick retry: guess) |
| 8 | One at a time | no second event NPC while one "is still trying to draw their attention" (Spawn mechanics) | refused while a Genie is out | matches (the re-roll then: guess) |
| 9 | Arrival | recorded: add beside the player; next tick smoke (spotanim 86 h124) x2, sounds 2301 and 1930 r7 at the add tile | add on the first free of 8 neighbours; the same tick: one smokepuff, sounds 2301 and 1930 r7, greeting | matches, except the tick (C2) and one smokepuff instead of two on the same tile (no visible difference) |
| 10 | Greeting | "Greetings, [Master/Mistress] [player name]!" (Transcript, Overhead); recorded with seq 863 and face player | `say` + `anim(emote_wave)` + `facePlayer` | matches (C3 for the untitled one) |
| 11 | Calls | three random lines (Transcript, Overhead); recorded every 10 ticks on the add's grid, each with seq 863 | `CALL_INTERVAL = 10` from the spawn, random pick, wave | matches |
| 12 | Follows | "follow players into banks" (Genie intro; Changes 1 Nov 2023); pre-2014 "teleport next to the player each time he gave a new overhead message" | on each call, teleports beside the owner if not adjacent | matches for banks; following on the call: pre-2014 behaviour, no recording of a player walking away (guess) |
| 13 | Ignored | "Maybe later." (Transcript); an ignored event "disappears after a minute" (Random events intro); recorded: "Maybe later." at add +100 instead of the 10th call, no seq; delete +103 with smoke and sound 1930 | 10th timer fire says "Maybe later.", leaves 3 ticks later (`IGNORED_LEAVE_TICKS`) with smoke and sound; no lamp in those ticks | matches (fixed in 3ccb86bd8: before it the NPC was deleted the same tick, so the line was never sent) |
| 14 | Talk-to | "Ah, so you are there, [Master/Mistress] [player name]. I'm so glad you summoned me. Please take this lamp and make your wish." then a lamp (Transcript, Standard); recorded: same tick as opnpc1: "Your reward is: ...1... x ...Lamp..." game message, lamp, chathead `chathap3` | `handOver` before the dialogue: lamp (`invAddOrDrop`), the reward message; `chatNpc(happy, ...)` | matches (mesanim `happy` gives chathap3 for 3 lines; not seen in game this run) |
| 15 | Departure after Talk-to | recorded: delete 10 ticks after Talk-to whatever the dialogue state; smoke + sound 1930 | `HAND_OVER_TICKS = 10`, independent of the dialogue | matches (puff tick: C4) |
| 16 | Talk-to after the lamp | "Have a nice day, [Master/Mistress]." (Transcript, completed event) | that line within the 10 ticks | matches (no recording) |
| 17 | No-event-zone Talk-to | "Have a nice day" also "in a zone where the random event isn't allowed" (Transcript) | not implemented: the lamp is given anywhere | gap (C5) |
| 18 | Other player's Talk-to | "Sorry, I'm trying to talk to someone else, but [he/she] seems to be ignoring me." (Transcript) | same, owner's pronoun | matches (no recording) |
| 19 | Other player's Dismiss | not on the wiki, not recorded | game message "He isn't interested in you." | guess (C6) |
| 20 | Dismiss | option Dismiss (Genie infobox) | removed with smoke | matches (no recording) |
| 21 | Master/Mistress | by gender since 29 May 2024 (Genie, Changes) | body type A = Master | matches |
| 22 | Full inventory | rewards are dropped and stay up to three hours (Random events, Trivia, Mod Ash) | `invAddOrDrop` with 3 h (18000 ticks) | matches (no recording) |
| 23 | Lamp interface | recorded: xpreward (240), title "Choose the stat you wish to be advanced!" | upstream's `chooseLampSkill` with that title | matches |
| 24 | Lamp XP | "10 times the current level" (Genie), "ten times" (Lamp); recorded 990 at level 99 | `statBase(stat) * 10` through `statAdvance` | matches; base vs boosted level unsourced and the world XP rate applies (C7) |
| 25 | Rub result | recorded: messagebox 229 "Your wish has been granted!<br>You have been awarded <xp> <Skill> XP!" + synth 2655 | `mesbox` with that text (skill display name capitalised), `soundSynth(found_gem)` | matches |
| 26 | Logout | not documented | Genie removed on logout | guess |
| 27 | Underwater Genie | possible at Mogre Camp / off Fossil Island (Genie, Trivia) | not implemented | gap (C5) |

## Caveats the PR must list
- **C1 zones.** The official zones are not published. Banks are inferred from booths (7 tiles) and chests (2),
  the GE square is a 27x27 box, instances stand for "most bosses and minigames". Not covered from the wiki list:
  Forestry events, Camdozaal golem workshop, POH building mode, boats, Lake Molch, Shayzien Agility Course,
  Death's Office, Goblin Cave, Stranglewood, The Scar, the non-instanced minigames, Abyssal Nexus, Crash Site
  Cavern, Neypotzli, Ape Atoll in greegree form, PvP worlds, Wintertodt prison. Of the banks with events only
  Ferox is excepted (not Giants' Foundry, Hosidius furnaces, Myths' Guild, Rogues' Den, Trouble Brewing, the
  built bank chests, Odovacar). Per-event restrictions (underwater, Kalphite Lair, Hunter areas) don't apply to
  a Genie-only schedule.
- **C2 arrival tick.** Official: the greeting, wave, smoke and sounds come the tick after the add, and the
  Genie may step to an orthogonal tile. Branch: all on the spawn tick, first free of 8 neighbours.
- **C3 title.** One recorded greeting has no Master/Mistress; the condition is unknown. Branch always uses it.
- **C4 puff tick.** 3056 shows the departure smoke one tick after the delete (+40/+41); 1718 shows it with the
  delete. The branch sends both on the same tick.
- **C5 not implemented.** "Have a nice day" in a no-event zone (the Genie gives the lamp there); the underwater
  Genie; the lamp's skill restrictions (members' skills on F2P, Construction/Runecraft/Herblore/Sailing
  prerequisites) belong to the shared lamp chooser.
- **C6 guesses.** "He isn't interested in you." on another player's Dismiss; missed (not delayed) while
  transformed; re-roll when due with a Genie out; any weak queue = Make-X; 1-tick retry; the 5-minute check
  from login; logout removes him; no lamp in the 3 ticks after "Maybe later.".
- **C7 XP.** Base level (boosts unsourced); `statAdvance` applies the world's XP rate (the recording gives 990
  at 99, i.e. no multiplier).
- Every random event is a Genie here; officially each eligible event has equal weight.
- The due time is a persistent attribute (epoch ms do not fit a 32-bit varp); a varp in epoch minutes would.

## Tests
`GenieRandomEventTest` (9, integration; AGENTS.md: state logic and item flows): due time 1-2 h ahead and
persisted; offline 10-70 min extension; a due event spawns and re-rolls; bank miss; transformed miss and leave;
interface delay; Make-X delay; an interrupted hand-over gives one lamp and he leaves after `HAND_OVER_TICKS`;
an ignored Genie stays 3 ticks after the lifetime, gives no lamp, then leaves (3ccb86bd8). Overheads, anims,
smoke, sounds and messages are checked in game, not in tests.

## Checks by sd30 (2026-10-20)
- Dev lane (`qa.sh dev sd30`): random-events `spotlessApply` + `integration` 9/9 at 3ccb86bd8.
- Fork CI at 3ccb86bd8: CI 39375088118 and Formatting 39375089748, both success.
- In game: not run this pass (the desktop devtools MCP was not reachable); the gate's in-game re-check covers
  rows 9-15, 23 and 25 against the table above.

## Ids
Timers `random_event` 65534, `random_event_genie` 65533; `dbtable.random_event_zones` 65501 and its seven rows
63886-63892; synth `macro_genie_appear` = 2301 (a name for an unnamed cache synth). Assigned by
`fix_gameval_conflicts.py` except the synth, which is the cache id.

## PR split
One change (`acc/genie` onto #355's branch). No dependencies. Other random events can build on the schedule as
their own PRs.
