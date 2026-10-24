# QA: fix/goblin-diplomacy-handin-367 (for content/goblin-diplomacy, #367)
- Branch: `fix/goblin-diplomacy-handin-367`
- Commit: `405e38601278b51a9dc38d75ff6315d2f856c90f`
## Upstream gate: **PASS**

Run by sq51 on 2026-10-24. All checks run to the end; findings collected, none open.

### Findings
None blocking.
- Formatting: the upstream Formatting workflow (format.yml, `spotlessCheck`, ratchet from main; fork main equals upstream main 5c4c9c106) dispatched on the scratch branch: run 40142381815 success, nothing to format. `fix_gameval_conflicts.py --check` run locally on the scratch tree: clean.
- Must be updated when the head moves: the PR text's CI line ("On the branch head (current `main` 829b9477d + the quest), the fork's GitHub Actions run ...") describes an earlier head; restate it for the pushed head and the current main when the update is posted.

### Checks
1. Source review (d77ac2b76..405e38601, GoblinDiplomacy.kt only): the brown try-on page passes `GRUBFOOT_BROWN_HEAD` explicitly; orange and blue commit (`commitHandIn`: `invDel`, look, stage) right after "I have some ... armour here."; brown commits armour and `completeQuest` together after the last page; a failed brown completion re-adds the armour and shows the room message; a failed `invDel` returns with no change.
2. Scope: this commit changes one file (+36/-11). Net diff vs merge base 829b9477d is the quest script, the removal of #328's GoblinGenerals.kt and Grubfoot.kt and one gamevals.toml line (the PR's earlier content). No test files. No comment lines added (grep of the diff and of the whole file: none). AGENTS.md checked: state in varbit (`intVarBit`), gamevals symbols, SCREAMING_SNAKE constants, no tests for dialogue wiring, no comments. CONTRIBUTING.md (Alter text) adds nothing further.
3. CI on the fork, scratch branch `gate/goblin-diplomacy-367` = 405e38601 + upstream main 5c4c9c106 (82eda6054), merged cleanly: run 40141034687 success (cache, compile and tests, boot to ready). Own build of the same tree with `qa.sh gate-run`: server ready in 9s.
4. PR text and spec (docs pr-texts/goblin-diplomacy.upstream.body, .comment, accuracy/goblin-diplomacy.md row 14): atomic hand-in, brown refund and the room-message guess are stated and true of the commit; the PR-text description of CI refers to the earlier head, to be re-stated by the dev when the update is pushed.
5. In game (branch server on 82eda6054, own desktop client, fresh account; cheats `::varbit gobdip_main 3`, `::item`, `::tele`), chathead model ids 671 brown, 672 orange, 673 blue:
   - Orange: armour held and stage 3 until the "Grubfoot!" page; there stage 4, look 1, armour gone. Walking away on that page leaves both changed.
   - Blue: same, stage 5, look 2. Grubfoot try-on head 673 (blue).
   - Brown: stage 5, look 2, armour held through the last page; walking away on the try-on page and on "Thanks... I think..." changed nothing. The try-on "What do you think?" showed head 671 (brown). After the last page: stage 6, look 0, gold bar, armour gone, quest complete.
   - Not played: the brown completion failure (a full inventory cannot trigger it, since the armour slot frees room for the bar); source-reviewed only.
   Evidence: re/evidence/goblin-diplomacy-review-367/sq51-ingame.txt.
