# Server state (OpenRune fork)

Read this first. It is rewritten, not appended to; history is in the server handoff and port notes
(`docs/server-handoff.md`, `docs/openrune-port.md` in osrs-plugins-reverse), which you only need for
details. Workflow: `docs/workflow.md` in osrs-plugins-reverse.

## What runs where
- **Live test server**: container `osrs-openrune` (game + JS5, port 43594), sidecars
  `osrs-openrune-http` (8080, Android, LOCAL_AUTH) and `osrs-openrune-http-desktop` (8082, desktop
  RuneLite via RSProx). Compose stack: `openrune/docker-compose.yml` in osrs-plugins-reverse.
- **Live code** (2026-10-18): still the old tree, `openrune/openrune.patch` (frozen) on fork main
  `c2492318d`, deployed as image bootcheck23 (commit fb10fd3). The next deploy moves it to a pinned
  `integration` commit.
- **Sidecar** image `osrs-rsps-test:241` is built from `server/` in osrs-plugins-reverse. jav_config
  param 3 is the desktop client's members-world flag (now true), param 5 the world flags.

## Branches (fork AlexPerrin/OpenRune-Server)
- `main` mirrors upstream; only `tools/fork.sh sync` moves it.
- `integration` = main + every branch in `integration.txt`, rebuilt by
  `scripts/build-integration.sh [remote]` (both on `local/integration-tools`). Recorded conflict
  resolutions (git rerere) are in `scripts/integration-rr-cache/`; an unrecorded conflict stops the
  build. To record one: resolve, `git add`, `git commit`, copy `.git/rr-cache/*` there, commit.
- `fix/NN-*`, `content/*`: one fix or feature each, off main. New changes are `content/<slug>` or
  `fix/<slug>` (no numbers), one change per PR (docs/workflow.md "Changes for upstream"). Real dependencies only:
  fix/69 on fix/07, local/skip-cs2-packs on fix/11, content/looting-bag on fix/71,
  content/pirates-treasure on fin/quest-test-fixture, content/sheep-shearer carries the same
  QuestTalkFixture commit (35900b277), local/test-commands on local/android.
- `content/sheep-shearer` (sd23, replaces #366's branch, Sheep Shearer only; ids from
  fix_gameval_conflicts.py) takes `fin/sheep-shearer`'s place in integration.txt; it needs the
  recorded resolution of its gamevals.toml lines next to content/pirates-treasure's (rr-cache
  666570cd...). #367/#368 still stack on #366's old commit f69c8ed4c (with Romeo & Juliet).
- `local/*`: never upstream. android (our RSProt build, Android client type, mobile toplevel),
  local-auth (token logins, shared RSA key), test-commands, world-settings (members, Lumbridge
  start, 1x XP, no clans), skip-cs2-packs, treasure-trails (ours while upstream writes its own clue
  scrolls; ids renumbered, varp names unchanged; dbrow trail_emote_seq_14 63892, off fix/25's
  60920), romeo-juliet (= fin/romeo-juliet; ours until upstream #299 merges, then drop it, since both
  bind Romeo, Juliet and the Apothecary), integration-tools.

## Ids and saved data
- New gamevals follow upstream's procedure (docs/workflow.md "Gameval ids" in osrs-plugins-reverse):
  write `name = -1`, run `python3 tools/scripts/fix_gameval_conflicts.py` on the branch, commit what it
  assigns; after `build-integration.sh`, run it with `--check` on the integration tree.
  `openrune/upstream/id-allocation.json` is the record of the 2026-10-18 renumbering; no new rows.
- Saves are keyed by **name** (`main.character_varps.varp = 'varp.sheep'`, `inventory_objs.obj =
  'obj.coins'`). Renumbering ids needs no data migration; renaming a varp, moving a varbit to other
  bits, or renaming an inv does. Unknown varp names are skipped on load and dropped on the next save.

