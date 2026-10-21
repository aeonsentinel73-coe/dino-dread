# Task 018: `RexSpawner`, the config-driven natural spawner for the T-rex

**Goal:** replace the STUB `src/main/java/com/dinodread/spawn/RexSpawner.java` with the real spawner. The public surface is FIXED (the
game tests in `RexSpawnGameTests` already call it): keep every public signature and the `NATURAL_TAG` value exactly as in the stub.
Everything is driven by an explicit `SpawnRules` argument (never read `SpawnConfig` here); `onLevelTick` is the only place that calls
`SpawnRules.fromConfig()`.

Working directory: `D:\Documents\Claude Code\Dino Dread-m6` (pass it as `working_dir`).

## PROCEDURE (follow exactly)
1. Read these files: `src/main/java/com/dinodread/spawn/RexSpawner.java` (stub), `src/main/java/com/dinodread/spawn/SpawnRules.java`
   (the record you are given; only the component names matter), `src/main/java/com/dinodread/world/DayNight.java`,
   `src/main/java/com/dinodread/world/IdTagList.java` (its `matches(List, Holder)` is what you call).
2. Write the whole `RexSpawner.java` in one write. Do NOT search the filesystem and do NOT grep for Minecraft symbols: every
   import and call below is already verified against the pinned sources.
3. Finish with `finish` in under 12 tool calls. You cannot run Gradle; Claude will build.

Do NOT touch: `.git/`, `TRex.java`, `ModEntities.java`, `SpawnRules.java`, `SpawnConfig.java`, any test, any other file.

## Imports (verified)
```java
import org.jspecify.annotations.Nullable;

import com.dinodread.entity.TRex;
import com.dinodread.registry.ModEntities;
import com.dinodread.world.DayNight;
import com.dinodread.world.IdTagList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
```
Drop any import you end up not using.

