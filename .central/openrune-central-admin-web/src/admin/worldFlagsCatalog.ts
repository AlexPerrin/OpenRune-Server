/** Mirrors `dev.or2.central.world.WorldFlag` (mask + optional RuneLite `types` name). */
export const WORLD_FLAG_CATALOG: { name: string; mask: number; runeliteTypeName: string | null }[] = [
  { name: "MEMBERS", mask: 0x1, runeliteTypeName: "MEMBERS" },
  { name: "QUICKCHAT", mask: 0x2, runeliteTypeName: null },
  { name: "PVPWORLD", mask: 0x4, runeliteTypeName: "PVP" },
  { name: "LOOTSHARE", mask: 0x8, runeliteTypeName: null },
  { name: "DEDICATEDACTIVITY", mask: 0x10, runeliteTypeName: null },
  { name: "BOUNTYWORLD", mask: 0x20, runeliteTypeName: "BOUNTY" },
  { name: "PVPARENA", mask: 0x40, runeliteTypeName: "PVP_ARENA" },
  { name: "HIGHLEVELONLY_1500", mask: 0x80, runeliteTypeName: "SKILL_TOTAL" },
  { name: "SPEEDRUN", mask: 0x100, runeliteTypeName: "QUEST_SPEEDRUNNING" },
  { name: "EXISTINGPLAYERSONLY", mask: 0x200, runeliteTypeName: null },
  { name: "EXTRAHARDWILDERNESS", mask: 0x400, runeliteTypeName: "HIGH_RISK" },
  { name: "DUNGEONEERING", mask: 0x800, runeliteTypeName: null },
  { name: "INSTANCE_SHARD", mask: 0x1000, runeliteTypeName: null },
  { name: "RENTABLE", mask: 0x2000, runeliteTypeName: null },
  { name: "LASTMANSTANDING", mask: 0x4000, runeliteTypeName: "LAST_MAN_STANDING" },
  { name: "NEW_PLAYERS", mask: 0x8000, runeliteTypeName: null },
  { name: "BETA_WORLD", mask: 0x10000, runeliteTypeName: "BETA_WORLD" },
  { name: "STAFF_IP_ONLY", mask: 0x20000, runeliteTypeName: null },
  { name: "HIGHLEVELONLY_2000", mask: 0x40000, runeliteTypeName: null },
  { name: "HIGHLEVELONLY_2400", mask: 0x80000, runeliteTypeName: null },
  { name: "VIPS_ONLY", mask: 0x100000, runeliteTypeName: null },
  { name: "HIDDEN_WORLD", mask: 0x200000, runeliteTypeName: null },
  { name: "LEGACY_ONLY", mask: 0x400000, runeliteTypeName: "LEGACY_ONLY" },
  { name: "EOC_ONLY", mask: 0x800000, runeliteTypeName: "EOC_ONLY" },
  { name: "BEHIND_PROXY", mask: 0x1000000, runeliteTypeName: null },
  { name: "NOSAVE_MODE", mask: 0x2000000, runeliteTypeName: "NOSAVE_MODE" },
  { name: "TOURNAMENT_WORLD", mask: 0x4000000, runeliteTypeName: "TOURNAMENT" },
  { name: "FRESHSTART", mask: 0x8000000, runeliteTypeName: "FRESH_START_WORLD" },
  { name: "HIGHLEVELONLY_1750", mask: 0x10000000, runeliteTypeName: null },
  { name: "DEADMAN", mask: 0x20000000, runeliteTypeName: "DEADMAN" },
  { name: "SEASONAL", mask: 0x40000000, runeliteTypeName: "SEASONAL" },
  { name: "EXTERNAL_PARTNER_ONLY", mask: -2147483648, runeliteTypeName: null },
];

export function maskFromSelectedFlags(selected: Set<string>): number {
  let m = 0;
  for (const row of WORLD_FLAG_CATALOG) {
    if (selected.has(row.name)) {
      m |= row.mask;
    }
  }
  return m >>> 0;
}

export function csvFromMask(mask: number): string {
  const parts: string[] = [];
  const u = mask >>> 0;
  for (const row of WORLD_FLAG_CATALOG) {
    const rm = row.mask >>> 0;
    if ((u & rm) !== 0) {
      parts.push(row.name);
    }
  }
  return parts.join(",");
}

const catalogNameSet = new Set(WORLD_FLAG_CATALOG.map((f) => f.name));

/** Parse `worlds.flags` CSV into flag names (trimmed, non-empty). */
export function parseFlagsCsvToSet(csv: string): Set<string> {
  return new Set(
    csv
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0),
  );
}

/** Catalog flags in table order, then any unknown tokens sorted (preserves legacy/extra names). */
export function flagsSetToOrderedCsv(selected: Set<string>): string {
  const out: string[] = [];
  for (const row of WORLD_FLAG_CATALOG) {
    if (selected.has(row.name)) {
      out.push(row.name);
    }
  }
  const extras = [...selected].filter((n) => !catalogNameSet.has(n)).sort((a, b) => a.localeCompare(b));
  out.push(...extras);
  return out.join(",");
}
