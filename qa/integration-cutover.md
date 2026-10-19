# QA: integration
- Branch: `integration`
- Commit: `5da8d3e46abc9020e786c740c150a94a85acd0d8`
## Internal gate: **PASS**
## Upstream gate: **N/A**

## Run at 5da8d3e46 (sq23, 2026-10-18): PASS
5da8d3e46 = be80d1d14 without fix/55-general-store-stock-wiring (local/integration-tools d87819fa2 drops it
from integration.txt). `git diff be80d1d14 5da8d3e46`: the two general-store scripts and integration.txt only.
Same setup as below: clone re/agents/sq23/or detached at the commit (tree clean before the run), Gradle home
volume `sq23-gradle`, every Gradle container through tools/buildq.sh (`--cpus=4 --memory=4g`, `-Xmx2g`, cache
tasks `-Xmx2600m`, `-Pkotlin.compiler.execution.strategy=in-process`, `--no-build-cache
--no-configuration-cache`, `--max-workers=2`, tests 1 worker).

| Step | Result | Time (incl. buildq queue) |
|---|---|---|
| `clean :or-cache:freshCache :or-cache:mergePluginGamevals` | pass | 1113 s |
| `:or-cache:buildCache` | pass | 376 s |
| `add_varcs.py` (1293, 1294), `fix_long_varps.py` (40 ops in 6 scripts: 778, 779, 5156, 5732, 5733, 9911), `fix_reftable.py` (groups 9973, 9974), `check_cache.py` (9975 groups, max 9974) | pass | 3 s |
| `generateRsa :server:app:installDist -x test` | pass | 620 s |
| `spotlessCheck` | pass | 154 s |
| `assemble` | pass | 310 s |
| `test --continue` (1 worker) | pass: 790 tests, 0 failed, 0 errors, 0 skipped | 724 s |
| `integration --continue` (1 worker) | pass: 210 tests, 0 failed, 0 errors, 0 skipped | 613 s |
| `fix_gameval_conflicts.py --check` | clean ("No gameval conflicts or unassigned IDs found.") | |

### Image and boot-check
- Image **`osrs-openrune:integration-5da8d3e`** (id 7c4e623c2d48), built with openrune/Dockerfile and
  .dockerignore from this clone's installDist, `.data` (fixed cache, gamevals, keys) and `game.yml`.
  `game.yml` and `.data/rsa.properties` are byte-identical to the live tree's (openrune/src).
- Boot-check (`--tmpfs /app/.data/postgres:uid=1000`, fresh DB): "Server ready in 11.26s", no ERROR, WARN or
  exception in the log.

