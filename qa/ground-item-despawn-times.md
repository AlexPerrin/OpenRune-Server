# QA: fix/ground-item-despawn-times
- Branch: `fix/ground-item-despawn-times`
- Commit: `5ae6a3aebae311e4dc336acd974ee1f1a44039fa`
## Upstream gate: **FAIL**
## Internal gate: N/A (dev-checked)

Gate by sq49, 2026-10-24, at 5ae6a3aeb (previous: sq45 PASS at d0fe09c63, sq48 FAIL at 83a64ba7e on scope).

## Why it fails: Formatting (spotlessCheck)
Branch merged with current upstream main 5c4c9c106 (clean merge, 130430a0d in my scratch clone; diff vs upstream main is the same
8 files, 106+/24-, no test file). `spotlessCheck` (`ratchetFrom origin/main`, set to 5c4c9c106) fails:
`:api:repo:spotlessKotlinCheck`, `api/repo/src/main/kotlin/org/rsmod/api/repo/obj/ObjRepository.kt`: the import
`java.util.concurrent.ConcurrentLinkedQueue` must be sorted into the block (after `jakarta.inject.Inject`), not at the end.
That is the "stray import-reorder hunk" sq48 asked to revert; it is not stray: the file is touched by the change, so the
ratchet checks it and the unsorted import fails the Formatting workflow (it was green at d0fe09c63 with the move in place).
Reverting it in 5ae6a3aeb broke the format check. Upstream's Formatting CI would be red.

Note on sq48's other point: the Obj.kt blank-line removal is gone and spotless does not flag Obj.kt.

## What must change
Put the `ConcurrentLinkedQueue` import back in sorted position in ObjRepository.kt (run `./gradlew :api:repo:spotlessApply`
and keep exactly what it changes; check the diff vs merge base afterwards: that import move is then a required part of the
change, keep it), then `spotlessCheck` must pass at the new commit. Re-gate.

## Checked at 5ae6a3aeb (stand unless the next commit changes them)
- Scope: net diff vs merge base e85278c9a: 8 files, no test file, no whitespace-only hunk; only the ObjRepository import
  position is wrong for spotless (above). Diff content = PlayerDropTimers, HeldInteractions, Obj timers, ObjRepository,
  ObjRegistry (reveal cycle, singleCopy ownerId), ZoneUpdateMap/Transformer (OBJ_ADD fields, copy ownerId),
  PlayerZoneUpdateProcessor.
- Accuracy: wiki Drop re-read (WebFetch; osrs-mcp not available in this session): tradeables public after 60 s and gone after
  another 120 s, untradeables 180 s, clue scrolls 61 min = 100/300, 300 never public, 6100. Constants in the diff match;
  RSProx values as in the spec (sq45 re-read them at source).
- Build with upstream main merged, local `qa.sh gate` (compile, cache, buildCache, rsa, installDist, assemble: rc=0;
  `test` 790 tests and `integration` 110 tests: 0 failures); only spotless rc=1. Fork CI on a scratch branch was NOT run: my hook
  denies any push except onto `docs`, so `gate/ground-item-despawn-times` could not be pushed (reported to the caller).
- PR text (cc24ebd8c): "Three gaps", three bullets, no test bullets, in-game bullet kept, constants and behaviour true of
  the commit. The spec `accuracy/...md` still has a stale "Tests" section naming the removed tests (docs only, not upstream).
- In game: not re-run (verdict fixed by the format failure; a new commit voids it). sq45's in-game result at d0fe09c63 stands for
  the code, which is unchanged apart from the removed tests.

## Verdict: **FAIL**
