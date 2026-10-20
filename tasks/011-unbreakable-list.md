# Task 011 — Switch block breaking from an allow-list to an "unbreakable" list

**Why (Thomas):** the T-rex should get through most player structures (cobblestone, bricks, wool, ...), not only wood/glass/leaves. What it must NOT break
is natural terrain (stone, dirt, sand, ores, snow, ...: otherwise it tunnels through the world instead of walking over it) plus a few deliberately
strong materials: smooth stone, obsidian, iron blocks, concrete and everything End-themed.

**Goal:** `RexBlockBreaker.isBreakable` becomes "breakable unless unbreakable"; the config list `breakableBlocks` is replaced by `unbreakableBlocks`.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`.
**You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the filesystem or grep for Minecraft symbols; everything needed is
in the files you edit plus the symbols listed here (verified for Minecraft 26.3).

## Files to EDIT (only these three)
1. `src/main/java/com/dinodread/config/RexConfig.java`
2. `src/main/resources/assets/dinodread/lang/en_us.json`
3. `src/main/java/com/dinodread/entity/ai/RexBlockBreaker.java`

## 1. `RexConfig`
Replace the field `BREAKABLE_BLOCKS` (declaration and its definition in the `obstacles` section) by
`public static final ModConfigSpec.ConfigValue<List<? extends String>> UNBREAKABLE_BLOCKS;` defined in the SAME place with the same `defineListAllowEmpty` call shape
(key `"unbreakableBlocks"`, new-element supplier `() -> "minecraft:obsidian"`, validator `element -> element instanceof String`).
Comment (replace the old one, as a single string):
`"Blocks the T-rex never breaks. Everything else it pushes against (up to maxBreakHardness) it may break, unless it holds data (see breakBlockEntities). Entries are block ids (minecraft:obsidian) or block tags with a leading # (#minecraft:dirt). Default: natural terrain (so it walks over the world instead of tunnelling through it), smooth stone, obsidian, iron blocks, concrete and End blocks."`

Default list, EXACTLY these 41 strings in this order:
`"#minecraft:base_stone_overworld"`, `"#minecraft:base_stone_nether"`, `"#minecraft:dirt"`, `"#minecraft:grass_blocks"`, `"#minecraft:mud"`, `"#minecraft:moss_blocks"`,
`"#minecraft:sand"`, `"#c:gravels"`, `"#c:ores"`, `"#minecraft:snow"`, `"#minecraft:ice"`, `"#minecraft:nylium"`, `"minecraft:dirt_path"`, `"minecraft:farmland"`,
`"minecraft:clay"`, `"minecraft:sandstone"`, `"minecraft:red_sandstone"`, `"minecraft:smooth_stone"`, `"minecraft:smooth_stone_slab"`, `"#c:obsidians"`,
`"#minecraft:concrete"`, `"#minecraft:concrete_slabs"`, `"#minecraft:concrete_stairs"`, `"minecraft:iron_block"`, `"minecraft:raw_iron_block"`, `"#c:end_stones"`,
`"minecraft:end_stone_bricks"`, `"minecraft:end_stone_brick_stairs"`, `"minecraft:end_stone_brick_slab"`, `"minecraft:end_stone_brick_wall"`, `"minecraft:purpur_block"`,
`"minecraft:purpur_pillar"`, `"minecraft:purpur_stairs"`, `"minecraft:purpur_slab"`, `"minecraft:end_rod"`, `"minecraft:end_portal_frame"`, `"minecraft:end_portal"`,
`"minecraft:end_gateway"`, `"minecraft:chorus_plant"`, `"minecraft:chorus_flower"`, `"minecraft:dragon_egg"`.
`List.of(...)` takes any number of arguments in Java 25 (it has a varargs overload), so put them in one `List.of(...)`, a few per source line.

## 2. Lang
In `en_us.json` rename the key `"dinodread.configuration.breakableBlocks"` to `"dinodread.configuration.unbreakableBlocks"` with value `"Unbreakable Blocks"` (same position; keep the JSON valid).

## 3. `RexBlockBreaker`
- Replace every use of `RexConfig.BREAKABLE_BLOCKS` by `RexConfig.UNBREAKABLE_BLOCKS`. Rename `matchesAllowList` to `matchesUnbreakableList`, and the cache fields
  `cachedBreakableBlocks` / `cachedBreakableBlockIds` / `cachedBreakableBlockTags` to `cachedUnbreakableBlocks` / `cachedUnbreakableBlockIds` / `cachedUnbreakableBlockTags`.
  Update the warning texts ("... in breakableBlocks config" -> "... in unbreakableBlocks config") and the comments/javadoc that talk about an "allow-list".
- `isBreakable(level, pos, state)` becomes true only if ALL hold:
  1. `!state.isAir()`;
  2. `!state.canBeReplaced()` (`BlockState.canBeReplaced()`, no arguments, exists in 26.3: plants, snow layers, fluids, fire are not obstacles and are never broken);
  3. the hardness check exactly as now (`>= 0` and `<= RexConfig.MAX_BREAK_HARDNESS.get()`);
  4. the block-entity check exactly as now;
  5. `!matchesUnbreakableList(state)`.
- In the matching code, test the id set with `cachedUnbreakableBlockIds.contains(state.getBlock())` instead of looping and calling `state.is(block)` (one hash lookup per block); keep the tag loop with `state.is(tag)`.
- Update the class javadoc and the `tick` javadoc wording accordingly ("breaks the blocks ... that are not on the unbreakable list").
- Everything else (volume, ordering, rate limit, griefing gate, `destroyBlock`) stays exactly as it is.

## DO NOT TOUCH
`.git/`, every other file (in particular `RexMover`, `TRexHuntGoal`, `TRexWanderGoal`, `gametest/`, `tasks/`, `Docs/`). No new numeric literals.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- GameTest `trex_block_breaker_follows_the_rules`, `trex_breaks_into_stone_brick_house`, `trex_leaves_concrete_house_alone`, `trex_climbs_sand_hill` and all earlier GameTests pass.
