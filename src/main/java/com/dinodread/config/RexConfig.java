package com.dinodread.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common-side T-rex tunables (attributes, senses, behavior). Registered as a
 * STARTUP config from {@link com.dinodread.DinoDread}; the file is {@code config/dinodread-startup.toml}.
 * STARTUP configs load while the mod is constructed, so these values are readable when entity attributes are created.
 */
public final class RexConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE;
    public static final ModConfigSpec.DoubleValue FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue STEP_HEIGHT;
    public static final ModConfigSpec.DoubleValue SCALE;

    public static final ModConfigSpec.DoubleValue SIGHT_RANGE;
    public static final ModConfigSpec.DoubleValue SIGHT_FOV_DEGREES;
    public static final ModConfigSpec.DoubleValue SNEAK_SIGHT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEARING_RADIUS;
    public static final ModConfigSpec.DoubleValue SNEAK_HEARING_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue WANDER_SPEED_MULTIPLIER;
    public static final ModConfigSpec.IntValue WANDER_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue WANDER_RANGE;
    public static final ModConfigSpec.DoubleValue STALK_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CHARGE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CHARGE_DISTANCE;
    public static final ModConfigSpec.IntValue STALK_TICKS_BEFORE_CHARGE;
    public static final ModConfigSpec.IntValue CHARGE_MAX_TICKS;
    public static final ModConfigSpec.IntValue CHARGE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue GIVE_UP_TICKS;
    public static final ModConfigSpec.BooleanValue USE_PATHFINDING;
    public static final ModConfigSpec.DoubleValue INVESTIGATE_REACH_DISTANCE;
    public static final ModConfigSpec.DoubleValue BITE_REACH;
    public static final ModConfigSpec.IntValue BITE_WINDUP_TICKS;
    public static final ModConfigSpec.IntValue BITE_COOLDOWN_TICKS;

    public static final ModConfigSpec.DoubleValue DAY_AGGRESSION;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("attributes");
        MAX_HEALTH = BUILDER
                .gameRestart()
                .comment("T-rex max health in half-hearts.")
                .defineInRange("maxHealth", 200.0, 1.0, 4096.0);
        ATTACK_DAMAGE = BUILDER
                .gameRestart()
                .comment("Damage of one bite in half-hearts (20 = a full unarmored player health bar).")
                .defineInRange("attackDamage", 20.0, 0.0, 1024.0);
        MOVEMENT_SPEED = BUILDER
                .gameRestart()
                .comment("Base movement speed attribute. The behavior multipliers below scale this.")
                .defineInRange("movementSpeed", 0.3, 0.05, 2.0);
        KNOCKBACK_RESISTANCE = BUILDER
                .gameRestart()
                .comment("1.0 = cannot be knocked back.")
                .defineInRange("knockbackResistance", 1.0, 0.0, 1.0);
        FOLLOW_RANGE = BUILDER
                .gameRestart()
                .comment("Follow range attribute in blocks (limits how far pathfinding searches).")
                .defineInRange("followRange", 64.0, 8.0, 128.0);
        STEP_HEIGHT = BUILDER
                .gameRestart()
                .comment("Step height attribute in blocks: how high it walks up without jumping.")
                .defineInRange("stepHeight", 1.5, 0.0, 4.0);
        SCALE = BUILDER
                .gameRestart()
                .comment("Scale attribute: multiplies the base hitbox and the model.")
                .defineInRange("scale", 3.0, 0.0625, 16.0);
        BUILDER.pop();

        BUILDER.push("senses");
        SIGHT_RANGE = BUILDER
                .comment("How far the T-rex can see a player (needs line of sight), in blocks.")
                .defineInRange("sightRange", 40.0, 4.0, 256.0);
        SIGHT_FOV_DEGREES = BUILDER
                .comment("Width of the T-rex's field of view in degrees. 360 = sees all around.")
                .defineInRange("sightFieldOfViewDegrees", 270.0, 30.0, 360.0);
        SNEAK_SIGHT_MULTIPLIER = BUILDER
                .comment("Multiplier on sight range against a sneaking player.")
                .defineInRange("sneakSightMultiplier", 0.5, 0.0, 1.0);
        HEARING_RADIUS = BUILDER
                .comment("How far the T-rex hears a player's noises (steps, mining, placing, ...), in blocks. 0 = deaf.",
                        "Vanilla makes no footstep noise for a sneaking player on the ground, so sneaking is silent for steps.")
                .defineInRange("hearingRadius", 52.0, 0.0, 256.0);
        SNEAK_HEARING_MULTIPLIER = BUILDER
                .comment("Multiplier on hearing radius for a sneaking player.")
                .defineInRange("sneakHearingMultiplier", 0.35, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("behavior");
        WANDER_SPEED_MULTIPLIER = BUILDER
                .comment("Speed multiplier while wandering.")
                .defineInRange("wanderSpeedMultiplier", 0.6, 0.1, 4.0);
        WANDER_INTERVAL_TICKS = BUILDER
                .comment("Average ticks between choosing a new wander destination.")
                .defineInRange("wanderIntervalTicks", 120, 20, 1200);
        WANDER_RANGE = BUILDER
                .comment("How far in blocks a wander destination can be from the T-rex.")
                .defineInRange("wanderRange", 24, 4, 64);
        STALK_SPEED_MULTIPLIER = BUILDER
                .comment("Speed multiplier while stalking or investigating a noise (approaching without full speed).")
                .defineInRange("stalkSpeedMultiplier", 0.8, 0.1, 4.0);
        CHARGE_SPEED_MULTIPLIER = BUILDER
                .comment("Speed multiplier while charging.")
                .defineInRange("chargeSpeedMultiplier", 1.4, 0.1, 4.0);
        CHARGE_DISTANCE = BUILDER
                .comment("Distance in blocks at which a stalking T-rex that can see its target starts charging.")
                .defineInRange("chargeDistance", 24.0, 2.0, 128.0);
        STALK_TICKS_BEFORE_CHARGE = BUILDER
                .comment("Ticks of continuously seeing its target while stalking before it charges even from beyond the charge distance.")
                .defineInRange("stalkTicksBeforeCharge", 100, 0, 2400);
        CHARGE_MAX_TICKS = BUILDER
                .comment("Longest a single charge lasts before the T-rex drops back to stalking.")
                .defineInRange("chargeMaxTicks", 120, 20, 2400);
        CHARGE_COOLDOWN_TICKS = BUILDER
                .comment("Ticks after a charge ends before it can charge again.")
                .defineInRange("chargeCooldownTicks", 100, 0, 2400);
        GIVE_UP_TICKS = BUILDER
                .comment("Ticks without seeing or hearing its target before the T-rex gives up and goes back to wandering.")
                .defineInRange("giveUpTicks", 240, 20, 12000);
        USE_PATHFINDING = BUILDER
                .comment("Use vanilla pathfinding while hunting. Off = walk straight at the target, which is reliable on open ground",
                        "but ignores terrain. Vanilla pathfinding is slow and unreliable for a body this wide (milestone 4 replaces both).")
                .define("usePathfinding", false);
        INVESTIGATE_REACH_DISTANCE = BUILDER
                .comment("How close to a heard noise counts as having arrived.")
                .defineInRange("investigateReachDistance", 6.0, 1.0, 32.0);
        BITE_REACH = BUILDER
                .comment("Bite reach in blocks, measured from the edge of the T-rex's hitbox (so it stays correct at any scale).")
                .defineInRange("biteReach", 3.0, 0.5, 16.0);
        BITE_WINDUP_TICKS = BUILDER
                .comment("Ticks between starting a bite and the damage landing (telegraph).")
                .defineInRange("biteWindupTicks", 10, 0, 100);
        BITE_COOLDOWN_TICKS = BUILDER
                .comment("Ticks between bites.")
                .defineInRange("biteCooldownTicks", 40, 5, 400);
        BUILDER.pop();

        BUILDER.push("daytime");
        DAY_AGGRESSION = BUILDER
                .comment("How active the T-rex is in full daylight compared with night (1 = no difference, lower = calmer).",
                        "Scales sight range, hearing radius, charge distance, charge duration and how long it keeps hunting.",
                        "Dusk and dawn blend between the two.")
                .defineInRange("dayAggression", 0.6, 0.0, 1.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private RexConfig() {}
}
