# Task 004 — `RexBlockBreaker`: server-side, rate-limited, allow-listed block breaking

**Goal:** add one new class `com.dinodread.entity.ai.RexBlockBreaker` that, when called each tick with the direction
the T-rex is pushing, breaks the allow-listed blocks in the volume just ahead of its hitbox. Nothing calls it yet
(task 006 wires it in); a GameTest (`trex_block_breaker_follows_the_rules`) already exercises it directly.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`, mod id `dinodread`.
Every symbol below was verified against the Minecraft 26.3 / NeoForge 26.3.0.85 sources in this project. Minecraft
renamed things in 26.x (there is NO `ResourceLocation`: the id class is `net.minecraft.resources.Identifier`).
**Use exactly these names and imports. Do not substitute APIs from memory.** You cannot run Gradle (bash is off);
Claude builds afterwards. If you need to check a symbol you may grep the extracted sources if given a path, otherwise
use only what this spec lists.

## File to CREATE (only this one)
`src/main/java/com/dinodread/entity/ai/RexBlockBreaker.java`

Reference to copy the style from: `src/main/java/com/dinodread/entity/ai/TRexSenses.java` (class javadoc, constructor
takes the `TRex`, private named constants for non-config numbers, every tunable read from `RexConfig`).
Common (server+client) code: do NOT import anything from `net.minecraft.client` or `com.dinodread.client`.

## Public surface (exact)
```java
public class RexBlockBreaker {
    public RexBlockBreaker(TRex rex)

    /**
     * Call once per server tick while the T-rex is pushing toward something. {@code heading} is the unit-length HORIZONTAL
     * direction (y is ignored) it is trying to move in; {@code breakNow} says whether it should be breaking this tick
     * (it is touching a wall, or stuck). Returns how many blocks were broken this tick.
     */
    public int tick(Vec3 heading, boolean breakNow)

    /** True if this block may be broken by the T-rex under the current config (used by tick and by tests). */
    public boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state)
}
```

## Behavior of `tick(heading, breakNow)`
1. Rate limit: private int field `cooldownTicks`. If `cooldownTicks > 0`, decrement and return 0.
2. If `!breakNow`, return 0 (the cooldown is NOT started, so the first contact breaks straight away).
3. `if (!(rex.level() instanceof ServerLevel level)) return 0;` (never run on the client).
4. Griefing gate: return 0 unless `RexConfig.GRIEFING_ENABLED.get()` is true AND
   `net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, rex)` returns true
   (signature `public static boolean canEntityGrief(ServerLevel level, Entity entity)`; it applies the mobGriefing game rule).
5. Volume: `AABB box = rex.getBoundingBox().expandTowards(heading.x * reach, 0.0, heading.z * reach)` where
   `reach = RexConfig.BREAK_REACH.get()`. Block coordinate ranges (use `net.minecraft.util.Mth.floor(double)`):
   min = `floor(box.minX + BLOCK_EDGE_EPSILON)`, `floor(box.minY + BLOCK_EDGE_EPSILON)`, `floor(box.minZ + BLOCK_EDGE_EPSILON)`;
   max = `floor(box.maxX - BLOCK_EDGE_EPSILON)`, `floor(box.maxY - BLOCK_EDGE_EPSILON)`, `floor(box.maxZ - BLOCK_EDGE_EPSILON)`;
   with `private static final double BLOCK_EDGE_EPSILON = 1.0E-3;` (so the floor under its feet and the air layer exactly above its
   head are NOT included, and a wall it is merely touching is included only through `reach`).
6. Candidates: iterate `BlockPos.betweenClosed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)` (the
   `Iterable<BlockPos>` overload; it REUSES one mutable `BlockPos`, so store `pos.immutable()`, never `pos`).
   For each: `BlockState state = level.getBlockState(pos);` skip if `state.isAir()`, skip if `!isBreakable(level, pos, state)`.
7. Order: nearest first. Let `AABB body = rex.getBoundingBox();` and the "front point"
   `Vec3 front = new Vec3(body.getCenter().x + heading.x * body.getXsize() / 2.0, body.minY, body.getCenter().z + heading.z * body.getZsize() / 2.0);`
   Sort candidates ascending by `pos.distToCenterSqr(front)` (`Vec3i.distToCenterSqr(Position)`; `Vec3` is a `Position`).
8. Break at most `RexConfig.BLOCKS_PER_BREAK.get()` of them, in that order, with
   `level.destroyBlock(pos, RexConfig.BREAK_DROPS_ITEMS.get(), rex)` (signature `boolean destroyBlock(BlockPos, boolean dropResources, @Nullable Entity breaker)`);
   count the calls that return true.
9. Always (even when nothing was breakable) set `cooldownTicks = RexConfig.BREAK_INTERVAL_TICKS.get()` and return the count.

## Behavior of `isBreakable(level, pos, state)` — true only if ALL hold
- `!state.isAir()`.
- `float hardness = state.getDestroySpeed(level, pos);` (signature `getDestroySpeed(BlockGetter, BlockPos)`; `ServerLevel` is a `BlockGetter`).
  Require `hardness >= 0.0F` (negative = unbreakable, e.g. bedrock) and `hardness <= RexConfig.MAX_BREAK_HARDNESS.get()`.
- `state.hasBlockEntity()` implies `RexConfig.BREAK_BLOCK_ENTITIES.get()` must be true.
- The block matches `RexConfig.BREAKABLE_BLOCKS` (a `List<? extends String>`): an entry starting with `#` is a block TAG
  (`TagKey.create(net.minecraft.core.registries.Registries.BLOCK, Identifier.tryParse(entryWithoutHash))`, matched with
  `state.is(TagKey<Block>)`); any other entry is a block id (`BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(entry))`
  gives an `Optional<Block>`, matched with `state.is(Block)`). `Identifier.tryParse(String)` returns null for a malformed
  string: skip such entries and log ONE warning per bad entry with `DinoDread.LOGGER.warn(...)` (import `com.dinodread.DinoDread`).
  Unknown ids or empty tags simply never match.
- Parse the config list ONCE into a cache (static fields: the last parsed `List<? extends String>`, a `Set<Block>`, a `List<TagKey<Block>>`)
  and re-parse only when `RexConfig.BREAKABLE_BLOCKS.get()` is no longer `.equals(...)` the cached list. Do not parse strings on every call.

## Imports you will need (all verified)
`com.dinodread.DinoDread`, `com.dinodread.config.RexConfig`, `com.dinodread.entity.TRex`, `net.minecraft.core.BlockPos`,
`net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.core.registries.Registries`, `net.minecraft.resources.Identifier`,
`net.minecraft.server.level.ServerLevel`, `net.minecraft.tags.TagKey`, `net.minecraft.util.Mth`, `net.minecraft.world.level.block.Block`,
`net.minecraft.world.level.block.state.BlockState`, `net.minecraft.world.phys.AABB`, `net.minecraft.world.phys.Vec3`,
`net.neoforged.neoforge.event.EventHooks`, plus `java.util.*` as needed.

## DO NOT TOUCH
`.git/`, every existing file (this task only creates the one new file), `build.gradle`, `gradle.properties`, the `client/` package,
`AtmosphereConfig`, `AtmosphereEvents`, `AtmosphereEffects`, the lightmap mixin, everything under `tasks/` and `Docs/`.
No magic numbers other than `BLOCK_EDGE_EPSILON` and the literal `2.0` (halving a size) and `0.0`/`1`.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- GameTest `dinodread:trex_block_breaker_follows_the_rules` (Claude runs it): planks, glass, leaves and a log in front of the rex are
  broken; stone, a chest and bedrock are not.
