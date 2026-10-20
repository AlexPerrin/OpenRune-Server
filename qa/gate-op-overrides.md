# QA: fin/op-overrides
- Branch: `fin/op-overrides`
- Commit: `46bc3ab31d147910709f4c94253add75ef5f2cb6`
## Upstream gate: **FAIL**
## Internal gate: N/A (dev-checked)

Date: 2026-10-19. QA: sq26. Upstream `main` at the check: `829b9477d` (Add Doric's Quest (#334)).
PR text checked: `openrune/upstream/pr-texts/op-overrides.{title,upstream.body}` (osrs-plugins-reverse),
spec: `openrune/upstream/accuracy/op-overrides.md`, row `content/op-overrides` in `UPSTREAM-PRS.md`.

## Accuracy: pass (code), caveat gap noted below
Engine API, no OSRS mechanic. Re-read the diff myself (`LocInteractions.opTrigger`,
`NpcInteractions.opTrigger`, `LocOpOverrides`, `NpcOpOverrides`, `LocOverrideEvents`, `NpcOverrideEvents`):
with no claims registered the chain is unchanged (one HashMap miss); a claim without a registered
`*OverrideEvents.Op` handler falls through (`eventBus.contains`); `apTrigger` is not consulted, as the spec says.
The spec's one gap (Ap path) is in the commit message but not in the PR text's Caveats (see PR text).

## In-game, build/CI, reverse, gameval check: not run
Stopped at the scope failure below; any fix needs a new commit, which voids these anyway.
For the record: the fork has no GitHub Actions run at `46bc3ab31` (`gh run list --commit` empty), and
`UPSTREAM-PRS.md` says no gate run passed at this commit either.

## Scope: FAIL
1. **Not on current upstream `main`.** Based on `c2492318d`; upstream `main` is `829b9477d`, 10 commits
   ahead (#334, #315, #333, #348, #369, #297, #312, #361, #359, #358). `git merge-tree` with `main` is clean,
   but the gate requires the branch rebased on current `main`.
2. **No user upstream any more.** The PR's stated motivation and only user is our Doric's Quest (anvils).
   Upstream merged its own Doric's Quest in #334, which gates Doric's anvils inside the smithing script
   (`content/skills/smithing/.../AnvilSmithingScript.kt:30,52,58` calls `DoricsQuest.mayUseAnvil`) without any
   override API. On current `main` both `LocOpOverrides` and `NpcOpOverrides` would ship with no caller and no
   test (QA.md 1: "New behaviour has tests").
3. **Stacking.** The text says Doric's Quest is "its own PR, stacked on this one" and relies on that PR's
   `DoricsQuestInteractionTest` for coverage; upstream PRs must merge on their own (no stacked PRs).

## PR text: FAIL (would also fail pr-text-review)
- Claims base `main` (`1be5dfb5f`); the commit is on `c2492318d`.
- Does not use upstream's PR template (added in #369: Summary / Type of change / How it was tested /
  Checklist / Screenshots-notes); the old CONTRIBUTING-style sections are used instead.
- Caveats omit the Ap-path gap that the accuracy spec and the commit message list.
- Build results quoted are from `1be5dfb5f`, not this commit.

## What must change (to the dev, through main)
Decide with the orchestrator/user whether this PR still has a purpose now that upstream's #334 solved the
anvil case another way. If it stays:
- rebase onto current upstream `main` (new commit);
- add a real user or a dedicated test in this PR (e.g. an integration test registering a loc and an npc claim
  and asserting the override event fires only for the claimed player, and that an unclaimed op / a claim with
  no handler falls through), so it merges on its own;
- rewrite the PR text on upstream's #369 template, with the correct base, no stacking claim, the Ap-path caveat
  and build/CI results at the new commit;
- push so fork CI runs at the new commit; then request the gate again.
Otherwise drop the branch and the `UPSTREAM-PRS.md` row (and the "Doric's Quest needs op-overrides" merge-order line).
