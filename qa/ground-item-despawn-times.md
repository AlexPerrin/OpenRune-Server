# QA: fix/ground-item-despawn-times
- Branch: `fix/ground-item-despawn-times`
- Commit: `7e3e0dc2b` (gated with upstream main 5c4c9c106 merged: scratch merge b1b8997e6, fork branch gate/ground-item-despawn-times)
## Upstream gate: **PASS**
## Internal gate: N/A (dev-checked)

Upstream gate by sq54 on 2026-10-24. History: sq45 PASSED d0fe09c63 (in game); sq48 FAILED scope; sq49 FAILED 5ae6a3aeb on spotless.

## Scope
Net diff vs merge base e85278c9a (`git merge-base 7e3e0dc2b upstream/main`): 8 files, +108/-27, no test file, no build file:
PlayerZoneUpdateProcessor, HeldInteractions, PlayerDropTimers (new), ObjRegistry, ZoneUpdateMap, ZoneUpdateTransformer, ObjRepository, Obj.kt.
`git diff --stat upstream/main b1b8997e6` is the same 8 files (+108/-27): the merge is clean. One change.
Whitespace/import hunks (`?: return` spacing in HeldInteractions, import order in ObjRepository, a blank line in Obj.kt) judged by the
formatter: `./gradlew :api:game-process:spotlessCheck :api:player:spotlessCheck :api:registry:spotlessCheck :api:repo:spotlessCheck
:engine:game:spotlessCheck` on the merge: rc=0, clean. They are what spotless requires, not stray edits.

## Build / CI
Fork CI run 40142485323 (CI) on b1b8997e6: completed success. Formatting run 40142514802: completed success.
(Cancelled run 40142438077 was a mistaken bare tip: ignored.)

## Accuracy
Spec accuracy/ground-item-despawn-times.md (docs 5297c65bc) vs wiki Drop page, as re-read by sq45 on this logic (same main code):
tradeable public at 60 s, gone 120 s later (100/300), untradeable 180 s (300, never public), clue scrolls 61 min (6100). The code
(PlayerDropTimers: 100/300/6100, stockmarket flag, clue name regex + Mimic) matches the spec table. Spec Tests section is true
(no unit tests; in-game checks recorded).

## In game
Not re-run. `git diff d0fe09c63 7e3e0dc2b --stat` is 4 files: PlayerDropTimersTest, ObjRegistrySplitOwnerTest, ObjAddTimersTest (removed)
and the `game-cache-test-conventions` plugin line in api/registry/build.gradle.kts (removed with the tests). No main-source file differs, so
no logic change; sq45's in-game PASS at d0fe09c63 (bronze axe 100/300, trowel 300 never public, clue 6100, delete at +299) stands.

## PR text review
pr-texts/ground-item-despawn-times.upstream.body at cc24ebd8c: "Three gaps" and three bullets (drop timers, OBJ_ADD timers, owner copy);
no test bullets or test claims remain; the How it was tested section states only the in-game run; checklist has no tests line. Every claim
(constants, stockmarket flag, lootDropDuration override, ObjRepository/ZoneUpdateTransformer behaviour, singleCopy/copy ownerId) is
true of the diff. CONTRIBUTING sections present, scope one change.

## Verdict: **PASS**
