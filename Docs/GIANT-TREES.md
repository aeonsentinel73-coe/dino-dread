# Giant trees — worldgen notes

Data-driven only (no Java worldgen code). All under `src/main/resources/data/`.

## What ships
- **`dinodread:giant_tree`** — the prehistoric giant: `mega_jungle_trunk_placer` (the 2×2 wandering
  jungle trunk with side branches), `base_height = 10`, `height_rand_a = 2`, `height_rand_b = 19`
  → 10–31 blocks tall (same geometry as vanilla mega-jungle trees, which reach 21+ trunk with
  branches), `jungle_foliage_placer` canopy (NOTE: 26.3's foliage-placer registry has NO
  `mega_jungle_foliage_placer` — that class is unregistered, naming it is a hard registry crash)
  and a light leave-vine dressing. Density: `count` via `weighted_list` {0:65, 1:35} — a giant
  attempt in ~35% of chunks in every tree biome; the original 1-in-24 rarity read as "never
  worked" (confirmed by live playtest).
- **`dinodread:giant_tree_jungle`** — the same feature at `count 1` (one attempt per chunk), via
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

## The placement-chain rule (this cost a full investigation — do not skip)
**A placement chain MUST contain a surface modifier (`minecraft:heightmap`).** In 26.3, chunk
decoration hands every PlacedFeature the RAW chunk origin — `y = -64` (bedrock bottom). Nothing
lifts the position to the surface except a modifier in the chain (`minecraft:heightmap` →
`OCEAN_FLOOR`, or the `surface_*` filter family). Without one, the feature runs thousands of
times per region, inspects bedrock, rejects, and places ZERO trees, with no error anywhere.
This is exactly why vanilla biomes never reference tree features directly: they point at wrapper
placed features (`minecraft:trees_jungle`, `minecraft:trees_plains`…) whose standard preamble is
`count → in_square → surface_water_depth_filter → heightmap → biome`. Our chains now copy that
preamble verbatim (plus a trailing `would_survive` sapling filter).
Verified end-to-end in a headless dev server (`runServer` + RCON `forceload`): 202 giants placed
across a 15×15-chunk lab, 30/529 saved chunks contain `jungle_log` at production density.

## Gotchas found while building this
- **26.3 `"minecraft:count"` takes an IntProvider** — a bare fraction (`0.35`) silently truncates
  to 0 attempts (valid JSON, no error, feature never runs). Sub-chunk density = `weighted_list`
  of integers, e.g. {0:65, 1:35} ≈ one attempt per three chunks — the same trick vanilla uses for
  `trees_jungle`'s 50.1. (`minecraft:uniform` is integer-only: `min_inclusive`/`max_inclusive`.)
- **Game tests can hide all of the above**: gametests call `ConfiguredFeature.place()` directly,
  which bypasses biome modifiers AND the placement chain. `giant_tree_attached_to_tree_biomes`
  now guards the real chain: asserts a `HeightmapPlacement` exists, the count IntProvider averages
  > 0 over 200 seeds, and the biome modifiers attach to forest + jungle.
- The biome modifier key is `step` (singular), value `vegetal_decoration`; `steps` fails to parse.
- `TreeFeature` refuses to place unless the trunk column **and its ±1 neighbours** up to
  `treeHeight - limit` are free — a failing `place()` is silent, indistinguishable from clipping.
  The placement game test clears a 3×3 shaft, 40 high, before direct placement.
- Overriding `minecraft:jungle_tree` affects vanilla worldgen packs that reference it by
  id; anyone else's datapack wins if it loads after ours (order = alphabetical, `dinodread` >
  `minecraft` so ours loads later and wins).

## Status
- Game tests (`RexWorldgenGameTests`) count solid log columns after direct placement:
  giant ≥ 2×2×15 columns, jungle ≥ 4 logs; plus the chain/attachment guard. Green.
- Headless-lab verified: giants land on the surface in forest/plains/taiga and save to disk.
- A 9×9 SkyFactory-style giant is not achievable with vanilla tree machinery (trunk placers only
  grow 1×1/2×2 columns + branches) — a true 9×9 would need a custom multiblock feature, which is
  a separate project if we ever want it.
- Existing worlds: chunks generated before the heightmap fix stay treeless; fly outward or use a
  new world. Giants only appear in NEW chunks.
