# Goblin Diplomacy (content/goblin-diplomacy)

Spec for Goblin Diplomacy on its own, rebuilt on upstream `main` 829b9477d to replace the contents of upstream
draft #367, which was stacked on #366's old commit (Sheep Shearer, Romeo & Juliet, `QuestTalkFixture`) and also
carried the Goblin Village lookout ladder. The ladder is now its own change (accuracy/goblin-village-lookout-ladder.md).
Rows reuse the 2026-10-18 audit in osrs-plugins-reverse `openrune/upstream/accuracy/goblin-diplomacy.md`
(rows 1-30, D1-D4), updated for the branch commit; D1-D4 were fixed on `fin/goblin-diplomacy` (c23023014) and
carry over unchanged.

Sources: OSRS Wiki pages Goblin Diplomacy (Walkthrough, Rewards), Transcript:Goblin Diplomacy, Transcript:Grubfoot,
Transcript:General Wartface, Transcript:General Bentnoze, Goblin mail (Products), Orange dye (Creation); Quest
Helper `GoblinDiplomacy` (BSD; varbit 2378 steps 0/3, 4, 5; crate varbits; item ids); the cache (npc 1965
`catwalk_goblin` multinpc [671 brown, 672 orange, 673 blue] on varbit 13594; the crate locs; the
`quest_goblindiplomacy` dbrow end state 6). The osrs-mcp server was not reachable for this pass; the wiki page
Goblin Diplomacy was re-read on 2026-10-19 (crate locations, rewards, dye notes), the rest are the audit's
citations checked against the code.

## Upstream first
- `gh pr list -R OpenRune/OpenRune-Server --search goblin` (2026-10-19): #367 (ours, draft, to be replaced) and
  #328 (merged, "Add Goblin Village": the generals' and Grubfoot's flavour dialogue, the cave ladders, Mudknuckles).
  No issue. #242 (closed) had nine F2P quests.
- `main` 829b9477d: no Goblin Diplomacy script; no dyeing of goblin mail; no dye mixing; Aggie sells/makes red,
  yellow and blue dye (`DraynorVillageNpcs`). Doric #334, Knight's Sword #333, Waterfall #297, Ides of Milk #315
  touch none of the files here; the quest uses only cache varbits, so no gameval is added.
- #328's `GoblinGenerals` and `Grubfoot` scripts bind op1 on the same npcs; their lines are the after-quest
  lines, so they move into the quest script (both files deleted).

