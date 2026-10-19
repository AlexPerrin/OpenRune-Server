# QA: content/sheep-shearer
- Branch: `content/sheep-shearer`
- Commit: `19aefd90a8b7fa462c3560c60c25d9a2dc7058e6`
## Internal gate: **PASS**
## Upstream gate: **PASS**

Run by sq24 (companion to sd23) on 2026-10-18 in its own clone `re/agents/sq24/or`, detached at the commit.
The branch is two commits (35900b277 `QuestTalkFixture`, 19aefd90a the quest) on c2492318d, which is
current `upstream/main` (re-fetched after the run), so the branch tree is upstream `main` + the branch.
The dev's run (sd23 scratchpad `gate/sheep-shearer-19aefd90a.logs/`) was read as evidence only.

## Upstream gate, first run: FAIL (fixed in docs 4a35daadc; see "Upstream re-run" at the end)
The PR's Caveats miss two gaps against the sources. Add them to `pr-texts/sheep-shearer.upstream.body`
(and to `accuracy/sheep-shearer.md` as rows), or implement them; either is a new commit or text change,
and the upstream gate is re-run:
1. **The Thing doesn't move away when someone tries to shear it.** Wiki Sheep (penguins): "Attempting to
   shear the fake sheep will result in it walking away and doesn't provide any wool". Wiki Sheep Shearer,
   Obtaining the wool: "attempting to shear it will cause the penguins to squeak and run away from you".
   `shearTheThing()` plays `seq.human_shearing` and sends the message. It doesn't move the npc and plays no
   sound. The spec doesn't list this either.
2. **The spec's own scope gap isn't in the PR.** `accuracy/sheep-shearer.md` "Scope gaps" says the
   shearing animation and timing on The Thing use the generic `human_shearing` seq. The PR's Caveats leave
   it out. There is also no `faceEntitySquare`, delay or sound, which generic `Sheep.shearSheep` has.
   One caveat line can cover items 1 and 2.

Follow-up for the orchestrator (not a fault of this branch): upstream #367 (Goblin Diplomacy) and #368
(Witch's Potion) are our PRs, stacked on #366's old commit `f69c8ed4c`. Their diffs carry the old
Sheep Shearer (`SheepShearer.kt`, gamevals 63712/63715/63716) and Romeo & Juliet. Once #366's head is
replaced with this branch, they have to be rebased onto it and their "Stacked" lines updated. If they
aren't, they carry a second, conflicting Sheep Shearer.

## Internal gate
### build-test (at 19aefd90a)
Own Gradle home volume `sq24-gradle`; every container through `tools/buildq.sh sq24` (`--cpus=4
--memory=4g`, `--max-workers=2`, test/integration with 1 worker, `-Xmx2g`, cache tasks `-Xmx2600m`,
`-Pkotlin.compiler.execution.strategy=in-process`, `--no-build-cache --no-configuration-cache`), each
step a separate run.

| Step | Result |
|---|---|
| `clean :or-cache:freshCache :or-cache:mergePluginGamevals` | pass |
| `:or-cache:buildCache` | pass |
| `generateRsa` | pass |
| `:server:app:installDist -x test` | pass |
| `spotlessCheck` | pass |
| `assemble` | pass |
| `test --continue` | pass: 689 tests, 0 failures, 0 errors; `SheepShearerInteractionTest` 6/6 |
| `integration --continue` | pass: 71 tests in 15 suites, 0 failures |
| boot (`sq24-or-server`, installDist in `eclipse-temurin:21-jre`, game port 43598) | "Server ready in 17.93s", no exception or error lines in the log; the startup `check(quest.maxSteps == 21)` passed |

Tree after the run: only regenerated `.data/gamevals/*.rscm` files (build output); no source file changed.
`python3 tools/scripts/fix_gameval_conflicts.py --check`: "No gameval conflicts or unassigned IDs found."

### accuracy-check
The osrs-mcp tools did not load in this session: ToolSearch found none, even after the orchestrator
reported the server reconnected. Sources used instead: the OSRS Wiki through its API (raw wikitext;
revisions: Transcript:Sheep Shearer 15320468, Transcript:Fred the Farmer 15143769, Transcript:Sheep
(penguins) 15284321, Sheep Shearer 15367050, Sheep (penguins) current), Joshua-F/osrs-dumps
`config/dump.npc` and `config/dump.dbrow`, and the branch's own `.data/gamevals-binary/gamevals.dat`.

Cache and ids, checked: npc 731 `sheep_shearer_the_thing` has `op1=Shear` and `op3=Talk-to`, so the
`onOpNpc1` and `onOpNpc3` bindings are right. Fred is npc 732 (`op1=Talk-to`). Obj 7670
`crafting_icon_dummy`, varp `sheep`=179, shears 1735, ball_of_wool 1759, seq `human_shearing` 893.
dbrow `quest_sheepshearer`: endstate 21, questpoints 1, crafting 1500 (tenths), so 150 XP. The Thing is
spawned from `lumbridge.toml` with no content group, so generic `Sheep` doesn't also bind it, and the
boot shows no conflict.

Spec rows against the code and the transcripts:
- Rows 1-3 (varp 179, rewards, partial and noted deliveries) match. Noted balls are a different obj, and
  `inv.count(obj.ball_of_wool)` counts only unnoted ones. The 60 coins aren't in the dbrow and come from
  the wiki's Rewards.
- Rows 4-12 (Fred's dialogue) match the Transcript:Sheep Shearer text line for line: the opening, the
  offer, the decline, the 20-ball branch right after "...the small matter of The Thing." with no
  exposition or prompt, the shears box, the spinning help with the pic-7670 box, the remaining count, the
  three help options including "I don't know how to spin wool" -> `tact above`, the unspun-wool reply,
  the partial hand-in lines and the final hand-in with no give box.
- Rows 13 and 15 (shear message, Talk-to line) match Transcript:Sheep (penguins).
- Rows 16-17 (the "I've seen The Thing" option during and after the quest; kill / lost otherwise) match
  Transcript:Fred the Farmer. "Interacted" is read as a shear attempt while holding shears, which is a
  caveat.
- Rows 14 and 18 (messages with no source) are caveats.
- Gap the spec misses: The Thing doesn't walk away or squeak when sheared (see "What must change", item 1).
  It's cosmetic and moves no items, so it doesn't block the live server, but it has to be a caveat
  upstream.

Item paths (AGENTS.md): the source is re-counted after every suspension before the take
(`alreadyHaveTheWool` re-checks for 20 balls; `handIn` takes `min(count, remaining)` with no suspension
in between). If the reward fails, the exact amount taken is given back (tested, `Int.MAX_VALUE` coins).
Shears go through `invAddOrDrop`.

### In-game
Done statically, not in a client. The orchestrator granted the desktop lock, then said the
osrs-devtools-desktop MCP tools aren't available in agent sessions and asked for a static check instead.
The lock was never taken. The static check: the op slots against the cache dump (above), and the
dialogue against the transcripts (above). The tests cover the item-moving paths. The branch server
booted (above) and was then removed. Nothing renders that a plugin reads, apart from varp 179 for Quest
Helper, which the tests check (1, 6, 21).

`reverse`: n/a (no claim about the client protocol).

**Internal verdict: PASS.** Build, spotless, tests, integration and boot pass at the commit. Behaviour
matches the spec and its sources, apart from one cosmetic gap the spec doesn't list (The Thing doesn't
walk away), which is for the upstream caveats.

## Upstream gate
- Internal PASS at the same commit: yes.
- Scope, one feature: the quest plus its test fixture (test sources only), declared under **Depends:**.
  Nothing unrelated in the diff (5 files: `gamevals.toml`, `sheep_shearer_vars.toml`, `SheepShearer.kt`,
  `QuestTalkFixture.kt`, `SheepShearerInteractionTest.kt`).
- Upstream overlap: `gh pr list -R OpenRune/OpenRune-Server --search sheep|shearer|fred` turns up #366
  (ours, which this replaces), #367 and #368 (ours, stacked on #366; see the follow-up). #299 (Romeo &
  Juliet) shares no file and no content with this branch. Every open PR's diff was grepped for the ids:
  #231's 65448/65449 are varps, not varbits, so they don't clash with these varbits, and nothing else
  uses varp 65472.
