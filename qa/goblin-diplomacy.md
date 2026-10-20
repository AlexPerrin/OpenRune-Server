# QA: content/goblin-diplomacy-829
- Branch: `content/goblin-diplomacy-829`
- Commit: `4f5e59627a1e47bf7d601c523b0a0a4f715f658e`
## Internal gate: **PASS**
## Upstream gate: **FAIL**

Run by sq25 (companion to sd24) on 2026-10-19. One commit on upstream `main` 829b9477d (the current tip), so the
gate tree is the commit itself (fresh `git clone --shared` `re/agents/sq25/g-gd`, `origin/main` = 829b9477d).
Spec `accuracy/goblin-diplomacy.md` and text `pr-texts/goblin-diplomacy.*` at fork docs 0fd090018. It would
replace the contents of upstream draft #367 only on the user's OK; the lookout ladder is a separate fix branch.

## Internal gate details (PASS)
### build-test
`openrune/upstream/qa/qa.sh gate` (`GATE_VOL=sq25-gradle`; remote build cache for compiles, tests never cached).
Logs `re/agents/sq25/g-gd.logs/`.

| Step | Result | Time |
|---|---|---|
| `clean :or-cache:qaCacheToolsClasspath` | pass | 27s |
| `:or-cache:freshCache :or-cache:mergePluginGamevals` | pass | 98s |
| `:or-cache:buildCache` | pass | 57s |
| `generateRsa` | pass | 27s |
| `:server:app:installDist` | pass | 35s |
| `spotlessCheck` | pass | 35s |
| `assemble` | pass | 86s |
| `test` | pass, 764 tests, 0 failures | 100s |
| `integration` | pass, 79 tests, 0 failures | 104s |
| boot (copy of the install + `.data` in `g-gd-run`, `sq25-or-server` 43597) | "Server ready in 22.1s" (the dbrow startup check passes), no Exception/ERROR line in the whole session | |
| `fix_gameval_conflicts.py --check` | "No gameval conflicts or unassigned IDs found." (no gamevals added) | |

