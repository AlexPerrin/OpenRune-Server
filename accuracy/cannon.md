# Dwarf multicannon (content/cannon, upstream PR #354)

Spec for the dwarf multicannon (`content/cannon`). It follows the 2026-10-18 audit (osrs-plugins-reverse
`openrune/upstream/accuracy/cannon.md`: 29 rows, D1-D10), with each row's status on this branch. Its sources:
OSRS Wiki "Dwarf multicannon" (Assembly; Mechanics: Damage and experience, Accuracy, Revolutions, Targets,
Breaking/Decay/Death; Prohibited areas; Changes), "Cannonball", "Cannon base", "Transcript:Nulodion"; the
cache (locs 6-9 and 14916, objs 2/6/8/10/12, varps 2-4, varbits 13672/1968/2180/6015, seq 827); RuneLite's
Cannon, Timers & Buffs and the hub Supplies Tracker (secondary). None of the RSProx recordings we have (rsprox.net database) shows a player cannon (swept
2026-10-19), so set-up timing, auto-load, Load X and Empty wording and `mcannonmulti` are guesses.

## Upstream first
`gh pr list --search cannon`: #354 (this change) and #329 (merged: adds
Nulodion, which this change extends). No multicannon code on `main`.

## Status of the audit's divergences on content/cannon
| Audit | Mechanic | On this branch | Status |
|---|---|---|---|
| D1 / rows 23 | No broken state | breaks after 25 min: loc 14916, "<col=ef1020>Your cannon has broken!", stops firing; Repair: "You repair your cannon, restoring it to working order." restarts 25 min | fixed |
| D2 / row 24 | Decay to the bank | gone 10 min after breaking (35 min after set-up when part-built), "Your cannon has decayed. Speak to Nulodion to get a new one!", `mcannon_decayed` = 1; Nulodion's "I've lost my cannon." (Transcript:Nulodion branches: still set up, too few free slots, replace) gives the first k parts for the saved stage k (`dropcannon`) and needs k free slots; the wiki has no rule for a part-built cannon, so this is our divergence; the balls come back loaded on the next set-up | fixed |
| D3 / row 25 | Removed on logout | the cannon stays in the world through logout and death; its timers are the loc's own despawn timers; the owner gets it back on login; one recorded but missing after a restart counts as lost | fixed |
| D4 / row 19 | Accuracy | the owner's roll for the current weapon and style (ranged; melee per stab/slash/crush; powered staves the higher of ranged and crush) with equipment and prayers, against heavy ranged defence; no target-conditional effects | fixed (powered-staff comparison approximated: caveat) |
| D5 / row 18 | Single-way | in single-way combat, while the owner is under attack, only the attacker | fixed (attacker = `aggressive_npc` while in combat; the cannon tile decides single/multi: caveat) |
| D6 / row 16 | Target regions, LOS | per-direction regions (straight: 3-wide column; diagonal: the quadrant beyond the corner) overlapping on the rows/columns one tile off the centre lines (RuneLite's double-hit tiles); line of sight from the 3x3; tiles under the cannon never shot | fixed (regions approximated from the wiki's picture; range 8 ours: caveat) |
| D7 / row 2 | Quest | Dwarf Cannon required through `QuestRequirements` | fixed (refusal message a guess) |
| D8 / row 12 | Load X | asks once "Set load-x cannonballs (1 - 30):", stores `option_cannonballs_load_x`, "You will now load N cannonballs when using Load-x." (wiki 24 Sep 2025 for the option and that its amount is a setting; prompt and lines are guesses) | fixed (wording guessed) |
| D9 / row 9 | `mcannon_world` | set to the world number modulo 128 (7-bit varbit; the client's `buff_bar_get_value` script compares it with `map_world % 128`, CS2 bundle 241.1) | fixed |
| D10 / row 27 | Empty wording | "You unload your cannon and receive Steel cannonball x N." (op: wiki 8 Feb 2018; wording a guess) | op fixed, wording unsourced |
| row 7 | Stage delay | 2, 2, 3, 3 ticks from the click | unsourced (guess) |
| row 13 | Load message | "You load the cannon with 30 cannonballs.", auto-load the tick after the furnace | unsourced (guess) |

Rows 1, 3-6, 8, 10, 11, 15, 17, 20-22, 26 and 28 matched in the audit and are unchanged.

## Still unsourced or approximated (PR caveats)
- Target regions (the wiki shows them only as a picture) and the 8-tile range; LOS to the target's
  south-west tile.
- Single-way attacker rule details (above); a monster another player is fighting can still be shot.
- `mcannonmulti` = `1 << 20` while firing.
- Messages not on the wiki: the quest refusal, "You cannot construct more than one cannon
  at a time.", "There isn't enough space to set up here.", "This isn't your cannon!", "Your cannon is already
  full.", "You don't have any ammo to load into the cannon.", "Your cannon is empty.", the inventory-space
  lines. Firing stops when the owner logs out. Nulodion's stage-based return for a part-built cannon is ours (the wiki is silent).

## LostCity (2004 reference, re/ref/Content `quests/quest_mcannon/scripts/cannon_setup.rs2`, `cannon_fire.rs2`)
- Agrees with ours: "You load the cannon with N cannonballs.", "There isn't enough space to set up here.", "Your
  cannon is out of ammo!". No timing evidence: 2004 set-up is one part per click (OSRS automatic since 11 July 2013,
  wiki Changes); no Empty (added 8 February 2018) and no auto-load.
- Lines LostCity marks as OSRS that differ from ours (not adopted; open question for review): the quest refusal,
  "This is not your cannon.", "The cannon is already full of ammo.", the pick-up space lines ("You need N free
  inventory spaces to pick that up."), the one-cannon line's capital "Cannon", and pick-up as two lines.

## Scope gaps (PR caveats)
Also: the buff bar's decay timer (`%buff_cannon_decay_start`, never set) and the trivia's "I'm
currently under attack" line.
Granite cannonballs; Combat Achievement capacities (35/45/60); prohibited areas other than the Grand
Exchange; bosses destroying cannons and immune monsters; the 1-damage floor at very low accuracy (2019);
the ornament kit; the south-west "push"; `mcannon_setuptime`.

## Tests
None are included in the PR. Verification was in game: set-up stages and varps, firing and XP, Load X, Empty, pick-up, the logout, the break at 25 minutes and Repair, Nulodion's "still happily parked" branch and the reclaim of a part-built cannon (see the PR text). Not run in game: the 10-minute decay, the 35-minute part-built decay and the replacement of a lost full cannon.

## Ids
Timer `mcannon_fire` 65535 (written as -1, assigned by `fix_gameval_conflicts.py`; timers have no OSRS
max).

## PR split
One change (`content/cannon`, #354's branch). It touches `content/areas/misc/dwarven-mine`
(Nulodion's "I've lost my cannon.") because the reclaim is part of the cannon's decay rules. No `Depends:`.
