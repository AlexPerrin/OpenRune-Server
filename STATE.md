# Server state (OpenRune fork)

Read this first. It is rewritten, not appended to; history is in the server handoff and port notes
(`docs/server-handoff.md`, `docs/openrune-port.md` in osrs-plugins-reverse), which you only need for
details. Workflow: `docs/workflow.md` in osrs-plugins-reverse.

## What runs where
- **Live test server**: container `osrs-openrune` (game + JS5, port 43594), sidecars
  `osrs-openrune-http` (8080, Android, LOCAL_AUTH) and `osrs-openrune-http-desktop` (8082, desktop
  RuneLite via RSProx). Compose stack: `openrune/docker-compose.yml` in osrs-plugins-reverse.
- **Live code** (since 2026-10-19 04:05 UTC): fork `integration` at `5cc473397` (fork main `db78abf73` + the
  branches in integration.txt, local/integration-tools 06090a107), image `osrs-openrune:integration-5cc4733`, tagged
  `:241` for compose. Checked (dev's check, CI/qa.sh gate): qa/integration-cutover.md. Rollback: `osrs-openrune:rollback-5da8d3e`
  (previous integration), then `rollback-fb10fd3` (the retired openrune.patch tree).
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
  666570cd...).
- Unstacked #367/#368 (sd25, 2026-10-19; no fixture, no tests): each is the current PR head + a merge commit whose
  tree is main 829b9477d + the quest, so pushing onto the PR branch is a fast-forward. Fork branches
  `content/goblin-diplomacy-unstacked` d77ac2b76 (merge f81121fa7 of sd24's 4f5e59627, + RSProx fixes: crate "but
  find nothing", synth `pick`, dye synth 2392 `gobdip_dye_goblin_mail`) and `content/witchs-potion-unstacked`
  23e1b7c5d (tree = 4dc1ac9c7; varbit `witchs_potion_progress` 65449, the same id as sheep_shearer_progress: the
  second to merge re-runs fix_gameval_conflicts). Squash-merge clean in any order with #366's 069c15fde and the
  ladder; with true merge commits WP conflicts after #366 + GD (both heads still contain f69c8ed4c). In
  integration: `content/witchs-potion-829` (same tree). Upstream push waits for the user. The ladder is
  `fix/goblin-village-lookout-ladder` 475eefb76 (new PR). Specs accuracy/{goblin-diplomacy,witchs-potion,
  goblin-village-lookout-ladder}.md, texts and #367/#368 comments in pr-texts/.
- `fix/edgeville-general-store-stock` (sd24, off main bcdb03292, data only; the toml test was dropped after the upstream gate): rebuilds upstream's truncated
  `generalshop2.toml` (size 0 -> AIOOBE on Trade) to the wiki stock, size 40; #351 (fix/55) depends on it
  and waits until it merges upstream. Spec accuracy/edgeville-general-store-stock.md.
- Review commits (sd24, on the fork heads, not rebased): fix/18 adds `gameplay.idle-logout` (default true) and
  drops IdleHandlerTest (#348). fix/57 (#362) keeps the stone arrangement in the Temp varbit
  `gameframe_stone_before_fullscreen` 65447 on varp `gameframe_state` 65471; merge e551ba7ce brings in main
  829b9477d and uses #315's `Gameframe.isGameplay`, so the PR is now only the stone restore.
- Audited rewrites (sd23, 2026-10-19, each one commit on main 731fd392a, waiting for QA and then the user's
  approval upstream): `acc/cannon` bc243d141 (on main 829b9477d; replaces #354; timer mcannon_fire 65535; upstream gate FAIL on PR text
  only, text fixed by sd31 2026-10-20, re-gate next), `acc/genie` 3ccb86bd8
  (fork #18, replaces #355; on main 829b9477d; timers 65534/65533, zones in dbtable.random_event_zones; sd23 fixed
  sq24's six RSProx divergences, sd30 added "Maybe later." before he leaves; spec accuracy/genie.md rewritten
  with the RSProx comparison 3056/1718 2026-10-20; re-gate next), `fix/rune-pouch-accuracy` bee197ba9 (fork #17, follow-up to
  merged #356). #357 (plank sack) merged as is; its sourced gaps are backlog items.
- `fix/fish-barrel-check-and-destroy` (sd26, 2 commits on main 829b9477d, head 4e2d4562a): FishBarrelScript Check opens
  the item box (objectbox 193, read by the Fish Barrel hub plugin at 193:2) instead of game messages; Destroy (op 5)
  works (was swallowed); Fill on a full barrel sends the wiki's full message. No new ids. Spec
  accuracy/fish-barrel-check-and-destroy.md; no RSProx recording of the barrel exists. Fork PR to be opened by the
  orchestrator (pr-texts/fish-barrel-check-and-destroy.*).
- `local/*`: never upstream. android (our RSProt build, Android client type, mobile toplevel),
  local-auth (token logins, shared RSA key), test-commands, world-settings (members, Lumbridge
  start, 1x XP, no clans), skip-cs2-packs, treasure-trails (ours while upstream writes its own clue
  scrolls; ids renumbered, varp names unchanged; dbrow trail_emote_seq_14 63892, off fix/25's
  60920; Check steps names the tier, 48b320359, accuracy/clue-check-steps-tier.md), romeo-juliet (= fin/romeo-juliet; ours until upstream #299 merges, then drop it, since both
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
- Gate: `openrune/upstream/qa/qa.sh gate <clone>` (= `build`): one priority ticket (buildq class gate,
  2 slots), one 5 GB container, steps clean+qaCacheToolsClasspath, freshCache+mergePluginGamevals,
  buildCache, generateRsa, installDist, spotlessCheck, assemble+test classes, test (2 JVMs -Xmx1536m),
  integration (1 JVM), each `--no-daemon`, 4 workers, Kotlin daemon, `--no-configuration-cache`.
  Remote build cache (tools/gradle-cache.sh, test tasks never cached); OpenRS2 download shared by id
  (volume openrune-dl-cache). About 17 min cold, 8-12 min warm. Then boot to "Server ready in".
- Dev builds: `qa.sh dev <owner> <clone> [tasks]` in the warm container qa-dev-<owner> (class dev,
  never behind gates; incremental compile ~10 s); `qa.sh dev-stop <owner>` when done. Evidence only.
- Shipping to integration (user, 2026-10-19): no QA step. The dev hands over the spec (`accuracy-check` incl. RSProx),
  behaviour checked against it, and green CI at the commit (fork GitHub Actions; `qa.sh gate` where CI doesn't apply);
  the orchestrator adds it to integration and deploys. server-qa runs only the upstream gate.
- Fallback when the cache node is broken: `qa.sh split-build` (old uncached run, one container per step).
- Fork integration needs `.m2-rsprot` in the clone (qa.sh copies it from openrune/src).
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
4. Dev's check (accuracy spec incl. RSProx + green CI) for the new branches, then the upstream gate where they go upstream: fix/06-76, content/reconnect-login,
   dragon-specials, pirates-treasure, looting-bag, fletching.
5. Treasure Trails: drop local/treasure-trails once upstream's clue scrolls land (keep varp names).
6. Romeo & Juliet: drop local/romeo-juliet when #299 merges (check the varp 144 saves carry over).
