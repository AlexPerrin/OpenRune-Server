# Witch's Potion (content/witchs-potion)

Spec for Witch's Potion on its own, rebuilt on upstream `main` 829b9477d to replace the contents of upstream draft
#368, which was stacked on #366's old commit (Sheep Shearer, Romeo & Juliet, `QuestTalkFixture`). Rows reuse the
2026-10-18 audit in osrs-plugins-reverse `openrune/upstream/accuracy/witchs-potion.md` (rows 1-20, D1-D3), updated
for the branch commit; D1-D3 were fixed on `fin/witchs-potion` (fddf71474) and carry over. The quest never
depended on Goblin Diplomacy; the only shared piece was the test fixture, dropped with the tests.

Sources: OSRS Wiki pages Witch's Potion (Details, Rewards, Rat's tail), Transcript:Witch's Potion, Transcript:Hetty,
Rat's tail (Item sources), Rat, Dungeon rat, Giant rat; Quest Helper `WitchsPotion` (BSD; varp 67 steps 0, 1, 2;
cauldron loc 2024); the cache (npc and obj names, the `quest_witchspotion` dbrow end state 3). The osrs-mcp server
was not reachable for this pass; the citations are the audit's, checked against the code.

## Upstream first
- `gh pr list -R OpenRune/OpenRune-Server --search witch` (2026-10-19): only #368 (ours, draft, to be replaced);
  #242 / #216 closed. No issue.
- `main` 829b9477d: Hetty has only her after-quest lines (`RimmingtonTownsfolkScript`); no rat drops a rat's tail.
  Doric #334, Knight's Sword #333, Waterfall #297 and Ides of Milk #315 add `bottomlessMilk` / `genericLocs`
  dependencies and their own gamevals next to ours in `content/quest` (merged by hand: `build.gradle.kts`).
- Upstream's quests keep progress in a server-only varbit over the quest varp (Doric: `dorics_quest_progress`);
  this follows the same pattern.

## Mechanics
| # | Mechanic | Official behaviour (source) | Branch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Progress | varp 67; Quest Helper steps 0 talk, 1 ingredients, 2 drink | `witchs_potion_progress` (bits 0-1 of `varp.hetty`): 1, 2, 3 complete; startup check against the dbrow | matches |
| 2 | Rewards | 1 QP, 325 Magic XP (Witch's Potion, Rewards) | `rewards { xp(magic, 325) }`, QP from the dbrow | matches |
| 3 | Hetty's opening, witch branch, offer, start prompt, decline, accept | Transcript:Witch's Potion, verbatim | `beforeQuest`, `offerQuest` | matches |
| 4 | No ingredients / some (14 combinations) | Transcript, verbatim; tail, meat, onion, newt order | `lookingForIngredients`, `ingredientsLine` | matches |
| 5 | Hand-in | "In fact I have everything!" ... one box "You pass the ingredients to Hetty ... The cauldron bubbles mysteriously." ... "Ok, now drink from the cauldron." | one `invTransaction` taking one of each, stage 2 only on success, one `mesbox` | matches (D2 fixed) |
| 6 | Hetty at stage 2 | "Well are you going to drink the potion or not?" / "Yes, I will." | `hettyDialogue` | matches |
| 7 | Cauldron before stage 2 | "As nice as that looks, I think I'll give it a miss for now." | `drinkFromCauldron` | matches |
| 8 | Drinking | message box "You drink from the cauldron. It tastes horrible! You feel yourself imbued with power.", then complete | `mesbox`, then `completeQuest` | matches (D3 fixed) |
| 9 | Hetty after the quest | "How's your magic coming along?" ... "Good, good." (Transcript:Hetty) | `hettyDialogue`; removed from `RimmingtonTownsfolkScript` | matches |
| 10 | Rat's tail: 100% once the quest is started | Rat's tail ("100% chance to drop once you begin the quest"; unobtainable after completion) | `WitchsPotionRatTail` (`NpcDeathKillHook`): stage 1-2, dropped for the killer | matches |
| 11 | Which rats | Rat (2854, 2855), Stronghold of Security rat (2492, 2513), Dungeon rat (2865-2867, 3607-3609), level-26 giant rat (2510-2512); not the other giant rats | the 13 `npc.*` names for those ids | matches (D1 fixed) |
| 12 | Tail at stage 2 | "during the quest" only | drops | guess (caveat) |
| 13 | Cauldron after the quest | the player's "As nice as that looks, I think I'll give it a miss for now." (RSProx 468: varp 67 = 3) | the "give it a miss" line | matches (RSProx) |

## RSProx comparison (2026-10-19, sd25)
Searched rsprox.net (references/rsprox.md) by NPC 4619 (Hetty), 1187; LOC 2024; VARP 67; OBJ 300 (rat's tail); TEXT
"rat's tail", "You pass the ingredients to Hetty", "You drink from the cauldron", "Witch's Potion", "Hetty", "As nice
as that looks". No recording has the quest itself (no talk with Hetty, no rat's tail drop: OBJ 300 has no hits, no
hand-in, no quest complete). Completed accounts show varp 67 = 3 at login (468, 3246, 350, 3931), as the branch's end
stage.
- **468** (rev 233.1), after the quest: tick 3384 oploc1 on `hettycauldron` 2024; tick 3386 the player's chathead
  (chat_right 217, anim chatneu1 588) "As nice as that looks, I think I'll give it a miss for now.", no varp change,
  sound or anim. The branch's line, `neutral` chathead and post-quest behaviour match (row 13 was a guess, now
  matches).
- **4182** (rev 238.1): the same line and chathead on another drinkable cauldron (55410), not Hetty's.
- Not recorded: Hetty's dialogue, the rat's tail stage, the hand-in box, drinking. Those rows stay on the wiki.

## Why a kill hook
A drop-table entry with `quest_mode = "during"` never passes under the default
`gameplay.quest-requirements.mode = assume-completed`, so on a default server no rat would drop a tail and the quest
could not be finished. The hook reads the quest's own stage and works in every mode.

## Guesses (PR caveats)
Row 12; the journal text; the quest scroll item (eye of newt); chathead expressions; rat 13624
(`rat_lowwander`) is not in the wiki's ids and doesn't drop it.

## Tests
None. The old `WitchsPotionInteractionTest` needed `QuestTalkFixture` (another change), and the maintainer asked
for #366's quest tests, hand-ins included, to be removed. The only item move is one `invTransaction` with a
strict count of 1 per ingredient (all or nothing).

## Ids
New server-only varbit `witchs_potion_progress`, assigned by `tools/scripts/fix_gameval_conflicts.py` from `-1`:
65449 (`--check` clean on the branch). On our integration tree it collides with content/sheep-shearer's
`sheep_shearer_progress` 65449; integration's `--check` reassigns the later branch.

## PR split
No `Depends:`. Replaces #368's contents as a fast-forward: `content/witchs-potion-unstacked` = the PR head + a merge commit whose tree is `main` + this quest (after the user approves).
