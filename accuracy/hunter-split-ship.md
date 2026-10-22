# Hunter: the 7 split branches (hunter-split-ship)

Ships the Hunter content that `fin/hunter` (7 commits on main c2492318d, pre-audit) carried, as 7 branches each off
current upstream `main` (e85278c9a, revision 241), one upstream PR each. The code is the audited split sd22 made
(`fin/hunter-*`, the linear `work` tree in re/upstream-pr/dev-hunter that passed the hunter integration suites 56/56),
rebuilt commit by commit on the new main; the only code change in this task is the trap timing fix below.

## Upstream first (2026-10-21)
`gh pr list -R OpenRune/OpenRune-Server --state all --search hunter`, issues, main's tree and last 100 commits: no
`content/skills/hunter` module, no hunter commit, no open hunter PR. #63 (Hunter, tracking issue) is open and
unclaimed; #196 (reldo-dev, snares and box traps) was closed 2026-09-29 without review and #229 (catesweb) the day it
opened as a duplicate of #196. On our #353 a-thoren (2026-10-18): "One PR per 'row' is fine, it's easier for us to
review bitesize PRs" and "just renumber against what's already on our main". PROGRESS.md: Hunter "nothing yet".

## The split and `Depends:`
| Branch (fork) | Commits on main | Depends |
|---|---|---|
| `content/hunter-module` | module skeleton (build files, empty gamevals) | - |
| `content/hunter-traps` | module, traps | hunter-module |
| `content/hunter-bird-houses` | module, bird houses | hunter-module |
| `content/hunter-butterflies` | module, butterflies | hunter-module |
| `content/hunter-herbiboar` | module, herbiboar | hunter-module |
| `content/hunter-implings` | module, implings | hunter-module |
| `content/hunter-rumours` | module, traps, herbiboar, butterflies, rumours (linear; was an octopus merge) | hunter-module, -traps, -herbiboar, -butterflies |

Each feature branch carries the module commit (c189c1969) so it builds alone; it goes upstream after the module PR
merges and is rebased then (docs/workflow.md "Changes for upstream"). The rumours chain re-picks the traps, herbiboar
and butterflies commits (the rumours commit changes `ButterflyScript.kt`, `HerbiboarScript.kt` and
`ButterflyScriptTest.kt`), so merging it next to `content/hunter-butterflies` and `-herbiboar` is an add/add conflict
on those three files; the resolution (the rumours versions) is recorded in `scripts/integration-rr-cache/` on
`local/integration-tools` and verified to auto-resolve. The merged tree of the 7 equals the linear `work` tree except
per-feature plugin packs (instead of one `HunterPluginPack`), a comment and gamevals ordering.

