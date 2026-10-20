# QA: content/sheep-shearer
- Branch: `content/sheep-shearer`
- Commit: `01da94f17e2eb0aeefb0d78c42e69bff2ca94f7a`
## Internal gate: N/A (dev-checked)
## Upstream gate: **PASS**

Run by sq24 on 2026-10-19 at 01da94f17 (fork PR AlexPerrin/OpenRune-Server#16; it updates upstream #366 by fast-forward,
069c15fde being its parent). Spec `accuracy/sheep-shearer.md` and PR text `pr-texts/sheep-shearer.upstream.body` read
from sd23's docs commit 65cc94f05 (the spec there is the repaired 100-line file). The new commit changes only
`SheepShearer.kt` (+20/-17): fixes 1-5 of the FAIL at 069c15fde below.

## Upstream gate at 01da94f17: PASS
- **build-test**: fork CI run 39353944420 (`CI`: cache, assemble/test/integration, boot "Server ready in 11.42s")
  and 39353946179 (`Formatting`) green at 01da94f17 (head sha checked). `fix_gameval_conflicts.py --check` clean.
  The branch server (`qa.sh gate-run` + `start`, upstream main 829b9477d + the branch = the commit's tree) booted
  "Server ready in 11.38s"; no exception or error lines over the session.
- **accuracy-check against 1718** (re-read at the transcript, ticks 7718-7741; seq ids through the mapping in the
  FAIL section below), each fix of the FAIL list:
  1. 20-ball branch after the start: the code checks the balls after `chatPlayer(happy, "Yes, okay...")` and
     `advanceQuestStage` (1718 t7730-7731). In game: the full exposition and the start prompt played with 20 balls
     held; varp 179 was still 0 on "Yes, okay. I can do that.", became 1 with "You've started a new quest" on that
     continue, then "In fact Fred, funnily enough..." (1718 t7731: varp 0 -> 1 and page 13 on the same tick).
  2. Hand-in ending: in game, "I guess no real 'arm was done..." -> mesbox "You give Fred 20 balls of wool."
     (inventory still 20 balls) -> "That's the last of them." (still 20) -> "I guess I'd better pay you then." ->
     on that continue varp 179 = 21, the balls gone and 60 coins in slot 0, "Congratulations, you've completed a
     quest", Crafting +150 x this world's rate. 1718: t7738 mesbox (no inventory change), t7739, t7740, t7741 (varp
     1 -> 21, balls removed and coins added in one update). The per-ball path (`handIn`) now goes through the same
     `lastDelivery` (re-count after the last page, give-back on a failed reward), by code.
  3. "woolly": in game "No! Well, maybe... They just looked a little woolly!" (1718 t7734).
  4. Mesanims (code; the devtools bridge doesn't expose chathead anims): "Yes, that's it..." `happy` (1718 569 =
     chathap), "Well now, no one has ever seen..." `shifty` (593 = chatshifty), "No! Well, maybe..." `worried`
     (597 = chatscared), "I guess I'd better pay you then." `sad` (610 = chatsad). Every other page of t7718-7740
     also matches: 616/615/614 angry, 588-590 neutral, 554 quiz, 598 worried, 567/568 happy, 610 sad ("I'm
     sorry..."), 589 neutral ("I guess no real 'arm...").
  5. Scroll model: in game 153:5 shows obj 1735 with 400 (zoom), at 78,128; 153:4 "You have completed Sheep
     Shearer!", 153:9-11 "1 Quest Point", "150 Crafting XP", "60 Coins" (1718 t7741, identical).
  Page texts and their order in game are identical to 1718 t7718-7740 (wrapping included).
- **In game, other claims** (desktop lock 22:11-, fresh account `sq24sh2`, branch server 43596): The Thing
  (npc 731) Shear with shears: "The... whatever it is... manages to get away from you!"; Talk-to: "That's a
  sheep...I think. I can't talk to sheep."; after completion Fred offers "I'm looking for something to kill.",
  "I'm lost." and "Fred! Fred! I've seen The Thing!" (the shear attempt set the flag); "I'm lost." gives the
  transcript's reply.
- **Caveats**: the PR text lists every gap from the RSProx comparison (The Thing's sounds, anim, face and movement
  with the recorded ticks; its idle seq 3570; the Talk-to variant; jingle 154 vs the framework's; per-ball
  hand-ins unrecorded; partial deliveries taking the balls before the give box) and the unsourced items in the
  spec.
- **Scope**: net diff against main is the quest only (3 files: `SheepShearer.kt`, `sheep_shearer_vars.toml`,
  quest-pack `gamevals.toml`). Open upstream PRs on the topic: only #366 (this one); #367/#368 match "sheep" only
  by text. On current main 829b9477d (merged in). No `Depends:`.
- **Conventions**: upstream's PR template, every section present; commit subjects `feat:`/`fix:`. Gamevals
  symbols only. No tests, as the maintainer asked on #366 (the PR says so).
- **pr-text-review**: every claim checked against the commit (code) and, where it plays, in game; true.
- Notes (not this branch): the quest start/complete messages carry `@q_mes@` from upstream's `Quest.kt`
  (1718: `<col=0ab0ff>`); the branch's history keeps the stile commit and its revert and the fixture commit
  and its removal (net zero; upstream can squash). `varbit.sheep_shearer_progress` 65449 is also #368's
  `witchs_potion_progress` id: whichever merges second needs `fix_gameval_conflicts.py` re-run (its CI check
  will flag it).

---
## History: earlier runs at 069c15fde and 19aefd90a (void)

### RSProx comparison at 069c15fde (sq24, 2026-10-19): FAIL
The upstream gate's accuracy check includes the RSProx comparison; the PASS below was recorded before any
recording was available (its checklist line said "we have no dumps"). This comparison, at the same commit,
finds gameplay-visible divergences in the quest's own code, so the upstream gate at 069c15fde is **FAIL**.
The earlier PASS section stays below as history. Fork CI run 39345674203 (green at 069c15fde) and the base
(upstream main 829b9477d, still the tip) are unchanged.

Recordings (RSProx database, local transcripts `re/ref/rsprox/txt/`; facts only, no names or chat):
- **1718** (rev 227.1, Leagues V), ticks 7184-7746: shearing, The Thing, spinning, Fred start + all-at-once
  hand-in, quest complete. Each fact below was re-read from the transcript, not from the notes alone.
- **1619-1622** (rev 225.1, quest already complete): shearing mechanics; 1620 has The Thing (t1645-1653
  shear, t1899 Talk-to).
- Chathead seq ids resolved through the branch's `gamevals.dat` (chatquiz 554-557, chathap 567-570, chatneu
  588-591, chatshifty 592-595, chatscared 596-599, chatsad 610-613, chatang 614-617); OpenRune mesanims:
  quiz, happy, neutral, shifty, worried = chatscared, sad, angry.

