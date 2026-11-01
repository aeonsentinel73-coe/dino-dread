package com.dinodread.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Super rex tunables: the rarer, faster, more aggressive T-rex variant. Registered as a STARTUP config
 * from {@link com.dinodread.DinoDread}; the file is {@code config/dinodread-superrex.toml}.
 * Only the values the super rex actually changes live here; everything else (obstacles, stuck handling,
 * prey, audio, footsteps) is shared with the T-rex through {@link RexConfig}.
 */
public final class SuperRexConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE;
    public static final ModConfigSpec.DoubleValue FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue STEP_HEIGHT;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.DoubleValue SWIM_SPEED_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue SIGHT_RANGE;
    public static final ModConfigSpec.DoubleValue SIGHT_FOV_DEGREES;
    public static final ModConfigSpec.DoubleValue SNEAK_SIGHT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEARING_RADIUS;
    public static final ModConfigSpec.DoubleValue SNEAK_HEARING_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue CHARGE_DISTANCE;
    public static final ModConfigSpec.IntValue STALK_TICKS_BEFORE_CHARGE;
    public static final ModConfigSpec.IntValue CHARGE_MAX_TICKS;
    public static final ModConfigSpec.IntValue CHARGE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue GIVE_UP_TICKS;
    public static final ModConfigSpec.IntValue BITE_WINDUP_TICKS;
    public static final ModConfigSpec.IntValue BITE_COOLDOWN_TICKS;

    public static final ModConfigSpec.DoubleValue DAY_AGGRESSION;

    public static final ModConfigSpec.IntValue DESPAWN_AFTER_SPOTTED_TICKS;
    public static final ModConfigSpec.DoubleValue SPOTTED_RANGE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("attributes");
        MAX_HEALTH = BUILDER
                .gameRestart()
                .comment("Super rex max health in half-hearts.")
                .defineInRange("maxHealth", 220.0, 1.0, 4096.0);
        ATTACK_DAMAGE = BUILDER
                .gameRestart()
                .comment("Damage of one bite in half-hearts (20 = a full unarmored player health bar).")
                .defineInRange("attackDamage", 30.0, 0.0, 1024.0);
        MOVEMENT_SPEED = BUILDER
                .gameRestart()
                .comment("Base movement speed attribute. The T-rex default is 0.3; the super rex is meant to outwalk a fleeing player.")
                .defineInRange("movementSpeed", 0.36, 0.05, 2.0);
        KNOCKBACK_RESISTANCE = BUILDER
                .gameRestart()
                .comment("1.0 = cannot be knocked back.")
                .defineInRange("knockbackResistance", 1.0, 0.0, 1.0);
        FOLLOW_RANGE = BUILDER
                .gameRestart()
                .comment("Follow range attribute in blocks (limits how far pathfinding searches).")
                .defineInRange("followRange", 80.0, 8.0, 128.0);
        STEP_HEIGHT = BUILDER
                .gameRestart()
                .comment("Step height attribute in blocks: how high it walks up without jumping.")
                .defineInRange("stepHeight", 3.0, 0.0, 4.0);
        SCALE = BUILDER
                .gameRestart()
                .comment("Scale attribute: multiplies the base hitbox and the model. The T-rex default is 3.0; 3.3 is about 10% bigger.")
                .defineInRange("scale", 3.3, 0.0625, 16.0);
        SWIM_SPEED_MULTIPLIER = BUILDER
                .gameRestart()
                .comment("How fast it moves in water (NeoForge's swim speed attribute). The T-rex default is 8; below that the super rex",
                        "falls behind a swimming player and a boat instead of overtaking them.")
                .defineInRange("swimSpeedMultiplier", 5.0, 0.1, 64.0);
        BUILDER.pop();

        BUILDER.push("senses");
        SIGHT_RANGE = BUILDER
                .comment("How far the super rex can see a player (needs line of sight), in blocks. It is meant to find the nearest player quickly.")
                .defineInRange("sightRange", 64.0, 4.0, 256.0);
        SIGHT_FOV_DEGREES = BUILDER
                .comment("Width of the super rex's field of view in degrees. 360 = sees all around, so it never needs to turn to spot you.")
                .defineInRange("sightFieldOfViewDegrees", 360.0, 30.0, 360.0);
        SNEAK_SIGHT_MULTIPLIER = BUILDER
                .comment("Multiplier on sight range against a sneaking player. Sneaking still helps, but less than against a T-rex.")
                .defineInRange("sneakSightMultiplier", 0.7, 0.0, 1.0);
        HEARING_RADIUS = BUILDER
                .comment("How far the super rex hears a player's noises, in blocks. 0 = deaf.")
                .defineInRange("hearingRadius", 64.0, 0.0, 256.0);
        SNEAK_HEARING_MULTIPLIER = BUILDER
                .comment("Multiplier on hearing radius for a sneaking player.")
                .defineInRange("sneakHearingMultiplier", 0.5, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("behavior");
        CHARGE_DISTANCE = BUILDER
                .comment("Distance in blocks at which a stalking super rex that can see its target starts charging.")
                .defineInRange("chargeDistance", 40.0, 2.0, 128.0);
        STALK_TICKS_BEFORE_CHARGE = BUILDER
                .comment("Ticks of continuously seeing its target while stalking before it charges even from beyond the charge distance.")
                .defineInRange("stalkTicksBeforeCharge", 20, 0, 2400);
        CHARGE_MAX_TICKS = BUILDER
                .comment("Longest a single charge lasts before it drops back to stalking.")
                .defineInRange("chargeMaxTicks", 200, 20, 2400);
        CHARGE_COOLDOWN_TICKS = BUILDER
                .comment("Ticks after a charge ends before it can charge again.")
                .defineInRange("chargeCooldownTicks", 40, 0, 2400);
        GIVE_UP_TICKS = BUILDER
                .comment("Ticks without seeing or hearing its target before it gives up and goes back to wandering. Much longer than a T-rex: it hunts persistently.")
                .defineInRange("giveUpTicks", 1200, 20, 12000);
        BITE_WINDUP_TICKS = BUILDER
                .comment("Ticks between starting a bite and the damage landing (telegraph). Shorter than a T-rex's.")
                .defineInRange("biteWindupTicks", 8, 0, 100);
        BITE_COOLDOWN_TICKS = BUILDER
                .comment("Ticks between bites.")
                .defineInRange("biteCooldownTicks", 30, 5, 400);
        BUILDER.pop();

        BUILDER.push("daytime");
        DAY_AGGRESSION = BUILDER
                .comment("How active the super rex is in full daylight compared with night (1 = no difference).",
                        "Scales sight range, hearing radius, charge distance, charge duration and how long it keeps hunting.")
                .defineInRange("dayAggression", 1.0, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("despawn");
        DESPAWN_AFTER_SPOTTED_TICKS = BUILDER
                .comment("A naturally spawned super rex leaves this many ticks after a player first gets it in view (2400 = 2 minutes):",
                        "the hunt is a timed event, and surviving the encounter ends it. It starts the clock when a survival player is within",
                        "spottedRange with clear line of sight to the rex. 0 = never despawn this way (the normal despawnWhenFar and",
                        "maxLifetimeTicks rules still apply). Spawn-egg and /summon super rexes are never touched.")
                .defineInRange("despawnAfterSpottedTicks", 2400, 0, 24000);
        SPOTTED_RANGE = BUILDER
                .comment("How far a player can be and still 'get the super rex in view' for the despawn clock, in blocks.",
                        "Keep it near the client render distance: beyond it the player cannot actually see the rex.")
                .defineInRange("spottedRange", 64.0, 8.0, 256.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SuperRexConfig() {}
}
