# Server track

The fork AlexPerrin/OpenRune-Server (of OpenRune/OpenRune-Server) is the test server; a change reaches it as soon as
its gate passes and, independently, upstream as a PR after the user's yes. `hd` is `python3 ~/osrs-harness/hd.py`;
the track script is `tools/track.sh <verb> <slug>`; the evidence dir is `evidence/<slug>/`.

## Branches on the fork
`main` mirrors upstream. `dev/<slug>` is the dev's ref, the only one a dev pushes; the change's head is its tip.
`content/<slug>` and `fix/<slug>` are the public PR branches, moved only by the harness after the user's yes.
`local/*` are test-only pieces that never go upstream. `gate/<slug>` holds the gate's merge of T. `local/merge/<a>-<b>`
is a recorded resolution of two heads, used only by the integration tree.

## Rules for upstream (re-read AGENTS.md, CONTRIBUTING.md and the PR template on upstream `main` every run; the
contract records their blob shas)
- One change per PR: one quest, feature or fix, off current upstream `main`, never stacked, no commit of another
  open PR. Shared code is its own change that merges first; dependencies are `Depends:` in the PR text.
- Check upstream first (gh pr list on OpenRune/OpenRune-Server with --search, and the issues). Theirs wins.
- A PR is ready only when every reported defect is fixed in it; nothing is promised for a later PR, no Not-done list,
  no unticked checklist item.
- Updates are new commits on the PR branch (merge `main` in, never force-push); the description is never replaced.
- Default no tests, except a dupe test per item-moving flow (below) and a rule with several cases, justified in
  the PR text. No new KDoc or explanatory comments. Nothing public for a test. Toggles in the config with a default.
  Subjects `feat:`/`fix:`. State in varbits and varps; gameval symbols, never raw ids; game data in the cache, not
  in Kotlin. A difference from the official game is fixed, never recorded as a quirk; one the server cannot fix is
  `hd ask`.
- Upstream texts never link a fork PR (`AlexPerrin/OpenRune-Server#N`, `.../pull/N`); naming the fork is fine. Never
  open a PR, issue or comment on the fork.

## Accuracy (the spec `evidence/<slug>/accuracy.md`, written for the contract, checked by dev and qa)
Sources in order: RSProx recordings of the official game and the OSRS wiki (with the cache and Mod posts) together;
where they disagree cite both and follow the newer; LostCity and other reference implementations only as evidence for
a guess, never a source. Look up with the `osrs-mcp` tools. Recordings may hold other players' names and chat: never
commit, quote or upload them; cite recording ids and packets. Per mechanic: its source, the behaviour, what the
recording shows (anims, spot anims, sounds, messages, dialogue order, varps per stage, interfaces, tick timings).
A claim of "no recording" is made only after `tools/track.sh recordings <packet|text>` searched the index.
A rule the wiki names but no cache, recording or transcript bounds is a reconstruction: implement the best one and
name it in the PR notes as unsourced.

## Gameval ids
Use the official id when the cache has one (`gameval_search`). For new state write `name = -1`, run
`tools/server/gamevals.py assign <clone>` (the highest id free in upstream main, every pin and every open dev head)
and push at once. The gate runs `gamevals.py check`: of two branches holding one id under two names, the later taker
renumbers.

## Dupe tests (upstream AGENTS.md "Unit tests")
Every flow that moves items (`inv*` of `api/invtx` and `ProtectedAccess`, `wornUnequip`, `objRepo.add/del`, shop ops)
has one test class in the module's `src/test/kotlin`, built like `FoundryRewardTransactionsTest.kt` or
`BullBonesTest.kt`, covering no space, overflow at `Int.MAX_VALUE`, repeats and races, and accounting of every obj's
total. A path that cannot occur is one plain line in the PR notes. QA tries each flow in game: full inventory, fast
repeat, source removed mid-action, counts read with `get_inventory` and `get_container_history`.

