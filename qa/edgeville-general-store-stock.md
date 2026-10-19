# QA: fix/edgeville-general-store-stock
- Branch: `fix/edgeville-general-store-stock`
- Commit: `a2412b26fe867f5dd2e8041b09423b93cb3af670`
## Internal gate: **PASS**
## Upstream gate: **FAIL**

Run by sq25 (companion to sd24) on 2026-10-19 in its own clone `re/agents/sq25/or-edge`. The branch is one
commit on c2492318d. Upstream `main` is now bcdb03292 (10 commits ahead); the branch merges into it with no
conflict, and the gate was run on that merge (local tree 972372b60 = bcdb03292 + a2412b26f), `origin/main` set
to bcdb03292 for the spotless ratchet. Fork PR AlexPerrin/OpenRune-Server#19; spec
`accuracy/edgeville-general-store-stock.md` and texts `pr-texts/edgeville-general-store-stock.*` at docs
8f358b8b9. The dev's runs were not used as evidence.

## Internal gate: PASS
### build-test
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

### accuracy-check
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

### In-game
Not run, by design: on upstream `main` + this branch nothing opens `inv.generalshop2` (the Edgeville
shopkeepers still open `inv.generalshop5`; #351 changes that). The static check of the packed inv above stands
in for it ("main", 2026-10-19).

### reverse
n/a (no client protocol claim).

## Upstream gate: FAIL
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