## Constants and fields
```java
public static final String NATURAL_TAG = "dinodread_natural_spawn";
private static final long TICKS_PER_DAY = 24000L;
private static final int DESPAWN_CHECK_INTERVAL_TICKS = 100;
// Probe a little below the feet to find the block the T-rex stands on.
private static final double FLOOR_PROBE = 0.5;
private static final IdTagList<Biome> BIOMES = new IdTagList<>("allowedBiomes", Registries.BIOME);
```
Class javadoc: "Natural T-rex spawning. Every {@code attemptIntervalTicks} each eligible player rolls once; if the rules allow it a T-rex is
placed on the surface {@code minSpawnDistance} to {@code maxSpawnDistance} blocks away (far: it should be heard before it is seen).
A natural T-rex carries {@link #NATURAL_TAG} and is persistent, so vanilla's despawn rules leave it alone; this class removes it instead
when no player is near or it has stayed too long. Egg and command T-rexes are never touched. Server-side only."

## Methods (every method is `static`; `public` unless marked private)

### `void onLevelTick(LevelTickEvent.Post event)`
`if (event.getLevel() instanceof ServerLevel level) { tickLevel(level, SpawnRules.fromConfig(), level.getRandom()); }`

### `int tickLevel(ServerLevel level, SpawnRules rules, RandomSource random)`
Returns how many T-rexes were spawned.
1. `long time = level.getGameTime();`
2. `if (time % DESPAWN_CHECK_INTERVAL_TICKS == 0) { despawnStrayRexes(level, rules); }`  (runs even when spawning is off, to clean up)
3. `if (!rules.spawnEnabled() || time % Math.max(1, rules.attemptIntervalTicks()) != 0) { return 0; }`
4. `int spawned = 0;` for each `ServerPlayer player : level.players()`: `if (trySpawn(level, player, rules, random) != null) { spawned++; }`
5. return `spawned`.

### `@Nullable TRex trySpawn(ServerLevel level, ServerPlayer player, SpawnRules rules, RandomSource random)`
Gates first, cheapest first, `return null` at the first one that fails (no side effects before the placement search):
1. `rules.allowedDimensions().contains(level.dimension().identifier().toString())` must be true.
2. `level.getDifficulty() != Difficulty.PEACEFUL` and `level.isSpawningMonsters()` must both be true.
3. `player.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)` must be true.
4. `level.getGameTime() / TICKS_PER_DAY >= rules.firstSpawnDelayDays()`.
5. if `rules.nightOnly()`: `DayNight.nightFactor(level, player.position()) >= rules.minNightFactor()` (a float compared with a double is fine).
6. caps: `countRex(level, null, 0.0) < rules.maxRexInWorld()` AND `countRex(level, player.position(), rules.capRadius()) < rules.maxRexPerPlayer()`.
7. the roll: `random.nextDouble() < rules.spawnChance()` (so chance 0 never passes and chance 1 always passes).

Then the placement search:
```java
TRex rex = ModEntities.TREX.get().create(level, EntitySpawnReason.NATURAL);
if (rex == null) { return null; }
double min = rules.minSpawnDistance();
double max = Math.max(rules.maxSpawnDistance(), min);
for (int attempt = 0; attempt < rules.placementAttempts(); attempt++) {
    double angle = random.nextDouble() * (Math.PI * 2.0);
    double distance = Mth.lerp(random.nextDouble(), min, max);
    double x = player.getX() + Math.cos(angle) * distance;
    double z = player.getZ() + Math.sin(angle) * distance;
    int blockX = Mth.floor(x);
    int blockZ = Mth.floor(z);
    // Only chunks that are loaded and running: this must never load or generate a chunk. Check BEFORE touching any block.
    if (!level.isPositionEntityTicking(new BlockPos(blockX, player.getBlockY(), blockZ))) { continue; }
    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
    BlockPos spot = new BlockPos(blockX, surfaceY, blockZ);
    if (!rules.allowedBiomes().isEmpty() && !BIOMES.matches(rules.allowedBiomes(), level.getBiome(spot))) { continue; }
    // Nobody (not just this player) may be inside the minimum distance of the spot.
    if (level.getNearestPlayer(x, surfaceY, z, min, EntitySelector.NO_CREATIVE_OR_SPECTATOR) != null) { continue; }
    if (!canStandAt(level, rex, x, surfaceY, z)) { continue; }
    return place(level, rex, random);   // canStandAt already moved the rex to this spot
}
return null;
```
(`getHeight` returns the y of the first free block above the surface, which is where the feet go. `player.getBlockY()` is just
a y for the chunk check, any value works.)

### `private TRex place(ServerLevel level, TRex rex, RandomSource random)` -> returns `@Nullable TRex`
1. `float yaw = random.nextFloat() * 360.0F; rex.setYRot(yaw); rex.setYHeadRot(yaw); rex.setYBodyRot(yaw);`
2. `EventHooks.finalizeMobSpawn(rex, level, level.getCurrentDifficultyAt(rex.blockPosition()), EntitySpawnReason.NATURAL, null);`
   (this fires the NeoForge finalize-spawn event; do not call `rex.finalizeSpawn` directly)
3. `if (rex.isSpawnCancelled()) { return null; }`
4. `markNatural(rex);`
5. `return level.addFreshEntity(rex) ? rex : null;`

### `boolean canStandAt(ServerLevel level, TRex rex, double x, double y, double z)`
Moves `rex` to the spot (so its REAL, scaled hitbox is what gets checked) and says whether it fits and has ground:
```java
rex.snapTo(x, y, z, rex.getYRot(), 0.0F);
BlockPos below = BlockPos.containing(x, y - FLOOR_PROBE, z);
BlockState floor = level.getBlockState(below);
return floor.isFaceSturdy(level, below, Direction.UP) && level.noCollision(rex) && rex.checkSpawnObstruction(level);
```
Javadoc: "Never hard-code a size here: the hitbox comes from the entity (type size times the scale attribute)."

### `void markNatural(TRex rex)`
`rex.addTag(NATURAL_TAG); rex.setPersistenceRequired();`

### `int despawnStrayRexes(ServerLevel level, SpawnRules rules)`
Returns how many were removed. Only T-rexes carrying `NATURAL_TAG` are ever considered.
```java
int removed = 0;
for (TRex rex : level.getEntities(EntityTypeTest.forClass(TRex.class), candidate -> candidate.entityTags().contains(NATURAL_TAG))) {
    boolean tooFar = rules.despawnWhenFar() && level.getNearestPlayer(rex.getX(), rex.getY(), rex.getZ(), rules.despawnDistance(), false) == null;
    boolean tooOld = rules.maxLifetimeTicks() > 0 && rex.tickCount > rules.maxLifetimeTicks() && !rex.getSenses().hasQuarry();
    if (tooFar || tooOld) {
        rex.discard();
        removed++;
    }
}
return removed;
```

### `private int countRex(ServerLevel level, @Nullable Vec3 center, double radius)`
Counts living T-rexes of ANY origin (eggs included): all in the level when `center == null`, else those whose `position()` is within `radius` of `center`
(compare `distanceToSqr` with `radius * radius`).
```java
for (TRex rex : level.getEntities(EntityTypeTest.forClass(TRex.class), candidate -> candidate.isAlive())) { ... }
```

## Worked example (what the tests expect)
Player at night, rules: chance 1.0, nightOnly, ring 24 to 60, caps 1/1, no T-rex around, 400 placement attempts, flat open ground
only in part of the ring: `trySpawn` returns a persistent T-rex tagged `NATURAL_TAG`, 24 to 60 blocks (horizontal) from the player,
standing on solid ground with no block inside its 9 x 8.1 box. At noon with `nightOnly` it returns null; with a T-rex already
alive and caps of 1 it returns null; `despawnStrayRexes` removes a tagged T-rex only when no player is within `despawnDistance`
(and `despawnWhenFar` is on) or it is older than `maxLifetimeTicks` and not hunting.

## Acceptance (Claude checks)
`.\gradlew build` passes; the spawner_* GameTests pass.