### Matches
- Fred's opening line, options "I'm looking for a quest.|I'm looking for something to kill.|I'm lost." under
  "Select an option", the offer pages, the exposition about The Thing, the start prompt title "Start the Sheep
  Shearer quest?" with "Yes.|No.", "Yes, okay. I can do that.": text identical (wrapping aside).
- Quest start: varp 179 0 -> 1 and "You've started a new quest: <col=0ab0ff>Sheep Shearer</col>" on the
  continue after "Yes, okay..." (1718 t7731); the framework sends the same line when the stage leaves 0.
- Completion: varp 179 1 -> 21 in one step (all-at-once hand-in), "Congratulations, you've completed a quest:",
  quest scroll lines "You have completed Sheep Shearer!", "1 Quest Point", "150 Crafting XP", "60 Coins",
  Crafting XP +150, coins 995 x60 into the inventory, the 20 balls removed (1718 t7741). The code's rewards
  and labels produce the same lines.
- The Thing shear message "The... whatever it is... manages to get away from you!", no wool, no transform, no
  inventory change (1718 t7259, 1620 t1651).
- Chathead anims on most pages: Fred page 1 chatang (angry), the offer pages chatneu (neutral), "What do you
  mean, The Thing?" chatquiz, "Some say..." chatscared (worried), "Well I don't have all day..." chatang,
  "Yes, okay..." / "In fact Fred..." / "That's the last of them." chathap, "Have you been shearing..." /
  "It's rude..." chatang, "I'm sorry..." chatsad, "I guess no real 'arm..." chatneu.

