# Clue scroll Check steps names the tier (local/treasure-trails)

Fix on our local-only Treasure Trails module (`content/other/treasure-trails`, branch `local/treasure-trails`;
never upstream, so no upstream PR and no upstream gate). Found by the client track: the Clue Steps hub plugin
reads the tier from Check's message, and ours said only "You have completed N steps on this clue scroll."

## Upstream first
Upstream has no Treasure Trails content (our module is local-only); nothing to build on.

## Behaviour
| Rule | Source | After |
|---|---|---|
| Check steps op | Cache: obj 23182 Clue scroll (beginner) `interfaceOptions=[Read, Check steps, null, null, Drop]` (iop 2); wiki Treasure Trails, Changes, 2 July 2015: "All clue scrolls now have a right-click 'Check steps' option" | unchanged: `onOpHeld2` on every clue obj |
| Message text | RSProx (below): `You have completed <n> steps on this <tier> clue scroll.` as a game message | `checkStepsMessage(tier, done)`: the tier word is `TrailTier.label` (beginner, easy, medium, hard) |
| Singular | RSProx 1034: "You have completed 1 step on this master clue scroll." | "step" for 1, "steps" otherwise (already so) |
| Zero | RSProx 1034: "You have completed 0 steps on this master clue scroll." | "0 steps" (already so) |
| Count | the tier's steps-done varp (unchanged) | unchanged |

## RSProx comparison
Content search (MESSAGE_GAME, 2026-10-20): "steps on this beginner clue scroll" 1 recording (91), "... easy ..." 0,
"... medium ..." 0, "... hard ..." 2 (91, 1106), "... elite ..." 2 (1043, 1057), "... master ..." 5 (3931, 1034,
1035, 91, 549), "1 step on this" 1 (1034). Transcribed 91 (rev 227), 1034 (rev 235), 1106 (rev 237); 3931 (rev 236)
was already local. Each message follows an inventory `if_button3` (iop 2, Check steps) on the clue the tick before:
- 91 tick 14364: "You have completed 2 steps on this beginner clue scroll." (after if_button3 on obj 23182, tick 14363)
- 91 tick 20213: "You have completed 4 steps on this hard clue scroll." (if_button3 on obj 10246)
- 1106 tick 2885: "You have completed 5 steps on this hard clue scroll." (if_buttonx op=3 on obj 29859)
- 1034 ticks around 291: "0 steps" and "1 step" on the master clue (if_buttonx op=3 on obj 19835)
- 3931 tick 133: "You have completed 3 steps on this master clue scroll." (if_buttonx op=3 on 19835, tick 132)
All match the new text for the tiers we run. Ours answers in the same tick as the op (the recordings show the message
in the next server tick, the usual op processing delay; not changed here).

## Caveats and guesses
- Guess: easy and medium use the same pattern with "easy"/"medium"; no recording has them, but beginner, hard,
  elite and master all do, so the tier word is the lowercase tier name.
- Elite and master are not run by the module, so their messages don't apply.

## Tests
`TreasureTrailScriptTest` "check names the clue's tier": the message for beginner 0, easy 1 (singular), medium 3,
hard 5.

## Change
One commit on `local/treasure-trails` (fork). Not for upstream.