- Based on current `upstream/main` c2492318d: yes.
- `fix_gameval_conflicts.py --check`: clean.
- CONTRIBUTING (`.github/CONTRIBUTING.md`) and AGENTS.md: the commits use `feat:` subjects. The text has
  context, release note, the checklist, data sources with the CC BY-NC-SA attribution, the Claude Code
  line and a **Depends:** line. The code follows AGENTS.md: varbits/varps, not attrs; gameval symbols,
  not raw ids; constants in constant case; no narration comments; tests limited to item-moving paths,
  as AGENTS.md asks.
- `pr-text-review`: every claim was checked against the commit, and all are true: varp values, the
  startup check, the rewards, the dialogue branches, noted balls, the 20-ball path, the reward-failure
  rollback on both paths, The Thing's ops and message, varbit gating, the gamevals, the 6 tests and what
  each covers, "Nothing was checked in game from this exact branch". The build numbers in the text are
  the dev's run; this run agrees (all pass), only the boot time differs (17.93s here, 11.42s there).
- Caveats cover every gap: **no**, items 1 and 2 under "What must change".

**Upstream verdict: FAIL.** Only the caveats need changing; the code doesn't have to. If the fix is a
PR-text change only, with the commit still 19aefd90a, the internal PASS stands. The upstream gate is
re-run on the new text.

## Upstream re-run (docs 4a35daadc, commit still 19aefd90a)
Re-run by sq24 on 2026-10-18. The code commit didn't change, so the internal PASS and the build-test
results above still stand. sd23's fix only changes text: `pr-texts/sheep-shearer.upstream.body` has one
new Caveats line, and `accuracy/sheep-shearer.md` has row 19 and an updated scope-gap line. Diff checked
against 28f1fe5e7:
- First-run item 1 (The Thing doesn't walk away or squeak) is covered by the new caveat, which quotes
  both wiki sources, and by spec row 19.
- First-run item 2 (generic animation and timing; no face, delay or sound like `Sheep.shearSheep`) is
  covered by the same caveat line and by the spec's scope-gap line.
- The new caveat's claims are true of 19aefd90a: `shearTheThing()` only calls `anim("seq.human_shearing")`
  and `mes(...)`. It doesn't move the npc, and it has no `faceEntitySquare`, `delay` or `soundSynth`.
- The rest of the PR text is unchanged, and every earlier claim is still true at the same commit.
- `upstream/main` is still c2492318d (re-fetched). The open PRs matching "sheep" are still only #366,
  #367 and #368 (all ours; the #367/#368 follow-up above still applies).
- Minor, not blocking: spec row 17 says the flag is "set on any shear attempt", but the code sets it only
  when shears are held. The PR text says it correctly ("A shearing attempt while holding shears").

**Upstream verdict (re-run): PASS** at 19aefd90a, with the PR text in docs 4a35daadc.