### Divergences that must be fixed (new commit on `content/sheep-shearer`)
1. **The 20-ball branch comes after the quest starts, not instead of it.** 1718 (player holding 20 balls of
   wool): after "...the small matter of The Thing." the full exposition still plays ("What do you mean, The
   Thing?" and Fred's three pages), then the start prompt, "Yes, okay. I can do that.", varp 179 0 -> 1 and the
   "You've started a new quest" message (t7731), and only then "In fact Fred, funnily enough, I actually have
   20 balls of wool already on me." (in place of "Good! Now one more thing..."). The branch jumps to
   `alreadyHaveTheWool()` straight after the third offer page: no exposition, no start prompt (the player
   can't decline), no varp 1, no start message. The wiki transcript places the branch there, but the
   recording of the live game is the stronger source. Fix: move the `ballsOfWool >= 20` check to after
   `chatPlayer(happy, "Yes, okay. I can do that.")` and `advanceQuestStage`.
2. **The all-at-once hand-in ends like a final delivery.** 1718 after "I guess no real 'arm was done. Hand the
   balls over...": mesbox (if 229) "You give Fred 20 balls of wool." (t7738), player "That's the last of
   them." (chathap, t7739), Fred "I guess I'd better pay you then." (chatsad, t7740), then completion on that
   continue, with the balls removed and coins added in the same tick (t7741). The branch completes straight
   after "Hand the balls over..." with none of these three pages. Fix: add the three pages before completing
   (keep the re-count of the 20 balls after the last suspension and the give-back if the reward fails).
3. **Spelling:** the player says "They just looked a little woolly!" (1718 t7734). The branch has "wooly"
   (copied from the wiki transcript). Fix: "woolly".
4. **Chathead mesanims** that differ from the recording (fix the mesanim argument):
   - Fred "Yes, that's it. Bring me 20 balls of wool..." is chathap (569): `happy`, not `neutral`.
   - Fred "Well now, no one has ever seen The Thing..." is chatshifty (593): `shifty`, not `worried`.
   - Player "No! Well, maybe... They just looked a little woolly!..." is chatscared (597): `worried`, not
     `shifty`.
   - Fred "I guess I'd better pay you then." is chatsad (610): `sad`, not `happy` (applies to `handIn`'s
     completion branch too, which uses the same line).
5. **Quest scroll model:** the official scroll shows the shears, obj 1735 at zoom 400 (if_setobject 153:5,
   1718 t7741). The branch shows `obj.ball_of_wool` at zoom 200. Fix: `ItemRewardDisplay("obj.shears", zoom =
   400)`.

### Divergences that stay caveats (PR text Caveats)
- **The Thing's reaction to a shear** (already a caveat; the recordings now give the detail): the player
  faces it and plays seq 893 with synth 760 (not the sheep's 761) when adjacent; next tick The Thing faces the
  player, plays seq 3570 and synth 686; the message comes 2-3 ticks after the player anim (1620 t1648 ->
  t1651, 1718 t7256 -> t7259); then it steps away from the player (1620: one step a tick for four ticks from t1651,
  south-west/south; 1718: a step north-west at t7260). The branch plays seq 893 and sends the message the same tick, with no sounds, npc anim,
  face or movement. The caveat line can cite the recordings instead of only the wiki.
- **The Thing plays seq 3570 by itself** at random (1718: t7178, 7181, 7215 and others) without a player:
  not implemented (the npc spawn is upstream's, with no idle anim); cosmetic.
- **The Thing's Talk-to:** 1620 (an account well past the quest, likely after Cold War) gets the player line
  "I can't speak to the penguins when I'm not in my<br>disguise." (chatshifty). The branch always uses the
  wiki's standard line "That's a sheep...I think. I can't talk to sheep." The condition for the other line
  isn't documented; caveat.
- **Quest complete jingle:** the official plays midi_jingle 154 (`jingle.high_seas` in today's gamevals;
  1718 t7741); upstream's framework plays `QUEST_COMPLETE_JINGLE` = `jingle.gaol` (153) for every quest. A
  framework choice outside this branch; caveat (or pass `completeJingle` if the id is confirmed for the
  current cache).
- **Per-ball hand-ins** (varp 179 values 2-20, "You give Fred n balls", the final per-ball delivery) are in no
  recording. The recording's all-at-once path shows a give box before "That's the last of them.", so the
  per-ball final delivery may have one too; the wiki transcript ("Finishing up") shows none. Keep the wiki
  version, as a caveat.
- **When the balls leave the inventory:** officially at completion, together with the coins (1718 t7741);
  the branch removes them before the dialogue pages (`handIn`) or, after fix 2, wherever the dev puts the
  take. No visible effect beyond the inventory updating a few pages earlier; caveat if it stays.
- Fred's other options ("something to kill", "I'm lost", "No."), the mid-quest dialogue and "Fred! Fred! I've
  seen The Thing!" are in no recording; they keep the wiki transcript as their source.
- 1718 is Leagues V: league task messages, the overlay (if 660, script 3343) and jingle 283 are not base game
  and were ignored.

### Out of scope (upstream's code, not in this diff)
Generic shearing (`Sheep.kt`: op -> seq 893 + synth 761 -> +2 ticks "You get some wool.", wool 1737,
transform to the sheared id, regrowth after 50 ticks, "This sheep has already been shorn.", "You need a set of
shears to do this."), spinning (if 270, seq 894, one ball per 3 ticks) and the pen gate (12986 -> 12988/12989,
synth 67, closes after 300 ticks) were read in 1619-1622 and 1718 for reference only (re-checked by a read-only subtask; spot-checked at
source: 1619 t144 anim + synth 761, t146 wool 1737 + transform 1304; 1621 t426 "already been shorn"; 1619
gate re-added t374). They aren't changed by
this branch and aren't judged here.

### What must change
- Code (new commit): fixes 1-5 above. A new commit voids the gate; the upstream gate is re-run on it.
- PR text (`pr-texts/sheep-shearer.upstream.body`): line 7 ("the last delivery goes straight to "That's the
  last of them." with no give box") and line 8 (the 20-ball exchange "comes right after ...the small matter of
  The Thing."") must describe the fixed flow; the rsprox checklist line ("we have no dumps of the official
  game") can be ticked, citing recordings 1718 and 1619-1622; add the caveats above (Thing details, idle anim,
  Talk-to variant, jingle, per-ball hand-ins unrecorded, item-removal timing if kept).
- Spec (`accuracy/sheep-shearer.md`): **the file on `docs` is corrupted** by commit 8d1f0ca03 (21,851 lines,
  1.9 MB: a three-line "Chosen (user, 2026-10-19): merge main ..." paragraph was inserted after every
  character). Stripping that paragraph plus its newline gives back exactly the 77-line version of 0c19c7bb3;
  the intended addition (that paragraph, once) has to be re-applied. Rows 6, 8/12 (D1, D3), 13, 15 and 19 then
  need the RSProx findings, and the rsprox row added.


Re-run by sq24 on 2026-10-19 at 069c15fde. Fork PR AlexPerrin/OpenRune-Server#16; it updates upstream #366
(head `content/quests-sheep-romeo`, now 19aefd90a) by fast-forward: 19aefd90a is an ancestor of 069c15fde.
Spec and PR text (upstream's template, #369) at fork docs 42c015d2a+. The verdicts at 19aefd90a below are void.

Commits since 19aefd90a: 6b456af97 (stile change), 60b5eff93 (tests and `QuestTalkFixture` removed, as the
maintainer asked), e52b004d3 (stile change reverted; it is now fix/stile-approach), 069c15fde (merge of upstream
main 829b9477d). **Net diff against main: the quest only, 3 files.** `SheepShearer.kt` and
`sheep_shearer_vars.toml` are byte-identical to 19aefd90a. `quest-pack` `gamevals.toml` adds the three
symbols next to main's.

### Internal gate at 069c15fde: PASS
- **build-test**:
  - `qa.sh gate` on clone `qa/sheep-069c15fde`, `origin/main` = 829b9477d.
  - Every step rc=0: compile, freshCache + mergePluginGamevals, buildCache, generateRsa, installDist,
    spotlessCheck, assemble.
  - `test` 764/0 (`content:quest` 207). `integration` 79/0. Total 648 s.
  - `fix_gameval_conflicts.py --check` is clean.
  - Boot: "Server ready in 16.76s". No exception or error lines; the startup check of 21 against the dbrow's
    end state passed.
- **accuracy-check**: the quest code is unchanged from 19aefd90a, whose accuracy check passed. The caveats
  that 19aefd90a's upstream run asked for are in the text: the penguins squeak and run away; only the generic
  shearing animation.
- **In game**: not re-run (optional per main). The code is identical to the commit a-thoren checked in game,
  and the stile is not in this branch.

### Upstream gate at 069c15fde: PASS (superseded by the RSProx comparison above)
- **Scope**: the quest only (3 files net). The stile fix is separate (fix/stile-approach); #366 doesn't
  depend on it, and the text says so.
- **Base**: upstream main 829b9477d (the tip, re-fetched) is merged in. The only conflict was the quest-pack
  `gamevals.toml`, resolved by keeping both sides; gamevals check clean.
- **Overlap**: `gh pr list --search sheep` shows #366 (this) and our #367/#368. Those two are stacked on the
  old #366 commit and carry an old Sheep Shearer; that is the orchestrator's follow-up from the 19aefd90a record,
  not this branch's.
- **Template (#369)**: every section is present.
- **Checklist** is honest:
  - "Builds and the server boots": unticked with a note. It is true now; it can be ticked.
  - tests: none, ticked with "none in this PR now";
  - gamevals symbols, no Kotlin data tables: true;
  - varps/varbits, no attrs: true;
  - quirks.md and rsprox: unticked with notes.
- **pr-text-review**:
  - True: varp 179 values; rewards; the hand-in lines; the 20-ball shortcut; the reward-room refusal; The
    Thing's lines; the -1 gamevals assigned by the script; the review notes (tests removed, stile separate);
    207 tests in `content:quest`, 0 failures; gamevals clean; "Not checked in game by us on this commit".
  - The merge note is accurate.
- **Caveats**: unchanged from the passing 19aefd90a list, plus the penguins line.
- Note for the orchestrator: the PR will show the add-then-remove commits (fixture, stile) and a merge commit.
  The net diff is clean; upstream may ask to squash (CONTRIBUTING).

### Earlier runs at 19aefd90a (void: superseded by 069c15fde)

Run by sq24 (companion to sd23) on 2026-10-18 in its own clone `re/agents/sq24/or`, detached at the commit.
The branch is two commits (35900b277 `QuestTalkFixture`, 19aefd90a the quest) on c2492318d, which is
current `upstream/main` (re-fetched after the run), so the branch tree is upstream `main` + the branch.
The dev's run (sd23 scratchpad `gate/sheep-shearer-19aefd90a.logs/`) was read as evidence only.

### Upstream gate, first run: FAIL (fixed in docs 4a35daadc; see "Upstream re-run" at the end)
The PR's Caveats miss two gaps against the sources. Add them to `pr-texts/sheep-shearer.upstream.body`
(and to `accuracy/sheep-shearer.md` as rows), or implement them; either is a new commit or text change,
and the upstream gate is re-run:
1. **The Thing doesn't move away when someone tries to shear it.** Wiki Sheep (penguins): "Attempting to
   shear the fake sheep will result in it walking away and doesn't provide any wool". Wiki Sheep Shearer,
   Obtaining the wool: "attempting to shear it will cause the penguins to squeak and run away from you".
   `shearTheThing()` plays `seq.human_shearing` and sends the message. It doesn't move the npc and plays no
   sound. The spec doesn't list this either.
2. **The spec's own scope gap isn't in the PR.** `accuracy/sheep-shearer.md` "Scope gaps" says the
   shearing animation and timing on The Thing use the generic `human_shearing` seq. The PR's Caveats leave
   it out. There is also no `faceEntitySquare`, delay or sound, which generic `Sheep.shearSheep` has.
   One caveat line can cover items 1 and 2.

Follow-up for the orchestrator (not a fault of this branch): upstream #367 (Goblin Diplomacy) and #368
(Witch's Potion) are our PRs, stacked on #366's old commit `f69c8ed4c`. Their diffs carry the old
Sheep Shearer (`SheepShearer.kt`, gamevals 63712/63715/63716) and Romeo & Juliet. Once #366's head is
replaced with this branch, they have to be rebased onto it and their "Stacked" lines updated. If they
aren't, they carry a second, conflicting Sheep Shearer.

### Internal gate
### build-test (at 19aefd90a)
Own Gradle home volume `sq24-gradle`; every container through `tools/buildq.sh sq24` (`--cpus=4
--memory=4g`, `--max-workers=2`, test/integration with 1 worker, `-Xmx2g`, cache tasks `-Xmx2600m`,
`-Pkotlin.compiler.execution.strategy=in-process`, `--no-build-cache --no-configuration-cache`), each
step a separate run.

| Step | Result |
|---|---|
| `clean :or-cache:freshCache :or-cache:mergePluginGamevals` | pass |
| `:or-cache:buildCache` | pass |
| `generateRsa` | pass |
| `:server:app:installDist -x test` | pass |
| `spotlessCheck` | pass |
| `assemble` | pass |
| `test --continue` | pass: 689 tests, 0 failures, 0 errors; `SheepShearerInteractionTest` 6/6 |
| `integration --continue` | pass: 71 tests in 15 suites, 0 failures |
| boot (`sq24-or-server`, installDist in `eclipse-temurin:21-jre`, game port 43598) | "Server ready in 17.93s", no exception or error lines in the log; the startup `check(quest.maxSteps == 21)` passed |

Tree after the run: only regenerated `.data/gamevals/*.rscm` files (build output); no source file changed.
`python3 tools/scripts/fix_gameval_conflicts.py --check`: "No gameval conflicts or unassigned IDs found."

### accuracy-check
The osrs-mcp tools did not load in this session: ToolSearch found none, even after the orchestrator
reported the server reconnected. Sources used instead: the OSRS Wiki through its API (raw wikitext;
revisions: Transcript:Sheep Shearer 15320468, Transcript:Fred the Farmer 15143769, Transcript:Sheep
(penguins) 15284321, Sheep Shearer 15367050, Sheep (penguins) current), Joshua-F/osrs-dumps
`config/dump.npc` and `config/dump.dbrow`, and the branch's own `.data/gamevals-binary/gamevals.dat`.

Cache and ids, checked: npc 731 `sheep_shearer_the_thing` has `op1=Shear` and `op3=Talk-to`, so the
`onOpNpc1` and `onOpNpc3` bindings are right. Fred is npc 732 (`op1=Talk-to`). Obj 7670
`crafting_icon_dummy`, varp `sheep`=179, shears 1735, ball_of_wool 1759, seq `human_shearing` 893.
dbrow `quest_sheepshearer`: endstate 21, questpoints 1, crafting 1500 (tenths), so 150 XP. The Thing is
spawned from `lumbridge.toml` with no content group, so generic `Sheep` doesn't also bind it, and the
boot shows no conflict.

Spec rows against the code and the transcripts:
- Rows 1-3 (varp 179, rewards, partial and noted deliveries) match. Noted balls are a different obj, and
  `inv.count(obj.ball_of_wool)` counts only unnoted ones. The 60 coins aren't in the dbrow and come from
  the wiki's Rewards.
- Rows 4-12 (Fred's dialogue) match the Transcript:Sheep Shearer text line for line: the opening, the
  offer, the decline, the 20-ball branch right after "...the small matter of The Thing." with no
  exposition or prompt, the shears box, the spinning help with the pic-7670 box, the remaining count, the
  three help options including "I don't know how to spin wool" -> `tact above`, the unspun-wool reply,
  the partial hand-in lines and the final hand-in with no give box.
- Rows 13 and 15 (shear message, Talk-to line) match Transcript:Sheep (penguins).
- Rows 16-17 (the "I've seen The Thing" option during and after the quest; kill / lost otherwise) match
  Transcript:Fred the Farmer. "Interacted" is read as a shear attempt while holding shears, which is a
  caveat.
- Rows 14 and 18 (messages with no source) are caveats.
- Gap the spec misses: The Thing doesn't walk away or squeak when sheared (see "What must change", item 1).
  It's cosmetic and moves no items, so it doesn't block the live server, but it has to be a caveat
  upstream.

Item paths (AGENTS.md): the source is re-counted after every suspension before the take
(`alreadyHaveTheWool` re-checks for 20 balls; `handIn` takes `min(count, remaining)` with no suspension
in between). If the reward fails, the exact amount taken is given back (tested, `Int.MAX_VALUE` coins).
Shears go through `invAddOrDrop`.

### In-game
Done statically, not in a client. The orchestrator granted the desktop lock, then said the
osrs-devtools-desktop MCP tools aren't available in agent sessions and asked for a static check instead.
The lock was never taken. The static check: the op slots against the cache dump (above), and the
dialogue against the transcripts (above). The tests cover the item-moving paths. The branch server
booted (above) and was then removed. Nothing renders that a plugin reads, apart from varp 179 for Quest
Helper, which the tests check (1, 6, 21).

`reverse`: n/a (no claim about the client protocol).

**Internal verdict: PASS.** Build, spotless, tests, integration and boot pass at the commit. Behaviour
matches the spec and its sources, apart from one cosmetic gap the spec doesn't list (The Thing doesn't
walk away), which is for the upstream caveats.

### Upstream gate
- Internal PASS at the same commit: yes.
- Scope, one feature: the quest plus its test fixture (test sources only), declared under **Depends:**.
  Nothing unrelated in the diff (5 files: `gamevals.toml`, `sheep_shearer_vars.toml`, `SheepShearer.kt`,
  `QuestTalkFixture.kt`, `SheepShearerInteractionTest.kt`).
- Upstream overlap: `gh pr list -R OpenRune/OpenRune-Server --search sheep|shearer|fred` turns up #366
  (ours, which this replaces), #367 and #368 (ours, stacked on #366; see the follow-up). #299 (Romeo &
  Juliet) shares no file and no content with this branch. Every open PR's diff was grepped for the ids:
  #231's 65448/65449 are varps, not varbits, so they don't clash with these varbits, and nothing else
  uses varp 65472.
- Based on current `upstream/main` c2492318d: yes.
- `fix_gameval_conflicts.py --check`: clean.
- CONTRIBUTING (`.github/CONTRIBUTING.md`) and AGENTS.md: the commits use `feat:` subjects. The text has
  context, release note, the checklist, data sources with the CC BY-NC-SA attribution, the Claude Code
  line and a **Depends:** line. The code follows AGENTS.md: varbits/varps, not attrs; gameval symbols,
  not raw ids; constants in constant case; no narration comments; tests limited to item-moving paths,
  as AGENTS.md asks.
- `pr-text-review`: every claim was checked against the commit, and all are true: varp values, the
  startup check, the rewards, the dialogue branches, noted balls, the 20-ball path, the reward-failure
  rollback on both paths, The Thing's ops and message, varbit gating, the gamevals, the 6 tests and what
  each covers, "Nothing was checked in game from this exact branch". The build numbers in the text are
  the dev's run; this run agrees (all pass), only the boot time differs (17.93s here, 11.42s there).
- Caveats cover every gap: **no**, items 1 and 2 under "What must change".

**Upstream verdict: FAIL.** Only the caveats need changing; the code doesn't have to. If the fix is a
PR-text change only, with the commit still 19aefd90a, the internal PASS stands. The upstream gate is
re-run on the new text.

### Upstream re-run (docs 4a35daadc, commit still 19aefd90a)
Re-run by sq24 on 2026-10-18. The code commit didn't change, so the internal PASS and the build-test
results above still stand. sd23's fix only changes text: `pr-texts/sheep-shearer.upstream.body` has one
new Caveats line, and `accuracy/sheep-shearer.md` has row 19 and an updated scope-gap line. Diff checked
against 28f1fe5e7:
- First-run item 1 (The Thing doesn't walk away or squeak) is covered by the new caveat, which quotes
  both wiki sources, and by spec row 19.
- First-run item 2 (generic animation and timing; no face, delay or sound like `Sheep.shearSheep`) is
  covered by the same caveat line and by the spec's scope-gap line.
- The new caveat's claims are true of 19aefd90a: `shearTheThing()` only calls `anim("seq.human_shearing")`
  and `mes(...)`. It doesn't move the npc, and it has no `faceEntitySquare`, `delay` or `soundSynth`.
- The rest of the PR text is unchanged, and every earlier claim is still true at the same commit.
- `upstream/main` is still c2492318d (re-fetched). The open PRs matching "sheep" are still only #366,
  #367 and #368 (all ours; the #367/#368 follow-up above still applies).
- Minor, not blocking: spec row 17 says the flag is "set on any shear attempt", but the code sets it only
  when shears are held. The PR text says it correctly ("A shearing attempt while holding shears").

**Upstream verdict (re-run): PASS** at 19aefd90a, with the PR text in docs 4a35daadc.
