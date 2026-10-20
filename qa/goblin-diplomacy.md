# QA: content/goblin-diplomacy-unstacked
- Branch: `content/goblin-diplomacy-unstacked`
- Commit: `d77ac2b76eefe47211f4d15138563c19ddf19bfd`
## Upstream gate: **PASS**
## Internal gate: N/A (dev-checked)

Run by sq27 on 2026-10-20 (harness task `gate-witchs-potion-goblin-diplomacy`; Goblin Diplomacy only, Witch's
Potion is a later run). The branch is upstream draft #367's head 5398c6087 + merge f81121fa7 (tree = upstream
`main` 829b9477d + the quest, 4f5e59627's tree) + d77ac2b76 (RSProx fixes), so it fast-forwards #367. Net diff
against `main` (829b9477d, still the tip): `GoblinDiplomacy.kt` (+749), the deletion of #328's
`GoblinGenerals.kt` and `Grubfoot.kt`, and one line in the quest pack's `gamevals.toml`
(`gobdip_dye_goblin_mail = 2392`). `GoblinCaveLadders.kt` is untouched (the ladder is #373). Spec
`accuracy/goblin-diplomacy.md` and text `pr-texts/goblin-diplomacy.{title,upstream.body,comment}` read at sd25's
docs commit ce5e5c8bb (not yet on the fork's `docs` branch; see Notes). This run re-checks the previous FAIL
(4f5e59627, below) at the new commit.

### Build
- Fork CI at d77ac2b76 (headSha checked): `CI` run 39353936705 success (freshCache/buildCache from cached game
  data, generateRsa, `assemble test integration`, boot step: "Server ready in 15.45s"), `Formatting` run
  39353938933 success.
- `python3 tools/scripts/fix_gameval_conflicts.py --check`: "No gameval conflicts or unassigned IDs found." on
  the branch and on upstream `main`. Synth 2392 has no name in the cache's gamevals (`.data/gamevals/synth.rscm`,
  osrs-mcp `gameval_search`), and no other `gamevals.toml` names it; `synth.pick` = 2581 already exists.
- Own run tree (`qa.sh clone gd d77ac2b76` + `gate-run`: cache, generateRsa, installDist, all rc=0) booted as
  `qa-or-server` on 43596: "Server ready in 14.1s", no Exception/ERROR line in the whole session.

### Scope
- One quest. `gh pr list --search goblin` (open): #367 (this PR, to be fast-forwarded), #373 (lookout ladder:
  only `GoblinCaveLadders.kt`; `git merge-tree` with d77ac2b76 is clean), #310 (Thieving: conflicts with `main`
  itself in unrelated files, none of these). No `Depends:` (none needed).
- Moving #328's lines: a script compared every string literal in the two deleted files with the quest file:
  all 40 are present verbatim (generals' four conversations, Grubfoot's five complaints, "Shut up Grubfoot!").
  The PR bullet now says "moved verbatim" and credits "#328 (Add Goblin Village)" (previous item 3: fixed).

### Accuracy (independent re-check)
- osrs-mcp `wiki_page` (all untruncated): Goblin Diplomacy, Transcript:Goblin Diplomacy, Transcript:Grubfoot,
  Transcript:General Wartface, Transcript:General Bentnoze, Goblin mail, Orange dye. Every in-scope transcript line
  is in the code verbatim with the same speaker, and every menu matches in text and order, except: the empty crate
  ("but but" on the wiki, one "but" in code, which follows the recording below) and the extra talk option
  "I have some <colour> armour here." (a listed guess). Code lines in no transcript are the spec's guesses
  (rows 8, 10, 12, 18, 19). Rewards on the wiki: 5 QP, 200 Crafting XP, a gold bar.
- Ids (osrs-mcp `gameval_search`): `gobdip_main` 2378; goblin mail 288, orange 286, darkblue 287, red 9054,
  yellow 9056, green 9057, purple 9058, pink 9059, black 9055.
- RSProx, read in the local transcripts at the cited ticks:
  - 3243 (rev 233.1) tick 3390: `gobdip_crate2_searched` 0->1, goblin_armour (288) into the inventory,
    `synth_sound` 2581, objectbox 193 "You find some goblin mail in the crate." (plus the mesbox chat line); tick
    3393: oploc1 16560 at (2951,3508), game message "You search the crate, but find nothing of interest.", no sound.
  - 1694 (rev 226.1) tick 1001: `if_buttont` obj 4622 (`golem_ink`) on obj 288, slot 9 -> 9055, slot 17 emptied,
    `synth_sound` 2392, "You dye the goblin mail black."; ticks 1724/1755 yellow and blue, same synth, no anim.
  The branch matches both (d77ac2b76). The spec's RSProx section has these facts (DoD: recorded in the spec).
- Gaps found, each covered by the PR's notes (guesses / not included): see the recommendation below on re-dyeing.

