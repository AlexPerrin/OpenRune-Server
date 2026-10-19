# QA: acc/cannon
- Branch: `acc/cannon`
- Commit: `0d0190f3cdae2ccddc656df34b2fae237845cc0b`
## Internal gate: **FAIL**
## Upstream gate: **PENDING**

Run by sq24 on 2026-10-19. One squashed commit (`feat: dwarf multicannon`) on upstream `main` 731fd392a. Fork
PR AlexPerrin/OpenRune-Server#20; it would replace upstream draft #354's branch `content/cannon` only on the
user's approval. Spec `accuracy/cannon.md` and PR text `pr-texts/cannon.{title,upstream.body}` at fork docs
1a7b39afe. The upstream gate was not run: the internal gate stopped at the first failure. The findings below
for the upstream gate are for the next commit, not verdicts.

## Internal gate: FAIL. What must change
**Ranged XP is paid on the rolled damage, not on the damage dealt.** `fireTick()` rolls `damage` (0-30),
queues the hit, and calls `statAdvance("stat.ranged", damage * XP_PER_DAMAGE)` with the uncapped roll. The hit
itself is capped at the target's hitpoints, so the hitsplat shows at most the target's remaining HP. The XP
counts the overkill too.
- In game, at the Lumbridge cows (8 HP): hitsplats of 8, while single shots gave 7,500 / 8,100 / 7,200 Ranged
  XP. That is 25 / 27 / 24 damage x 2 x this world's XP rate 150. Level 1 -> 43 Ranged in about 30 shots.
- OSRS gives XP for damage dealt: the wiki's "2 Ranged experience per damage". Upstream's own player combat does
  the same: `PlayerAttackManager` computes `cappedDamage = min(damage, target.hitpoints)` before XP.
- Fix: cap the XP at `min(damage, target.hitpoints)` when the shot is fired, the way `PlayerAttackManager`
  does, or grant it from the hit as applied. Add the overkill case to `DwarfMulticannonTest` (a 30-damage roll
  on a low-HP target gives 2 x remaining HP).

## Evidence
- **build-test**:
  - `qa.sh gate` on clone `qa/cannon-0d0190f3c`: one container, the shared remote build cache for compiles,
    tests never cached, `origin/main` = 731fd392a for the spotless ratchet.
  - Every step rc=0: compile, freshCache + mergePluginGamevals, buildCache, generateRsa, installDist,
    spotlessCheck, assemble.
  - `test` 726/0. `integration` 94/0: `DwarfMulticannonTest` 14/14, `NulodionCannonSaleTest` 5/5. Total 580 s.
  - `fix_gameval_conflicts.py --check` is clean on the branch and on the merge with the current upstream main
    829b9477d (timer `mcannon_fire` 65535; no other timer uses it).
  - Boot: "Server ready in 11.77s". No exception or error lines over the session.
- **In game** (branch server, desktop lock 19:09-19:15, fresh account `sq24cn` made members, world 255,
  Lumbridge cow field, single-way):

| Check | Result |
|---|---|
| Set-up (base Set-up) | "You place the cannon base on the ground." (tick 68), "You add the stand." (70), "You add the barrels." (73), "You add the furnace." (76), "You load the cannon with 30 cannonballs." (77). The cannon has ops Fire/Pick-up/Empty/Load X. |
| Varps | `dropcannon` (varp 2) = 4, `rockthrower` (3) = 30, `ownedmcannon` (4) = packed coord |
| `mcannon_world` | varbit 1968 = 127 (255 % 128). Matches the client: `buff_bar_get_value` compares `calc(map_world % 128) = %mcannon_world` (osrs-dumps `[proc,buff_bar_get_value].cs2`) |
| Fire | `mcannonmulti` (varp 1) = 1048576 (1 << 20). Shots at several cows (not under attack, so several targets, as on the wiki); 30 -> 20 balls in 12 s. |
| XP | **FAIL**, above |
| Empty | "You unload your cannon and receive Steel cannonball x 9.", ammo 0 |
| Load X, first use | count prompt; entered 7: "You will now load 7 cannonballs when using Load-x.", nothing loaded |
| Load X, second use | "You load the cannon with 7 cannonballs.", ammo 7 |
| Pick-up, full inventory | "You don't have enough inventory space to pick up the cannon.", cannon stays |
| Pick-up | "You pick up the cannon. It's really heavy.": four parts and 7 balls back; varps 2/3/4 = 0 |

  Not run after the failure: single-way attacker-only, multi-way, break/repair/decay (25/35 min) and
  Nulodion. The integration tests cover them, and they are for the re-check. Clean-up: desktop RuneLite back on
  the live target (logged in as `desktop`), qa-or-server/qa-or-http removed, lock freed 19:15.
- **accuracy-check**, sources re-read 2026-10-19 (osrs-mcp was down, so the wiki through its page source):
  - Wiki "Dwarf multicannon" confirms: quest required; base/stand/barrels/furnace auto-assembled; max hit 30;
    2 Ranged XP per damage, no Hitpoints XP; accuracy rules (ranged, melee by style, powered staves the
    higher; target's heavy ranged defence; no slayer-helmet boost); 8 directions at 0.6 s; single-way fires
    only at the attacker; 25 min to break; repair by clicking; 10 more minutes to decay; 30 balls; Load X
    (24 September 2025, amount set in the settings menu); Empty (8 February 2018).
  - RuneLite (master): the Cannon plugin matches "You add the furnace." and "Your cannon is out of ammo!".
    Timers & Buffs matches the four stage lines, "You pick up the cannon. It's really heavy.", "You repair your
    cannon, restoring it to working order.", "Your cannon has been destroyed!" and "<col=ef1020>Your cannon has
    broken!". All of these are what the branch sends.

## For the next run (upstream-gate findings, not verdicts)
1. **Base and PR template.** Upstream `main` moved from 731fd392a to 829b9477d. #369 added
   `.github/pull_request_template.md`. Rebase onto the current main (the merge is clean, gamevals included) and
   put the PR text in the template's sections: Summary, Type of change, How it was tested, Checklist,
   Screenshots / notes. Tick its checklist honestly:
   - "checked against rsprox dumps": unticked (no captures);
   - deviations: in the PR notes or in `docs/quirks.md`.
2. **Caveats to add:**
   - The wiki's Trivia says firing in single-way combat makes the player say "I'm currently under attack". Not
     implemented. ("Someone else is fighting that" was removed on 24 September 2015, so leaving it out is
     right.)
   - The client's buff bar decay timer: `buff_bar_get_value` also reads `%buff_cannon_decay_start`, which the
     branch never sets, so the in-game buff bar shows no decay timer. List it next to `mcannon_setuptime`.
3. **Load X / load messages:** with balls already in the cannon, below 30, and none in the inventory,
   `load`/`loadX` say "Your cannon is already full." (`loadBalls` returns 0 and `cannon.ammo != 0`). Check room
   and ammo in the inventory separately. Both lines are guesses anyway.
4. **The LostCity lines (main's question).** Neither the OSRS Wiki (Dwarf multicannon; Transcript:Nulodion) nor
   RuneLite's plugins quote any of them:
   - LostCity's quest refusal, "This is not your cannon.", "The cannon is already full of ammo.", the pick-up
     space lines and the two-line pick-up are all unsettled.
   - What OSRS sources do settle, and the branch already uses: the four stage lines, the pick-up line, repair,
     broken, out of ammo, and the Grand Exchange refusal (wiki).
   - So keeping ours as declared guesses is fine. Optionally say in the caveats that LostCity's 2004 lines
     differ.
5. Tests: the XP-cap test above.
