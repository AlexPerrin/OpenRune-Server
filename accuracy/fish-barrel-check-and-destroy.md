# Fish barrel: Check and Destroy (fix/fish-barrel-check-and-destroy)

Fix for upstream's `FishBarrelScript` (`content/skills/fishing`, added in Fishing #191), on main 829b9477d.
Found by the client track: the Fish Barrel hub plugin (molo-pl, `fish-barrel`) learns the barrel's count from
Check's chatbox message box and showed "?" on OpenRune; and a barrel could not be got rid of.

## Upstream first
`gh pr list -R OpenRune/OpenRune-Server --state all --search "fish barrel"` and `gh issue list ... --search barrel`
(2026-10-19): nothing about the barrel (only our merged #358, skilling message wording). `main`: the script as in #191.

## Before
- Check (op 2) sent game messages: "The fish barrel is empty." or "The fish barrel contains (n/28):" and one
  "<name> x <count>" line per fish.
- Destroy (op 5) was registered by the script and fell into `else -> {}`: nothing happened, and since the script's
  handler replaces the default op 5, the generic destroy never ran either.
- Fill on a full barrel: "You have no raw fish to store, or the barrel is full."

## Behaviour
| Rule | Source | After |
|---|---|---|
| Check, empty | Hub plugin `FishBarrelWidgetParser` (reads interface 193 comp 2 after Check): `"The barrel is empty."`; tictac7x-charges `U_FishBarrel`: "(Your\|The) barrel is empty." | message box (`mesbox`, interface 193 comp 2): "The barrel is empty." |
| Check, contents | Wiki, Fish barrel: Check "tells the amount and type of raw fish the player has inside"; hub plugin parser and its test: `The barrel contains:<br>1 x Raw anglerfish, 2 x Raw monkfish, 3 x Raw<br>shrimps, ...` (`<count> x <item name>`, comma separated) | message box: "The barrel contains:<br>" + "<count> x <item name>" joined with ", "; the box wraps the lines |
| Full | Wiki, Fish barrel: "When the barrel is full ... players will get a message in the chatbox stating: 'The barrel is full. It may be emptied at a bank.'"; hub plugin reads it as a GAMEMESSAGE | Fill on a full barrel sends that game message |
| Destroy op | Cache: obj 25582 `interfaceOptions=[Fill, Check, Open, null, Destroy]`, 25584 `[Fill, Check, Close, null, Destroy]`; wiki: "the Destroy right-click option will destroy the barrel itself" | op 5 opens the destroy confirmation (interface `confirmdestroy`, clientscript 814) |
| Destroy texts | Wiki infobox "Destroy": "Empty: You may win another barrel from Tempoross. Filled: The barrel and its contents will be lost. You may win another barrel from Tempoross."; cache `param.destroy_note_desc` on 25582/25584 = the filled text; the sack barrels' param = "You may win another barrel from Tempoross and buy another fish sack from Alry at Molch lake."; title = `param.destroy_note_title` default "Are you sure you want to destroy this item?" | fish barrel: the empty text when the store is empty, else the item's param; sack barrels: their param; title from the param |
| Destroy result | Wiki infobox (filled: "The barrel and its contents will be lost") | on Yes the barrel is deleted from its slot; on No nothing changes. The contents are cleared when no barrel (inventory or worn) is left |

## Caveats and guesses
- Guess: the plugin test's line breaks inside the list ("3 x Raw<br>shrimps") are the box's wrapping; our
  `mesbox` wraps the same way (TextAlignment, 470 px), so the joined list is sent without manual breaks.
- Guess: more than ten fish types continue in a second message box without the header (the box holds two pages
  and the server can't page further). The official split is unknown; ten types of raw fish at once is rare.
- Guess: barrels share one store (wiki: "the items will share their inventory"), so destroying one of two barrels
  keeps the contents; they go with the last one.
- Guess: the full message is sent on Fill when the barrel is already full. The wiki doesn't say which action sends
  it (catching into a full open barrel may too); auto-deposit into a full open barrel stays silent as before.
- Not changed: Fill's and Empty's other texts ("You fill the fish barrel.", "You empty the fish barrel.") are not
  checked against a source here; tictac7x suggests "You empty the barrel." for the deposit box. Out of scope.

## RSProx comparison
rsprox.net content search (POST /api/search, 2026-10-19), cached in `re/ref/rsprox/search/fishbarrel_*.json`:
OBJ 25582, 25584, 25585, 25587: 0 recordings; MESSAGE_GAME "The barrel is full. It may be emptied at a bank."
and "The barrel is full": 0; TEXT "The barrel contains", "The barrel is empty", "You may win another barrel": 0.
The search itself was checked with known hits (OBJ 995, NPC 1290, MESSAGE_GAME "The herb sack is empty."). The
local transcripts (`re/ref/rsprox/txt/`) have no barrel either. No recording exists: every rule above rests on the
wiki, the cache and the two plugins, and the layout details marked as guesses stay guesses.

## Tests
- `FishBarrelMessagesTest` (unit): empty text, one type, several types comma separated, the split after ten types,
  the full and empty-destroy texts.
- In game (desktop, branch server): Check on an empty barrel and on one holding fish (message box, no game
  messages), Fill on a full barrel, Destroy with No then Yes on the empty and filled barrel.

## PR split
One fix, no dependencies.
