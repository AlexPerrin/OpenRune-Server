# fix/11-dense-clientscript-ids (fork only; no upstream PR)

Title: fix: Number custom clientscripts right after the stock range, read from the cache and the CS2 project

## Problem
The spawn menu and quest pack clientscripts were numbered 45500-45553. The official Android client
(241.4) sizes its clientscript table from the archive and crashes at cache sync on group ids far
above the stock maximum. The first version of this branch renumbered them to 9973-9985 behind a
hard-coded stock maximum (9972, the 241.1 cache); Jagex's 241.4 cache has stock scripts up to 10196,
so those ids shadowed stock scripts: the symbol conflict strip removed the stock lines, PackCs2
failed on `Unable to find id for ClientScriptSymbol(script9973)`, the LIVE cache was never built and
128 tests failed on the missing custom params (integration train int-20261022-3).

## Change
- `StockClientScripts` (or-cache) reads the stock ids at cache-build time: index 12 of the cache
  plus the installed CS2 project's `symbols/clientscript.sym` (the unpacked bundle the build
  compiles; it can lag the cache, and the cache can hold earlier builds' custom scripts, so both are
  combined and a custom block at the top of the run, or above a gap wider than 256, is discounted).
  On a fresh install only the pristine cache exists, which is dense and complete on its own.
- `PluginPacks.checkClientScriptIds` fails the cache build when a custom clientscript id is inside
  the stock range (shadowing) or more than 256 above it (`OPENRUNE_MAX_CLIENTSCRIPT_ID` overrides
  the upper limit), naming the first free id.
- Custom scripts renumbered to 10197-10209: quest 10197-10198, spawn menu 10199-10209.

## Tests
- `StockClientScriptsTest` (or-cache): the rule has several cases that a renumbering alone would
  not exercise: a fresh cache, an earlier build's own scripts right after the stock range, a stale
  custom block far above it (a restored CI cache), custom ids inside the range, and bundle gaps.
- `QuestPolicySyncScriptTest` updated to the new id.

## Data sources
- https://cdn.openrune.dev/cs2-bundles/manifest.json; bundle 241.4 symbols/clientscript.sym
  (max id 10196; 241.1: 9916); cache 241.4 idx12: 10197 groups. Notes in
  re/evidence/11-dense-clientscript-ids/ (main checkout).

## Caveats
- Fork only: upstream numbers its custom scripts at 45553+, which the Android client cannot load.
- `local/skip-cs2-packs` stacks on this branch (rebased: b1647ce50).

## Release note
Custom clientscripts are numbered right after the stock range read from the cache and the CS2
project (10197+ at 241.4); the cache build fails with the first free id when one shadows a stock
script or sits too far above the range.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
