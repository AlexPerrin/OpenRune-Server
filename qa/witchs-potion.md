# QA: content/witchs-potion-829
- Branch: `content/witchs-potion-829`
- Commit: `4dc1ac9c71e1614658e9b689b599d9f925b4d745`
## Internal gate: **PASS**
## Upstream gate: **FAIL**

Run by sq25 (companion to sd24) on 2026-10-19. One commit on upstream `main` 829b9477d (the current tip,
re-fetched), so the gate tree is the commit itself (fresh `git clone --shared` `re/agents/sq25/g-wp`, `origin/main`
= 829b9477d for the ratchet). Spec `accuracy/witchs-potion.md` and text `pr-texts/witchs-potion.*` at fork docs
a3e80a809. It would replace the contents of upstream draft #368 (stacked on #366's old commit) only on the user's
OK.

## Internal gate details (PASS)
### build-test
`openrune/upstream/qa/qa.sh gate` (`GATE_VOL=sq25-gradle`; remote build cache for compiles, tests never cached).
Logs `re/agents/sq25/g-wp.logs/`.

| Step | Result | Time |
|---|---|---|
| `clean :or-cache:qaCacheToolsClasspath` | pass | 31s |
| `:or-cache:freshCache :or-cache:mergePluginGamevals` | pass | 85s |
| `:or-cache:buildCache` (packs `witchs_potion_vars.toml`) | pass | 43s |
| `generateRsa` | pass | 21s |
| `:server:app:installDist` | pass | 33s |
| `spotlessCheck` | pass | 23s |
| `assemble` | pass | 81s |
| `test` | pass, 764 tests, 0 failures | 118s |
| `integration` | pass, 79 tests, 0 failures | 183s |
| boot (copy of the install + `.data` in `g-wp-run`, `sq25-or-server` 43597) | "Server ready in 14.7s" (the dbrow startup check passes), no Exception/ERROR line in the whole session | |
| `fix_gameval_conflicts.py --check` | "No gameval conflicts or unassigned IDs found." | |

### accuracy-check
Sources re-read on 2026-10-19 (osrs-mcp was disconnected, so the wiki's raw pages were read directly: Witch's
Potion, Transcript:Witch's Potion, Rat's tail, Rat, Dungeon rat, Giant rat):
- Rewards: wiki "325 Magic experience", 1 QP: matches (`rewards { xp(magic, 325) }`, QP from the dbrow).
- Rat list: wiki ids Rat 2854, 2855 and Stronghold of Security 2492, 2513; Dungeon rat 3607-3609 and 2865-2867;
  Giant rat level 26 2510-2512 (levels 3/6 have no tail line). The 13 `npc.*` symbols in `WitchsPotionRatTail`
  resolve (gamevals.dat) to exactly those ids; `rat_lowwander` 13624 is not among them (spec guess). Matches.
- Rat's tail: "100% chance to drop once you begin the quest"; "unobtainable" after completion: matches.
- Dialogue: every transcript line compared with the code and, where reached, with what the client showed
  (below): opening, witch branch, offer, start prompt "Start the Witch's Potion quest?", decline, accept,
  none-of-the-items, the 14 partial lines (the code's four parts compose exactly the transcript's 14 sentences),
  the hand-in, stage 2, cauldron before and the drinking box. All verbatim.
- Hetty after the quest (Transcript:Hetty, per the spec) and the cauldron after the quest: as the spec; the latter
  is the spec's guess, listed in the PR.
- No divergence found beyond the spec's guesses (rows 12, 13; journal, quest scroll, chatheads), all in the PR.

### In game (desktop RuneLite, fresh account `sq25wp` on the branch server, desktop lock 19:35-19:43)
Cheats used, all for things the branch doesn't implement: `::tele` for travel (Lumbridge to Hetty's house, and
into the neighbouring house where the `rat_indoors` spawn, which isn't reachable from Hetty's room), `::item` for
eye of newt, onion and burnt meat (shop/cooking content outside the branch). The rat's tail came from killing a
rat (the branch's hook).
- Before the quest: a Lumbridge rat (2854) dropped bones only.
- Hetty: opening line; "I've heard that you are a witch." branch with both Hetty lines, then "I am in search of a
  quest." -> offer -> "Start the Witch's Potion quest?" Yes/No; No -> "No, I have my principles and honour." /
  "Suit yourself, but you're missing out.", varp 67 = 0; again with Yes -> the four start lines, varp 67 = 1.
- Cauldron at stage 1: "As nice as that looks, I think I'll give it a miss for now."
- No ingredients: "I'm afraid I don't have any of them yet." + Hetty's reminder, verbatim.
- Killed a Rimmington rat (2855): a Rat's tail (300) on the ground next to bones; picked up.
- Tail only: "I have the rat's tail (ewww), I don't have any burnt meat, I don't have an onion, and I don't have an
  eye of newt." / "Great, but I'll need the other ingredients as well."
- All four: "In fact I have everything!" / "Excellent, can I have them then?" / the one message box / "Well, is it
  ready?" / "Ok, now drink from the cauldron."; inventory went from the four items to empty; varp 67 = 2.
- Stage 2 Hetty: "Well are you going to drink the potion or not?" / "Yes, I will."
- Cauldron: the drinking message box, quest complete interface (153), varp 67 = 3, quest points (varp 101) = 1,
  Magic XP +48,750 = 325 x the dev realm's `baseXpRate` 150 (upstream's reward path for every quest, not this
  branch).
