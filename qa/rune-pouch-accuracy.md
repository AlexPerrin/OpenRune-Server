# QA: fix/rune-pouch-accuracy
- Branch: `fix/rune-pouch-accuracy`
- Commit: `bee197ba90c7cec7a56e1174651b21662c70bf08`
## Internal gate: **PASS**
## Upstream gate: **PASS**

Re-run by sq24 on 2026-10-19 at bee197ba9, one commit on upstream `main` 731fd392a (re-fetched: still the tip),
fork PR AlexPerrin/OpenRune-Server#17 (head bee197ba9). Spec and PR text at fork docs ec16f5d67. Both
verdicts at 01c233d80 below are void; the four upstream items they asked for are done.

## Internal gate at bee197ba9: PASS
- **build-test** (`qa.sh gate`, one container, shared remote build cache for compiles, tests never cached; clone
  `qa/rpa-bee197ba9`, `origin/main` set to 731fd392a for the spotless ratchet; logs `rpa-bee197ba9.logs/`):
  compile, freshCache + mergePluginGamevals, buildCache, generateRsa, installDist, spotlessCheck, assemble all
  rc=0; `test` 726 tests, 0 failures/errors; `integration` 79 tests, 0 failures/errors (`RunePouchScriptTest`
  5/5); total 632 s. Boot: "Server ready in 9.36s", no exception or error lines. The tree is upstream main +
  the commit (the base is the tip). `fix_gameval_conflicts.py --check`: no conflicts.
- **accuracy-check**: the change is `if (internal in RunePouch.pouches) { mes(DONT_BE_SILLY); return }` in
  `inventoryOp()`, after the Op10 examine and before the rune check, and `const val DONT_BE_SILLY = "Don't be
  silly."`. The same condition, order and line as 01c233d80's `storeRejection` (checked in game there, table
  below), without the helper. Wiki Rune pouch, Trivia (re-read 2026-10-18 through osrs-mcp) unchanged.
- **In-game**: not re-run. The player-visible path is identical to 01c233d80's, which was checked in game as a
  members player (all three pouches "Don't be silly.", coins the non-rune line, air stored).

## Upstream gate at bee197ba9: PASS
- **The 01c233d80 FAIL items are done:** no new test, the companion object stays `private`, no KDoc, based on
  the current upstream main 731fd392a, PR text updated.
- **Scope**: one file, +5 lines (`RunePouchScript.kt`); no docs files on the code branch.
- **Overlap**: `gh pr list -R OpenRune/OpenRune-Server --state open --search "rune pouch"`: only #319
  (gravestones), which doesn't touch the module. No `Depends:`.
- `fix_gameval_conflicts.py --check` clean; no ids added.
- **CONTRIBUTING / AGENTS.md**: `fix:` subject, Co-Authored-By line; Release note, Checklist, Data sources
  (CC BY-NC-SA), manual testing notes, Claude Code line present. No test, per AGENTS.md "Unit tests" (a
  message in a simple op handler); no comments.
- **pr-text-review** (`pr-texts/rune-pouch-accuracy.upstream.body` at ec16f5d67, title matches the commit
  subject), every claim against bee197ba9:
  - True: interface 190; the four pouches (12791, 24416, 27281, 27509) answer "Don't be silly." before the
    rune check, other non-runes keep the line; "No new test", `RunePouchScriptTest` still passes (5/5 here,
    the PR says 5/5); spotless/test/integration pass; the wiki quote is verbatim; fork PR link #17 correct.
  - The in-game paragraph describes QA's check, which ran at 01c233d80 on main bcdb03292 + that commit, not
    on bee197ba9. Accepted: same code path and line (above). If upstream asks, it was the first version of
    this fix.
- **Caveats complete**: the divine/locked-by-extension line (recommended at 01c233d80) is in, and the module
  gaps from the earlier FAIL are all listed (unchanged from 01c233d80, checked there).

