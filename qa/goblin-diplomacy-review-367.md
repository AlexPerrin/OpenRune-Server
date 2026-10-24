# QA: fix/goblin-diplomacy-handin-367 (for content/goblin-diplomacy, #367)
- Branch: `fix/goblin-diplomacy-handin-367`
- Commit: `1a610f8e42e80ddb9f47c8a88fe7e2e686ae3f99`
## Upstream gate: **FAIL**

Run by sq50 on 2026-10-24. Stopped at the first failing item (the gate stops at the first failure); a new commit voids the CI and in-game checks anyway, so they were not run.

### Must change
1. AGENTS.md "Comments - less is more" violated by the commit. It adds a KDoc on `commitHandIn` (a ~25 line method; KDoc only for methods that are genuinely complex AND large), a mid-method comment in `handIn` ("Orange and blue commit before the generals react; brown commits after the last page...") and one in the brown branch ("The look is written by the commit below, so the brown head is explicit here."), the last being narration about the change. Remove all three (names carry the meaning). The #367 PR comment also says "The KDoc is removed and the helpers are private", which the commit would contradict.

### Checked, fine (by source review of d77ac2b76..1a610f8e4, one file)
- Head regression fixed: the brown try-on page calls `grubfoot(quiz, "What do you think?", GRUBFOOT_BROWN_HEAD)`; the other brown pages use no Grubfoot line; orange and blue pages still read the look, written by the commit before them, so their heads are unchanged.
- Orange and blue commit right after "I have some ... armour here."; `invDel` then look and stage are written with no dialogue page between. Brown commits armour (`invDel`) and `completeQuest` in one call after the last page; walking away earlier leaves both untouched, no page lies between the two writes. A failed completion re-adds the armour and shows the room message; failure of `invDel` returns early without changes.
- Scope: the 3-file removal and gamevals line are the PR's earlier content; this fix is `GoblinDiplomacy.kt` only; no test files anywhere in the net diff against the merge base 829b9477d.
- PR text (docs e3e64719a): atomic hand-in and brown refund stated, the room message listed under Guesses.

### Not run (stopped at first failure)
CI on a scratch merge with upstream main, in-game orange/blue/brown hand-ins. Re-run both on the corrected commit.
