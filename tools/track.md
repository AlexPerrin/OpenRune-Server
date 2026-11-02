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
- Default no tests, except a dupe test per item-moving flow (below), a rule with several input cases, or a test a
  reviewer asked for, each named so in the PR text; never a test of an interaction, a dialogue, quest stages or a
  script's flow (qa fails `style` on it). No new KDoc or explanatory comments. Nothing public for a test. Toggles in the config with a default.
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
`tools/server/gamevals.py assign <clone>` (the highest id free in upstream main, every pin of hd.yaml and every open dev head)
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
`tools/track.sh test <slug> [module...]` builds, then runs each module's tests, its integration tests (a module with `src/integration`, as upstream CI's `integration`) and spotlessCheck on the build copy
(no module: every module the change touches) and writes `evidence/<slug>/test.log` (the `spotless` row runs spotlessCheck alone on those modules at T, so lint in a touched file fails the hand-over, not CI); never gradle by hand.
`tools/track.sh log <slug> <pattern>` reads the server log; `tools/track.sh restart <slug>` logs the desktops out, restarts that server,
its game.yml and database kept, and logs the desktops in again. A run's server has idle logout off (live keeps it), and
until `down` each desktop that is logged out (a disconnect, a server restart, a logout of yours) is logged in again
(`state/<slug>/keep-<port>.log`); a refusal is not retried until the server restarts. Each up starts a fresh database; `RESUME=1
tools/track.sh up <slug>` starts from the accounts as the change's last `down` saved them (down waits until the server
has saved and dropped every character), so a run resumes where the last stopped. The realm is live's (`dev_mode` off, xp at 1x) and every
desktop's account a member at rank 0, as a player on live; `STAFF=<N>[,<M>...]` on one `up` makes those desktops' accounts
administrators, for rows that need staff on several accounts. `tools/track.sh up <slug> token` starts one desktop whose login is a token
login (auth type 7) for its account, the token minted by the local issuer `token-<slug>` (`server/issuer.py`: a compact
signed token of at most 90 bytes, whose secret game.yml's `token-login` block holds); `tools/track.sh token <slug>
[TOKEN]` sets the token its next login sends (`server/issuer.py mint` makes expired and forged ones). The server `up` starts runs the image's game.yml with
`quest-requirements` `mode` set by the change's front matter `quest-mode:`: `respect-progress` by default, so every
quest requirement is judged for real, or `assume-completed` (upstream's default, which skips every quest check) for
content gated by a quest main does not register; `QUEST_MODE=<mode>` on one `up` overrides it (a refusal row of
the other mode). PR notes name it as the verification setting. Every server run goes
through track.sh, never a container by hand. Helper scripts and evidence live in `evidence/<slug>/`, never a session scratchpad. Test through `osrs-devtools-desktop` data tools; a
screenshot only for how something is drawn. Write `pr-title.txt`, `pr-body.md` (on an open PR they replace the live ones
that differ), `pr-comment.md` (the push summary: names the head and every commit the push carries since the PR's live
head, no quote, no @) and, for each finding answered, `pr-reply-<finding id>.md` (the reply alone: the finding quoted
with `> `, @ only its author). Push `dev/<slug>`, then `hd handover <slug>`:
it runs every script row of the contract and refuses on any failure. `hd ask <slug> "..."` when a decision is not
yours; `hd show <slug>` first, always.

The desktops are Windows programs run from bash through WSL interop by absolute path, located by
`WINDOWS_SYSTEM32` (default `/mnt/c/Windows/System32`: netstat.exe, netsh.exe, taskkill.exe, robocopy.exe) and `DESKTOP_DIR`
(default `/mnt/c/osrs-test`: the template, its JDK, RSProx launcher and staged home; jps.exe locates JVMs). Each up
creates the desktops it needs: instance N is `DESKTOP_DIR/run/N`, claimed by its mkdir (the first N whose devtools port
7779+N no program holds), naming the up's slug and its claim in `run/N/owner`, with its own home copied from the template's.
`desktop_login.py` (up's and restart's logins, the keeper) acts only on the instance whose owner file holds the text it started with: it sends no call once that instance changes hands, and a keeper ends then.
One up, restart or down of a slug runs at a time (`state/<slug>/desktops.lock`): a second waits for the first, so none acts on desktops another is starting or removing. Its launcher is
`tools/server/OsrsTestLauncher.java`, compiled once per source into `DESKTOP_DIR/rsprox/launcher-<sha>`; RSProx's ports
(jav_config 43600+skip, proxy 43701+skip) are moved past every range Windows refuses a bind on (WSL's mirrored
reservation of the Linux ephemeral range, which moves each boot, and Windows' excluded port ranges), and a launcher whose
start throws exits, so up fails with its exception. Every JVM
of instance N carries `-Dosrstest.marker=osrs-test-runN`, and only such a JVM is ever stopped (never the user's own
RuneLite). `state/<slug>/up.json` is the stack's one record, written by up before its first container: its `containers`, the
`script` that started it (down and hd's repair run that script's `down`), its key's `modulus`, `accounts`, `staff` and `token`;
`down` removes the containers it names, ends when the server is gone at any point, and removes the record once none is left.
The live stack's key pair is `state/live/key` of osrs-harness (the modulus every APK carries): ship puts it in each image it deploys,
refuses an image whose key is not it before the boot and the tag, and names its modulus in `state/live/tree.json`.
up lists each desktop in `up.json` (field `desktops`: `desktopN:port`) before its login, with the up's `token`:
each desktop's devtools endpoint answers only a request carrying `Authorization: Bearer <token>` (server/DevtoolsOwner.java,
loaded into every JVM of the instance), so a script that calls it directly sends its own change's token; `down` stops the
JVMs of every instance its slug owns until none is alive and none holds a file (jps can miss a live one), then removes those instances, each owner file last so a failed down re-runs; each JVM up starts is recorded, its Windows pid and image, in `state/winpids/<the run's slice>/` of osrs-harness, which ends it with the run, and down drops the record of each JVM it stops; The server logs out a desktop that sends no input
for a while, as live does: a script that only reads (a sampler) presses a key now and then, and reads `gameState`
from `get_client_state`; the `osrs-devtools-desktop` answers name a session that dropped. A dropped desktop logs in
again with `python3 tools/server/desktop_login.py <port> <account> <account>` (both from `up.json`). Each launcher's
output is `state/<slug>/desktopN.log`; the client's own is `DESKTOP_DIR/run/N/home/.runelite/logs/client.log` while the
instance lives. a build waits for a running build of its slug (`state/<slug>/build.lock`) and reuses its image.

## The reviewer
Never writes the code it checks. Judges every contract row from the same build (`tools/track.sh up <slug>`),
in game for every player-facing claim (on a resumed up only a row whose every step it plays after the resume, never
one whose steps the saved state skips), and records `hd verdict <slug> <head> rows.json` with every row pass or
fail and a note per failure. A text-only failure (PR title, body, comment): fix the text in the evidence dir and
mark the row pass. A difference from the official game is a failed row, never a caveat. Only an entry of the
definition's ## Answers accepts a difference: a list in accuracy.md, a `kind: local` change or a test-only branch
accepts none. A failing note's remedy agrees with the source it cites.

## Never
Touch a mobile or the tester account's items (keep its GE lobster offer, drop only what you spawned);
restart the live server (`osrs-openrune*`); push any ref but yours; open or edit PRs; connect to Jagex; read
`C:\Users\Alex\.runelite`; stop a process you did not start; use the DB tool for writes.

## Seed rows
Rows the contract starts from (a row tagged `upstream` judges the upstream PR and seeds only a change that opens one; `check:` rows run with SLUG, HEAD, T, TCOMMIT, BASE, CLONE, BUILD, EVIDENCE in the env).
- claims | every PR claim, id, file, test name and ticked box is true of the exact head | judge: pr-text
- rsprox | timings, steps and messages checked against RSProx recordings tick by tick before the wiki | judge: accuracy
- no-deferral | no Not-done list, no unticked item, no open ask; every reported defect fixed in this PR | judge: pr-text | upstream
- net-change | on an existing PR, pr-comment.md covers only the commits since the PR's live head | judge: pr-text | upstream
- unsourced | the PR notes name every reconstruction and every cheat used to verify | judge: accuracy
- no-raw-data | no game data or raw ids in Kotlin; gameval symbols; state in varbits and varps | judge: qa-server
- playable | every flow playable without a cheat; in-game evidence in the evidence dir | judge: qa-server
- one-change | one feature, nothing unrelated, no open upstream PR for the same content, Depends stated | judge: pr-text | upstream
- style | no added comments or KDoc, no test but a dupe test, a multi-case rule or a reviewer's request, nothing public for a test, no quirk entries | judge: qa-server
- dupes | each item-moving flow's four paths tested with totals, or a reason that holds | judge: dupes
- accuracy | each rule of accuracy.md matches in game; a difference no entry of the definition's ## Answers accepts fails it | judge: accuracy
- evidence | accuracy.md, pr-title.txt, pr-body.md, pr-comment.md present and non-empty | check: tools/server/text.py evidence
- subject | commit subjects (on an existing PR, those after its live head) start with feat: or fix:; no Claude-Session trailer; the co-author line only | check: tools/server/text.py commits | upstream
- texts | template sections present; no fork PR link; pr-comment.md names HEAD | check: tools/server/text.py texts | upstream
- gamevals | custom gameval ids unique across upstream main, the pins and open dev heads | check: tools/server/gamevals.py check
- dupe-tests | every module whose main sources gain an item-moving call has a test source added or changed | check: tools/server/text.py dupes
- spotless | spotlessCheck passes on every module the head changes from main | check: tools/track.sh spotless
- ci | fork CI and Formatting green on gate/<slug> at T | gate: tools/track.sh gate-ci