- After: Hetty's "How's your magic coming along?" lines; cauldron line as before; a rat (2855) dropped bones only.
- Not tried in game: the tail at stage 2 (spec guess, row 12), the other 13 partial lines (code composes them),
  the other rat types.

### RSProx recordings
None available for Witch's Potion yet ("main", 2026-10-19). The comparison (dialogue, animations, varps per
stage, rewards) is still to do before upstream; see the upstream items.

### reverse
n/a.

## Upstream gate details (FAIL)
Passes:
- Scope: one quest (quest script, its varbit, the kill-hook binding, the `api.death` dependency, Hetty's lines
  moved out of `RimmingtonTownsfolkScript`). Nothing unrelated, none of our docs.
- Overlap: `gh pr list --search witch`: only #368 (ours, to be replaced). Open PRs touching the same files:
  #366/#367/#299/#298 add lines to `content/quest/pack/.../gamevals.toml` (and #298 to `QuestModule.kt`):
  textual neighbours only, no shared content.
- Ids: `--check` clean on the branch. Varbit 65449 is also used by `sheep_shearer_progress` in our open upstream
  PR #366 and on our integration tree; whichever merges second gets reassigned by upstream's script/CI.
- AGENTS.md: no new KDoc; one one-line constraint comment on the hook ("A kill hook, not a quest-gated drop:
  assume-completed mode never passes an in-progress gate."), the kind AGENTS.md allows. State in a varbit, not
  attrs. No test: the hand-in moves items, which AGENTS.md says should have a test, but the maintainer asked
  for #366's quest tests (hand-in included) to be removed ("These tests can be removed"), and the PR says so;
  acceptable, but a reviewer may still ask for a hand-in test.
- Template sections all present; Type of change (New content, Cache/gamevals) right; checklist boxes true, the
  rsprox box unticked with the reason; Data sources (CC BY-NC-SA) and release note in the notes; title `feat:`
  matches the commit.

What must change before upstream:
1. **"How it was tested" is out of date.** Replace the dev-build line and "Not yet checked in game from this exact
   branch" with this record: the full gate (test 764, integration 79, boot, `--check`) and the in-game run
   above, saying that `::tele` (travel) and `::item` (eye of newt, onion, burnt meat) were used and the rat's tail
   came from a kill.
2. **RSProx comparison.** The user wants the quest checked against RSProx recordings of the official game before
   upstream (accuracy-check skill, Sources item 3). No recording exists yet; when one does, compare and record it
   here, and add any divergence to the PR's notes. Until then the upstream gate can't pass.
3. Recommended (not blocking): one line in the notes that varbit 65449 is also taken by #366's
   `sheep_shearer_progress`, so the second to merge needs the id re-run (as #362's text says for its ids).

Re-check after 1 (text) and 2 (recordings): the upstream gate only, same commit.

## Upstream re-check of the text at docs d802709e1 (same commit): still FAIL (RSProx pending)
Checked by sq25 on 2026-10-19 against this record and 4dc1ac9c7:
- Item 1 done: "How it was tested" now gives this gate (764 / 79, boot with no error, `--check`) and the in-game
  run with the cheats (`::tele` for travel, `::item` for eye of newt, onion and burnt meat; the tail from a kill),
  each bullet matching the In-game section above; the untried items (tail at stage 2, the other 13 partial lines,
  the other rat types) are stated. "Every line shown matched the wiki transcript word for word": true of what I
  compared.
- Item 3 done: the notes say varbit 65449 is shared with #366's `sheep_shearer_progress`.
- One wording fix, to bundle with the next change: "a clean run per step" over-reads my run. It was one `clean`
  first, then each step as its own `./gradlew` run, with compiles of unchanged modules from the shared build cache
  (tests never cached). Suggest: "a clean build, each step its own run (compiles from the build cache, every
  test run)".
- Item 2 open: the RSProx comparison (dialogue, animations, varps per stage, rewards) waits for the transcript
  from the transcription agent. The upstream gate stays FAIL until it is done and recorded here.
