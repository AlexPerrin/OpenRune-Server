# Genie #355 review fixes (content/genie, change genie-review-355)

Scope: the upstream #355 head `content/genie` (1d9b6fa49, rebased onto upstream main 5c4c9c106) plus the two
findings of our earlier gate. The feature spec for the richer fork build is `accuracy/genie.md`; this spec covers
only what these fixes change. Upstream check (2026-10-24): #355 is still the only Genie/random-event PR.

| # | Mechanic | Source | Planned behaviour | Verdict |
|---|---|---|---|---|
| 1 | `::randomevent` | review P1 (eaglepurerocketgrape, 2026-10-22): `CheatHandlerBuilder` checks rights only when set | `requiredRights = Rights.ADMINISTRATOR` like `::craftmat`, `::pet` | matches review |
| 2 | Talk-to hand-over | review P2; RSProx 3056 t4030: the reward message, lamp and the dialogue come on the same tick as opnpc1 | lamp (`invAddOrDrop`) and "Your reward is: 1 x Lamp." before the dialogue opens; Genie stays tracked, so cancel or logout at the open page loses nothing and leaves nothing | matches 3056 |
| 3 | Departure | RSProx 1718 G1/G3 and 3056: he leaves on his next call tick on the add's grid, not Talk-to + N; no call that tick | owner's Talk-to marks the Genie handed over; the call timer's next fire removes him with no call | matches (see accuracy/genie.md rows 14, 15 for the recordings) |
| 4 | Full inventory | wiki Random events, Trivia (Mod Ash): rewards are dropped and stay | `invAddOrDrop`: the lamp is on the floor under the player | matches |
| 5 | Talk-to again | Transcript:Genie, completed event | "Have a nice day, Master/Mistress." until he leaves | matches (transcript only) |
| 6 | Lamp XP | Lamp, Genie: 10 x level; RSProx 3056 t4038: 990 XP at level 99 in both the message and the XP | `statAdvance(..., rate = 1.0, globalRate = 1.0)`; message "Your wish has been granted! You have been awarded N Skill XP!" with the same N | matches (before: message said 10 x level, world rate gave x150) |
| 7 | Logout | not documented | Genie removed | guess (unchanged) |

Caveats: the message is a chat line here, the official one a message box (the branch uses upstream's simple lamp
flow); the unscaled XP ignores the world XP rate deliberately (official: no multiplier).
Tests (new, integration; each guards a reported regression or an item flow): cancelled dialogue keeps the lamp and he
leaves; logout at the open dialogue leaves no orphan and keeps the lamp; full inventory drops the lamp (no loss); he
leaves on the next call tick and not before. The Rub message is not unit tested (the chooser is an interface flow).
PR split: one change, on the #355 head. Depends: none.