## The dev
Clone `~/src/work/<slug>` (a shared clone of `~/src/OpenRune-Server`, remote `fork` = the fork, `origin` = upstream).
Never edit the shared clone. Commit; `tools/track.sh build <slug>` builds T in the build copy; `tools/track.sh
up <slug> [N]` starts the server from that build with N logged-in desktops and prints their devtools ports;
`tools/track.sh log <slug> <pattern>` reads the server log; `tools/track.sh restart <slug>` restarts that server,
its game.yml and database kept, and logs the desktops in again. The server `up` starts runs the image's game.yml with
`quest-requirements` `mode` set by the change's front matter `quest-mode:`: `respect-progress` by default, so every
quest requirement is judged for real, or `assume-completed` (upstream's default, which skips every quest check) for
content gated by a quest main does not register; `QUEST_MODE=<mode>` on one `up` overrides it (a refusal row of
the other mode). PR notes name it as the verification setting. Every server run goes
through track.sh, never a container by hand. Test through `osrs-devtools-desktop` data tools; a
screenshot only for how something is drawn. Write `pr-title.txt`, `pr-body.md`, `pr-comment.md` (names the head
and covers every commit the push carries since the PR's live head). Push `dev/<slug>`, then `hd handover <slug>`:
it runs every script row of the contract and refuses on any failure. `hd ask <slug> "..."` when a decision is not
yours; `hd show <slug>` first, always.

The desktops are Windows programs run from bash through WSL interop by absolute path, located by
`WINDOWS_SYSTEM32` (default `/mnt/c/Windows/System32`: netstat.exe, taskkill.exe) and `DESKTOP_DIR` (default
`/mnt/c/osrs-test`, the Nth instance `DESKTOP_DIR<N>`, devtools port 7779+N; jps.exe locates JVMs). Every JVM of
instance N carries `-Dosrstest.marker=osrs-test-N`, and only such a JVM is ever stopped (never the user's own RuneLite).
An up, under `state/desktop.lock`, first stops the desktops of every instance no live run's `up.json` lists (a live run's
server container runs), then claims the first instance that no run lists and no desktop answers on, and keeps the lock
until it lists that desktop in `up.json` (field `desktops`: `desktopN:port:pids`, the launcher's and the port's Windows PIDs) before its
login; `down` stops exactly those PIDs.

## The reviewer
Never writes the code it checks. Judges every contract row from the same build (`tools/track.sh up <slug>`),
in game for every player-facing claim, and records `hd verdict <slug> <head> rows.json` with every row pass or
fail and a note per failure. A text-only failure (PR title, body, comment): fix the text in the evidence dir and
mark the row pass. A difference from the official game is a failed row, never a caveat.

## Never
Touch a mobile or the tester account's items (keep its GE lobster offer, drop only what you spawned);
restart the live server (`osrs-openrune*`); push any ref but yours; open or edit PRs; connect to Jagex; read
`C:\Users\Alex\.runelite`; stop a process you did not start; use the DB tool for writes.

## Seed rows
Rows the contract starts from (`check:` rows run with SLUG, HEAD, T, BASE, CLONE, BUILD, EVIDENCE in the env).
- claims | every PR claim, id, file, test name and ticked box is true of the exact head | judge: pr-text
- rsprox | timings, steps and messages checked against RSProx recordings tick by tick before the wiki | judge: accuracy
- no-deferral | no Not-done list, no unticked item, no open ask; every reported defect fixed in this PR | judge: pr-text
- net-change | on an existing PR, pr-comment.md covers only the commits since the PR's live head | judge: pr-text
- unsourced | the PR notes name every reconstruction and every cheat used to verify | judge: accuracy
- no-raw-data | no game data or raw ids in Kotlin; gameval symbols; state in varbits and varps | judge: qa-server
- playable | every flow playable without a cheat; in-game evidence in the evidence dir | judge: qa-server
- one-change | one feature, nothing unrelated, no open upstream PR for the same content, Depends stated | judge: pr-text
- style | no added comments or KDoc, no unjustified test, nothing public for a test, no quirk entries | judge: qa-server
- dupes | each item-moving flow's four paths tested with totals, or a reason that holds | judge: dupes
- accuracy | each rule of accuracy.md matches in game or is a fixed difference | judge: accuracy
- evidence | accuracy.md, pr-title.txt, pr-body.md, pr-comment.md present and non-empty | check: tools/server/text.py evidence
- subject | commit subjects (on an existing PR, those after its live head) start with feat: or fix:; no Claude-Session trailer; the co-author line only | check: tools/server/text.py commits
- texts | template sections present; no fork PR link; pr-comment.md names HEAD | check: tools/server/text.py texts
- gamevals | custom gameval ids unique across upstream main, the pins and open dev heads | check: tools/server/gamevals.py check
- dupe-tests | every module whose main sources gain an item-moving call has a test source added or changed | check: tools/server/text.py dupes
- ci | fork CI and Formatting green on gate/<slug> at T | gate: tools/track.sh gate-ci
