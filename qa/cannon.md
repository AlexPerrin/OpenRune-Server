# QA: acc/cannon
- Branch: `acc/cannon`
- Commit: `bc243d141fc27d5128d29d8266cf51e89a4f6c08`
## Internal gate: N/A (dev-checked)
## Upstream gate: **FAIL**

Run by sq24 on 2026-10-19 at bc243d141 (one commit `feat: dwarf multicannon` on upstream main 829b9477d, the tip;
fork PR AlexPerrin/OpenRune-Server#20, to replace upstream draft #354's content only on the user's approval). Spec
`accuracy/cannon.md` and PR text `pr-texts/cannon.upstream.body` read from sd23's docs commit 65cc94f05. The code
passed every check below; the FAIL is one false claim in the PR text (and the same in the spec). No code change
is needed, so the build and in-game evidence below stays valid for bc243d141.

## What must change (text only)
**The PR text says "we have no dumps of the official game"; we have 40 RSProx recordings, none with the cannon.**
A read-only sweep of all 40 transcripts in `re/ref/rsprox/txt/` found no player cannon at all: no `loc_add_change`
of locs 5-9, 14916 or 43027-43029; cannonballs and parts only in bank dumps; varps `mcannon` (0), `rockthrower`
(3), `ownedmcannon` (4) only in the login dumps, never changing; no "cannon" game message; the only cannon
animations are the Dwarven Mine scenery cannon in 3243 (`mcannon_w_turn` 520 etc. at 3009,3441, with
`fai_dwarf_working_cannon`), which is not a player cannon. Spot-checked at the source: `grep -c "message_game.*[Cc]annon"`
over txt/ finds only the Pirate Pete quest line.
- PR text, checklist line "New content checked against rsprox dumps": keep it unticked, but replace "we have no
  dumps of the official game" with "none of the RSProx recordings we have (rsprox.net database) shows a player
  cannon".
- Spec, header: "no such capture exists (we never connect to Jagex servers)" -> "none of the RSProx recordings we
  have shows a player cannon (swept 2026-10-19), so these items are guesses".
- Optional: "How it was tested" can now cite this run at bc243d141 instead of the previous commit.
A re-check of the text alone is enough for this commit; the break/decay/replace path below is still to be seen in
game at the re-check (it is covered by the integration tests at this commit).

## Checked at bc243d141
- **build-test**: fork CI run 39350604565 (`CI`: cache, assemble/test/integration, boot "Server ready in 15.59s")
  and 39350606847 (`Formatting`) green at bc243d141 (head sha checked). `fix_gameval_conflicts.py --check` clean
  (timer `mcannon_fire` 65535). Branch server (`qa.sh gate-run` + `start`): "Server ready in 9.39s"; 0 exception or
  error lines over the session. Tests at the commit: `DwarfMulticannonTest` 15 (incl. the XP-overkill case and
  break/repair, decay, logout, restart-loss, single-way), `NulodionCannonSaleTest` 5.
- **Previous FAIL (0d0190f3c) fixed**:
  - XP: `rangedXp(damage, target.hitpoints)` = `min(damage, hp) * 2`. In game every shot paid at most 2 x 8 x 150
    = 2,400 Ranged XP on 8-HP cows and 1,800 on 6-HP calves (hitsplats 8 and 6), never more.
  - Load messages: with 10 balls in the cannon and none in the inventory, Load X gave "You don't have any ammo to
    load into the cannon." (was "Your cannon is already full.").
- **In game** (desktop lock 22:11-22:38, fresh account `sq24cn2` made members with `qa-members.sh`, Lumbridge cow
  field, single-way):

| Check | Result |
|---|---|
| Set-up | "You place the cannon base on the ground." (t82), "You add the stand." (84), "You add the barrels." (87), "You add the furnace." (90), "You load the cannon with 30 cannonballs." (91) |
| Varps | `dropcannon` 4, `rockthrower` 30, `ownedmcannon` 53365962 (= 3257,3274, the SW tile), `mcannon_world` 127 |
| Fire | shots at several cows and calves while not under attack; "Your cannon is out of ammo!" when it ran dry |
| Load X | first use: prompt "Set load-x cannonballs (1 - 30):", 5 -> "You will now load 5 cannonballs when using Load-x."; then "You load the cannon with 5 cannonballs." twice (ammo 10) |
| Empty | "You unload your cannon and receive Steel cannonball x 10." |
| Single-way | while a cow I attacked was attacking me (t361-366) no cannon hits on other cows, after hits on several targets nearly every tick before (weak evidence; the test covers it) |
| Logout | logged out and in: the cannon still there with ops Fire/Pick-up/Empty/Load X, varps 2/3/4 restored |
| Nulodion, cannon set up | "I've lost my cannon." -> "Hmmm. I think you'll find it's still happily parked on the spot where you put it." / "Oh, is it still there?..." / "Ha ha ha, what a muddle-headed numpty you are!" / "..." (Transcript:Nulodion, identical) |
| RuneLite plugins | Cannon plugin draws the ball count over the cannon (22); Timers & Buffs shows the cannon timer (19:25 about 5.5 min after set-up) |
| Pick-up | "You pick up the cannon. It's really heavy.": four parts back, `ownedmcannon` 0 |

  Not seen in game this run (time): the 25-minute break, Repair, the 10-minute decay and Nulodion's replacement.
- **accuracy-check** (osrs-mcp `wiki_page` "Dwarf multicannon" and "Transcript:Nulodion", re-read): quest
  requirement, auto-assembly order, 30-ball capacity, max hit 30, 2 XP per damage, no Hitpoints XP, accuracy by
  weapon type against heavy ranged defence with no target-conditional boosts, 8 directions a tick, single-way
  attacker rule, double-hit tiles, SW tile of large monsters, 25 min break / click to repair / 10 more to
  disappear / 35 min when not repaired, Nulodion replacement with the loaded balls, Load X (24 Sep 2025) and Empty
  (8 Feb 2018): all as in the branch or declared caveats. The wiki's other prohibited areas (Dwarven Mine, Ice
  Mountain, Black Knights' Fortress and the rest), the granite balls, CA capacities and the "push" are in the
  caveats as not implemented. The Nulodion lines match the transcript.
- **Scope**: one feature (new module + Nulodion's lost-cannon branch, which the decay rules need). Upstream open
  PRs on "cannon": only #354 (ours). On current main. No `Depends:`.
- **Conventions**: upstream's PR template, every section present; `feat:` subject. Review note: the Grand Exchange
  box is a Kotlin `Area` constant (no area gameval for it exists); a reviewer may ask for it as cache data.

---
## History: internal gate at 0d0190f3c (void)

Run by sq24 on 2026-10-19. One squashed commit (`feat: dwarf multicannon`) on upstream `main` 731fd392a. Fork
PR AlexPerrin/OpenRune-Server#20; it would replace upstream draft #354's branch `content/cannon` only on the
user's approval. Spec `accuracy/cannon.md` and PR text `pr-texts/cannon.{title,upstream.body}` at fork docs
1a7b39afe. The upstream gate was not run: the internal gate stopped at the first failure. The findings below
for the upstream gate are for the next commit, not verdicts.

### Internal gate: FAIL. What must change
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

### Evidence
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

### For the next run (upstream-gate findings, not verdicts)
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