### accuracy-check
Sources re-read on 2026-10-19 (osrs-mcp disconnected; the wiki's raw pages read directly): Goblin Diplomacy
(Reward), Transcript:Goblin Diplomacy, Transcript:Grubfoot, Transcript:General Wartface (shared with Bentnoze).
- Rewards: 200 Crafting XP and a gold bar (wiki), 5 QP (dbrow): matches.
- Varbits (gamevals.dat): `gobdip_main` 2378, `gobdip_crate1..3_searched` 2379-2381, `gobdip_grubfoot_vis` 13594,
  `catwalk_goblin` 1965, crate locs 16559-16561, as the spec.
- Dialogue: a script compared every transcript line (all four pages) with the code's strings. Every line in
  scope is in the code verbatim, including the crate's "You search the crate, but but find nothing of
  interest." (the wiki has the double "but"). The only lines not in the code are the ones the spec and PR
  list as not included: the village goblins' quest-era chatter, the Another Slice of H.A.M. dialogues 6-8,
  Land of the Goblins, and "Where's Bentnoze gone now?". The rest are templated colour lines, which the code
  builds.
- #328's lines (see the upstream section) are the wiki's after-quest lines: Transcript:Grubfoot puts its five
  under "After Goblin Diplomacy", and the generals' "Standard dialogue" includes Dialogue 5, "Now you've solved
  our argument...". The branch plays them only at stage 6. No divergence beyond the spec's guesses.

### In game (desktop RuneLite, fresh account `sq25gd` on the branch server, desktop lock 19:52-20:03)
Cheats used: `::tele` for travel (to the village and next to the generals' hut and the west hut). `::item` for
the third goblin mail, because the third crate is up the lookout ladder, a separate fix not on this branch,
and for red, yellow and blue dye (Aggie's dye-making is upstream content outside this PR). Two goblin mails came
from the crates, and orange dye came from the branch's red + yellow mixing.
- Generals at stage 0: a random opener ("Green armour best." / "No no red every time." / "Go away human, we busy."),
  then the topic menu. "Why are you arguing..." swapped to "What is the goblin new century?". Then the peace
  lines, "Do you want me to pick an armour colour for you?" and "What about a different colour?", the prompt
  "Start the Goblin Diplomacy quest?" with Yes, the start lines, and the help loop (armour, then the D-Y-E lines,
  then "Okay, I'll be back soon."). Varbit 2378: 0 -> 3. All verbatim to the transcript.
- Grubfoot at stage 3: "Grubfoot wear red armour! ..." / "Why they not make up their minds?" / "Shut up Grubfoot!".
- Crates: 16559 and 16560 each gave the objbox "You find some goblin mail in the crate.", and varbits 2379 and
  2380 went to 1. A second search gave "You search the crate, but but find nothing of interest." and no item.
- Plain mail used on Bentnoze at stage 3: "What do you think of this colour?" / "That wrong colour." / "We tell you
  get orange armour."
- Red + yellow dye: "You mix the two dyes and make orange dye." (one of each taken, orange dye added).
  Orange dye on mail: "You dye the goblin mail orange." Blue dye on mail: "You dye the goblin mail blue."
- Orange mail used on Wartface: the try-on lines verbatim, 2378 = 4, 13594 = 1, and Grubfoot shows as npc 672
  (orange). His orange lines are verbatim.
- Blue through the talk menu's "I have some blue armour here." option: the try-on lines verbatim, 2378 = 5,
  13594 = 2, Grubfoot 673 (blue). His blue lines are verbatim.
- Brown through the talk menu: the final lines, then the quest-complete interface (153). 2378 = 6, 13594 = 0
  (Grubfoot back to 671), quest points (varp 101) = 5, a gold bar in the inventory, and Crafting +30,000
  (200 x the dev realm's `baseXpRate` 150, upstream's reward path).
- RuneLite's Quest Helper followed every step from these varbits: its overlay read "Talk to one of the Goblin
  Generals once more, 1 x Goblin mail" at stage 5 and highlighted the hand-in option (the "[4]" prefix seen in
  the options is Quest Helper's, not the server's).
- After the quest: the generals gave #328's dinner and weapons conversations, and Grubfoot gave "Don't talk to
  me!" and "Me lonely.", each followed by a general's "Shut up Grubfoot!". A gold bar used on Wartface gave
  "Nothing interesting happens."
- Not tried in game: the other openers and the "fat" menu; red/green picks; declining the start; the other
  dyes and mixes; the crate with a full inventory; the third crate (needs the ladder fix).

### RSProx recordings
None available for Goblin Diplomacy yet ("main", 2026-10-19). The comparison (dialogue, animations, varps per
stage, rewards) is still to do before upstream.

### reverse
n/a.

## Upstream gate details (FAIL)
**Deleting #328's `GoblinGenerals.kt` and `Grubfoot.kt`: in scope, keep it as is.** Both files bind op1 on the
generals and Grubfoot, the same npcs the quest needs at every stage, and two op1 scripts can't share an npc.
The wiki puts all of #328's lines after the quest (Transcript:Grubfoot "After Goblin Diplomacy"; the generals'
standard dialogue includes the post-quest Dialogue 5). The branch moves them into the quest as its stage-6
dialogue, verbatim: the generals' four conversations and Grubfoot's five complaints with the random "Shut up
Grubfoot!", same chatheads (`catwalk_goblin_brown` once he's back in brown). It adds the wiki's fifth general
line. Keeping #328's files with quest-gated additions would need a cross-module hook from `content/areas` into
the quest for the same lines, with no behavioural gain. The PR says why the files go. Recommended: say "moved
verbatim" and credit #328 by name in that bullet, since it removes another contributor's merged work.

Passes:
- Scope: one quest; 3 files (the quest script, the two #328 deletions). The ladder is split out.
- Overlap: `gh pr list --search goblin`: #367 (ours, to be replaced) and #310 (Thieving, which touches none of
  these files). No other open PR touches `goblin-village` or the quest's directory.
- No gamevals added; `--check` clean.
- AGENTS.md: no new KDoc or comments (the two deleted files had a class KDoc each; the quest file has none);
  state in cache varbits. No test: dyeing, mixing, the crate and the hand-in move items, and AGENTS.md wants
  tests for those, but the maintainer asked for #366's quest tests to be removed, and the PR says so. A
  reviewer may still ask.
- Template sections present; Type of change (New content) right; checklist true, with the rsprox box
  unticked and the reason given; Data sources (CC BY-NC-SA) and release note in the notes; `feat:` title
  matches the commit.

What must change before upstream:
1. **"How it was tested" is out of date.** Replace the dev-build line and "Not yet checked in game from this exact
   branch" with this record: the full gate (test 764, integration 79, boot, `--check`) and the in-game run. Name
   the cheats (`::tele`; `::item` for the third mail and the red, yellow and blue dye) and say what wasn't tried.
2. **RSProx comparison.** Pending: no recording yet. The upstream gate can't pass until it is done and
   recorded here.
3. Recommended: in the #328 bullet, "moved verbatim from #328 (Add Goblin Village)".

Re-check after 1 (text) and 2 (recordings): the upstream gate only, same commit.
