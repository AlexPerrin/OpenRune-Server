# QA: fix/fish-barrel-check-and-destroy
- Branch: `fix/fish-barrel-check-and-destroy`
- Commit: `4e2d4562a3eeaeeece1e9bffb6c58c36a963aa81`
## Upstream gate: **PASS**
## Internal gate: N/A (dev-checked)

Date: 2026-10-21 (sq31, server-qa). Fork PR #22 (open, head = this commit, base `main`). Dev: sd26, evidence
`re/evidence/fish-barrel-check-and-destroy/` (dev files `01..08-*.txt`, `local-gate-*.txt`; mine `qa-sq31-0[1-4]-*.txt`).

## 1. Build and tests: pass
- Fork GitHub Actions at the exact commit (both `workflow_dispatch`): CI
  https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39367710003 success, job "build & boot" (assemble,
  test, integration, boot); Formatting https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39367712026
  success. `gh pr checks 22` reports none on the PR itself (the runs were dispatched by hand); the sha matches.
- `python3 tools/scripts/fix_gameval_conflicts.py --check` at the commit: "No gameval conflicts or unassigned IDs
  found."
- Branch tree: 2 commits on 829b9477d (Doric's Quest); `upstream/main` is e85278c9a, 11 commits further.
  `git merge-tree` clean; the QA run clone `main e85278c9a + 4e2d4562a` (merge e3465e53b) built with `qa.sh gate-run`
  (compile 55 s, cache 250 s, installDist 97 s, all rc=0) and booted: "Server ready in 11.9s", no exception. Same
  treatment as earlier gates (idle-packet-logout: behind `main`, merges clean, fine).
- Tests: new `FishBarrelMessagesTest` (5 unit tests on the text builder) ran in CI; they fail without
  `FishBarrelMessages`. The item-moving path (Destroy deletes one slot after the confirmation) reuses
  `confirmDestroy` + `invDel(slot=)` as the engine's own `HeldInteractions.destroyWarning` does; no dupe path.

## 2. Accuracy: pass
`accuracy/fish-barrel-check-and-destroy.md` re-checked at the sources through `osrs-mcp` and the plugin repos
(not from the spec's quotes):
- Wiki "Fish barrel" (`wiki_page`): Check "tells the amount and type of raw fish"; "the Destroy right-click
  option will destroy the barrel itself, not the content"; full message "The barrel is full. It may be emptied
  at a bank." in the chatbox; infobox Destroy "Empty: You may win another barrel from Tempoross. Filled: The
  barrel and its contents will be lost. You may win another barrel from Tempoross."; several barrels "share
  their inventory". All match `FishBarrelMessages` and the destroy branch.
- Cache (`cache_search` item 25582/25584/25585/25587, LIVE and SERVER): `interfaceOptions=[Fill, Check,
  Open|Close, null, Destroy]` on 25582/25584 (op 5 = Destroy); param 65482 (= `destroy_note_desc`,
  `.data/gamevals/param.rscm:267`) "The barrel and its contents will be lost. You may win another barrel from
  Tempoross." on both; on the sack barrels "You may win another barrel from Tempoross and buy another fish
  sack from Alry at Molch lake."; no `destroy_note_title` (65481) param, so the default title. The code reads
  both through `params.*`, no raw ids.
- Gamevals: 193 = `interface.objectbox`, 229 = `interface.messagebox` (`gameval_search`); the box used is 193.
- Hub plugin molo-pl `fish-barrel` at the hub-pinned commit 6aaa599 (fetched by a subtask, quoted with line
  numbers): `CHECK_WIDGET_INTERFACE = 193`, `CHECK_WIDGET_COMPONENT = 2` read on `WidgetLoaded`;
  `FishBarrelWidgetParser`: `"The barrel is empty."`, `^The barrel contains: (([0-9]+) x [a-zA-Z ]+,? ?)+$`
  after `<br>`→space; fixture `"The barrel contains:" <br> "1 x Raw anglerfish, 2 x Raw monkfish, 3 x Raw" <br>
  "shrimps, ..."`; `BARREL_FULL_MESSAGE` as `GAMEMESSAGE` exact string. tictac7x `U_FishBarrel`: `(Your|The)
  barrel is empty.`, header `The barrel contains:`, entries `(\d+).x.(.*?)(,|$)` on GAMEMESSAGE/DIALOG/SPAM/MESBOX.
  The branch's texts satisfy both parsers (raw item names, `, ` separators, no trailing period, no tags).
- RSProx: the spec's rsprox.net searches (`re/ref/rsprox/search/fishbarrel_*.json`: OBJ 25582/25584/25585/25587
  and the texts, 0 recordings) stand; no local transcript has the barrel. The layout guesses stay guesses and are
  in the PR's caveats.
- No upstream overlap: `gh pr list -R OpenRune/OpenRune-Server --state all --search "fish barrel"` → only our
  merged #358 (skilling message wording); open PRs matching "barrel" → #354 (dwarf multicannon, unrelated);
  no issue. Scope: 3 files under `content/skills/fishing`, one fix, no `Depends:`.

## 3. In game: pass
Branch server (run clone above, port 43596, fresh DB) with the isolated desktop RuneLite, account
`desktop` made members with `qa-members.sh`, driven through the desktop devtools bridge (data tools; no
screenshots). Desktop lease 00:00-00:27 UTC-4 (hd lease). Evidence `qa-sq31-01..04`.
- Check, empty (25582): interface 193 open (`list_interfaces` visible 193), `get_widget` 193:2 text "The barrel
  is empty.", `get_dialogue` `{"type":"item","itemId":25582}`; chat history: one `MESBOX` "The barrel is
  empty.", no `GAMEMESSAGE` (01).
- Fill 3 raw shrimps + 2 raw anchovies → "You fill the fish barrel."; Check: 193:2 "The barrel contains:<br>2 x
  Raw anchovies, 3 x Raw shrimps" (02).
- Failure path, Fill with no raw fish: "You have no raw fish to store, or the barrel is full." (unchanged, not a
  claim of the PR).
- Filled to 28, one more shrimp, Fill → `GAMEMESSAGE` "The barrel is full. It may be emptied at a bank."; Check at
  28: "The barrel contains:<br>2 x Raw anchovies, 26 x Raw shrimps" (02).
- Destroy on the full barrel: interface 584, texts "Are you sure you want to destroy this item?", item 25582,
  "Fish barrel", "The barrel and its contents will be lost. You may win another barrel from Tempoross."; No →
  barrel still in slot 0, Check still 28 fish; Destroy again, Yes → inventory without the barrel (03).
- Open fish barrel (25584): Check "The barrel is empty." with item 25584; Destroy note "You may win another
  barrel from Tempoross."; Yes removes it (03).
- Fish sack barrel (25585): Destroy note "You may win another barrel from Tempoross and buy another fish sack
  from Alry at Molch lake."; No keeps it (03).
- Shared store: sack barrel filled with 1 shrimp, a second (plain) barrel spawned; its Destroy shows the filled
  note (store not empty), Yes removes only it; after the last barrel is destroyed a new barrel's Check is "The
  barrel is empty." (store cleared with the last barrel) (04). The sack barrel's inventory menu has no Check
  (cache: `Check`/`Open` are its worn-op params 451/452), so its Check was not driven; outside the PR.
- Plugin reaction: not checked here; the client task hub-fish-barrel-check verifies the hub plugin against the
  deployed server (definition of done).

## 4. PR text: pass
`pr-texts/fish-barrel-check-and-destroy.upstream.body` is byte-identical to PR #22's body. Every claim checked
against the commit and the run above: the before-texts (diff of `check()`/`fill()`), 193/objbox, the three
texts, op 5 wiring (`onOpHeld5(barrel) { destroy(it) }`), the empty/filled/sack notes, slot delete, the shared
store, 5 unit tests, spotless (Formatting run green), the in-game list. Template: Summary / Type of change /
How it was tested / Checklist / Screenshots-notes all present; "State in varbits/varps" left unchecked with the
reason (the contents stay in #191's attr, untouched); RSProx item unchecked with the reason (no recording);
subjects `fix: ...` (CONTRIBUTING `type: summary`); Claude Code line present. Caveats cover every gap found:
list splitting, which action sends the full message, destroying one of two barrels.

Notes, not blocking: the script's `destroy()` does not publish `HeldDropEvents.Destroy` (the engine's default
destroy does; its only listener is the OpenRune Central activity log). The hub plugin also expects bank-side
`GAMEMESSAGE`s ("You empty all of your containers into the bank.", "Your bank could not hold your fish.") where
the script still says "You empty the fish barrel into your bank."; out of this PR's scope, a follow-up change.

Verdict: **PASS**.
