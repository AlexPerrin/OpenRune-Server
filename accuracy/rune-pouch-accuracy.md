# Rune pouch accuracy (fix/rune-pouch-accuracy, follow-up to merged #356)

Upstream merged #356 (the pre-audit rune pouch, squashed as 36f2cfeeb on `main`) on 2026-10-18. This spec is
for the follow-up `fix/rune-pouch-accuracy` (commit 01c233d80 on main d2a0cd1b1): the delta from the
merged code to the audited behaviour. It reuses the 2026-10-18 audit (osrs-plugins-reverse
`openrune/upstream/accuracy/rune-pouch.md`, kept below) and the QA FAIL in `openrune/upstream/qa/rune-pouch.md`
(at af57c4ad8, the code that merged). Sources: OSRS Wiki "Rune pouch" (intro, Interface, Changes, Trivia),
"Divine rune pouch" (intro, Interface, Changes); the cache (items 12791, 24416, 27281, 27509; interface
190; enum 982; item 12791 destroy text, param 65482); RuneLite's Rune Pouch plugin (secondary).

## Upstream first
`gh pr list --search "rune pouch"` (2026-10-18): #356 merged; nothing open. #319 (open, gravestones and
Death's Office) is where death handling would hook in.

## What the merged version gets wrong (QA FAIL items) and what this change does
1. D1, gameplay-visible: storing the pouch itself from interface 190 says "You can only store runes in the
   rune pouch."; OSRS says "Don't be silly." (Rune pouch, Trivia). **Fixed here**, with a test.
2. Undeclared gaps in the merged PR's caveats: death (PvP loss unless locked; PvM gravestone keeps the
   runes), Empty and deposit in a bank, Destroy (the cache's destroy text says the contents fall out; the
   module neither drops nor clears the varbits), auto-store, the divine pouch's creation and Revert, one
   pouch per player, Glarial's Tomb / Koschei, Empty stopping at the first slot that does not fit, the
   op-to-quantity mapping and Store-X / Withdraw-X not setting the custom amount. **Listed in this PR's
   Caveats** as still missing in the merged module; each sourced gap is a separate future fix (backlog), not
   part of this change.
3. Data sources: this PR names the wiki pages (capacity, type counts, NMZ exclusion, quantity buttons,
   Trivia).
4. Tests paragraph: says what was and was not checked in game.
5. The members-player re-check (`qa-members.sh`) is the QA's, on this commit.

## Tests
`RunePouchScriptTest` +1 (integration): every pouch is refused with "Don't be silly.", a non-rune with the
non-rune line, a rune accepted. Without the change it fails (no `storeRejection`, wrong line).

## Ids
None.

## PR split
One fix, no dependencies. The other gaps above are separate future fixes.

