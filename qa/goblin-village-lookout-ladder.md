# QA: fix/goblin-village-lookout-ladder
- Branch: `fix/goblin-village-lookout-ladder`
- Commit: `b878191583dee2759738c2ca8227b362dec6b9f8`
## Upstream gate: **PASS**
## Internal gate: N/A (dev-checked)

Re-gate by sq25 on 2026-10-19 at b87819158 (sd24's fix for the FAIL at 475eefb76, kept below as history). Two
commits on upstream `main` 829b9477d (still the tip, re-fetched): 475eefb76 and b87819158; net diff +4 lines in
`GoblinCaveLadders.kt` (two `onOpLoc1` bindings to `climb(LOOKOUT_TOP)` / `climb(LOOKOUT_BOTTOM)`, constants
(2955, 3497, 2) / (2955, 3497, 0); the earlier `climbInPlace` is gone). Run tree `re/agents/sq25/r-ladder` at
b87819158. Spec `accuracy/goblin-village-lookout-ladder.md` and text `pr-texts/goblin-village-lookout-ladder.{title,upstream.body}`
read from sd24's docs worktree at aac2488f1 (not yet on fork `docs`).

## Accuracy (independent, osrs-mcp)
- Gamevals: `loc.goblin_ladder_bottom` 16450, `loc.goblin_ladder_middle` 16557, `loc.goblin_ladder_top` 16556.
  Cache (LIVE) 16450 "Ladder", op Climb-up, 1x1. Unchanged from the first run (ops of 16556/16557 as recorded there).
- Wiki and Quest Helper levels as in the first run (bottom on level 0, top on level 2): rows 1-3 match.
- Row 4 (landing tile, a guess, caveated): in game both ladders face east (`list_objects`: 16450 at (2954, 3497, 0)
  and 16556 at (2954, 3497, 2), orientation 1536, "E"); the posts 12439 are at (2954, 3496/3498, 0), not on
  (2955, 3497). (2955, 3497) is walkable on both levels (stood on, walked off, below). The spec's reason holds.
- RSProx comparison: none found (rsprox.net LOC 16450 / 16556, 0 recordings; my search in the first run today; the
  spec and the PR's unticked rsprox item now say so). The landing tile stays a guess, listed as the known gap.

## In game (desktop RuneLite, account `sq25lad` on the branch server 43597, desktop lock 21:48-21:59)
The account is the one from the first run (its save in the run tree; logged in at (2955, 3497, 0)). No `::tele`.
- Climb-up from the west tile (2953, 3497, 0) -> (2955, 3497, 2).
- **Climb-down from (2954, 3498, 2), the case that failed -> (2955, 3497, 0)**, not the post's tile.
- Climb-up from (2955, 3497, 0) -> (2955, 3497, 2).
- Climb-down from the south tile (2954, 3496, 2) -> (2955, 3497, 0).
- Climb-up from the village side, walking from (2955, 3492, 0) -> (2955, 3497, 2); animation 828
  (`human_reachforladder`) at tick 170, reset at 173 (`get_timeline`).
- Server log: no Exception/ERROR line.

## Build
- Fork GitHub Actions at b878191583dee2759738c2ca8227b362dec6b9f8 (workflow_dispatch): `CI` run
  https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39353952217 success (job "build & boot": tests
  "BUILD SUCCESSFUL in 7m 17s", "Server ready in 15.07s"); `Formatting` run 39353954027 success.
- Branch server (`qa.sh gate-run` on r-ladder): "Server ready in 11.3s".
- `fix_gameval_conflicts.py --check` on the tree: "No gameval conflicts or unassigned IDs found."

## Scope, conventions, text
- One fix, one file, off current `main`, no `Depends:`. No comments or KDoc added; gamevals symbols; no test
  (simple op handler, AGENTS.md). Subjects `fix:`, authored Alex Perrin (noreply), Co-Authored-By lines.
- Upstream overlap: #310 and #227 (search "goblin"/"ladder") don't touch these locs or `GoblinCaveLadders`. Our
  own draft **#367 still carries the same ladder**: as in the first run, it must be replaced by the quest-only
  commit (content/goblin-diplomacy-829) before or when this PR opens.
- pr-text-review of the body at aac2488f1: every template section present; data sources with licence; release
  note; Claude Code line. Claims checked: the problem (no content group, #328 cave ladders only); the change and
  the post (12439) reason; the gate line (764/0, 79/0, boot, `--check`) matches sd24's gate logs for this commit
  (`re/agents/sd24/glad.logs`, commit b878191583) and the green CI above; every in-game line matches this
  record's runs; the rsprox item is unticked with the search stated; known gap = the landing tile. Both required
  text changes from the first run are done.

---

## Previous gate at 475eefb76 (voided by b87819158; kept as history)

Its header was: # QA: fix/goblin-village-lookout-ladder / - Branch: `fix/goblin-village-lookout-ladder` / - Commit: `475eefb76ce8cf0ea3a680e4b3e0d278e614a775` / ## Upstream gate: FAIL / ## Internal gate: N/A (dev-checked)


Run by sq25 on 2026-10-19. One commit on upstream `main` 829b9477d (the current tip, re-fetched), so the branch is
its own run tree (fresh `git clone --shared` `re/agents/sq25/r-ladder`). Spec `accuracy/goblin-village-lookout-ladder.md`
and text `pr-texts/goblin-village-lookout-ladder.{title,upstream.body}` at fork docs aa3080530.

## Failing item: in game, Climb-down can land the player inside a solid support post

The handler telejumps to the player's own x/z on the other level (`climbInPlace`). On level 2 the player can stand
on any side of the top ladder (2954, 3497, 2), but on level 0 the tiles north and south of the bottom ladder,
(2954, 3498, 0) and (2954, 3496, 0), hold loc 12439 (a lookout post, cache `solid=2`). Seen on the branch server:
- Up a ladder from the west tile (2953, 3497, 0) -> (2953, 3497, 2); walking to the crate 16561 on level 2 moved the
  player to (2954, 3498, 2).
- Climb-down from there -> **(2954, 3498, 0), the post's tile**: the player stands inside loc 12439 (screenshot
  taken; `list_objects` shows 12439 on that tile). The player could walk off it, but a ladder must not place the
  player on a blocked tile, and the PR's caveat ("the player's own tile ... not documented") doesn't cover it.

**Required change (code, then a re-gate at the new commit):** land on a fixed walkable tile instead of the
player's own tile, e.g. the tile in front of the ladder, (2955, 3497), on the other level for both ops (the ladder
faces east on both levels; walking up from the village approaches from that tile, and it is walkable on 0 and
2), or otherwise never a blocked tile. Then update spec row 4 and the PR's landing-tile caveat to match.

**Required text changes (with the new commit):**
1. "How it was tested" says "Not yet checked in game from this exact branch": replace with the in-game checks of
   the new commit (this record's section, re-run by QA).
2. The rsprox checklist note says "we have no rsprox dumps of the official game". That is no longer true (the
   RSProx database is approved, `references/rsprox.md`). Say what was searched: no recording uses these locs
   (rsprox.net LOC search, 2026-10-19: 16450 and 16556, 0 results), so the item stays unticked. Add the same
   sentence to the spec ("RSProx comparison: none found").

## Checks done before the failure

### Accuracy (independent, osrs-mcp)
- Cache (LIVE): 16450 "Ladder", op Climb-up; 16556 "Ladder", op Climb-down; 16557 "Ladder", no ops (interactive
  -1). Gamevals: `loc.goblin_ladder_bottom` 16450, `loc.goblin_ladder_middle` 16557, `loc.goblin_ladder_top`
  16556. Matches spec rows 1-3 and the code's symbols.
- Wiki, Goblin Diplomacy (re-read): crate "Up a ladder near the gate to the goblin village"; "You can go up the
  only ladder in the goblin village, up there will be some crates". Matches.
- Quest Helper `GoblinDiplomacy` (read on GitHub): `goUpLadder` on GOBLIN_LADDER_BOTTOM at (2954, 3497, 0),
  `upstairs` zone (2952, 3495, 2)-(2959, 3498, 2), `goDownLadder` on GOBLIN_LADDER_TOP at (2954, 3497, 2), the
  crate 3 at (2955, 3498, 2). Level 2 matches.
- RSProx comparison: rsprox.net search LOC 16450 and LOC 16556 (2026-10-19): 0 recordings; the map square 11830
  has recordings but none with these ladders. No recorded detail to compare; the landing tile stays a guess.
- Upstream on `main` 829b9477d: no handler for the three locs (only the examines in `locs.csv`).

### In game (desktop RuneLite, fresh account `sq25lad`, branch server on 43597, desktop lock 20:29-20:41)
`::tele` only for travel to (2954, 3492, 0).
- Climb-up from the east approach: animation 828 (`human_reachforladder`), then (2955, 3497, 2). The top ladder
  16556, crates and floor are there.
- Climb-down from (2955, 3497, 2) -> (2955, 3497, 0). Fine.
- Climb-up from the west tile (2953, 3497, 0) -> (2953, 3497, 2). Fine.
- Climb-down from the north side -> inside the post (above). **FAIL.**
- Server log: no Exception/ERROR line.

### Build
- Fork GitHub Actions at 475eefb76 (dispatched by QA, no fork PR yet): `Formatting` run 39347629752 success;
  `CI` run 39347626547: success (see the end of this record).
- `fix_gameval_conflicts.py --check` on the branch tree: "No gameval conflicts or unassigned IDs found."
- Branch server (`qa.sh gate-run`) booted: "Server ready in 13.8s", no Exception/ERROR.

### Scope
One fix, one file (+10 lines in `GoblinCaveLadders.kt`), off current `main`, no `Depends:`. Upstream overlap: only
our own draft #367, which still carries the same ladder; it must be replaced by the quest-only commit
(content/goblin-diplomacy-829) before or when this PR opens, or the two PRs overlap.

### CI result
`CI` run https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39347626547 at head
475eefb76ce8cf0ea3a680e4b3e0d278e614a775: success (job "build & boot"). `Formatting` run 39347629752 at the same
sha: success. The build is not the failing item.
