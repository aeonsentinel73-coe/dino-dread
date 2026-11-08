# Giant trees — worldgen notes

Data-driven only (no Java worldgen code). All under `src/main/resources/data/`.

## What ships
- **`dinodread:giant_tree`** — the prehistoric giant: `mega_jungle_trunk_placer` (the 2×2 wandering
  jungle trunk with side branches), `base_height = 16`, `height_rand_a = 4`, `height_rand_b = 10`
  → 16–30 blocks tall, proper `jungle_foliage_placer` canopy (blob foliage leaves the 2×2
  trunk top exposed; NOTE: 26.3's foliage-placer registry has NO `mega_jungle_foliage_placer` —
  that class is unregistered, and naming it in the JSON is a hard registry-load crash)
  and a light leave-vine dressing. Placed at `count 0.35` — a giant roughly
  every third chunk in every tree biome; the original 1-in-24 rarity was so sparse it read as
  "never worked" (confirmed by live playtest in a standard world).
- **`dinodread:giant_tree_jungle`** — the same feature at `count 1` (dense), via
  `giant_trees_jungle.json` on `#minecraft:is_jungle`, so real jungles are thick with them.
- **Biome tag `dinodread:has_giant_trees`** — `#minecraft:is_forest`, `#is_jungle`, `#is_taiga`,
  `#is_savanna`, plus swamp, mangrove_swamp, cherry_grove, meadow, plains, sunflower_plains,
  wooded_badlands, grove, windswept_forest. Everything that has trees naturally; no oceans,
  beaches, or bare tundra.
- **`neoforge/biome_modifier/giant_trees.json`** + **`giant_trees_jungle.json`** — `add_features`
  at step `vegetal_decoration`.
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
- Density is now `0.35` base / `1` in jungle. A 9×9 SkyFactory-style giant is not achievable with
  vanilla tree machinery (trunk placers only grow 1×1/2×2 columns + branches) — a true 9×9 would
  need a custom multiblock feature, which is a separate project if we ever want it.
- **Not yet walked through a real generated world since the density fix** — walk a fresh chunk
  border in a forest/plains and confirm giants are plainly visible; tune `count` from there.
