# QA: fix/edgeville-general-store-stock
- Branch: `fix/edgeville-general-store-stock`
- Commit: `ca527418a529f4e69bf6cac04775bff486a23d01`
## Internal gate: **PASS**
## Upstream gate: **PASS**

Re-run by sq25 on 2026-10-19 at ca527418a (sd24's fix for the first upstream FAIL: test dropped, rebased onto
bcdb03292). The first run at a2412b26f follows below, unchanged except for its heading levels.

ca527418a is one commit on bcdb03292 touching only `.data/raw-cache/server/shops/generalshop2.toml`, byte-identical
to a2412b26f's (`git diff a2412b26f ca527418a -- <toml>` empty); `ShopInventoryTomlTest` is gone. Upstream `main`
moved on to 731fd392a (#297 Waterfall Quest, no shared files); the branch merges cleanly and the gate ran on
that merge (fresh `git clone --shared` `re/agents/sq25/g-edge`, tree 6047fe64a = 731fd392a + ca527418a,
`origin/main` = 731fd392a for the spotless ratchet). Texts: fork docs fe39bad9a
(`pr-texts/edgeville-general-store-stock.*`, `accuracy/edgeville-general-store-stock.md`).

## Internal gate details (PASS)
`openrune/upstream/qa/qa.sh gate` (the build-test skill's gate now: one priority ticket, one container, remote
build cache for compiles, tests never cached; `GATE_VOL=sq25-gradle`). Logs:
`re/agents/sq25/g-edge.logs/` (summary, tests, one log per step).

| Step | Result | Time |
|---|---|---|
| `clean :or-cache:qaCacheToolsClasspath` | pass | 25s |
| `:or-cache:freshCache :or-cache:mergePluginGamevals` | pass | 81s |
| `:or-cache:buildCache` | pass | 43s |
| `generateRsa` | pass | 17s |
| `:server:app:installDist` | pass | 31s |
| `spotlessCheck` | pass | 22s |
| `assemble` | pass | 58s |
| `test` | pass, 726 tests, 0 failures | 132s |
| `integration` | pass, 79 tests, 0 failures | 129s |
| boot (`sq25-or-server`, 43597) | "Server ready in 10.8s", no Exception/ERROR line | |
| `fix_gameval_conflicts.py --check` | "No gameval conflicts or unassigned IDs found." | |

Packed cache, the same static check as at a2412b26f (`ServerCacheManager.init(241)`, `getInventory(4)`): `size=40
flags=4 stack=Always scope=Shared`, 14 stock entries, objs 1931, 1935, 20742, 1735, 1925, 22660, 1923, 1887,
590, 1755, 2347, 946, 550, 9003 with counts 5, 2, 5, 2, 3, 15, 2, 2, 2, 2, 5, 1, 5, 5 and restock cycles 10,
100, 20, 100, 10, 10, 50, 50, 100, 100, 100, 2, 100, 100: the wiki's table (accuracy-check at a2412b26f below;
the data is unchanged). In-game: n/a as before (nothing on `main` opens `inv.generalshop2`).

