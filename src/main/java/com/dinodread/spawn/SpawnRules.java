package com.dinodread.spawn;

import java.util.List;

import com.dinodread.config.SpawnConfig;

/**
 * A snapshot of the spawning config, so the spawner can be driven by explicit rules (the game tests do) instead of
 * reading the config directly. {@link #fromConfig()} is what the game uses.
 */
public record SpawnRules(
        boolean spawnEnabled,
        boolean nightOnly,
        double minNightFactor,
        int firstSpawnDelayDays,
        int attemptIntervalTicks,
        double spawnChance,
        List<? extends String> allowedDimensions,
        List<? extends String> allowedBiomes,
        double minSpawnDistance,
        double maxSpawnDistance,
        int placementAttempts,
        int maxRexPerPlayer,
        double capRadius,
        int maxRexInWorld,
        boolean despawnWhenFar,
        double despawnDistance,
        int maxLifetimeTicks) {

    public static SpawnRules fromConfig() {
        return new SpawnRules(
                SpawnConfig.SPAWN_ENABLED.get(),
                SpawnConfig.NIGHT_ONLY.get(),
                SpawnConfig.MIN_NIGHT_FACTOR.get(),
                SpawnConfig.FIRST_SPAWN_DELAY_DAYS.get(),
                SpawnConfig.ATTEMPT_INTERVAL_TICKS.get(),
                SpawnConfig.SPAWN_CHANCE.get(),
                SpawnConfig.ALLOWED_DIMENSIONS.get(),
                SpawnConfig.ALLOWED_BIOMES.get(),
                SpawnConfig.MIN_SPAWN_DISTANCE.get(),
                SpawnConfig.MAX_SPAWN_DISTANCE.get(),
                SpawnConfig.PLACEMENT_ATTEMPTS.get(),
                SpawnConfig.MAX_REX_PER_PLAYER.get(),
                SpawnConfig.CAP_RADIUS.get(),
                SpawnConfig.MAX_REX_IN_WORLD.get(),
                SpawnConfig.DESPAWN_WHEN_FAR.get(),
                SpawnConfig.DESPAWN_DISTANCE.get(),
                SpawnConfig.MAX_LIFETIME_TICKS.get());
    }

    public SpawnRules withSpawnEnabled(boolean value) {
        return new SpawnRules(value, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withNightOnly(boolean value) {
        return new SpawnRules(spawnEnabled, value, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withMinNightFactor(double value) {
        return new SpawnRules(spawnEnabled, nightOnly, value, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withFirstSpawnDelayDays(int value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, value, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withAttemptIntervalTicks(int value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, value, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withSpawnChance(double value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, value, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withAllowedDimensions(List<? extends String> value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, value, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withAllowedBiomes(List<? extends String> value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, value, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withMinSpawnDistance(double value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, value, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withMaxSpawnDistance(double value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, value, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withPlacementAttempts(int value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, value, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withMaxRexPerPlayer(int value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, value, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withCapRadius(double value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, value, maxRexInWorld, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withMaxRexInWorld(int value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, value, despawnWhenFar, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withDespawnWhenFar(boolean value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, value, despawnDistance, maxLifetimeTicks);
    }

    public SpawnRules withDespawnDistance(double value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, value, maxLifetimeTicks);
    }

    public SpawnRules withMaxLifetimeTicks(int value) {
        return new SpawnRules(spawnEnabled, nightOnly, minNightFactor, firstSpawnDelayDays, attemptIntervalTicks, spawnChance, allowedDimensions, allowedBiomes, minSpawnDistance, maxSpawnDistance, placementAttempts, maxRexPerPlayer, capRadius, maxRexInWorld, despawnWhenFar, despawnDistance, value);
    }
}
