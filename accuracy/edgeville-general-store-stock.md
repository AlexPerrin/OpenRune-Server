# Edgeville General Store stock (fix/edgeville-general-store-stock)

Spec for rebuilding `inv.generalshop2` (the Edgeville General Store's shop inv), which upstream ships
truncated. Upstream PR #351 (ours, draft) points the Edgeville shopkeepers at `inv.generalshop2` and depends on
this change; this branch is off upstream `main` bcdb03292 (first written off c2492318d) and does not include #351.

## The defect
`.data/raw-cache/server/shops/generalshop2.toml` (added in 6299e4114 "Mapped some osrs shops", unchanged since)
lost its header and first stock entries: no `sellMultiplier`/`buyMultiplier`/`delta`, no `size`, no
`protect`/`runWeight`/`restock`/`allStock`/`placeholders`, and the chisel entry's `[[inventory.stock]]` line is
gone, so `obj`/`count`/`restockCycles` sit on the inventory table itself. Packed, the inv has size 0, flags 1
(protect only, the codec default), 4 stock entries (hammer, knife, newcomer map, security book), and
`Inventory.create` (engine/game/.../inv/Inventory.kt:159) allocates `type.size` slots and writes every stock entry
into it: ArrayIndexOutOfBoundsException when the shop opens (found by sq23 on the integration cutover).

## Upstream first
- `gh pr list -R OpenRune/OpenRune-Server --search "general store"` / `generalshop2` (2026-10-18): #351 (ours,
  draft), #341 (merged, "Fix general store stock mappings": Falador, Karamja, Nardah, Miscellania via area-pack
  overrides; Edgeville untouched), #314 (merged, live-capture inv mappings; not generalshop2). No issue
  mentions generalshop2. Issue #116 (shop mapping tracker) is general.
- `main` c2492318d: `generalshop2` is referenced only by its toml and
  `tools/wiki-dumping/src/main/resources/shopmappings.csv` (`generalshop2,edgeville_general_store`). No script
  opens it (Edgeville's `EdgevilleGeneralStore.kt` opens `inv.generalshop5`, which #351 fixes).

## Sources
- OSRS Wiki, "Edgeville General Store", Stock section (via osrs-mcp `wiki_page`, 2026-10-18): Sells at 130.0%,
  Buys at 40.0%, Change per 3.0%; 14 rows, in this order:

  | Item | Stock | Restock | toml obj |
  |---|---|---|---|
  | Pot | 5 | 10t | obj.pot_empty |
  | Jug | 2 | 100t | obj.jug_empty |
  | Empty jug pack | 5 | 20t | obj.pack_jug_empty |
  | Shears | 2 | 100t | obj.shears |
  | Bucket | 3 | 10t | obj.bucket_empty |
  | Empty bucket pack | 15 | 10t | obj.pack_bucket |
  | Bowl | 2 | 50t | obj.bowl_empty |
  | Cake tin | 2 | 50t | obj.cake_tin |
  | Tinderbox | 2 | 100t | obj.tinderbox |
  | Chisel | 2 | 100t | obj.chisel |
  | Hammer | 5 | 100t | obj.hammer |
  | Knife | 1 | 2t (1.2s) | obj.knife |
  | Newcomer map | 5 | 100t | obj.newcomer_map |
  | Security book | 5 | 100t | obj.sos_security_book |

  Changes: "19 January 2022 (Update) The Edgeville General Store now stocks knives." The four surviving entries
  in the truncated toml (hammer 5/100, knife 1/2, newcomer map 5/100, security book 5/100, chisel 2/100 on the
  table) agree with the wiki, which confirms the file was cut, not mis-dumped.
- Obj names: the same names as `generalshop5.toml` (Lumbridge) and `generalshop4.toml` (#341's Falador
  override), which list the same items.
- Shop inv header: `generalshop5.toml` (Shared, stack Always, 1300/400/30, protect/runWeight/allStock/placeholders
  false, restock true), the same values every general store toml in the tree uses.

## Planned behaviour
| # | Field | Value | Source |
|---|---|---|---|
| 1 | Stock, order, counts, restock cycles | the 14 rows above, in wiki order | wiki Stock |
| 2 | sellMultiplier / buyMultiplier / delta | 1300 / 400 / 30 | wiki 130% / 40% / 3% (informational; the server's pricing comes from `shops.open`) |
| 3 | restock | true | stock restocks (wiki restock times) |
| 4 | protect / runWeight / allStock / placeholders | false | same as every other general store toml |
| 5 | size | 40 | upstream's own #341 (merged) rewrote Falador, Karamja, Nardah and Miscellania from live captures with size 40: real general stores hold the items players sell to them beyond the base stock. The older raw-cache tomls use size = stock count (Lumbridge 15). |

## Caveats / guesses
- Size 40 is taken from #341's live captures of other general stores, not a capture of Edgeville's.
- General stores in OSRS buy any tradeable item; with `allStock = false` (as in every upstream general store
  toml) this one only buys what it stocks. Pre-existing upstream behaviour for all general stores, not changed
  here.
- Multipliers are not read by the server today; they are kept for the dumper's format.

## Other shop tomls (report only, not fixed here)
A structural scan of the 395 tomls in `.data/raw-cache/server/shops` and the 6 inventories in area-pack configs:
generalshop2 is the only one with stock keys on the inventory table, a missing `size`, or `size` below its stock
count. Seven files (axe_shop, brew_reward_shop, hunting_customfurshop, poh_costume_room_armour_inv,
rangingguild_trade_tickets, templeknight_armoury1/2) omit `sellMultiplier`/`buyMultiplier` but are otherwise
complete; that is how the wiki dumper writes shops whose wiki infobox has no multiplier, not truncation.

## Tests
None in the PR. Upstream's AGENTS.md skips tests for config-matches-cache/wiki checks, so the structural toml
test from the first version (ShopInventoryTomlTest) was dropped after the upstream gate (qa record).
Verification: `buildCache`, then the packed `inv.generalshop2` decoded from `.data/cache/SERVER` (size 40, 14
stock entries in wiki order); the scan of every shop toml above was a one-off script, not a test.
