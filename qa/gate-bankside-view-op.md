# QA: fix/74-bankside-view-op
- Branch: `fix/74-bankside-view-op`
- Commit: `a86998e235e16fa332af0b6e7fc461a8aa1c4ee0`
## Upstream gate: **FAIL**

Checked by sq28 on 2026-10-20 (fork `fix/74-bankside-view-op` re-fetched: tip a86998e23 = 5015d98ef + a
spotless commit). Stopped at the first failure (scope/base), so no build, accuracy or in-game run was done at
this commit; the earlier incomplete record (openrune/upstream/qa/bankside-view-op.md, 5015d98ef) stays void.

## Scope and base: FAIL (stale)
- Merge base with upstream `main` is `c2492318d`; upstream `main` is now `829b9477d`, 16 commits ahead.
  The gate requires the branch based on current `upstream/main`.
- Not superseded: on upstream `main` 829b9477d `BankInvScript.sideInvOp` still has no `Op1` case (the
  branch diff against it is only the Op1 early return plus one spotless space), and
  `gh pr list -R OpenRune/OpenRune-Server --state all` finds no PR for "bankside" / "Invalid main inv op".
  None of the 16 upstream commits touch the bank module; `git merge-tree` of the branch onto 829b9477d is clean.
- Diff is one file (`content/interfaces/bank/.../BankInvScript.kt`, +5/-1): one fix, nothing unrelated.
- No accuracy spec (`accuracy/*bank*`) or PR text (`pr-texts/*bank*`) is on the fork `docs` branch for this
  fix; the old text lives only in `batch-3-texts/bankside-view-op.{title,body}`.

## What must change
1. Rebase `fix/74-bankside-view-op` onto upstream `main` 829b9477d (or the tip at that time); squash the
   spotless commit into the fix if upstream wants one commit; push, get fork CI green at the new sha.
2. Put the accuracy spec at `accuracy/bankside-view-op.md` (client sources: `bankside_drawitem`,
   `bankside_build`, `bankside_switchview` op 1; the disconnect on main) and the PR text at
   `pr-texts/bankside-view-op.md` on the fork `docs` branch, with every template section and a `Depends:` line.
3. Hand over again; the gate re-runs in full at the new commit (CI, boot, accuracy re-check, in-game
   "Configure" on a rune pouch / "View" on a looting bag in the bankside on main vs the branch).
