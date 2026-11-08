# Giant trees — worldgen notes

Data-driven only (no Java worldgen code). All under `src/main/resources/data/`.

## What ships
- **`dinodread:giant_tree`** — mega-jungle trunk (the 2-wide jungle giant vanilla ships for its
  own jungle trees), `base_height = 16`, `height_rand_a = 4`, `height_rand_b = 10` → 16–30 blocks
  tall, blob foliage cap. Placed with a `rarity_filter` of 1-in-24 per chunk attempt, so "a few
  per biome" rather than a forest of them.
- **Biome tag `dinodread:has_giant_trees`** — `#minecraft:is_forest`, `#is_jungle`, `#is_taiga`,
  `#is_savanna`, plus swamp, mangrove_swamp, cherry_grove, meadow.
- **`neoforge/biome_modifier/giant_trees.json`** — `add_features` at step `vegetal_decoration`.
- **Vanilla jungle trees taller**: `minecraft:jungle_tree` and `minecraft:jungle_tree_no_vine`
  configured features overridden with `straight_trunk_placer` base 10 + rand 10 → 10–20 tall
  (vanilla default is 4–12). Decorators (cocoa, vines) preserved.

## Gotchas found while building this
- The biome modifier key is `step` (singular), value `vegetal_decoration`; `steps` fails to parse.
- `TreeFeature` refuses to place unless the trunk column **and its ±1 neighbours** up to
  `treeHeight - limit` are free — a `rarity_filter`-wrapped `PlacedFeature.place` failing
  *silently and randomly* is indistinguishable from clipping in a game test. The game test calls
  the `ConfiguredFeature` directly and clears a 3×3 shaft, 40 high, before placement.
- Overriding `minecraft:jungle_tree` affects vanilla worldgen packs that reference it by
  id; anyone else's datapack wins if it loads after ours (order = alphabetical, `dinodread` >
  `minecraft` so ours loads later and wins).

## Status
- Game tests (`RexWorldgenGameTests`) count solid log columns after direct placement:
  giant ≥ 2×2×15 columns, jungle ≥ 4 logs. Green.
- **Not yet walked through a real generated world** — density (1/24) is the first thing to
  eyeball in `runClient` with a jungle/forest seed, together with the taller jungle trunks.
