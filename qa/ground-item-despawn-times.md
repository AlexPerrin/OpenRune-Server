# QA: fix/ground-item-despawn-times
- Branch: `fix/ground-item-despawn-times`
- Commit: `d0fe09c630e7456433348ee273f3e9cd8353cc82`
## Upstream gate: **PASS**
## Internal gate: N/A (dev-checked)

Upstream gate by sq45 on 2026-10-24 at d0fe09c63 (4 commits on upstream main e85278c9a; fork PR #23).

## Scope
12 files, +318/-27: PlayerDropTimers, HeldInteractions, Obj timers, ObjRepository, ObjRegistry, ZoneUpdateTransformer/Map,
PlayerZoneUpdateProcessor, 3 test files (the PR text justifies each: drop timers, packet fields, owner copy). One change.
Base is 19 commits behind upstream main 5c4c9c106; `git merge` with it is clean and none of those commits touch these files.
No open upstream PR on the topic (`gh pr list -R OpenRune/OpenRune-Server --search despawn / "ground item"`: only #342, #182, #354, unrelated).
`fix_gameval_conflicts.py --check`: clean. Commit subjects `fix:`, Alex Perrin, Co-Authored-By. Notes: a few
whitespace-only hunks (`?: return` spacing in HeldInteractions, import order in ObjRepository, a blank line in Obj.kt) are
spotless fixes beside the change; the PR text's intro says "Two gaps" and lists three bullets (the owner-copy bullet): wording only.

## Accuracy (independent)
- Wiki Drop page re-read (WebFetch; osrs-mcp is not available in this session): "Tradeable items appear to other players 60 seconds ...
  after being dropped" and "disappear after another 120 seconds"; "Untradeable items disappear after 180 seconds"; "Clue scrolls
  disappear after 61 minutes". Matches 100/300, 300 never public, 6100 never public.
- RSProx re-read at source: 1621 line 4343 `[obj_add] id=1737 reveal=100, despawn=300, ownership=self, neverturnpublic=false`;
  73 line 37692 (401 drop) and line 38428 (re-send `reveal=49, despawn=249`). As the spec says.
- Spec caveats cover NPC loot, wilderness hook, playtime rule, logout pausing, merged stacks, `tradeable` flag, GIM, opflags.

## Build
Fork CI run 39701238580 (CI) success at d0fe09c63; Formatting run 39700614177 success. Branch + upstream main 5c4c9c106 merged
(e73ec7a67 in my tree) builds (`qa.sh gate-run` rc=0) and boots: "Server ready in 9.15s", no Exception/ERROR in the log.

## In game (desktop RuneLite via RSProx, branch server, account `desktop`, Lumbridge 3221,3218)
`::item 1351 / 676 / 10180`, dropped each. RSProx transcript of my session:
- Bronze axe 1351: `reveal=100, despawn=300, ownership=self, neverturnpublic=false`.
- Trowel 676 (untradeable): `despawn=300, ownership=self, neverturnpublic=true` (the fixed stockmarket flag works).
- Clue scroll (easy) 10180: `despawn=6100, ownership=self, neverturnpublic=true`.
- OBJ_ADD at tick [15], OBJ_DEL axe and trowel at tick [314] = +299 (as official). devtools `list_ground_items`: all three at
  +192 s; at +207 s axe and trowel gone, clue scroll still there. (A first run on a tree where my merge had silently failed, i.e.
  plain main, sent all timers 0 and deleted all three after ~200 ticks: confirms the branch is what changes it.)
Evidence: re/evidence/ground-item-despawn-times/sq45-gate-objadd-lines.txt. Containers stopped, lock released.

## Tests
Unit tests only for the timers and packet fields (new logic that moves items); justified in the PR text. CI green.

## PR text review
Claims checked against the diff and my run: constants (100/300/6100), stockmarket flag, `lootDropDuration` override kept,
ObjRepository/ZoneUpdateTransformer fields, ownerId in singleCopy and copy, test names and contents, caveats. True of the commit.

## Verdict: **PASS**
