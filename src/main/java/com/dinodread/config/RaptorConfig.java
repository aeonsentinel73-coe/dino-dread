package com.dinodread.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Velociraptor tunables (the pack variant) and raptor natural spawning. Registered as a STARTUP config
 * from {@link com.dinodread.DinoDread}; the file is {@code config/dinodread-raptors.toml}.
 * Everything not listed here (stuck handling, wander range, block lists) is shared with the T-rex through {@link RexConfig}.
 */
public final class RaptorConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE;
    public static final ModConfigSpec.DoubleValue FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue STEP_HEIGHT;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.DoubleValue HITBOX_WIDTH;
    public static final ModConfigSpec.DoubleValue HITBOX_HEIGHT;
    public static final ModConfigSpec.DoubleValue EYE_HEIGHT;
    public static final ModConfigSpec.DoubleValue SWIM_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WATER_EXIT_HEIGHT;
    public static final ModConfigSpec.DoubleValue SWIM_DEPTH;

    public static final ModConfigSpec.DoubleValue SIGHT_RANGE;
    public static final ModConfigSpec.DoubleValue SIGHT_FOV_DEGREES;
    public static final ModConfigSpec.DoubleValue SNEAK_SIGHT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEARING_RADIUS;
    public static final ModConfigSpec.DoubleValue SNEAK_HEARING_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue HUNT_HOSTILE_MOBS;

    public static final ModConfigSpec.IntValue GIVE_UP_TICKS;
    public static final ModConfigSpec.DoubleValue BITE_REACH;
    public static final ModConfigSpec.DoubleValue BITE_REACH_BELOW;
    public static final ModConfigSpec.IntValue BITE_WINDUP_TICKS;
    public static final ModConfigSpec.IntValue BITE_COOLDOWN_TICKS;

    public static final ModConfigSpec.DoubleValue DAY_AGGRESSION;

    public static final ModConfigSpec.DoubleValue FOOTSTEP_VOLUME;
    public static final ModConfigSpec.DoubleValue FOOTSTEP_STRIDE_BLOCKS;
    public static final ModConfigSpec.DoubleValue BITE_VOLUME;
    public static final ModConfigSpec.DoubleValue ROAR_VOLUME;
    public static final ModConfigSpec.IntValue ROAR_COOLDOWN_TICKS;

    public static final ModConfigSpec.IntValue PACK_MIN_SIZE;
    public static final ModConfigSpec.IntValue PACK_MAX_SIZE;
    public static final ModConfigSpec.IntValue PACK_SPREAD_BLOCKS;
    public static final ModConfigSpec.IntValue FLEE_TICKS;
    public static final ModConfigSpec.DoubleValue FLEE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue JUMP_STRENGTH;
    public static final ModConfigSpec.IntValue SCATTER_FLEE_TICKS;
    public static final ModConfigSpec.DoubleValue FLANK_RADIUS_BLOCKS;
    public static final ModConfigSpec.DoubleValue FLANK_SWEEP_DEGREES;
    public static final ModConfigSpec.DoubleValue FLANK_SPEED_BOOST;
    public static final ModConfigSpec.DoubleValue FLANK_ARC_DEGREES_PER_SECOND;

    public static final ModConfigSpec.BooleanValue SPAWN_ENABLED;
    public static final ModConfigSpec.BooleanValue NIGHT_ONLY;
    public static final ModConfigSpec.DoubleValue MIN_NIGHT_FACTOR;
    public static final ModConfigSpec.IntValue SPAWN_ATTEMPT_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue SPAWN_CHANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;
    public static final ModConfigSpec.DoubleValue MIN_SPAWN_DISTANCE;
    public static final ModConfigSpec.DoubleValue MAX_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue PLACEMENT_ATTEMPTS;
    public static final ModConfigSpec.IntValue MAX_RAPTOR_PER_PLAYER;
    public static final ModConfigSpec.DoubleValue CAP_RADIUS;
    public static final ModConfigSpec.IntValue MAX_RAPTOR_IN_WORLD;
    public static final ModConfigSpec.BooleanValue DESPAWN_WHEN_FAR;
    public static final ModConfigSpec.DoubleValue DESPAWN_DISTANCE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("attributes");
        MAX_HEALTH = BUILDER
                .gameRestart()
                .comment("Velociraptor max health in half-hearts (a player has 20). 24 = four iron-sword swings (7 damage each).")
                .defineInRange("maxHealth", 24.0, 1.0, 4096.0);
        ATTACK_DAMAGE = BUILDER
                .gameRestart()
                .comment("Raw damage of one bite in half-hearts. This is what an unarmored player takes; armor cuts the rest.",
                        "At 10 a player in full iron takes 6.0 per bite (iron defense 15, toughness 0: armor soaks",
                        "clamp(15 - damage/2, 3, 20)/25 of it), a pack bite still stings through plate.")
                .defineInRange("attackDamage", 10.0, 0.0, 1024.0);
        MOVEMENT_SPEED = BUILDER
                .gameRestart()
                .comment("Base movement speed attribute. This version's steering doubles as the forward input (Mob.setSpeed",
                        "sets the walk input too), so ground speed is quadratic: blocks/s = about 43 x (attribute x hunt",
                        "multiplier 0.8)^2. At 0.43 a raptor chases at about 5.1 blocks/s: faster than a walking player",
                        "(4.3), under a sprinting one. 0.443 (5.4) tested in live play: the whole pack still kept up with",
                        "a sprinting player - bite knockback, the cut-off surge and flank boost all widen the player's",
                        "effective gap, so keep the base at the outrunnable 0.43 until a controlled test says otherwise.",
                        "A completed flank surges at flank speed for 5 s - that one IS faster than a sprint.")
                .defineInRange("movementSpeed", 0.43, 0.05, 2.0);
        KNOCKBACK_RESISTANCE = BUILDER
                .gameRestart()
                .comment("Fraction of knockback a raptor ignores. 0.8 lives: every hit (even a fist) applies vanilla",
                        "damage knockback, and a bare-hand spam kept shoving hunters out of their own bite range -",
                        "one player with no weapon could leash a whole raptor forever (live playtest). Punching now",
                        "staggers a raptor a little; it does not stop one. 1.0 would make them freight trains.")
                .defineInRange("knockbackResistance", 0.8, 0.0, 1.0);
        FOLLOW_RANGE = BUILDER
                .gameRestart()
                .comment("Follow range attribute in blocks (limits how far pathfinding searches).")
                .defineInRange("followRange", 48.0, 8.0, 128.0);
        STEP_HEIGHT = BUILDER
                .gameRestart()
                .comment("Step height attribute in blocks: how high it walks up without jumping.")
                .defineInRange("stepHeight", 1.5, 0.0, 4.0);
        SCALE = BUILDER
                .gameRestart()
                .comment("Scale attribute: multiplies the base hitbox and the model. The Peridot Scout model is 2.9 blocks tall at scale 1, so 0.6 matches the hitbox.")
                .defineInRange("scale", 0.6, 0.0625, 16.0);
        JUMP_STRENGTH = BUILDER
                .gameRestart()
                .comment("Vertical jump impulse attribute. Vanilla's 0.42 suits a 1.8-block frame; at the raptor's "
                        + "scale a full player-height jump looks like a flea. 0.3 is roughly a half-block hop.")
                .defineInRange("jumpStrength", 0.3, 0.0, 1.0);
        HITBOX_WIDTH = BUILDER
                .gameRestart()
                .comment("Width of the hitbox, in blocks before the scale (2.0 at scale 0.6 is 1.2 blocks wide).")
                .defineInRange("hitboxWidth", 2.0, 0.25, 8.0);
        HITBOX_HEIGHT = BUILDER
                .gameRestart()
                .comment("Height of the hitbox, in blocks before the scale (3.8 at scale 0.6 is 2.28 blocks tall).")
                .defineInRange("hitboxHeight", 3.8, 0.25, 8.0);
        EYE_HEIGHT = BUILDER
                .gameRestart()
                .comment("Eye height, in blocks before the scale: where it sees from.")
                .defineInRange("eyeHeight", 3.2, 0.1, 8.0);
        SWIM_SPEED_MULTIPLIER = BUILDER
                .gameRestart()
                .comment("How fast raptors move in water (NeoForge's swim speed attribute; the T-rex is 8). Raptors still",
                        "hate deep water, but at 1.0 they can swim to the bank instead of drowning against it.")
                .defineInRange("swimSpeedMultiplier", 1.0, 0.1, 64.0);
        WATER_EXIT_HEIGHT = BUILDER
                .gameRestart()
                .comment("Highest ledge, in blocks, a raptor hauls itself up onto when it swims into a bank.",
                        "1.5 was too tight for ordinary two-block shorelines; 3 matches the T-rex.")
                .defineInRange("waterExitHeight", 3.0, 0.0, 8.0);
        SWIM_DEPTH = BUILDER
                .gameRestart()
                .comment("Blocks of body to keep under the surface while swimming. 0 (the default) = vanilla floating: a raptor rides the surface",
                        "instead of wading submerged like the T-rex.")
                .defineInRange("swimDepth", 0.0, 0.0, 6.0);
        BUILDER.pop();

        BUILDER.push("senses");
        SIGHT_RANGE = BUILDER
                .comment("How far a raptor can see a target (needs line of sight), in blocks.")
                .defineInRange("sightRange", 32.0, 4.0, 256.0);
        SIGHT_FOV_DEGREES = BUILDER
                .comment("Width of a raptor's field of view in degrees. 360 = sees all around.")
                .defineInRange("sightFieldOfViewDegrees", 300.0, 30.0, 360.0);
        SNEAK_SIGHT_MULTIPLIER = BUILDER
                .comment("Multiplier on sight range against a sneaking player.")
                .defineInRange("sneakSightMultiplier", 0.5, 0.0, 1.0);
        HEARING_RADIUS = BUILDER
                .comment("How far a raptor hears a player's noises, in blocks. 0 = deaf.")
                .defineInRange("hearingRadius", 32.0, 0.0, 256.0);
        SNEAK_HEARING_MULTIPLIER = BUILDER
                .comment("Multiplier on hearing radius for a sneaking player.")
                .defineInRange("sneakHearingMultiplier", 0.35, 0.0, 1.0);
        HUNT_HOSTILE_MOBS = BUILDER
                .comment("Whether raptors also see and hunt hostile mobs (zombies, skeletons, creepers, ...), not just players.",
                        "Named, leashed and tamed mobs are never targeted either way.")
                .define("huntHostileMobs", true);
        BUILDER.pop();

        BUILDER.push("behavior");
        GIVE_UP_TICKS = BUILDER
                .comment("Ticks without seeing or hearing a target before a raptor gives up and rejoins the pack's wandering.")
                .defineInRange("giveUpTicks", 240, 20, 12000);
        BITE_REACH = BUILDER
                .comment("Bite reach in blocks, measured from the edge of the (scaled) hitbox.")
                .defineInRange("biteReach", 1.2, 0.5, 16.0);
        BITE_REACH_BELOW = BUILDER
                .comment("How far below its own feet a raptor can bite down at a target.")
                .defineInRange("biteReachBelow", 1.5, 0.0, 32.0);
        BITE_WINDUP_TICKS = BUILDER
                .comment("Ticks between starting a bite and the damage landing (telegraph).")
                .defineInRange("biteWindupTicks", 6, 0, 100);
        BITE_COOLDOWN_TICKS = BUILDER
                .comment("Ticks between bites.")
                .defineInRange("biteCooldownTicks", 20, 5, 400);
        BUILDER.pop();

        BUILDER.push("daytime");
        DAY_AGGRESSION = BUILDER
                .comment("How active raptors are in full daylight compared with night (1 = no difference).")
                .defineInRange("dayAggression", 0.8, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("audio");
        FOOTSTEP_VOLUME = BUILDER
                .comment("Volume of the running-gait sound. Raptors use their own sound events, which never feed the T-rex camera shake.")
                .defineInRange("footstepVolume", 3.0, 0.1, 16.0);
        FOOTSTEP_STRIDE_BLOCKS = BUILDER
                .comment("Blocks per footstep: tunes how fast the walk and run animations play against ground speed.")
                .defineInRange("footstepStrideBlocks", 2.0, 0.5, 20.0);
        BITE_VOLUME = BUILDER
                .comment("Volume of a raptor bite.")
                .defineInRange("biteVolume", 2.0, 0.1, 16.0);
        ROAR_VOLUME = BUILDER
                .comment("Volume of the call a raptor makes when it starts hunting.")
                .defineInRange("roarVolume", 3.0, 0.1, 16.0);
        ROAR_COOLDOWN_TICKS = BUILDER
                .comment("Minimum ticks between two hunting calls.")
                .defineInRange("roarCooldownTicks", 600, 0, 72000);
        BUILDER.pop();

        BUILDER.push("pack");
        PACK_MIN_SIZE = BUILDER
                .comment("Smallest natural pack (a spawn that cannot fit this many bodies fails).")
                .defineInRange("packMinSize", 3, 1, 16);
        PACK_MAX_SIZE = BUILDER
                .comment("Largest natural pack.")
                .defineInRange("packMaxSize", 4, 1, 16);
        PACK_SPREAD_BLOCKS = BUILDER
                .comment("How wide, in blocks around the pack's spawn point, the pack members are spread.")
                .defineInRange("packSpreadBlocks", 6, 1, 32);
        FLEE_TICKS = BUILDER
                .comment("When any pack member dies, the rest run away from the nearest player for this many ticks and then despawn",
                        "(600 = 30 seconds). A spawn-egg pack flees but is not despawned. 0 = the survivors do not flee.")
                .defineInRange("fleeTicks", 600, 0, 24000);
        FLEE_SPEED_MULTIPLIER = BUILDER
                .comment("Movement multiplier while fleeing.")
                .defineInRange("fleeSpeedMultiplier", 1.3, 0.1, 4.0);
        SCATTER_FLEE_TICKS = BUILDER
                .comment("How long a raptor runs away after it loses its quarry (unreachable path, out of the senses). "
                        + "Unlike a pack death this never ends in a despawn: it is breathing room for the player. 0 disables.")
                .defineInRange("scatterFleeTicks", 300, 0, 24000);
        FLANK_RADIUS_BLOCKS = BUILDER
                .comment("Radius of the circle a flanking raptor runs around the quarry. One raptor in a pack of three "
                        + "(two in a pack of four, one on each side) circles while the rest chase straight on. Wide "
                        + "circles read as a real envelopment; the arc speed self-caps to what the body can run.")
                .defineInRange("flankRadiusBlocks", 40.0, 2.5, 64.0);
        FLANK_SWEEP_DEGREES = BUILDER
                .comment("How far around the quarry a flanker commits before the circle ends and it joins the "
                        + "head-on chase (the end hook). 720 = run laps until the quarry is lost: the pre-fix behavior, "
                        + "which just looked like laps.")
                .defineInRange("flankSweepDegrees", 180.0, 30.0, 720.0);
        FLANK_SPEED_BOOST = BUILDER
                .comment("Movement multiplier while flanking, on top of the hunt speed. Ground speed is quadratic in ",
                        "this multiplier (see movementSpeed), so 1.77 makes a flanker about 16 blocks/s (~2.8x a sprinting",
                        "player) - sized to hold the same ~23 deg/s sweep as the old 20-block/1.25 ring so a wider",
                        "envelope costs the flanker no more wall-clock time per attempt.",
                        "The boost cannot outlast a sprint though: a flank that stops making progress around the quarry",
                        "(you are running away) is abandoned after ~5 s and everyone chases at normal chase speed, which",
                        "IS outrunnable. Lost the moment the raptor stops circling (quarry lost, water, flight).")
                .defineInRange("flankSpeedBoost", 1.77, 1.0, 4.0);
        FLANK_ARC_DEGREES_PER_SECOND = BUILDER
                .comment("How far ahead on the circle the flanker's waypoint rides, in degrees: the point it runs to is",
                        "this far around its own bearing (capped by what its legs can actually reach, floored so it never",
                        "sits on the raptor's nose and stalls it). Higher turns tighter-chasing; lower wider, lazier arcs.")
                .defineInRange("flankArcDegreesPerSecond", 60.0, 10.0, 360.0);
        BUILDER.pop();

        BUILDER.push("spawning");
        SPAWN_ENABLED = BUILDER
                .comment("Master switch for natural raptor packs. Spawn eggs and /summon always work.")
                .define("raptorSpawnEnabled", true);
        NIGHT_ONLY = BUILDER
                .comment("Natural packs only spawn while it is dark at the player.")
                .define("raptorNightOnly", true);
        MIN_NIGHT_FACTOR = BUILDER
                .comment("How dark it must be at the player: 0 = full daylight counts, 1 = only the middle of the night.")
                .defineInRange("raptorMinNightFactor", 0.5, 0.0, 1.0);
        SPAWN_ATTEMPT_INTERVAL_TICKS = BUILDER
                .comment("Every this many ticks each eligible player rolls once for a pack (1200 ticks = 1 minute).")
                .defineInRange("raptorAttemptIntervalTicks", 1200, 20, 72000);
        SPAWN_CHANCE = BUILDER
                .comment("Chance that one roll spawns a pack, if everything else allows it. 0 = never, 1 = every roll.")
                .defineInRange("raptorSpawnChance", 0.1, 0.0, 1.0);
        ALLOWED_DIMENSIONS = BUILDER
                .comment("Dimensions where natural packs spawn, as dimension ids.")
                .defineListAllowEmpty("raptorAllowedDimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld", element -> element instanceof String);
        MIN_SPAWN_DISTANCE = BUILDER
                .comment("Closest, in blocks, to a player that a natural pack may appear.")
                .defineInRange("raptorMinSpawnDistance", 40.0, 8.0, 256.0);
        MAX_SPAWN_DISTANCE = BUILDER
                .comment("Furthest, in blocks, from the player that a natural pack may appear.")
                .defineInRange("raptorMaxSpawnDistance", 72.0, 16.0, 256.0);
        PLACEMENT_ATTEMPTS = BUILDER
                .comment("Random pack anchor points tried per roll.")
                .defineInRange("raptorPlacementAttempts", 16, 1, 256);
        MAX_RAPTOR_PER_PLAYER = BUILDER
                .comment("No natural pack if this many raptors (of any origin) are already within capRadius of the player.")
                .defineInRange("maxRaptorPerPlayer", 8, 0, 64);
        CAP_RADIUS = BUILDER
                .comment("Radius in blocks around a player for maxRaptorPerPlayer.")
                .defineInRange("raptorCapRadius", 160.0, 16.0, 512.0);
        MAX_RAPTOR_IN_WORLD = BUILDER
                .comment("No natural pack if this many raptors (of any origin) are already loaded in the dimension.")
                .defineInRange("maxRaptorInWorld", 12, 0, 64);
        DESPAWN_WHEN_FAR = BUILDER
                .comment("A naturally spawned raptor is removed when no player is within despawnDistance. Spawn-egg raptors are never touched.")
                .define("raptorDespawnWhenFar", true);
        DESPAWN_DISTANCE = BUILDER
                .comment("Distance in blocks used by raptorDespawnWhenFar.")
                .defineInRange("raptorDespawnDistance", 128.0, 32.0, 512.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private RaptorConfig() {}
}