### General stores (fix/55 dropped)
Static check at 5da8d3e46 on the packed SERVER cache (config archive 2, group 60). Both stores open an inv
whose size covers its stock, so Inventory.create cannot overflow:
- Edgeville General Store -> `inv.generalshop5`: size 15, restock on, 15 items (the wiki's Lumbridge stock; the
  wiki's Edgeville stock is the same minus the spade, with knife x1 at a 2-tick restock).
- Lumbridge General Store -> `inv.generalshop1`: size 13, restock on, 13 items (the wiki's Al Kharid stock; no
  spade, no knife, so the X Marks the Spot spade is not sold here).
This is the wiring the live server runs now. The mismatches against the wiki are known and come before this
cutover; fixing them is fix/55's job, once generalshop2.toml is repaired (see the be80d1d14 run below).

### Short dry run on a copy of the live DB
- `pg_dump -Fc` of the live osrs-openrune DB (read only; postgres:16-alpine client on its network namespace,
  live containers untouched). Restored with `pg_restore --clean --if-exists --no-owner` into the image's
  embedded PostgreSQL on volume `sq23-pgdata` (server `sq23-dry` on 127.0.0.1:43612), then restarted:
  "Server ready in 10.64s". Flyway after restart: `public` at 28 (23 migrations) and `main` at 1, both "up
  to date. No migration necessary", the same history as in the dump, so no migration runs at cutover.
- Headless login/logout (tools/multiclient/desktop_login.py, modulus served from scratch on 127.0.0.1 only):
  `tester`: login OK, 8 UPDATE_STOCKMARKET_SLOT, toplevel 601 (its saved gameframe is the mobile one, so the
  script's desktop-toplevel check reports FAIL; expected for this account). `desktop`: login OK, toplevel 161,
  PASS.
- Saves of both accounts (varps, inventories, objs, attrs, stats, position), compared before login and after the
  disconnect-logout had settled (saves stable across two snapshots 30 s apart): **no key lost**.
  For `tester`, these are unchanged: inventory (9), worn (4), bank (35), collection_transmit (2), stats, attrs and
  position. The GE offer is unchanged: `ge_slot1_item` 380 (lobster), count 1, price 1, state 2. The quest varps
  are unchanged (`cookquest` 2, `rjquest` 100, `sheep` 21, `doricquest` 0, `questlist` 5131, `qp` 11).
  Changed, as in a normal session: `playtime`, `map_clock`, `date_vars`, `cat_growth` (tester). Added:
  `ge_logout_minute` (both). `follower_npc` is back to 0 after logout.
- The server log had 1 line: `[ERROR] Cycle took too long (elapsed=604ms)` during the tester login. The host
  was running other agents' Gradle builds at the time. Load, not a functional error.
- Cleanup: `sq23-dry` and `sq23-pgdata` removed, the dump and the snapshots deleted.

### Cleanup
All sq23 containers and the local RSA HTTP servers are stopped. The superseded images
`osrs-openrune:integration-1f7af4e` and `-be80d1d` were removed. Kept: `osrs-openrune:integration-5da8d3e`
and the Gradle volume `sq23-gradle`. Logs:
/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/sq23/r4/.

## History: run at be80d1d14 (FAIL, fix/55-general-store-stock-wiring)
- Commit: `be80d1d148eee5cdf964e16b2c8dd26d7a2052a8`
- Internal gate: **FAIL**

be80d1d14 = 1f7af4e38's set with spotless commits on fix/71, content/looting-bag, fix/74, fix/76, fix/75 and
local/android, plus fix/55-general-store-stock-wiring (a2d14ae73) in integration.txt.

### What must change (be80d1d14)
fix/55 points the Edgeville General Store at `inv.generalshop2` (the mapping matches
tools/wiki-dumping shopmappings.csv), but that inv's server config is broken upstream:
`.data/raw-cache/server/shops/generalshop2.toml` (unchanged since 6299e4114 "Mapped some osrs shops", the same on
upstream/main) is truncated. It has no `sellMultiplier`/`buyMultiplier`/`delta`/`size`/`protect`/`runWeight`/
`restock`/`allStock`/`placeholders` lines, and the first nine `[[inventory.stock]]` entries are missing (the
chisel entry has also lost its header). Decoded from the packed SERVER cache built at be80d1d (config archive 2,
group 60):
- `inv.generalshop2` (4): size=0, flags=1 (protect only, no restock), stock hammer x5, knife x1 (2t), newcomer_map
  x5, sos_security_book x5.
- `inv.generalshop5` (24, Lumbridge): size=15, flags=4 (restock), 15 items matching the wiki's Lumbridge General
  Store stock exactly (pot 5/10t, jug 2/100, jug pack 5/20, shears 2/100, knife 5/100, bucket 3/10, bucket pack
  15/10, bowl 2/50, cake tin 2/50, tinderbox 2/100, chisel 2/100, spade 5/100, hammer 5/100, newcomer map 5/100,
  security book 5/100).
- `inv.generalshop1` (3, Al Kharid): 13 items, matching the wiki (no spade, no knife).

`Inventory.create` (engine/game/.../inv/Inventory.kt:159) allocates `arrayOfNulls(type.size)` = 0 slots and then
writes the 4 stock entries into `objs[0..3]`, so Shops.open (api/shops/.../Shops.kt:173) on the Edgeville
shopkeeper's Trade option throws ArrayIndexOutOfBoundsException. Before fix/55, Edgeville opened generalshop5 and
worked. Either repair generalshop2.toml on fix/55 to the wiki's Edgeville stock (that is generalshop5 without the
spade, with knife x1 restocking every 2 ticks, 14 slots, restock on, generalshop5's multipliers), or take fix/55
out of the cutover. The Lumbridge half is correct.

### Steps run at be80d1d
Uncached `clean :or-cache:freshCache :or-cache:mergePluginGamevals` (341 s), `:or-cache:buildCache` (89 s) and the
fix scripts (add_varcs 1293/1294, fix_long_varps 40 ops in 6 scripts, fix_reftable 9973/9974, check_cache 9975
groups, max 9974) passed. The rest of the chain (install, spotlessCheck, assemble, test, integration, image,
boot, DB dry run) was stopped once the failure above was confirmed, on main's instruction (fix/55 is dropped from
the next integration commit). `fix_gameval_conflicts.py --check` was clean per main.

## History: run at 1f7af4e38 (FAIL, spotlessCheck)
- Commit: `1f7af4e38934e5a4d485f3500c3d52281b5e17b9`

Scope: the live-server cutover ("safe to run live"): fork main c2492318d + the 61 branches of
local/integration-tools:integration.txt. Accuracy per branch is out of scope (each branch gets its own gates).
Run by sq23 on 2026-10-18 in its own clone (re/agents/sq23/or, detached at the commit), Gradle home volume
`sq23-gradle`, every Gradle container through tools/buildq.sh (`--cpus=4 --memory=4g`, `--max-workers=2`,
`-Xmx2g`, cache tasks `-Xmx2600m`, `-Pkotlin.compiler.execution.strategy=in-process`,
`--no-build-cache --no-configuration-cache`).

### What must change (1f7af4e38)
`spotlessCheck` fails in 7 modules (10 files). Each file is ratcheted against `origin/main` because a branch
touched it; run `spotlessApply` on each branch named below (on that branch, not on integration), push, rebuild
integration:
- **fix/71-item-config-tradeable**: `or-cache/.../codec/osrs/impl/ItemServerCodec.kt` (unused imports
  `DefinitionOpcodeParams`, `ObjectServerType`; `val rev : Int` -> `val rev: Int`; double blank line;
  `//Not sure` -> `// Not sure`), `or-cache/.../tools/PackServerConfigOSRS.kt` (import order of
  `CacheDelegate`; `private val rev : Int`).
- **content/looting-bag**: `api/core/.../module/PlayerModule.kt` (import order: `music.MusicPlayer` after the
  `hook.*` imports; the misorder is on main, but the branch's edit puts the file under the ratchet),
  `content/other/looting-bag/.../LootingBagScript.kt` (import order of `boolVarBit`),
  `.../LootingBagTakeHook.kt` (import order of `LootingBagScript.Companion.BAGS`),
  `content/interfaces/bank/.../BankInvScript.kt` (`RSCMType.COMPONENT,it.value.packed` needs a space after the
  comma; the file is touched by content/looting-bag and fix/74-bankside-view-op, check both).
- **fix/76-obj-add-timers**: `api/repo/.../obj/ObjRepository.kt` (import order of
  `java.util.concurrent.ConcurrentLinkedQueue`), `engine/game/.../obj/Obj.kt` (a blank line to remove near
  line 194).
- **fix/75-coal-bag-messages**: `content/skills/smithing/.../coalbag/CoalBagScript.kt` (a blank line to remove).
- **local/android**: `engine/game/.../entity/Player.kt` (a blank line needed after `public var username`,
  before the `mobileClient` KDoc).

Nothing else failed. The fixes are formatting only, but they are new commits: the build, the tests and the boot
must be re-run on the new integration commit.

### Build and test (at 1f7af4e38)
| Step | Result | Time |
|---|---|---|
| `clean :or-cache:freshCache :or-cache:mergePluginGamevals` (fresh OpenRS2 cache 2740, rev 241) | pass | 293 s |
| `:or-cache:buildCache` | pass | 41 s |
| `add_varcs.py` (1293, 1294), `fix_long_varps.py` (40 ops in 6 scripts: 778, 779, 5156, 5732, 5733, 9911), `fix_reftable.py` (groups 9973, 9974), `check_cache.py` (9975 groups, max 9974) | pass | 2 s |
| `generateRsa :server:app:installDist -x test` (keys and `rsa.properties` = the live image's / server/data's shared key; `.m2-rsprot` = openrune/src/.m2-rsprot, RSProt 1.0.0-ALPHA-20261001.99) | pass | 635 s |
| `spotlessCheck` (then `--continue` for the full list above) | **FAIL** | 36 s |
| `assemble` | pass | 94 s |
| `test --continue` (1 worker) | pass: 790 tests, 0 failed, 0 skipped | 283 s |
| `integration --continue` (1 worker) | pass: 210 tests, 0 failed, 0 skipped | 347 s |
| `fix_gameval_conflicts.py --check` | clean | |

The first `install` attempt hit `OutOfMemoryError` in freshCache with the JavaExec default heap (1 GB in a 4 GB
container); rerun with `-Xmx2600m` as qa.sh does. Infrastructure, not the branch.

### Image and boot-check (1f7af4e38)
- Image `osrs-openrune:integration-1f7af4e` (id 2fc8c22fdf99), built with openrune/Dockerfile and .dockerignore
  from this clone's installDist, `.data` (fixed cache, gamevals, keys, rsa.properties) and `game.yml`
  (identical to the live tree's).
- Boot-check (`--tmpfs /app/.data/postgres:uid=1000`): "Server ready in 12.1s", no ERROR, WARN or exception in the
  log.
- Built from 1f7af4e38, so it carries the FAIL above; it is not the image to deploy once the spotless fixes land.

### Dry run on a copy of the live DB (1f7af4e38)
- `pg_dump` of the live osrs-openrune DB (read only, through a postgres:16 client on its network), restored into
  the image's own embedded PostgreSQL on volume `sq23-pgdata`, server on port 43612 (live containers untouched),
  restarted on the restored data: "Server ready in 12.6s". Flyway history: 23 migrations, max 28, the same in the
  live DB and in the image's fresh DB, so no migration runs at cutover.
- Headless round trip (tools/multiclient/desktop_login.py, modulus served locally from server/data/rsa.properties):
  - `tester` / android-local: login OK, GE slots sent (UPDATE_STOCKMARKET_SLOT x8), toplevel 601 (its saved
    `gameframe` varp is the mobile frame, so the script's desktop-toplevel check reports FAIL; expected for this
    account).
  - `desktop` / desktop: login OK, toplevel 161, PASS.
- Saves before and after, every row of both accounts (varps, inventories, inventory objs, attrs, stats,
  position): **no key lost**. Inventory (9), worn (4), bank (35), collection_transmit (2), stats, attrs and
  position unchanged. GE offer unchanged: `ge_slot1_item` 380 (lobster), count 1, price 1, state 2. Quest varps
  unchanged (`cookquest` 2, `rjquest` 100, `sheep` 21, `doricquest` 0, `questlist` 5131, `qp` 11). Changed, as in a
  normal session: `playtime`, `map_clock`, `date_vars`, `cat_growth` (kitten growth ticks, same bit layout as live),
  `follower_npc` (0 at logout; PetFollowers.onLogout, the same code as the live tree), `score_gnomeball_game`
  (desktop). Added: `ge_logout_minute` (tester).
- Names dropped on load (decodeVarpKey skips unknown names, and the logout save then deletes them): **none**.
  Every distinct name in the live DB resolves in the image's gamevals (gamevals.dat + merged .rscm): 458 varps,
  88 objs, 13 invs, with no id change for names both trees list. The 13 other accounts were not logged in (their
  passwords are unknown); their 20 varp names not covered by tester/desktop all resolve, and the custom ones
  (`kc_cave_bug`, `temp_restore_65527`) have server configs in .data/raw-cache/server/varp.toml.
- No data migration needed (saves keyed by name, as expected).

### Cleanup (1f7af4e38)
Containers sq23-boot and sq23-dry removed, volume sq23-pgdata removed, the dump and the DB-copy snapshots
deleted. Kept: the image and the Gradle home volume `sq23-gradle` for the re-run. Logs:
/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/sq23/
(1-cache.log ... 8-integration.log, 5b-spotless-continue.log, boot.log, dry.log, summary).
