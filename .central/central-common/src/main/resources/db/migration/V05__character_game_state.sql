CREATE TABLE IF NOT EXISTS character_varps (
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    varp TEXT NOT NULL,
    value INTEGER NOT NULL,
    PRIMARY KEY (character_id, varp)
);

CREATE INDEX IF NOT EXISTS idx_character_varps_character ON character_varps (character_id);

CREATE TABLE IF NOT EXISTS character_attrs (
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    attr TEXT NOT NULL,
    value_json TEXT NOT NULL,
    PRIMARY KEY (character_id, attr)
);

CREATE INDEX IF NOT EXISTS idx_character_attrs_character ON character_attrs (character_id);

CREATE TABLE IF NOT EXISTS stats (
    character_id INTEGER NOT NULL,
    stat_id INTEGER NOT NULL,
    vis_level INTEGER NOT NULL,
    base_level INTEGER NOT NULL,
    fine_xp INTEGER NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (character_id) REFERENCES account_characters (id) ON DELETE CASCADE,
    UNIQUE (character_id, stat_id)
);

CREATE INDEX IF NOT EXISTS idx_stats_character_id ON stats (character_id);

CREATE TABLE IF NOT EXISTS inventories (
    character_id INTEGER NOT NULL,
    inv TEXT NOT NULL,
    PRIMARY KEY (character_id, inv),
    FOREIGN KEY (character_id) REFERENCES account_characters (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inventories_character_id ON inventories (character_id);

CREATE TABLE IF NOT EXISTS inventory_objs (
    character_id INTEGER NOT NULL,
    inv TEXT NOT NULL,
    slot INTEGER NOT NULL,
    obj TEXT NOT NULL,
    count INTEGER NOT NULL,
    vars INTEGER NOT NULL,
    PRIMARY KEY (character_id, inv, slot),
    FOREIGN KEY (character_id, inv) REFERENCES inventories (character_id, inv) ON DELETE CASCADE
);
