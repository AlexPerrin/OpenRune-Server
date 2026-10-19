# Sheep Shearer (content/sheep-shearer)

Spec for the Sheep Shearer quest on its own, split out of upstream PR #366 ("Sheep Shearer and Romeo &
Juliet") after the maintainer asked for Romeo & Juliet to be left to #299. Rows reuse the 2026-10-18 audit
in osrs-plugins-reverse `openrune/upstream/accuracy/quests-sheep-romeo.md` (rows 1-17, D1-D5), updated for
the branch commit; the Romeo & Juliet rows (18-37, D6-D14) are out of scope here.

Sources: OSRS Wiki pages Sheep Shearer (Rewards, Spinning the wool, Changes), Transcript:Sheep Shearer,
Transcript:Fred the Farmer, Transcript:Sheep (penguins); Quest Helper `SheepShearer` (BSD; varp 179,
steps 1-20); the cache (npc 731 `sheep_shearer_the_thing` ops Shear / Talk-to, obj 7670
`crafting_icon_dummy`, the quest dbrow `quest_sheepshearer` end state 21). The osrs-mcp server was not
reachable for this pass; the citations are the audit's, checked against the code below.

## Upstream first
- `gh pr list -R OpenRune/OpenRune-Server --search sheep` (2026-10-18): only #366 (ours) and #242 (closed;
  "Add nine free-to-play quests", closed by the maintainer as several quests had landed). No issue.
- `main` (c2492318d): no Sheep Shearer script. Generic shearing (`content/generic/generic-npcs/.../Sheep.kt`),
  spinning and the castle stairs exist and are reused.
- Romeo & Juliet: upstream #299 (open, "Add the Romeo & Juliet quest with working mansion stairs"). Not in
  this change; our version stays on `local/romeo-juliet` for the live server until #299 merges.

