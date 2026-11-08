package com.dinodread.spawn;

import java.util.List;

import com.dinodread.config.RaptorConfig;

/** An immutable snapshot of {@link RaptorConfig}'s spawning section, passed through the pack spawner (and GameTests). */
public record RaptorRules(
        boolean spawnEnabled,
        boolean nightOnly,
        double minNightFactor,
        int attemptIntervalTicks,
        double spawnChance,
        List<String> allowedDimensions,
        double minSpawnDistance,
        double maxSpawnDistance,
        int placementAttempts,
        int packMinSize,
        int packMaxSize,
        int packSpreadBlocks,
        int maxRaptorPerPlayer,
        double capRadius,
        int maxRaptorInWorld,
        boolean despawnWhenFar,
        double despawnDistance) {

    public static RaptorRules fromConfig() {
        return new RaptorRules(
                RaptorConfig.SPAWN_ENABLED.get(),
                RaptorConfig.NIGHT_ONLY.get(),
                RaptorConfig.MIN_NIGHT_FACTOR.get(),
                RaptorConfig.SPAWN_ATTEMPT_INTERVAL_TICKS.get(),
                RaptorConfig.SPAWN_CHANCE.get(),
                List.copyOf(RaptorConfig.ALLOWED_DIMENSIONS.get()),
                RaptorConfig.MIN_SPAWN_DISTANCE.get(),
                RaptorConfig.MAX_SPAWN_DISTANCE.get(),
                RaptorConfig.PLACEMENT_ATTEMPTS.get(),
                RaptorConfig.PACK_MIN_SIZE.get(),
                RaptorConfig.PACK_MAX_SIZE.get(),
                RaptorConfig.PACK_SPREAD_BLOCKS.get(),
                RaptorConfig.MAX_RAPTOR_PER_PLAYER.get(),
                RaptorConfig.CAP_RADIUS.get(),
                RaptorConfig.MAX_RAPTOR_IN_WORLD.get(),
                RaptorConfig.DESPAWN_WHEN_FAR.get(),
                RaptorConfig.DESPAWN_DISTANCE.get());
    }

    public RaptorRules withSpawnChance(double value) {
        return new RaptorRules(spawnEnabled, nightOnly, minNightFactor, attemptIntervalTicks, value,
                allowedDimensions, minSpawnDistance, maxSpawnDistance, placementAttempts,
                packMinSize, packMaxSize, packSpreadBlocks, maxRaptorPerPlayer, capRadius, maxRaptorInWorld,
                despawnWhenFar, despawnDistance);
    }

    public RaptorRules withNightOnly(boolean value) {
        return new RaptorRules(spawnEnabled, value, minNightFactor, attemptIntervalTicks, spawnChance,
                allowedDimensions, minSpawnDistance, maxSpawnDistance, placementAttempts,
                packMinSize, packMaxSize, packSpreadBlocks, maxRaptorPerPlayer, capRadius, maxRaptorInWorld,
                despawnWhenFar, despawnDistance);
    }

    public RaptorRules withPackSize(int min, int max) {
        return new RaptorRules(spawnEnabled, nightOnly, minNightFactor, attemptIntervalTicks, spawnChance,
                allowedDimensions, minSpawnDistance, maxSpawnDistance, placementAttempts,
                min, max, packSpreadBlocks, maxRaptorPerPlayer, capRadius, maxRaptorInWorld,
                despawnWhenFar, despawnDistance);
    }

    public RaptorRules withCaps(int perPlayer, int inWorld) {
        return new RaptorRules(spawnEnabled, nightOnly, minNightFactor, attemptIntervalTicks, spawnChance,
                allowedDimensions, minSpawnDistance, maxSpawnDistance, placementAttempts,
                packMinSize, packMaxSize, packSpreadBlocks, perPlayer, capRadius, inWorld,
                despawnWhenFar, despawnDistance);
    }

    public RaptorRules withSpawnDistances(double min, double max) {
        return new RaptorRules(spawnEnabled, nightOnly, minNightFactor, attemptIntervalTicks, spawnChance,
                allowedDimensions, min, max, placementAttempts,
                packMinSize, packMaxSize, packSpreadBlocks, maxRaptorPerPlayer, capRadius, maxRaptorInWorld,
                despawnWhenFar, despawnDistance);
    }

    public RaptorRules withDespawnWhenFar(boolean value) {
        return new RaptorRules(spawnEnabled, nightOnly, minNightFactor, attemptIntervalTicks, spawnChance,
                allowedDimensions, minSpawnDistance, maxSpawnDistance, placementAttempts,
                packMinSize, packMaxSize, packSpreadBlocks, maxRaptorPerPlayer, capRadius, maxRaptorInWorld,
                value, despawnDistance);
    }
}