## Build, test, deploy
- Every build container goes through `tools/buildq.sh <owner> -- docker run ...` (FIFO, 2 slots),
  `--cpus=4 --memory=4g`, `--max-workers=2`, `-Xmx2g`, `-Pkotlin.compiler.execution.strategy=in-process`,
  `--no-build-cache --no-configuration-cache` for branch checks. `integration` runs out of memory with
  2 workers: use 1.
- Branch gate: clean main cache, `buildCache mergePluginGamevals`, `spotlessCheck`, `assemble`, `test`,
  `integration` (separate runs), boot to "Server ready in".
- Our image also needs, after buildCache: `openrune/scripts/add_varcs.py`, `fix_long_varps.py`
  (OpenRune's CS2 compiler writes long varps with int opcodes), `fix_reftable.py` (zero-size groups
  crash the Android client), and `.m2-rsprot` (our patched RSProt, `openrune.sh rsprot`).
- Boot-check every image (`--tmpfs /app/.data/postgres:uid=1000`, wait for "Server ready"). Unit
  tests never run plugin `startup()`; startup crashes only show at boot.
- Only the orchestrator deploys (rollback tag first, both devices free) and pushes.

## Gotchas
- `spotlessApply` on the whole tree reformats files you didn't touch; run it on a branch, never on
  the live tree.
- A shared working tree for several branches leaks build outputs between them; use one clone or
  worktree per agent and `clean`.
- `completeQuest` returns false when declared item rewards don't fit (#339); hand-in items taken
  before it must be given back (Doric, Sheep Shearer fixed in the live tree, not yet on fin/*).
- Item-on-item: pair scripts in either order, then the target's default, then the selected item's
  (fix/73). Without it, firemaking's and herblore's defaults swallow items used on other objects.
- Bank `bankside:items` op 1 is the client's View/Configure (fix/74).
- The desktop client is F2P-looking unless jav_config param 3 is "true".
- Account data: tools/openrune-sql.sh for reads; writes only through the orchestrator.

## Cutover status (2026-10-18, end of the twenty-second server dev)
- Pushed to the fork: local/* (7 incl. treasure-trails), fix/* (21), content/* (5), docs.
- **Not pushed yet** (in scratchpad/sd22/or, a clone of re/upstream-pr/or):
  - content/pirates-treasure ca5b0d335: state varp 64053 -> 64083 (64053 is fin/farming's
    farming_growth_offset; buildCache failed on the duplicate).
  - local/integration-tools 4154e7edd: build-integration.sh stops on a merge that fails without
    a conflict; the recorded resolution for that varp line.
- `build-integration.sh .` there: integration 99d201e3e, all 60 branches, no unrecorded conflicts, no
  duplicate gameval ids (checked by a scan of every gamevals.toml).
- The uncached integration build, tests and boot-check have **not** passed yet (the only run stopped
  at the varp collision). The fin/* commits in integration.txt are the PR agent's and may still move.

## Next tasks
1. Push the two branches above; rebuild integration from the fork (`build-integration.sh origin`);
   uncached `buildCache`, the three fix scripts, `assemble`, `test`, `integration` (1 worker),
   image, boot-check.
2. Dry run: boot that image on a copy of the live DB (pg_dump approved for this, delete the dump
   after), log the tester in and out headlessly (`tools/multiclient/desktop_login.py`, password
   android-local), compare inventories, bank, GE offer and quest varps before/after. No data
   migration is expected (saves are by name).
3. Cutover: the orchestrator deploys the pinned integration commit; then the client QA pass.
4. QA gates (accuracy spec + checklist) for the new branches: fix/06-76, content/reconnect-login,
   dragon-specials, pirates-treasure, looting-bag, fletching.
5. Treasure Trails: drop local/treasure-trails once upstream's clue scrolls land (keep varp names).
6. Romeo & Juliet: drop local/romeo-juliet when #299 merges (check the varp 144 saves carry over).