### In game (desktop RuneLite, fresh account `sq27gd` on the branch server, desktop lock 23:53-00:08 EDT)
Read through the desktop devtools data tools (`get_dialogue`, `get_var`, `get_inventory`, `get_sound_history`,
`get_chat_history`, `get_xp_history`, `dump_interface`). Evidence and driver scripts:
`re/evidence/gate-witchs-potion-goblin-diplomacy/sq27-d77ac2b76/` (chat, vars, sounds, inv, xp, server.log).
Cheats: `::tele` (into the generals' hut and next to the crates), `::item` for red, yellow and blue dye, the third
goblin mail (its crate needs #373), and red goblin mail for the wrong-colour check.
- Stage 0, Wartface: the "stupid general" opener verbatim, the four-option topic menu; "Why are you arguing..."
  then the menu's first entry is "What is the goblin new century?"; pick colour -> "What about a different
  colour?" -> Yes/No prompt -> Yes: start lines verbatim, 2378 0 -> 3; the D-Y-E help loop verbatim, "Okay, I'll be
  back soon." ends it.
- Grubfoot at stage 3: "Grubfoot wear red armour! ..." / "Why they not make up their minds?" / Bentnoze "Shut up
  Grubfoot!".
- Crate 16560 (2951,3508): objbox with item 288 "You find some goblin mail in the crate." (and the MESBOX chat
  line), sound 2581, 2380 = 1; again: "You search the crate, but find nothing of interest.", no sound. Crate 16559
  (2959,3514, searched from the north): same, 2379 = 1, sound 2581.
- Red dye on yellow dye: "You mix the two dyes and make orange dye.", both gone, orange dye added, no sound. Orange
  dye on mail: "You dye the goblin mail orange.", 286; mail on blue dye (reverse order): "...blue.", 287; sound
  2392 each.
- Orange mail used on Bentnoze: "I have some orange armour here." and the try-on verbatim, 2378 = 4, 13594 = 1,
  Grubfoot shows as 672 and gives his orange lines. Talk menu shows "I have some blue armour here." (Quest Helper
  adds a "[4]" prefix): try-on verbatim, 2378 = 5, 13594 = 2, Grubfoot's blue lines. Red goblin mail used on
  Wartface at stage 5: "What do you think of this colour?" / "That wrong colour." / "We tell you get brown
  armour.", mail kept.
- Plain mail used on Bentnoze: final lines verbatim, "Congratulations, you've completed a quest: Goblin
  Diplomacy", interface 153 ("200 Crafting XP", "5 Quest Point", "A gold bar", "Total Quest Points: 5"), 2378 = 6,
  13594 = 0 (Grubfoot 671), varp 101 = 5, gold bar in the inventory, mail gone, Crafting +30,000 (200 x the dev
  realm's rate 150).
- After the quest: Grubfoot "Grubfoot do this! ..." + "Shut up Grubfoot!"; Bentnoze's weapons conversation;
  red mail used on Wartface: "Nothing interesting happens.".
- Not tried: other openers, the "fat" menu, peace, red/green picks, declining the start, other dyes and mixes, full
  inventory at a crate, the third crate. The start prompt's title text isn't exposed by `get_dialogue` (Yes/No seen).

### PR text (pr-text-review, ce5e5c8bb)
- Title `feat: Goblin Diplomacy quest` matches the head commit style (CONTRIBUTING `type: summary`); every template
  section present; Type of change (New content, Cache/gamevals) right; checklist boxes true, the two unticked ones
  explained (no tests: same call as on #366; quirks).
- Previous item 1 fixed: "How it was tested" gives the fork CI at the head (freshCache, buildCache, generateRsa,
  `assemble test integration`, boot, Formatting, `--check`), the in-game run, the cheats by name and what wasn't
  tried. Every in-game claim in it was reproduced at d77ac2b76 above.
- RSProx claims (3243 rev 233, 1694 rev 226; wording, synths 2581 and 2392, no anim) checked at the source; only
  ids and packet facts are cited, per the reference's rule.
- "plain goblin mail also drops from goblins outside the village": true on `main` (`goblin_drop_table1`,
  `GoblinDropTable`).

### reverse
n/a (no client protocol claim).

### Recommended (not blocking)
- "Not included: re-dyeing already dyed mail ... (both shown in a recording made during Land of the Goblins)" is
  true, but reads as if re-dyeing were Land of the Goblins content. The Goblin Diplomacy walkthrough itself says
  "dying Yellow Goblin Mail with red dye makes Red Goblin Mail (and vice versa)" and that the village goblins' red
  or green mail drops "can be dyed". Suggest: "re-dyeing already dyed goblin mail (the walkthrough mentions it;
  RSProx 1694 shows it), so the village goblins' red/green mail can't be dyed for the quest; plain mail from other
  goblins can". The spec's "belong to Land of the Goblins" for re-dyeing should say the same.
- The objbox uses the default item zoom; the official objectbox sent zoom 400 (3243). Cosmetic.

### Notes
- sd25's docs commit ce5e5c8bb (spec RSProx section, the PR text and comment) is not on the fork's `docs` branch
  yet; it should be pushed before #367 is updated so the record's sources are published.

---

# Earlier runs (history)

### Run at 4f5e59627 (content/goblin-diplomacy-829)
- Branch: `content/goblin-diplomacy-829`
- Commit: `4f5e59627a1e47bf7d601c523b0a0a4f715f658e`
- Internal gate then: PASS
- Upstream gate then: FAIL

Run by sq25 (companion to sd24) on 2026-10-19. One commit on upstream `main` 829b9477d (the current tip), so the
gate tree is the commit itself (fresh `git clone --shared` `re/agents/sq25/g-gd`, `origin/main` = 829b9477d).
Spec `accuracy/goblin-diplomacy.md` and text `pr-texts/goblin-diplomacy.*` at fork docs 0fd090018. It would
replace the contents of upstream draft #367 only on the user's OK; the lookout ladder is a separate fix branch.

### Internal gate details (PASS)
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

### Upstream gate details (FAIL)
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
