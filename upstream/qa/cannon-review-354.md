# QA: content/cannon
- Branch: `content/cannon` (upstream PR #354 head)
- Commit: `60cffedfbcde06c541f165a8786d66c57121c93c`
## Upstream gate: **FAIL**

Date 2026-10-24, QA agent sq46.

## What must change (the only blocker)
The PR body is stale against the review fix. Update it so every claim is true of 60cffedfb:
- Summary, "Nulodion" bullets: "lost, with fewer than 4 free slots: ..." and "lost: the four parts" are now "as many parts as the saved stage (`dropcannon`)": stage k returns parts 1..k, and the free-space check needs k slots (not 4). The wiki's Transcript:Nulodion says "fewer than 4"; the stage-based count is our divergence and goes in the caveats.
- Caveats: "Nulodion gives all four parts even when only part of the cannon was set up." is now false. Replace with the new behaviour (wiki is silent on partial cannons: Transcript:Nulodion and Dwarf multicannon give no rule for them).
- `accuracy/cannon.md` (D2 row, "gives four parts even for a part-built cannon") needs the same update.
- Tests line: NulodionCannonSaleTest "replaced with four parts" is true only for a full cannon; say so (or state that partial reclaim was checked in game).
Nothing in the code needs to change.

## Section results
- Review fix 1 (Mark7625): PASS. `DwarfMulticannon.kt` buildStages: `while (cannon.stage < 4)` is now `repeat(PARTS.size - cannon.stage)`; the `return`s are non-local returns from the inline `repeat`, behaviour unchanged. In game: the four stages were built in order.
- Review fix 2 (eaglepurerocketgrape P1): PASS. `Nulodion.kt` replaceLostCannon gives `CANNON_SET.take(4).take(player.cannonStage)` (PARTS order = twpart1..4 = base, stand, barrels, furnace, so stage k = the first k parts); the free-space check uses `player.cannonStage`; `LOST_CANNON_SLOTS` removed. Diff vs 9c08b42ca is these two files only (+4/-4).
- CI: PASS. `gh pr view 354`: head 60cffedfb, checks `build & boot`, `gameval-conflicts`, `spotless (advisory)` all SUCCESS at that commit (fork dispatch CI ignored as instructed).
- Scope: PASS with a note. Net diff vs merge base 829b9477d: the cannon only (Nulodion.kt, new module dwarf-multicannon, gamevals.toml +2, two test files). The review fix commits add NO test files (60cffedfb drops the one a2202478b had added). The PR's own original tests (DwarfMulticannonTest 15, NulodionCannonSaleTest +1) remain in the diff from the gated bc243d141 and the PR text justifies them; the maintainer has declined tests for content like this (#348), so the console may want to decide whether to drop them. Merge base is behind upstream/main (5c4c9c106); a local merge of main + the branch is clean and builds. No other open upstream PR for the cannon (`gh pr list --search cannon`: only #354 open). `fix_gameval_conflicts.py --check`: clean.
- Accuracy: PASS for the change under review. Re-read against the wiki (Transcript:Nulodion, Dwarf multicannon, via WebFetch; the osrs-mcp tools were not available in this run): the branches (still set up / <4 slots / replace / not stolen) and the decayed reclaim match; the wiki gives no rule for partial cannons, so the stage-based return is a review-driven divergence that must be a stated caveat (see above). Spec file read: accuracy/cannon.md (orch clone).
- Build/boot: PASS. Branch merged on current upstream/main (66164d68b in the QA run tree) built with `qa.sh gate-run` (compile, cache, installDist rc=0); server booted ("Server ready in 8.26s" and again after the restart).
- In-game: PASS. Branch server on 43646/8146, own desktop2 RuneLite client (devtools port 7781), four fresh members accounts. Setup stage order and timing (account sq46c4, ticks): base 381, stand 383, barrels 386, furnace 389 ("You place the cannon base on the ground.", "You add the stand.", "You add the barrels.", "You add the furnace."); varp 2 (`dropcannon`) went 1, 2, 3, 4 on the other accounts. Each account built to a different stage, logged out, the server was restarted (cannons are in memory, so each counts as lost on login), then "I've lost my cannon." to Nulodion (3011,3453): 

  | stage (varp 2) | parts received | varp 2 after |
  |---|---|---|
  | 1 | Cannon base | 0 |
  | 2 | base, stand | 0 |
  | 3 | base, stand, barrels | 0 |
  | 4 (full) | base, stand, barrels, furnace | 0 |

  No duplicated or extra parts at any stage. Not exercised in game: the free-space refusal at stage k (covered by reading the code only).
- Cleanup: client stopped (target restored), qa-or-server and qa-or-http removed.

## Verdict: **FAIL** (PR text only; re-gate after the body and accuracy spec are updated, a new commit is only needed for accuracy/cannon.md if it lives on the branch)
