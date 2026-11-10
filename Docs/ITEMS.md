# Items

## Something Fancy (`dinodread:something_fancy`)
Dinosaur meat — a friend requested the dinosaurs "drop something fancy", and the name stayed
literal. Textures are vanilla beef / cooked beef recolored to the T-rex's mossy green
(average rex green `#37522C`, luminance-mapped so the shading survives).

| | food | saturation | notes |
|---|---|---|---|
| Something Fancy (raw) | 2 | low | a bite |
| Something Fancy, Cooked | 20 | full | refills the whole bar |

**Cooking is lava-only.** Shapeless crafting: raw + lava bucket → cooked; vanilla returns the
empty bucket automatically (the lava bucket's `craftRemainder`). There is deliberately **no
furnace or campfire recipe** — a full bucket is the price of one meal.

**Drops** (loot tables under `data/dinodread/loot_table/entities/`, bound to the entities by id):

| mob | drop |
|---|---|
| Velociraptor | 45% chance of 1 |
| Tyrannosaurus Rex | 1 |
| Super Rex | 1–2 (uniform) |

26.3 loot JSON uses **singular** `condition` / `modifier` keys (not `conditions` / `functions`),
and there is no `set_probability` function — chance lives on a pool/entry as
`{"condition": {"type": "minecraft:random_chance", "chance": 0.45}}`.

Game tests (`RexItemGameTests`): food values, the real RecipeManager match + bucket remainder,
and deterministic rolls of the three loot tables through the parsed `LootTable` objects
(`LootTable.getRandomItems(params, random)` with `LootContextParamSets.EMPTY` — these tables
need no context parameters).