Gamevals: `fix_gameval_conflicts.py --check` clean on every branch. The 18 herbiboar dbrows moved from 60233-60250
(upstream's Slayer #312 took them) to 63874-63891, the ids fin/hunter a4a74b5a9 already used in integration; saves are
keyed by name.

## RSProx comparison (27 local transcripts, re/ref/rsprox/txt; rsprox.net's index has no topic search)
No recording shows the local player doing any Hunter action. What exists:
- **Traps** (1106, rev 237, Varlamore; 350, rev 230, Corsair Cove): other players' bird snares and box traps changing
  state via `loc_add_change`. Snare: 9345 set -> 9349 `trapping_jungle` -> 9373 `full_jungle` two ticks later
  (1106 t5620/5622, t5080/5082, t5689/5691); miss: 9346 `failing` -> 9344 `broken` one tick later (t5121/5122,
  t5402/5403, t5455/5456). Box trap: 9380 empty -> 9392 `trapping_chinchompa_big_s` -> 9383 full two ticks later
  (1106 t5298/5300; 350 t1160/1162, t1319/1321 with `_n` 9390). Before a miss the bird teleports onto the trap tile
  (t5117), idles there, the trap fails at t5121 with area sound 2637, the bird is deleted at t5122 with the broken
  loc; the full loc comes with sound 2625. **Result**: locs and the 2-tick trapping->full match the branch; failing->
  broken was 2 ticks and is now 1 (`FAIL_TICKS`, test "a missed attempt fails the trap and breaks it the next
  tick"); the bird-on-the-tile sequence, the `_n`/`_s` choice and the sounds (unnamed synths 2637/2625) are caveats
  in the traps PR text.
- **Bird houses, butterflies, Herbiboar, implings, Hunters' Rumours**: no recording (only ambient NPCs, login varbits
  such as `fossil_activity_valleybirdhouse`, bank items, and 1106's clue-scroll visit to the Hunter Guild). Their
  timings stay wiki-sourced or guesses, as the tables below mark them.
- Not Hunter: 468's impling-jar crafting at the Chemist (not in these PRs), 1694's quest Hunter XP.

## Tests, in-game
Integration suites per branch (fork CI at each commit): HunterTrapScriptTest 8, BirdHouseScriptTest 11,
ButterflyScriptTest 6 (7 with rumours), HerbiboarScriptTest 5, ImplingScriptTest 4 + ImplingLootScriptTest 7 +
CrystalImplingScriptTest 3, HunterRumoursTest 7 + HunterGuildScriptTest 5. Not re-checked in game on these commits:
traps (lay/catch/Check) and the rumour dialogues were device-checked on earlier builds of the same code
(server-handoff.md); the upstream gate's in-game re-check is still to come.

## Rules per feature (from each commit's sources; Status: sourced / guess / not done)
# Hunter PRs: rule tables

One table per content PR, extracted from the feature commit's Change / Data sources / Caveats paragraphs. Status: `sourced` (the message names a source), `guess` (listed under Guesses), `not done` (listed under Not done). Tests are listed as the message lists them; caveats are verbatim.

## content/hunter-traps (66368fdee): bird snares and box traps

| Rule | Source | Behaviour | Status |
|---|---|---|---|
| Lay level | wiki: Bird snare, Box trap, creature pages | Hunter level per trap and creature | sourced |
| Trap count | wiki | 1-5 traps at Hunter 1/20/40/60/80 | sourced |
| Attempt cadence | Mod Ash (3-tick attempts, box traps) | every 3 ticks a set trap with a matching creature within 2 tiles rolls the catch | sourced (box traps); guess (snares) |
| Catch roll | wiki "Skilling success chart" per creature | OSRS 256 roll at the player's Hunter level, low at 1 / high at 99 | sourced |
| Bird curves | wiki + Mod Ash 15 Feb 2022 | crimson swift 100/420, golden warbler 92/400, copper longtail 85/390, cerulean twitch 82/380, tropical wagtail 75/370 | sourced |
| Chinchompa curves | wiki + Mod Ash 23 Sep 2015 | grey 6/268; carnivorous and black -78/228 | sourced |
| Ferret and jerboa curves | none (no wiki chart) | crimson swift curve shifted to their level: 15/335 and -24/296 | guess |
| Trap loc sequence | cache locs; RuneLite Hunter plugin | trapping/failing locs to full or broken | sourced |
| Chinchompa / jerboa respawn | wiki (respawn times) | caught one comes back after 7 ticks | sourced |
| Bird / ferret respawn | none | 50 ticks | guess |
| Collapse | none | 200 ticks | guess |
| Check | wiki (loot, XP, the jerboa message) | loot, trap and XP; "You've caught a <creature>." / jerboa "...or maybe not."; room only for what doesn't stack (box traps, bird snares, chinchompas stack) | sourced |
| Other ops | - | Dismantle, Reset, Investigate | sourced (ops); most messages guess |
| Creatures | wiki creature pages | five birds, ferrets, embertailed jerboas (Hunter 39, 137 XP, jerboa tail), grey/red/black chinchompas | sourced |
| XP conflicts | wiki (creature page vs trap page) | cerulean twitch 64.5 (vs 64.6), tropical wagtail 95.2 (vs 95), ferret 115.2 (vs 115); creature pages used | unsourced, needs a game check |
| Logout | none | traps dropped on logout | guess |
| Eagles' Peak requirement, smoking and baiting, extra Wilderness trap, baby chinchompa pet | - | - | not done |
| Data | - | dbtables hunter_traps 56004, hunter_trap_creatures 56005 (low/high/respawn columns), rows 60172-60183, timer 436, HunterTrapPluginPack | - |

Tests (HunterTrapScriptTest, 7): each creature's catch curve against its wiki chart and Mod Ash's numbers; 7-tick chinchompa respawns; the trap limit at 20/40/60/80 (in the file, not listed in the message); lay/catch/check a crimson swift; a full inventory leaves the trap; Check with a full inventory when the box trap and the chinchompa stack; the level-1 trap limit.

Caveats (verbatim): XP where the wiki disagrees with itself (unsourced, needs a game check): cerulean twitch 64.5 (creature page; Bird snare page says 64.6), tropical wagtail 95.2 (creature page; Bird snare 95), ferret 115.2 (creature page; Box trap 115); the creature pages' values are used. Guesses: the ferret and jerboa catch curves (no wiki chart; the crimson swift curve shifted to their level: 15/335 and -24/296), the 50-tick respawn of birds and ferrets, the snare attempt cadence, the 200-tick collapse, most trap messages, traps dropped on logout. Not done: Eagles' Peak requirement, smoking and baiting, the extra Wilderness trap, the baby chinchompa pet.

## content/hunter-bird-houses (7967d0fc5): bird house trapping

| Rule | Source | Behaviour | Status |
|---|---|---|---|
| Spaces | cache varps; RuneLite Time Tracking | four spaces are multilocs on varps 1626-1629 | sourced |
| Build | wiki: Bird house trapping | build, or use a bird house on the space | sourced |
| Seeds | wiki: Bird house trapping/Seeds | 10 low-level seeds, or 5 wildblood hop or ranarr-and-higher herb seeds; mixing allowed | sourced |
| Fill time | wiki | 50 minutes | sourced |
| Empty / Reset loot | wiki; Birdhouse (scenery) | clockwork, 10 raw bird meat (always dropped to the ground), 30-60 feathers, nests, Hunter XP | sourced |
| Collect message | RuneLite Loot Tracker | the message its parser reads | sourced |
| Seed nest chance | wiki chart | skilling formula: 1/256 at 1, 101/256 at 50, 201/256 at 99 | sourced |
| Nest rolls | wiki nest charts | five rolls on the nest table | sourced |
| Early empty | wiki | asks first; keeps the clockwork, loses house, seeds and loot | sourced (rule); guess (wording) |
| Reset | wiki; bird house item pages | new house one tick later: a carried house first, else built from logs + clockwork with hammer and chisel (Crafting level and XP) | sourced |
| Reset with several log types | none | builds the best house the player can place, no log-type prompt | guess |
| Messages, op layout per state | none | - | guess |
| Clue nests, strung rabbit foot, crafting houses from the inventory, building from materials on an empty space | - | - | not done |
| Data | - | dbtable birdhouse_types 56003, rows 60163-60171 (logs, Crafting level/XP columns), server varps 63762-63769, ::birdhouses, BirdHousePluginPack | - |

Tests (BirdHouseScriptTest, 11): the Loot Tracker pattern; the multiloc varps; tier order; nest and seed-nest chances against the wiki charts (three tests); build/seed/wait/empty with the raw bird meat on the ground; five high-level seeds and mixed seeds; emptying early; Reset building an oak house from logs at Crafting 20; a full inventory.

Caveats (verbatim): Guesses: the early-empty warning's wording, the other game messages, the op layout per state. With several log types Reset builds the best house the player can place instead of asking for the log type. Not done: clue nests, the strung rabbit foot, crafting bird houses from the inventory, building from materials on an empty space.

## content/hunter-butterflies (53a52d555): butterfly and moth netting

| Rule | Source | Behaviour | Status |
|---|---|---|---|
| Tool | wiki creature pages | wielded butterfly net or magic butterfly net, or barehanded at 10 levels above the requirement | sourced |
| Catch roll | wiki success charts | OSRS 256 roll; charts for black warlock, sunlight moth, moonlight moth | sourced |
| Ruby harvest, sapphire glacialis, snowy knight curves | none (no chart) | the black warlock's curve | guess |
| Jar | wiki | a butterfly jar keeps the catch; without one it is released and its effect applies | sourced |
| Release op | cache item ops | jarred butterflies: Attack/Defence/Strength boosts, Hitpoints, sunlight restore, moonlight prayer | sourced |
| Sunlight moth restore | wiki: Sunlight Moth (Sailing bug note) | restores drained stats except Prayer and not Sailing | sourced |
| Sunlight moth run energy | wiki: Sunlight Moth | jarless catch restores 5% run energy; the jarred moth does not | sourced |
| Swing cadence | none | one 2-tick swing per click | guess |
| Barehanded animation | none | - | guess |
| Respawn | none | 7 ticks | guess |
| Below level | none | fails silently | guess |
| Messages | none | - | guess |
| Moonlight moth variants | none | which are catchable | guess |
| Sharing effects in multicombat, butterfly mixes | - | - | not done |
| Data | - | dbtable hunter_butterflies 56012, rows 60281-60287, ButterflyPluginPack | - |

Tests (ButterflyScriptTest, 6): a jar is swapped for exactly one butterfly; a jarless catch applies the boost; a jarless sunlight moth restores 5% run energy; a jarred sunlight moth restores drained stats but not Sailing or run energy; Release gives the jar back once; barehanded below ten extra levels always fails.

Caveats (verbatim): Guesses: the ruby harvest, sapphire glacialis and snowy knight curves (no chart; the black warlock's), one 2-tick swing per click, the barehanded animation, the 7-tick respawn, the messages, failing silently below the level, which moonlight moth variants are catchable. Not done: sharing effects in multicombat, butterfly mixes.

## content/hunter-herbiboar (63d44a4a2): Herbiboar tracking

| Rule | Source | Behaviour | Status |
|---|---|---|---|
| Trail | cache trail varbits and multilocs | driven by the client's own trail varbits (footprint multilocs), so RuneLite's Herbiboar plugin works | sourced |
| Start | wiki: Herbiboar | start locs need Hunter 80 (boostable) and 31 Herblore | sourced |
| Clue inspects | wiki (messages) | the wiki's messages, 50 XP and fossil rolls | sourced (messages, fossil table) |
| 50 XP cadence | wiki says "occasionally" | 50 XP on every inspect but the first and last | guess |
| Tunnel | wiki (tunnel XP) | Hunter XP by level, from the base level; stunned herbiboar via fossil_herbiboar_visible | sourced |
| Harvest | wiki herb chart | 1-3 herbs, 2-4 with magic secateurs, by Herblore level; 25 Herblore XP per herb after the first; harvest count | sourced |
| Network | map footprints; RuneLite Herbiboar plugin per-loc trail lists | dbtables herbiboar_legs/spots/tunnels/herbs | sourced |
| Route length | none | 3-5 legs, and which legs end a route | guess |
| Seaweed / mushroom-cap messages, other non-wiki messages | none | - | guess |
| Tunnel Search op | none | same as Attack | guess |
| New-track warning dialogue, Herbi pet, horn of plenty's extra herbs | - | - | not done |
| Data | - | dbtables 56006-56009, rows 60184-60270 and 63874-63891 (18 rows renumbered from 60233-60250, taken by Slayer #312), server varps 63770-63771, HerbiboarPluginPack | - |

Tests (HerbiboarScriptTest, 5): tunnel XP against the wiki table; herb level ranges against the wiki chart (the test allows two levels' tolerance); every leg's trail varbit is a footprint varbit and every group can be left; tracking from every start to stun and harvest; a wrong clue loc changes nothing.

Caveats (verbatim): Guesses: route length (3-5 legs) and which legs end a route, 50 XP on every inspect but the first and last (the wiki says "occasionally"), the seaweed and mushroom-cap messages and the other non-wiki messages, the tunnel's Search op doing the same as Attack. Not done: the new-track warning dialogue, the Herbi pet, the horn of plenty's extra herbs.

## content/hunter-implings (f89a2fd60): implings

| Rule | Source | Behaviour | Status |
|---|---|---|---|
| Tool | wiki: Impling | wielded butterfly net, or 10 extra Hunter levels barehanded; needs an impling jar | sourced |
| Catch chance | Mod Ash (quoted on the wiki) | 12% at level 1 to 98% at 99; +8 points barehanded or with a magic net | sourced |
| Impling data | wiki: Impling | dbtable impling_types 56002, rows 60152-60162: npc, Puro-Puro npc, jar, level, Puro-Puro and Gielinor XP, respawn ticks | sourced |
| Jar loot | wiki impling jar pages | Loot rolls the jar's table | sourced |
| Jar break | Mod Ash | empty jar comes back except a 10% break chance | sourced |
| Dragon javelin tips | wiki Dragon impling jar note; Mod Ash | only after Monkey Madness II (QuestRequirements); without it the roll is over the rest of the dragon table | sourced |
| Clue stacking | wiki: Treasure Trails, Stacking limitations | no tertiary clue while a clue or challenge scroll of that tier is in the inventory or bank; "sneaking suspicion" message | sourced |
| Sneaking-suspicion toggle | - | message always shown (Settings toggle not read) | caveat |
| Quest mode | - | under assume-completed quest mode MM2 counts as done | caveat |
| Crystal impling spawn | wiki: Crystal impling | one per world in Prifddinas, 12 spots, 17 named implings, new one 3 minutes after each :00 and :30 (UTC) | sourced (timing); guess (spots read off the wiki map) |
| Crystal impling at server start | none | one spawned at server start | guess |
| Crystal impling level / XP | wiki | Hunter 80 (90 barehanded), 280 XP | sourced |
| Crystal catch curve | wiki catch-chance chart | skilling formula low 8 / high 141 with a net (116/256 at 80 to 142/256 at 99); 28 / 161 barehanded or magic net (+20/256, the wiki's 7.8%) | sourced |
| Attempt cadence | none | 2-tick attempts, at most 20 per click | guess |
| Catch, level and no-jar messages | none | - | guess |
| Catching without a jar in Gielinor, lucky impling clue table, scroll boxes and clue stack cap, Gielinor/Puro-Puro spawn rotation (map spawns with fixed respawns used) | - | - | not done |

Tests: ImplingScriptTest (4); ImplingLootScriptTest (7; new: dragon javelin tips only with Monkey Madness II, a held tier's clue is skipped, a clue in the bank counts as held); CrystalImplingScriptTest (3; new: the catch curve reproduces all 40 points of the wiki chart).

Caveats (verbatim): Not done: catching without a jar in Gielinor (immediate loot), the lucky impling's clue table, scroll boxes and the clue stack cap (X Marks the Spot), the Gielinor/Puro-Puro spawn rotation (map spawns with fixed respawns are used). The sneaking-suspicion message is always shown (the Settings toggle isn't read). Under the default assume-completed quest mode Monkey Madness II counts as done. Guesses: the catch, level and no-jar messages, 2-tick attempts (at most 20 per click), the crystal spawn spots (read off the wiki map) and the spawn at server start.

## content/hunter-rumours (dc65bd14e): Hunters' Rumours

| Rule | Source | Behaviour | Status |
|---|---|---|---|
| State | - | server varps 63772-63783: one rumour per guild hunter, the current hunter, the pity count | - |
| Assignment | wiki: Hunters' Rumours (list, notes, rules) | each hunter's list, unboostable Hunter level, no duplicate across hunters | sourced |
| Quest gates | wiki; cache dbrows quest_eaglespeak, quest_bonevoyage | Eagles' Peak for chinchompa and jerboa rumours; Bone Voyage and 31 Herblore for the herbiboar (QuestRequirements) | sourced |
| Quest mode | - | under assume-completed quest mode the requirements count as met | caveat |
| Rare part roll | wiki (rates and pity) | catches of the current creature (box traps, bird snares, herbiboar harvest, butterflies and moths) roll the part with the wiki's rates and pity thresholds | sourced |
| Part rolling on Check | none | the trap part rolls on Check | guess |
| Held part / full inventory | wiki | a held part blocks; a full inventory drops a rolled part and destroys a pity part | sourced |
| Pity reset | none | reset on a new rumour | guess |
| Turn-in XP | wiki | (level + 5) * 50/55/60 | sourced |
| Turn-in reward | wiki | the tier's loot sack | sourced |
| Whistle | wiki: Basic quetzal whistle | the Rumour op names the target | sourced (no-rumour message: guess) |
| Dialogue | wiki transcripts (Gilman, Cervus/Ornus, Aco/Teco, Wolf, Verity); RuneLite Hunter Rumours plugin turn-in lines | assign, remind, switch, hand in; "one of its" / "some of its" / "its" per transcript (Goat hunting for "one of its hooves"); "No." returns to the options; Wolf's "Never mind." -> "See you later." | sourced |
| Level line for hunters other than Wolf, nothing-to-offer line, Gilman's Yes/No switch without an old rumour | none | - | guess |
| Wagtail and herbiboar part wordings | none | - | guess |
| Verity | wiki transcript | unlocks rumours, counts them, toggles back-to-back rumours | sourced (the back-to-back rule itself: guess) |
| Gilman's shop | wiki transcript | sells Trapper's Tipple and stew | sourced |
| Guild hunter outfit pity, loot sacks opening, milestone unlocks, Wolf's At First Light requirement, Verity's spare-gear trade, "intel reshuffle" lines, rumours for creatures the server can't catch | - | - | not done |
| Data | - | dbtables hunter_rumour_hunters 56010, hunter_rumours 56011 (quest and Herblore columns), rows 60271-60280 and 60288-60293, HunterRumourPluginPack, ::rumour | - |

Tests: HunterRumoursTest (7: turn-in XP, assignment rules, quest and Herblore requirements, pity and the whistle, a pity part on a full inventory, turn-in once; the seventh, in the file, is back-to-back off not re-offering the rumour just handed in); HunterGuildScriptTest (5: the "one of / some of / its" lines, Wolf's leave option, Gilman's stew (three tests)); ButterflyScriptTest's rumour catch.

Caveats (verbatim): Under the default assume-completed quest mode the quest requirements count as met. Guesses: wagtail and herbiboar part wordings, the pity reset on a new rumour, the part rolling on Check, the no-rumour whistle message, the level line for hunters other than Wolf, the nothing-to-offer line, Gilman's Yes/No switch without an old rumour, the back-to-back rule. Not done: guild hunter outfit pity, loot sacks opening, milestone unlocks, Wolf's At First Light requirement, Verity's spare-gear trade, the "intel reshuffle" lines, rumours for creatures the server can't catch.