## Mechanics
| # | Mechanic | Official behaviour (source) | Planned / branch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Progress varp | varp 179; 0 not started, 1..20 in progress, wool needed = 21 - varp (Quest Helper `SheepShearer`) | STARTED 1, + balls handed in, COMPLETE 21; startup check against the dbrow's end state | matches |
| 2 | Rewards | 1 QP, 150 Crafting XP, 60 coins (Sheep Shearer, Rewards) | `rewards { xp(crafting,150); item(coins,60) }`, QP from the dbrow | matches |
| 3 | 20 unnoted balls, partial deliveries | "you can make smaller deliveries"; noted balls refused (Sheep Shearer, Spinning the wool) | takes `min(unnoted balls, remaining)` | matches |
| 4 | Fred's opening, three options | Transcript:Sheep Shearer, Starting off | `beforeQuest` | matches |
| 5 | Offer and start prompt, decline | Transcript, Starting off ("Start the Sheep Shearer quest?"; "No, I'll give it a miss." / "Suit yourself.") | `offerQuest` | matches |
| 6 | Holding 20 balls at the start | the "In fact Fred, funnily enough..." branch right after "...the small matter of The Thing.", no Thing exposition, no prompt, no give box (Transcript, Starting off; Changes 2 Aug 2018) | `alreadyHaveTheWool` after the third Fred line; takes 20 balls, sets 21 | matches (D1 fixed) |
| 7 | Shears from Fred | "Fred gives you a set of sharp shears." when the player has none (Transcript; Changes 14 May 2020) | `howToShear`: `invAddOrDrop` + objbox; no second pair if held | matches |
| 8 | Spinning help and icon box | tbox with the spinning wheel map icon (pic 7670) (Transcript) | `objbox(obj.crafting_icon_dummy, ...)` (obj 7670) | matches (D2 fixed) |
| 9 | Mid-quest count and three help options | Transcript, Before delivering any balls of wool | `shearingTalk` | matches |
| 10 | Unspun wool reply | Transcript, With regular wool | `shearingTalk` | matches |
| 11 | Partial hand-in | "You give Fred [1-19] ... of wool." / "That's all I've got so far." / "I need [n] more..." (Transcript, Handing in) | `handIn` | matches |
| 12 | Final hand-in | "That's the last of them." / "I guess I'd better pay you then.", no give box (Transcript, Finishing up) | `handIn` completion branch, no box | matches (D3 fixed) |
| 13 | Shearing The Thing | "The... whatever it is... manages to get away from you!", no wool (Transcript:Sheep (penguins)) | op1 Shear and shears used on npc 731 | matches |
| 14 | Shearing The Thing without shears | not documented | OpenRune's sheep line "You need a set of shears to do this." | unsourced (caveat) |
| 15 | The Thing's Talk-to | "That's a sheep...I think. I can't talk to sheep." (Transcript:Sheep (penguins)) | op3 → that player line | matches (D4 fixed) |
| 16 | "Fred! Fred! I've seen The Thing!" during the quest | after interacting with The Thing (Transcript, After interacting with The Thing) | gated on server varbit `sheep_shearer_saw_thing` | matches |
| 17 | After the quest | kill / lost, plus the Thing option only if the player has interacted with it (Transcript:Fred the Farmer) | flag set on any shear attempt, kept at completion; else `choice2` | matches (D5 fixed; "interacted" read as a shear attempt, caveat) |
| 18 | Reward does not fit (coins at the stack limit) | OSRS never loses the hand-in; message not documented | `setQuestStage` returns false (#339): the balls taken are given back, the quest stays open, "You don't have enough room for the reward." | matches in effect; message unsourced (caveat) |
| 19 | The Thing reacting to a shear attempt | the penguins "squeak and run away" (Sheep Shearer, Obtaining the wool); "walking away and doesn't provide any wool" (Sheep (penguins)) | generic `seq.human_shearing` and the message; The Thing doesn't move, no sound, no face or delay (unlike `Sheep.shearSheep`) | diverges (cosmetic; caveat) |

## Unsourced / guesses (PR caveats)
- Journal texts (reworked 14 January 2026; the current text is not in the sources).
- Row 14's message; row 18's message.
- "How do I shear sheep, again?" without shears hands over a new pair (the transcript has Fred's line but no
  give box on that path).
- Chathead expressions.

## Scope gaps
- Shearing animation and timing on The Thing use the generic `human_shearing` seq; The Thing doesn't run away or squeak (row 19). Both are a PR caveat.

## Tests
None on the branch now: the maintainer asked for `SheepShearerInteractionTest` to be removed on #366
(2026-10-19), and `QuestTalkFixture` went with it (nothing else on this branch used it).

## Review: the sheep pen stile (#366, a-thoren)
The bug is in upstream's shared `StileScript`, not in the quest: fixed as its own change, `fix/stile-approach`
(spec `accuracy/stile-approach.md`). #366 doesn't depend on it.

## Ids
New server-only gamevals, assigned by `tools/scripts/fix_gameval_conflicts.py` from `-1`:
varp `sheep_shearer_state` 65472; varbits `sheep_shearer_progress` 65449 (bits 0-4 of varp.sheep),
`sheep_shearer_saw_thing` 65448 (bit 0 of sheep_shearer_state).

## PR split
- `content/sheep-shearer` (#366): the quest only. New commits on 19aefd90a (tests and fixture removed; the stile
  change added and reverted, now in `fix/stile-approach`). No `Depends:`.
- The branch is on main c2492318d and conflicts with current main 829b9477d in
  `content/quest/pack/src/main/resources/gamevals.toml` (appended lines next to Doric, Knight's Sword, Waterfall).
  Two ways to update #366, the user's choice at push time:
  1. Force-push `content/sheep-shearer-829` (one commit 5445240b8 on 829b9477d: the same quest files, gamevals
     with both sides kept, no tests): a clean single-commit PR.
  2. Merge main into `content/sheep-shearer` (no history rewrite), resolving the same lines.
- Romeo & Juliet is left to upstream #299.