## Upstream re-check: PASS (PR text in upstream's PR template, docs bc84271a8, same commit)
Checked by sq25 on 2026-10-19. sd24 rewrote `pr-texts/rune-pouch-accuracy.upstream.body` into upstream's new PR template
(#369, `.github/pull_request_template.md` on upstream `main` 829b9477d); the commit is unchanged.
- Template sections present, in order: Summary, Type of change, How it was tested, Checklist, Screenshots / notes
  for reviewers. Data sources and the release note are in the notes; `.github/CONTRIBUTING.md` asks for a
  release-note summary "in the PR body" and data-source notes, not headings, so that is fine.
- Checklist ticked honestly: builds and boots (true, record above); no test added (true); symbols, not raw ids
  (true); no new state (true); "Matches the OSRS wiki" ticked with the reason that the gaps are listed in the
  notes rather than in `docs/quirks.md` (true: the message is the wiki's Trivia line; the divine/locked extension is stated in the notes); "checked against rsprox dumps" left unticked with the
  reason (we have none); no unrelated changes (true).
- Claims re-checked against bee197ba9 (one commit on 731fd392a, +5 lines in `RunePouchScript.kt`) and this
  record: interface 190, the four pouches answer "Don't be silly." before the rune check; the in-game paragraph
  says it ran on an earlier commit with the same condition, order and message and that the current commit was
  not re-run (true, as recorded above; items were spawned, which the text says); 731fd392a + the branch:
  test 726/0, integration 79/0 with `RunePouchScriptTest` 5/5, boot (sq24's run above); no new test (AGENTS.md).
  Fork PR link #17 correct; title matches the commit subject. Data sources (wiki Rune pouch, Trivia, CC
  BY-NC-SA) and the release note present.
- Merges cleanly onto upstream `main` 829b9477d (`git merge-tree`), and nothing between 731fd392a and 829b9477d
  touches `content/skills/magic/rune-pouch` (`git diff --name-only 731fd392a 829b9477d`), as the text says.
- Caveats: unchanged from the text that passed above, all present in the notes.

## Earlier run at 01c233d80 (void: superseded by bee197ba9)

Run by sq24 (companion to sd23) on 2026-10-18/19. Fork PR AlexPerrin/OpenRune-Server#17. Spec
`accuracy/rune-pouch-accuracy.md` and PR text `pr-texts/rune-pouch-accuracy.{title,upstream.body}` at docs
ea4868eb1. The commit is one commit on upstream `main` d2a0cd1b1 (after #356 merged the pre-audit pouch).
Earlier record: osrs-plugins-reverse `openrune/upstream/qa/rune-pouch.md` (FAIL at af57c4ad8). The dev's
run (`re/agents/sd23/wt/fix-rune-pouch-accuracy.logs`) was read as evidence only.

**Upstream `main` moved** during the gate: d2a0cd1b1 -> bcdb03292 (#359 drawers/Camelot chest, #361
Varrock platebody smelting, #312 Slayer). None of them touches `content/skills/magic/rune-pouch`, the
rune enums or the varbits the pouch uses; `git merge-tree` of bcdb03292 and the commit is clean. It doesn't
matter for this change, but the next commit should be based on the current `main`.

### Upstream gate at 01c233d80: FAIL (void). What must change
The behaviour is right (internal gate below). The commit doesn't follow upstream `AGENTS.md` (on `main`
bcdb03292), and the PR text has to follow the code:
1. **Drop the new test and keep the companion object private.** AGENTS.md, "Unit tests — where they earn
   their keep": "Skip tests for straight-line content wiring (dialogue, simple op handlers, ...) and for
   config-matches-cache/wiki checks". The test checks that a simple op handler's message equals the wiki
   line, which is that kind of test, and the maintainers have just said small changes don't need tests. The
   `private companion object` -> `companion object` change exists only so the test can reach
   `storeRejection`. Without the test, put it back to `private` (or inline the check in `inventoryOp()`).
   The test is not warranted.
2. **Remove the two new KDoc comments**: `/** Storing the pouch itself (OSRS Wiki: Rune pouch, Trivia). */`
   on `DONT_BE_SILLY` and `/** Why [obj] can't go into the pouch ... */` on `storeRejection`. AGENTS.md,
   "Comments — less is more": "Small or obvious methods get nothing", and "no comment on a field whose name
   already says what it is". The source belongs in the commit message and the PR text, where it already is.
3. **Base the new commit on the current upstream `main`** (bcdb03292 or later).
4. **PR text** (`pr-texts/rune-pouch-accuracy.upstream.body`), to match the new commit:
   - Change: drop "The companion object is public so the test can reach it."
   - Tests: drop the `RunePouchScriptTest` +1 bullet. Give the new build/test numbers. Replace "Nothing was
     checked in game from this exact branch." with the in-game check (QA, below: the three pouches answer
     "Don't be silly.", coins get the non-rune line, an air rune is stored).
   - Caveats (recommended, one line): the wiki's Trivia line names the rune pouch only. The divine pouch and
     the locked variants get the same line because they use the same interface (190). The Divine rune pouch
     page doesn't mention it.

Everything else in the upstream gate passes:
- **Scope:** one fix, two files (`RunePouchScript.kt`, `RunePouchScriptTest.kt`). None of our docs files are
  on the code branch.
- **No upstream overlap:** `gh pr list -R OpenRune/OpenRune-Server --state open --search "rune pouch"` /
  `"pouch"`: only #319 (gravestones, Death's Office) and #310 (Thieving). Neither touches the rune pouch
  module. #319 is where the death caveat would hook in later.
- **`Depends:`** none. The PR text names #356 as the merged base.
- `python3 tools/scripts/fix_gameval_conflicts.py --check`: "No gameval conflicts or unassigned IDs found"
  on the commit tree and on main bcdb03292 + the commit. The commit adds no ids.
- **CONTRIBUTING** (`.github/CONTRIBUTING.md`): the subject is in `fix:` style. The body has the Release
  note, the Checklist, Data sources, manual testing notes and the Claude Code line. The commit ends with the
  Co-Authored-By line.
- **pr-text-review**, every claim checked against 01c233d80:
  - True: interface 190; `storeRejection(obj)`; the four pouches (`RunePouch.pouches` = bh_rune_pouch,
    bh_rune_pouch_trouver, divine_rune_pouch, divine_rune_pouch_trouver = 12791, 24416, 27281, 27509) get
    "Don't be silly."; other non-runes keep the old line; the companion object became public.
  - Build/test numbers match this run: test 685, integration 77, `RunePouchScriptTest` 6/6, spotless,
    assemble and boot.
  - The test fails without the change: `storeRejection` doesn't exist before it, so it doesn't compile.
  - The wiki quote is verbatim (Rune pouch, Trivia, re-read through osrs-mcp `wiki_page`). The CC BY-NC-SA
    attribution is present.
- **Caveats complete** against the earlier FAIL's item 2 and the spec's gap list: death (PvP/PvM), bank
  Empty/deposit and loadouts, Destroy (cache item 12791 param 65482 "If you destroy the pouch, all the
  contents will fall out.", re-read via `cache_search`), auto-store, divine creation/Revert, one pouch per
  player and Glarial's Tomb/Koschei, and the unsourced items (messages, Empty stopping early, the
  op-to-quantity mapping, X not setting the custom amount) are all listed. Also on the wiki now, but not a
  rune pouch module gap: Construction can't take runes from the pouch (Trivia). That belongs to
  Construction, so no caveat is needed.

### Internal gate at 01c233d80: PASS (void)

### build-test
Fresh self-contained clone at 01c233d80 (scratchpad `qa/rpa-01c233d80`, `game.yml` from
`game.example.yml`). Its `origin/main` was set to d2a0cd1b1, the commit's upstream base, for spotless's
`ratchetFrom("origin/main")`. Run with `openrune/upstream/qa/qa.sh build` (each step its own container
through `tools/buildq.sh`: `--cpus=4 --memory=4g`, `-Xmx2g`, cache steps `-Xmx2600m`, `--no-build-cache
--no-configuration-cache`, in-process Kotlin, QA Gradle home volume; test and integration with 1 worker).
Logs: `<scratchpad>/qa/rpa-01c233d80.logs/`.

| Step | Result |
|---|---|
| `clean :or-cache:freshCache :or-cache:mergePluginGamevals` | pass (5m35s) |
| `:or-cache:buildCache` | pass (38s) |
| `generateRsa`, `:server:app:installDist` | pass |
| `spotlessCheck` | pass |
| `assemble` | pass |
| `test` | pass, 685 tests, 0 failures/errors |
| `integration` | pass, 77 tests, 0 failures/errors; `RunePouchScriptTest` 6/6 incl. `storing the pouch itself is refused with the official line` |
| boot (upstream main bcdb03292 + 01c233d80, tree `qa/rpa-01c233d80-run`, `qa.sh runbuild` + `start`) | "Server ready in 9.88s"; no exception or error lines in the server log over the session |

### accuracy-check
Re-read through osrs-mcp: `wiki_page` "Rune pouch" (Trivia: "If a player attempts to store the rune pouch
while the storage interface is open, the message "Don't be silly." appears in the chatbox."), "Divine rune
pouch" (no such line), `cache_search` item 12791, `gameval_search` obj rune_pouch (the four ids above).
- D1 (the earlier FAIL's item 1) is fixed. `inventoryOp()` answers "Don't be silly." for every pouch
  before the rune check. Op10 (examine) is still handled first.
- The divine and locked pouches get the same line. The wiki states it for the rune pouch only. Same
  interface, a reasonable extension; see upstream item 4.
- Nothing else in the module changed. The spec's other rows are unchanged from the 2026-10-18 audit and are
  listed in the PR's Caveats.

### In-game (branch server)
`qa-or-server` (upstream main bcdb03292 + 01c233d80) on 43596 with the sidecar `qa-or-http` on 8083.
Isolated desktop RuneLite through RSProx (`DESKTOP_TARGET_YAML`) under the desktop lock granted by main.
Fresh account `sq24rp`, made members with `qa-members.sh` and relogged (the pouch showed "Open"). Spawned
rune pouch, divine rune pouch, rune pouch (l), 100 air runes and 100 coins (`::item`). The divine pouch in
the inventory made the interface open as "Divine Rune Pouch" (varp 261 = 4). Left-click on each item in
interface 190's inventory panel (component 190:7), as a player would:

| Clicked | Chat (tick) | Result |
|---|---|---|
| Rune pouch (12791) | "Don't be silly." (111) | not stored |
| Divine rune pouch (27281) | "Don't be silly." (114) | not stored |
| Rune pouch (l) (24416) | "Don't be silly." (117) | not stored |
| Coins (995) | "You can only store runes in the rune pouch." (120) | not stored |
| Air rune (556) | none | 1 stored: pouch slot air x1, inventory 99 |

The divine (l) pouch (27509) was not spawned. It is covered by the test, which loops over all four pouches.
Clean-up: logged out, `desktop.sh stop`, `qa.sh stop` (removed qa-or-server, qa-or-http), desktop RuneLite
restarted on the live target (plain `desktop.sh start`, logged in as `desktop`), lock freed 23:42.
osrs-openrune, osrs-openrune-http and osrs-openrune-http-desktop were not touched.

### reverse
n/a: no claim about the client's protocol.