## Audit (2026-10-18, at af57c4ad8)

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Pouch items | Rune pouch 12791 / (l) 24416, divine 27281 / (l) 27509 (Rune pouch, Divine rune pouch infoboxes; RuneLite RunepouchOverlay ids) | `regularPouches` bh_rune_pouch(+_trouver), `divinePouches` divine(+_trouver) | matches |
| 2 | Rune types per pouch | Rune pouch 3, divine 4 (comparison table) | `slotCount()` 3 / 4 | matches |
| 3 | Capacity | 16,000 per type (comparison table) | `CAPACITY = 16_000` | matches |
| 4 | Allowed runes | Runes, except Nightmare Zone runes (Rune pouch intro); client draws enum 982 (23 runes incl. combination, wrath 21880, sunfire 28929, aether 30843) | `rune_compact_ids` enum = same set; NMZ runes not in it | matches |
| 5 | Storage varbits | rune_pouch_type_n (compact id) + rune_pouch_quantity_n (RunepouchOverlay reads 6 pairs, enum 982) | `setSlot()` writes the same varbits | matches |
| 6 | Same rune stacks | Same type goes into its existing slot | `slotFor()` held slot first, else first free | matches |
| 7 | Item ops | Interface ops Open, Empty (op 4), Configure, Destroy (cache item 12791) | `onOpHeld1` open, `onOpHeld4` empty | matches |
| 8 | Interface | Storage interface 190 with pouch and inventory panels (Interface) | `ifOpenMainModal("interface.rune_pouch")`, events on pouch 0..5 and inventory | matches |
| 9 | Quantity buttons | 1, 5, X, All for deposit and withdraw; X usable as left-click since 9 April 2025 (Changes) | `selectQuantity()` on 1/5/x/all components, `varbit.rune_pouch_selectedquantity`/`customquantity` | matches |
| 10 | Op-to-quantity mapping | Not documented (clientscript) | Op1 selected, Op2 1, Op3 5, Op4 custom X, Op5 X prompt, Op6 All | unsourced |
| 11 | Drag reorder | Slots can be rearranged (interface has DragTarget) | `onIfModalDrag(POUCH)` swaps | matches |
| 12 | Rune on pouch | Use a rune on the pouch stores it (common behaviour; not stated on the page) | `onOpHeldU(rune, pouch)` stores all | unsourced |
| 13 | Store the pouch itself | "Don't be silly." when storing the rune pouch while the interface is open (Trivia) | Non-rune, incl. the pouch: "You can only store runes in the rune pouch." | diverges (fixed on this branch: D1) |
| 14 | Spell casting from the pouch | Spells use stored runes (intro) | Existing server code reads the varbits (PR text) | matches |
| 15 | Empty | Empties into the inventory (in a bank: into the bank) | Withdraws each slot into the inventory; stops on the first failure | matches (inventory case) |
| 16 | Messages | Not on the wiki | "You can only store runes in the rune pouch.", "Your rune pouch has no room for another type of rune.", "Your rune pouch cannot hold any more of those runes.", "You don't have enough inventory space.", "Your rune pouch is empty." | unsourced |

## Divergences

### D1. Storing the pouch itself
- Severity: cosmetic
- Official: "If a player attempts to store the rune pouch while the storage interface is open, the message "Don't be silly." appears in the chatbox." (Rune pouch, Trivia)
- Patch: `inventoryOp()` sends `NOT_A_RUNE` for every non-rune, including the pouch.
- Fix: in `inventoryOp()`, `if (internal in RunePouch.pouches) { mes("Don't be silly."); return }` before the rune check.

## Unsourced
- All five messages listed in row 16; need game captures (e.g. storing a 4th type in a 3-type pouch, a full 16,000 stack, withdrawing with a full inventory, Empty on an empty pouch, a non-rune click).
- The Op1-Op6 to quantity mapping and the `selectedquantity` values 0..3 (One/Five/All/Custom); needs the interface 190 clientscript (op labels set by the init script).
- Use-rune-on-pouch storing the whole stack (and whether using the pouch on a rune is also handled).
- Empty with too little space: whether OSRS empties what fits slot by slot (patch stops at the first slot that fails) and its message.

## Not implemented (scope gaps)
- Death: PvP death in the Wilderness/PvP worlds loses the pouch (and its runes) unless locked; PvM death sends it to the gravestone with the runes kept (since 21 June 2023); locked pouches stay in the inventory (Rune pouch intro, Changes). Not declared in the PR text.
- Bank: Empty/deposit into the bank, F2P-world deposits, Configure load-outs (10 since 1 October 2025, names, bank withdraw-into-pouch). Load-outs are declared ("No loadouts (Configure)"); bank Empty is not.
- Auto-store setting (pickup, runecrafting, pickpocketing, Hallowed Sepulchre, Sailing loot; toggle in Settings / Magic combat tutor). Not declared.
- Divine rune pouch creation (thread of Elidinis + needle, 75 Crafting) and Revert with the slot-4 warning. Not declared.
- Destroy: "If you destroy the pouch, all the contents will fall out." (item 12791 param 65482) i.e. the varbits must be cleared/dropped. Not declared.
- One rune pouch per player; Glarial's Tomb/Koschei only when empty. Not declared.
