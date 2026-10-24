# QA: cannon-review-354
- Branch: `fix/cannon-drop-tests`
- Commit: `0df5c63a009b75fffb33f8ead897b432f55fcead` (one commit on 60cffedfb, the #354 head)
## Internal gate: N/A (dev-checked)
## Upstream gate: **PASS**

Run by sq55 on 2026-10-24. Spec `accuracy/cannon.md` and PR text `pr-texts/cannon.upstream.body` read at docs 9159e5e6c.

## Checked
- **CI on the scratch merge** (0df5c63a0 + upstream main, fork branch `gate/cannon-drop-tests` at 0175c293a): `CI` run 40142538549
  (build & boot) success; `Formatting` run 40142539998 (spotless) success. The main build compiles without the removed
  test-only deps (libs.guice, combatWeapon, integration-test-suite plugin).
- **Scope**: net diff vs merge base 829b9477d is 4 files: Nulodion.kt, DwarfMulticannon.kt, gamevals.toml,
  content/other/dwarf-multicannon/build.gradle.kts (new, 9 lines: base-conventions + combatFormulas, pluginCommons, quest).
  No test file. `NulodionCannonSaleTest.kt` is byte-identical to upstream main (`git diff upstream/main HEAD` empty).
  Against 60cffedfb the commit only deletes DwarfMulticannonTest.kt, the PR's 18 lines in NulodionCannonSaleTest.kt and 8 build lines.
- **spotlessCheck** run locally on `:content:other:dwarf-multicannon` and `:content:areas:misc:dwarven-mine`: pass (rc 0).
- **Logic unchanged**: `git diff 60cffedfb 0df5c63a0` on Nulodion.kt and DwarfMulticannon.kt is empty, so sq46's in-game
  result at 60cffedfb stands (stage build in order; partial reclaim at stages 1/2/3/full OK). No in-game rerun. No `while`
  in DwarfMulticannon.kt; `replaceLostCannon` returns `CANNON_SET.take(4).take(player.cannonStage)`.
- **PR text and spec**: both say no tests are included and verification was in game; "Not run in game" lists the 10-minute
  decay, the 35-minute part-built decay and Nulodion's replacement of a lost full cannon (honest: sq46 covered partial and full
  reclaim stages, not the decay timers). No revision history or justification wording, no shas, no internal run names. Claims
  match the code (stage-based return, k free slots, no tests, spotless passes). The fork-PR browsing link is the only non-code line.