## Upstream gate details (PASS)
- The three FAIL items at a2412b26f are fixed: no test file (and so no KDoc), based on bcdb03292 (current when
  rebased; `main` gained only #297 since, merges cleanly, gate run on the merge), commit message no longer
  mentions a test.
- Scope: one data file; nothing unrelated, none of our docs. No other upstream PR for this content (#351 is
  ours and depends on it).
- `fix_gameval_conflicts.py --check` clean; `fix:` subject, authored as Alex Perrin, Co-Authored-By line.
- pr-text-review (`pr-texts/edgeville-general-store-stock.upstream.body` at fe39bad9a): every section present
  (Problem, Change, Tests, Data sources, Caveats, Release note, Checklist, Claude Code line). Claims checked:
  nine stock entries and the header lost, five surviving entries matching the wiki (true of c2492318d's toml);
  14 entries in wiki order with those counts and cycles; header values; size 40 per #341; "None added
  (AGENTS.md skips config-matches-wiki tests)" (AGENTS.md "Unit tests — where they earn their keep"); the
  decode result (matches my decode above); the one-off scan of 395 shop tomls + 6 pack inventories finding
  only generalshop2 (my independent scan: 401 inventories, only generalshop2 on `main`, none on the branch);
  "existing suites pass" (my run above); "Nothing was checked in game" (true).
- Caveats: the spec's three gaps (size 40 from other stores' captures, `allStock = false` as in every upstream
  general store, multipliers unused) are all listed; the accuracy check found no others.

## Upstream re-check: PASS (PR text in upstream's PR template, docs bc84271a8, same commit)
Checked by sq25 on 2026-10-19. sd24 rewrote `pr-texts/edgeville-general-store-stock.upstream.body` into upstream's new PR template
(#369, `.github/pull_request_template.md` on upstream `main` 829b9477d); the commit is unchanged.
- Template sections present, in order: Summary, Type of change, How it was tested, Checklist, Screenshots / notes
  for reviewers. Data sources and the release note are in the notes; `.github/CONTRIBUTING.md` asks for a
  release-note summary "in the PR body" and data-source notes, not headings, so that is fine.
- Checklist ticked honestly: builds and boots (true, record above); no test added (true); symbols, not raw ids
  (true); no new state (true); "Matches the OSRS wiki" ticked with the reason that the gaps are listed in the
  notes rather than in `docs/quirks.md` (true: stock, counts, restock times and multipliers match the wiki; size 40 and `allStock = false` are not wiki statements contradicted by this PR); "checked against rsprox dumps" left unticked with the
  reason (we have none); no unrelated changes (true).
- Claims re-checked against ca527418a and this record: the Summary (truncation, crash, the 14 rows, header,
  size 40); "How it was tested" (731fd392a + the branch: buildCache, spotless, assemble, test 726/0,
  integration 79/0, boot, `--check` clean; the packed `getInventory(4)` decode; the 395 + 6 inventory scan;
  not checked in game because nothing on `main` opens the shop) all match the internal gate above. Data sources
  (wiki Stock section and Changes, #341) and the release note present.
- Merges cleanly onto upstream `main` 829b9477d (`git merge-tree`), and nothing between 731fd392a and 829b9477d
  touches `.data/raw-cache/server/shops` (`git diff --name-only 731fd392a 829b9477d`), as the text says.
- Caveats: unchanged from the text that passed above, all present in the notes.

---

## Earlier run at a2412b26f
Run by sq25 (companion to sd24) on 2026-10-19 in its own clone `re/agents/sq25/or-edge`. The branch is one
commit on c2492318d. Upstream `main` is now bcdb03292 (10 commits ahead); the branch merges into it with no
conflict, and the gate was run on that merge (local tree 972372b60 = bcdb03292 + a2412b26f), `origin/main` set
to bcdb03292 for the spotless ratchet. Fork PR AlexPerrin/OpenRune-Server#19; spec
`accuracy/edgeville-general-store-stock.md` and texts `pr-texts/edgeville-general-store-stock.*` at docs
8f358b8b9. The dev's runs were not used as evidence.

### Internal gate at a2412b26f: PASS
#### build-test
One container (`eclipse-temurin:21`, `--cpus=4 --memory=4g`, Gradle home volume `sq25-gradle`,
`--no-build-cache --no-configuration-cache`, `--max-workers=2`, test/integration 1 worker,
`-Pkotlin.compiler.execution.strategy=in-process`, cache steps `-Xmx2600m`), one `./gradlew --no-daemon` run per
step, `game.yml` copied from `game.example.yml`. Logs: sq25 scratchpad `sq25/logs-or-edge/`.

| Step | Result | Time |
|---|---|---|
| `clean` + compile of or-cache's classpath (the 33 packs, engine map/routefinder, or-cache) | pass | 72s |
| `:or-cache:freshCache :or-cache:mergePluginGamevals` | pass | 192s |
| `:or-cache:buildCache` | pass | 44s |
| `generateRsa` | pass | 34s |
| `:server:app:installDist -x test` | pass | 627s |
| `spotlessCheck` (ratchet from bcdb03292) | pass | 37s |
| `assemble` | pass | 94s |
| `test` | pass, 709 tests, 0 failures (`ShopInventoryTomlTest` 1/1) | 267s |
| `integration` | pass, 79 tests, 0 failures | 159s |
| boot (`sq25-or-server`, port 43597) | pass, "Server ready in 13.5s", no Exception/ERROR line | |
| `fix_gameval_conflicts.py --check` | "No gameval conflicts or unassigned IDs found." | |

Infrastructure note: the first attempt ran `clean :or-cache:freshCache` in one step and the freshCache JavaExec
fork (`-Xmx2600m` from JAVA_TOOL_OPTIONS) was OOM-killed by the container's memory cgroup next to the Gradle
daemon that had just compiled the packs (dmesg: "Memory cgroup out of memory: Killed process ... java"). Rerun
with the compile in its own step first, so the cache step's daemon starts fresh. Not the branch.

#### accuracy-check
Re-read through osrs-mcp `wiki_page` "Edgeville General Store" (2026-10-19): Sells at 130.0%, Buys at 40.0%,
Change per 3.0%; Pot 5 (10t), Jug 2 (100t), Empty jug pack 5 (20t), Shears 2 (100t), Bucket 3 (10t), Empty
bucket pack 15 (10t), Bowl 2 (50t), Cake tin 2 (50t), Tinderbox 2 (100t), Chisel 2 (100t), Hammer 5 (100t),
Knife 1 (2t), Newcomer map 5 (100t), Security book 5 (100t); Changes: knives added 19 January 2022.
The toml at the commit (parsed) has those 14 entries, in that order, with those counts and restock cycles,
1300/400/30, size 40, Shared, stack Always, restock true, protect/runWeight/allStock/placeholders false. The obj
names are the ones `generalshop5.toml` uses. Matches the spec's planned behaviour table; the spec's caveats
(size 40 from #341's captures, `allStock = false` as in every upstream general store, multipliers unused) are
the remaining gaps and all three are in the PR's Caveats.

Packed cache (static check, accepted by "main" in place of in-game): the branch's built `.data/cache/SERVER`
loaded with `ServerCacheManager.init(241)` from the installDist classpath, `getInventory(4)` (inv.generalshop2):
`size=40 flags=4 (restock only) stack=Always scope=Shared`, 14 stock entries, objs 1931, 1935, 20742, 1735,
1925, 22660, 1923, 1887, 590, 1755, 2347, 946, 550, 9003 (pot, jug, empty jug pack, shears, bucket, empty bucket
pack, bowl, cake tin, tinderbox, chisel, hammer, knife, newcomer map, security book) with the wiki's counts and
restock cycles. Size 40 >= 14, so `Inventory.create` no longer overruns.

The PR's scan claim was re-run independently (Python, same rules as the test) over every `[[inventory]]` in
`.data/raw-cache/server/shops/*.toml` and the content packs' `pack/configs/*.toml`: 401 inventories; on
bcdb03292 only `inv.generalshop2` is flagged, on the branch none.

#### In-game
Not run, by design: on upstream `main` + this branch nothing opens `inv.generalshop2` (the Edgeville
shopkeepers still open `inv.generalshop5`; #351 changes that). The static check of the packed inv above stands
in for it ("main", 2026-10-19).

#### reverse
n/a (no client protocol claim).

### Upstream gate at a2412b26f: FAIL
Scope (one fix, no unrelated files: the toml and one test), no competing upstream PR (`gh pr list --search
"general store"`: #351 ours, #341/#314/#276 merged), `fix_gameval_conflicts.py --check` clean, `fix:` subject,
every CONTRIBUTING section present, and the stock/size/header claims are true. It fails on:

1. **`ShopInventoryTomlTest` goes against upstream's AGENTS.md.** "Unit tests — where they earn their keep":
   tests are for complex logic/maths and for code that moves items; "Skip tests for straight-line content
   wiring ... and for config-matches-cache/wiki checks". This test checks config files, not logic (the
   maintainer already said "No Test is needed for this tbh" on #348). It also adds a KDoc block on a small class
   ("Comments — less is more": no KDoc on small or obvious code). **Drop the test** (and its KDoc with it). The
   scan result can stay in the PR text as a manual check ("a structural scan of the 401 inventory tables finds
   only generalshop2 broken").
2. **Not based on current `upstream/main`.** The branch is on c2492318d; `main` is bcdb03292. It merges
   cleanly, but the gate requires the current base, and the branch needs a new commit for item 1 anyway:
   rebase onto bcdb03292 (or whatever `main` is then).
3. **PR text** (`pr-texts/edgeville-general-store-stock.upstream.body`), after 1 and 2:
   - Tests: replace the `ShopInventoryTomlTest` paragraph with the manual scan, and the commit message's "and
     add a test that every inventory toml's size covers its stock".
   - The results line: name the base it was run on (the new `main`).
   - "Nothing was checked in game" can stay; add the packed-cache check (size 40, 14 stock entries) if wanted.

Then the internal and upstream gates are re-run at the new commit.