## Mechanics
| # | Mechanic | Official behaviour (source) | Branch behaviour | Verdict |
|---|---|---|---|---|
| 1 | Progress varbit | `gobdip_main` (2378): Quest Helper steps 0/3 orange, 4 blue, 5 brown | 3 / 4 / 5, 6 complete (startup check against the dbrow) | matches |
| 2 | Start value 3 | Quest Helper maps 0 and 3 to one step; nothing shows the write | start writes 3 | guess (caveat) |
| 3 | Rewards | 5 QP, 200 Crafting XP, a gold bar (Goblin Diplomacy, Rewards) | `rewards { xp(crafting, 200); item(gold_bar) }`, QP from the dbrow | matches |
| 4 | Generals' five random openers | Transcript, Starting off, Dialogues 1-5 | `generalsDialogue` | matches |
| 5 | "Fat" opener menu | yes / no / "I'll leave you to it." (ends) | `fatOpener` choice3 | matches (audit D1 fixed) |
| 6 | Topic menu, "Why are you arguing" swaps to "What is the goblin new century?", leave always ends | Transcript, Dialogue 1 tree | `mainMenu` loop with `askedWhy` | matches (D2 fixed) |
| 7 | Peace, pick red / green, different colour, start prompt, D-Y-E help loop | Transcript, verbatim | `mainMenu`, `pickColour`, `differentColour`, `whereToGet` | matches |
| 8 | "Different colour" asked mid-quest | not in the transcript | "Human! Get us <colour> armour!" | guess (caveat) |
| 9 | Three crates, one goblin mail each, once | objbox "You find some goblin mail in the crate."; then "You search the crate, but but find nothing of interest." Locations: behind the generals' hut, the western hut, "up a ladder near the gate" (Goblin Diplomacy, Walkthrough) | `searchCrate` on `gobdip_crate1..3_searched` | matches; the third crate needs the lookout ladder fix |
| 10 | Crate with a full inventory | not documented | "You don't have enough inventory space.", varbit not set | guess (caveat) |
| 11 | Dyes on plain goblin mail | orange / blue: "You dye the goblin mail orange." (Transcript); every colour has a product (Goblin mail, Products) | red, yellow, blue, orange, green, purple, pink, black dye, one transaction each, same message | matches for orange / blue; other messages guessed |
| 12 | Dye mixing | red+yellow orange, yellow+blue green, red+blue purple (Orange dye, Creation; no message given) | `mixDyes`, one transaction, "You mix the two dyes and make <colour> dye." | message guessed |
| 13 | Wrong colour on a general | "What do you think of this colour?" / "That wrong colour." / "We tell you get <colour> armour." | `useOnGeneral` | matches |
| 14 | Right colour: Grubfoot try-on dialogue, orange → blue → brown, complete | Transcript, verbatim | `handIn`: takes the armour, sets the look varbit and stage; dialogue only | matches (cutscene is a scope gap) |
| 15 | Grubfoot's look | orange then blue (multinpc on varbit 13594) | `gobdip_grubfoot_vis` 1 / 2, 0 at the end | matches |
| 16 | Grubfoot before / during the quest | "Grubfoot wear red armour! Grubfoot wear green armour!" / "Why they not make up their minds?"; orange / blue lines while wearing them | `grubfootDialogue` | matches (D4 fixed) |
| 17 | Grubfoot and generals after the quest | five random lines each, + "Shut up Grubfoot!" (Transcript:Grubfoot, Transcript:General Wartface / Bentnoze, Dialogues 1-5) | `grubfootChatter`, `afterQuest` | matches (D3 fixed) |
| 18 | Armour used on a general outside the quest | not documented | "Nothing interesting happens." | guess (caveat) |
| 19 | Hand-in from the talk menu | the transcript shows only using the armour on a general | extra option "I have some <colour> armour here." when the wanted armour is held | guess (caveat) |
| 20 | Completion | "Congratulations, you've completed a quest: Goblin Diplomacy" | framework `completeQuest` | matches |

## Guesses (PR caveats)
Rows 2, 8, 10, 11 (colours other than orange/blue), 12, 18, 19; the journal text; the quest scroll item; chathead
expressions; black dye (`golem_ink`) used up like the other dyes (no vial back).

## Scope gaps (PR notes)
- Grubfoot's try-on cutscenes (curtain, camera) are dialogue only.
- Re-dyeing already dyed mail (yellow + red dye → red, Goblin Diplomacy Walkthrough) and white goblin mail
  belong to Land of the Goblins; not included.
- The generals' Another Slice of H.A.M. dialogues, "Where's Bentnoze gone now?" (no line of sight) and the village
  goblins' quest-era chatter are not included.

## Tests
None. AGENTS.md asks for tests on item-moving logic, but the maintainer asked for #366's quest tests (hand-ins
included) to be removed, and the old `GoblinDiplomacyInteractionTest` needed `QuestTalkFixture`, which is another
change. Every item move is one `invTransaction` (dye, mix) or a single `invAdd`/`invDel` checked before the state
changes (crate, hand-in). Verification is the dev build and an in-game run.

## PR split
- `content/goblin-diplomacy` (this change), one commit off upstream `main`. No `Depends:`.
- Related, not a dependency: `fix/goblin-village-lookout-ladder` (the third crate is on the lookout). Without it,
  plain goblin mail still drops from goblins outside the village.
- Replaces #367's contents (force-push after the user approves).
