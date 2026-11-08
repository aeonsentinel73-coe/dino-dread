package com.dinodread.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Natural T-rex spawning and the vanilla-hostile toggle. Registered as a STARTUP
 * config from DinoDread; the file is {@code config/dinodread-spawning.toml}. Read through SpawnRules, which snapshots these values.
 */
public final class SpawnConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SPAWN_ENABLED;
    public static final ModConfigSpec.BooleanValue NIGHT_ONLY;
    public static final ModConfigSpec.DoubleValue MIN_NIGHT_FACTOR;
    public static final ModConfigSpec.IntValue FIRST_SPAWN_DELAY_DAYS;
    public static final ModConfigSpec.IntValue ATTEMPT_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue SPAWN_CHANCE;
    public static final ModConfigSpec.DoubleValue SUPER_REX_CHANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_BIOMES;

    public static final ModConfigSpec.DoubleValue MIN_SPAWN_DISTANCE;
    public static final ModConfigSpec.DoubleValue MAX_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue PLACEMENT_ATTEMPTS;
    public static final ModConfigSpec.BooleanValue SPAWN_ALERTED;

    public static final ModConfigSpec.IntValue MAX_REX_PER_PLAYER;
    public static final ModConfigSpec.DoubleValue CAP_RADIUS;
    public static final ModConfigSpec.IntValue MAX_REX_IN_WORLD;

    public static final ModConfigSpec.BooleanValue DESPAWN_WHEN_FAR;
    public static final ModConfigSpec.DoubleValue DESPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_LIFETIME_TICKS;

    public static final ModConfigSpec.BooleanValue DISABLE_VANILLA_HOSTILES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> VANILLA_HOSTILE_EXCEPTIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_SPAWN_REASONS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("spawning");
        SPAWN_ENABLED = BUILDER
                .comment("Master switch for natural T-rex spawns. Spawn eggs and /summon always work.")
                .define("spawnEnabled", true);
        NIGHT_ONLY = BUILDER
                .comment("Natural spawns only happen while it is dark at the player.")
                .define("nightOnly", true);
        MIN_NIGHT_FACTOR = BUILDER
                .comment("How dark it must be at the player for nightOnly: 0 = full daylight counts, 1 = only the middle of the night. 0.5 starts a little after dusk.")
                .defineInRange("minNightFactor", 0.5, 0.0, 1.0);
        FIRST_SPAWN_DELAY_DAYS = BUILDER
                .comment("No natural spawn until the world has run this many full days (24000 ticks of game time each, 20 minutes). 0 = from the start.")
                .defineInRange("firstSpawnDelayDays", 0, 0, 10000);
        ATTEMPT_INTERVAL_TICKS = BUILDER
                .comment("Every this many ticks each eligible player rolls once for a spawn (600 ticks = 30 seconds).")
                .defineInRange("attemptIntervalTicks", 600, 20, 72000);
        SPAWN_CHANCE = BUILDER
                .comment("Chance that one roll spawns a T-rex, if everything else allows it. This is the main rarity knob: with the defaults a player at night waits about 10 minutes on average. 0 = never, 1 = every roll.")
                .defineInRange("spawnChance", 0.05, 0.0, 1.0);
        SUPER_REX_CHANCE = BUILDER
                .comment("Chance that a natural spawn is a super rex instead of a T-rex (0.05 = one in 20). The super rex is faster on land, slower in water, slightly bigger, and hunts players until it despawns 2 minutes after being seen. Spawn eggs and /summon always give exactly what you ask for.")
                .defineInRange("superRexChance", 0.05, 0.0, 1.0);
        ALLOWED_DIMENSIONS = BUILDER
                .comment("Dimensions where natural spawns happen, as dimension ids.")
                .defineListAllowEmpty("allowedDimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld", element -> element instanceof String);
        ALLOWED_BIOMES = BUILDER
                .comment("Biomes where natural spawns happen, as biome ids (minecraft:plains) or biome tags with a leading # (#minecraft:is_forest). An empty list = any biome.")
                .defineListAllowEmpty("allowedBiomes", List.of("#minecraft:is_overworld"), () -> "#minecraft:is_overworld", element -> element instanceof String);
        BUILDER.pop();

        BUILDER.push("placement");
        MIN_SPAWN_DISTANCE = BUILDER
                .comment("Closest, in blocks, to a player that a natural spawn may appear. Keep it above the T-rex's sight range so it is never conjured in view; it should be heard before it is seen.")
                .defineInRange("minSpawnDistance", 56.0, 8.0, 256.0);
        MAX_SPAWN_DISTANCE = BUILDER
                .comment("Furthest, in blocks, from the player that a natural spawn may appear. Only chunks that are loaded and running can be used, so very large values just fail more often.")
                .defineInRange("maxSpawnDistance", 96.0, 16.0, 256.0);
        PLACEMENT_ATTEMPTS = BUILDER
                .comment("Random spots tried per roll. The T-rex needs room for its whole body, so forests and hills fail more often; raise this if spawns seem too rare there.")
                .defineInRange("placementAttempts", 24, 1, 256);
        SPAWN_ALERTED = BUILDER
                .comment("A newly spawned T-rex is told where the player who rolled the spawn is, so it heads that way (and roars as it sets off) instead of wandering aimlessly.",
                        "Off = it spawns calm and may never find anyone, since it spawns beyond its own sight and hearing.")
                .define("spawnAlerted", true);
        BUILDER.pop();

        BUILDER.push("limits");
        MAX_REX_PER_PLAYER = BUILDER
                .comment("No natural spawn if this many T-rexes (of any origin) are already within capRadius of the player.")
                .defineInRange("maxRexPerPlayer", 1, 0, 16);
        CAP_RADIUS = BUILDER
                .comment("Radius in blocks around a player for maxRexPerPlayer.")
                .defineInRange("capRadius", 160.0, 16.0, 512.0);
        MAX_REX_IN_WORLD = BUILDER
                .comment("No natural spawn if this many T-rexes (of any origin) are already loaded in the dimension. Dino Dread v1 is ONE T-rex.")
                .defineInRange("maxRexInWorld", 1, 0, 64);
        BUILDER.pop();

        BUILDER.push("despawn");
        DESPAWN_WHEN_FAR = BUILDER
                .comment("A naturally spawned T-rex is removed when no player is within despawnDistance, so one that wandered off never blocks the next spawn. Off = it stays until killed. Spawn-egg T-rexes are never touched.")
                .define("despawnWhenFar", true);
        DESPAWN_DISTANCE = BUILDER
                .comment("Distance in blocks used by despawnWhenFar. Keep it above maxSpawnDistance and below the server's simulation distance (a T-rex in a chunk that is not running cannot be checked).")
                .defineInRange("despawnDistance", 128.0, 32.0, 512.0);
        MAX_LIFETIME_TICKS = BUILDER
                .comment("A naturally spawned T-rex leaves after this many loaded ticks (48000 = 40 minutes), unless it is hunting right then. 0 = no limit. Reloading the chunk restarts the count.")
                .defineInRange("maxLifetimeTicks", 48000, 0, 24000000);
        BUILDER.pop();

        BUILDER.push("vanillaHostiles");
        DISABLE_VANILLA_HOSTILES = BUILDER
                .comment("Stop other hostile mobs (everything in the monster category: zombies, skeletons, creepers, phantoms, mod monsters...) from spawning on their own, so the T-rex is the only thing that hunts. Spawn eggs, /summon and the T-rex itself are never affected.")
                .define("disableVanillaHostiles", false);
        VANILLA_HOSTILE_EXCEPTIONS = BUILDER
                .comment("Hostile mobs that still spawn when disableVanillaHostiles is on, as entity ids (minecraft:zombie) or entity tags with a leading # (#minecraft:skeletons).")
                .defineListAllowEmpty("vanillaHostileExceptions", List.of(), () -> "minecraft:zombie", element -> element instanceof String);
        BLOCKED_SPAWN_REASONS = BUILDER
                .comment("Which kinds of spawn disableVanillaHostiles blocks, by vanilla spawn reason name: NATURAL (the normal night spawning), CHUNK_GENERATION (mobs made when terrain is generated), PATROL (pillager patrols), STRUCTURE (mobs placed by structures). Add SPAWNER to also stop dungeon and trial spawners. Egg, command, breeding, raid and summon spawns are not in the list and never blocked.")
                .defineListAllowEmpty("blockedSpawnReasons", List.of("NATURAL", "CHUNK_GENERATION", "PATROL", "STRUCTURE"), () -> "NATURAL", element -> element instanceof String);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SpawnConfig() {}
}
