package com.dinodread.spawn;

import org.jspecify.annotations.Nullable;

import com.dinodread.entity.TRex;
import com.dinodread.entity.Velociraptor;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Natural T-rex spawning. Every {@code attemptIntervalTicks} each eligible player rolls once; if the rules allow it a T-rex is
 * placed on the surface {@code minSpawnDistance} to {@code maxSpawnDistance} blocks away (far: it should be heard before it is seen).
 * A natural T-rex carries {@link #NATURAL_TAG} and is persistent, so vanilla's despawn rules leave it alone; this class removes it instead
 * when no player is near or it has stayed too long. Egg and command T-rexes are never touched. Server-side only.
 */
public final class RexSpawner {
    public static final String NATURAL_TAG = "dinodread_natural_spawn";
    private static final long TICKS_PER_DAY = 24000L;
    private static final int DESPAWN_CHECK_INTERVAL_TICKS = 100;
    // Probe a little below the feet to find the block the T-rex stands on.
    private static final double FLOOR_PROBE = 0.5;
    private static final IdTagList<Biome> BIOMES = new IdTagList<>("allowedBiomes", Registries.BIOME);

    private RexSpawner() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tickLevel(level, SpawnRules.fromConfig(), level.getRandom());
        }
    }

    public static int tickLevel(ServerLevel level, SpawnRules rules, RandomSource random) {
        long time = level.getGameTime();
        if (time % DESPAWN_CHECK_INTERVAL_TICKS == 0) {
            despawnStrayRexes(level, rules);
        }
        if (!rules.spawnEnabled() || time % Math.max(1, rules.attemptIntervalTicks()) != 0) {
            return 0;
        }
        int spawned = 0;
        for (ServerPlayer player : level.players()) {
            if (trySpawn(level, player, rules, random) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    public static @Nullable TRex trySpawn(ServerLevel level, ServerPlayer player, SpawnRules rules, RandomSource random) {
        if (!rules.allowedDimensions().contains(level.dimension().identifier().toString())) {
            return null;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.isSpawningMonsters()) {
            return null;
        }
        if (!player.isAlive() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) {
            return null;
        }
        if (level.getGameTime() / TICKS_PER_DAY < rules.firstSpawnDelayDays()) {
            return null;
        }
        if (rules.nightOnly() && DayNight.nightFactor(level, player.position()) < rules.minNightFactor()) {
            return null;
        }
        if (countRex(level, null, 0.0) >= rules.maxRexInWorld()
                || countRex(level, player.position(), rules.capRadius()) >= rules.maxRexPerPlayer()) {
            return null;
        }
        if (random.nextDouble() >= rules.spawnChance()) {
            return null;
        }

        TRex rex = pickType(rules, random).create(level, EntitySpawnReason.NATURAL);
        if (rex == null) {
            return null;
        }
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
            if (!level.isPositionEntityTicking(new BlockPos(blockX, player.getBlockY(), blockZ))) {
                continue;
            }
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            BlockPos spot = new BlockPos(blockX, surfaceY, blockZ);
            if (!rules.allowedBiomes().isEmpty() && !BIOMES.matches(rules.allowedBiomes(), level.getBiome(spot))) {
                continue;
            }
            // Nobody (not just this player) may be inside the minimum distance of the spot.
            if (level.getNearestPlayer(x, surfaceY, z, min, EntitySelector.NO_CREATIVE_OR_SPECTATOR) != null) {
                continue;
            }
            if (!canStandAt(level, rex, x, surfaceY, z)) {
                continue;
            }
            // canStandAt already moved the rex to this spot
            TRex placed = place(level, rex, random);
            if (placed != null && rules.spawnAlerted()) {
                placed.getSenses().alertTo(player.position());
            }
            return placed;
        }
        return null;
    }

    /** One in {@code superRexChance} of natural spawns is a super rex instead (0.05 = one in 20). */
    private static EntityType<? extends TRex> pickType(SpawnRules rules, RandomSource random) {
        if (rules.superRexChance() > 0.0 && random.nextDouble() < rules.superRexChance()) {
            return ModEntities.SUPER_REX.get();
        }
        return ModEntities.TREX.get();
    }

    private static @Nullable TRex place(ServerLevel level, TRex rex, RandomSource random) {
        float yaw = random.nextFloat() * 360.0F;
        rex.setYRot(yaw);
        rex.setYHeadRot(yaw);
        rex.setYBodyRot(yaw);
        EventHooks.finalizeMobSpawn(rex, level, level.getCurrentDifficultyAt(rex.blockPosition()), EntitySpawnReason.NATURAL, null);
        if (rex.isSpawnCancelled()) {
            return null;
        }
        markNatural(rex);
        return level.addFreshEntity(rex) ? rex : null;
    }

    /**
     * Never hard-code a size here: the hitbox comes from the entity (type size times the scale attribute).
     */
    public static boolean canStandAt(ServerLevel level, TRex rex, double x, double y, double z) {
        rex.snapTo(x, y, z, rex.getYRot(), 0.0F);
        BlockPos below = BlockPos.containing(x, y - FLOOR_PROBE, z);
        BlockState floor = level.getBlockState(below);
        return floor.isFaceSturdy(level, below, Direction.UP) && level.noCollision(rex) && rex.checkSpawnObstruction(level);
    }

    public static void markNatural(TRex rex) {
        rex.addTag(NATURAL_TAG);
        rex.setPersistenceRequired();
    }

    public static int despawnStrayRexes(ServerLevel level, SpawnRules rules) {
        int removed = 0;
        for (TRex rex : level.getEntities(EntityTypeTest.forClass(TRex.class),
                candidate -> candidate.entityTags().contains(NATURAL_TAG) && !(candidate instanceof Velociraptor))) {
            boolean tooFar = rules.despawnWhenFar()
                    && level.getNearestPlayer(rex.getX(), rex.getY(), rex.getZ(), rules.despawnDistance(), false) == null;
            boolean tooOld = rules.maxLifetimeTicks() > 0 && rex.tickCount > rules.maxLifetimeTicks() && !rex.getSenses().hasQuarry();
            if (tooFar || tooOld) {
                rex.discard();
                removed++;
            }
        }
        return removed;
    }

    private static int countRex(ServerLevel level, @Nullable Vec3 center, double radius) {
        int count = 0;
        // Raptors run under their own caps (RaptorSpawner); they never count toward the T-rex's.
        for (TRex rex : level.getEntities(EntityTypeTest.forClass(TRex.class), candidate -> candidate.isAlive() && !(candidate instanceof Velociraptor))) {
            if (center == null || rex.position().distanceToSqr(center) <= radius * radius) {
                count++;
            }
        }
        return count;
    }
}
