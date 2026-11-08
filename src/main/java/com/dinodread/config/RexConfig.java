package com.dinodread.config;

import java.util.List;

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
    public static final ModConfigSpec.DoubleValue HITBOX_WIDTH;
    public static final ModConfigSpec.DoubleValue HITBOX_HEIGHT;
    public static final ModConfigSpec.DoubleValue EYE_HEIGHT;
    public static final ModConfigSpec.DoubleValue MODEL_OFFSET_BLOCKS;
    public static final ModConfigSpec.DoubleValue SWIM_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WATER_EXIT_HEIGHT;
    public static final ModConfigSpec.DoubleValue SWIM_DEPTH;

    public static final ModConfigSpec.DoubleValue SIGHT_RANGE;
    public static final ModConfigSpec.DoubleValue SIGHT_FOV_DEGREES;
    public static final ModConfigSpec.DoubleValue SNEAK_SIGHT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEARING_RADIUS;
    public static final ModConfigSpec.DoubleValue SNEAK_HEARING_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue WANDER_SPEED_MULTIPLIER;
    public static final ModConfigSpec.IntValue WANDER_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue WANDER_RANGE;
    public static final ModConfigSpec.DoubleValue WANDER_ARRIVAL_DISTANCE;
    public static final ModConfigSpec.DoubleValue STALK_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CHARGE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CHARGE_DISTANCE;
    public static final ModConfigSpec.IntValue STALK_TICKS_BEFORE_CHARGE;
    public static final ModConfigSpec.IntValue CHARGE_MAX_TICKS;
    public static final ModConfigSpec.IntValue CHARGE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue GIVE_UP_TICKS;
    public static final ModConfigSpec.BooleanValue USE_PATHFINDING;
    public static final ModConfigSpec.DoubleValue INVESTIGATE_REACH_FROM_EDGE;
    public static final ModConfigSpec.DoubleValue BITE_REACH;
    public static final ModConfigSpec.DoubleValue BITE_REACH_BELOW;
    public static final ModConfigSpec.IntValue BITE_WINDUP_TICKS;
    public static final ModConfigSpec.IntValue BITE_COOLDOWN_TICKS;

    public static final ModConfigSpec.DoubleValue DAY_AGGRESSION;

    public static final ModConfigSpec.BooleanValue GRIEFING_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UNBREAKABLE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOLIAGE_BLOCKS;
    public static final ModConfigSpec.BooleanValue BREAK_BLOCK_ENTITIES;
    public static final ModConfigSpec.DoubleValue MAX_BREAK_HARDNESS;
    public static final ModConfigSpec.IntValue BREAK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue BLOCKS_PER_BREAK;
    public static final ModConfigSpec.DoubleValue BREAK_REACH;
    public static final ModConfigSpec.BooleanValue BREAK_DROPS_ITEMS;

    public static final ModConfigSpec.IntValue STUCK_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue STUCK_MIN_PROGRESS;
    public static final ModConfigSpec.IntValue STUCK_BREAK_AFTER_TICKS;
    public static final ModConfigSpec.IntValue STUCK_HOP_AFTER_TICKS;
    public static final ModConfigSpec.IntValue STUCK_SIDESTEP_AFTER_TICKS;
    public static final ModConfigSpec.IntValue STUCK_GIVE_UP_AFTER_TICKS;
    public static final ModConfigSpec.IntValue STUCK_SIDESTEP_SWITCH_TICKS;
    public static final ModConfigSpec.DoubleValue STUCK_SIDESTEP_DISTANCE;
    public static final ModConfigSpec.DoubleValue STUCK_HOP_VELOCITY;
    public static final ModConfigSpec.DoubleValue STUCK_HOP_FORWARD_VELOCITY;

    public static final ModConfigSpec.BooleanValue PREY_ENABLED;
    public static final ModConfigSpec.IntValue PREY_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue PREY_RANGE;
    public static final ModConfigSpec.DoubleValue PREY_SPEED_MULTIPLIER;
    public static final ModConfigSpec.IntValue PREY_EAT_TICKS;
    public static final ModConfigSpec.IntValue PREY_MAX_CHASE_TICKS;

    public static final ModConfigSpec.DoubleValue FOOTSTEP_VOLUME;
    public static final ModConfigSpec.DoubleValue FOOTSTEP_STRIDE_BLOCKS;
    public static final ModConfigSpec.DoubleValue BITE_VOLUME;
    public static final ModConfigSpec.DoubleValue ROAR_VOLUME;
    public static final ModConfigSpec.IntValue ROAR_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue DISTANT_ROAR_ENABLED;
    public static final ModConfigSpec.IntValue DISTANT_ROAR_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue DISTANT_ROAR_VOLUME;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("attributes");
        MAX_HEALTH = BUILDER
                .gameRestart()
                .comment("T-rex max health in half-hearts.")
                .defineInRange("maxHealth", 200.0, 1.0, 4096.0);
        ATTACK_DAMAGE = BUILDER
                .gameRestart()
                .comment("Raw damage of one bite in half-hearts, before armor. At 18: full iron (defense 15, toughness 0)",
                        "takes 13.7 and survives one bite; full diamond (defense 20, toughness 2) takes 8.8 and survives",
                        "two. The third bite kills either way.")
                .defineInRange("attackDamage", 18.0, 0.0, 1024.0);
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
                .comment("Step height attribute in blocks: how high it walks up without jumping. The T-rex is 12 blocks tall, so 3 is a normal stride for it.")
                .defineInRange("stepHeight", 3.0, 0.0, 4.0);
        SCALE = BUILDER
                .gameRestart()
                .comment("Scale attribute: multiplies the base hitbox and the model.")
                .defineInRange("scale", 3.0, 0.0625, 16.0);
        HITBOX_WIDTH = BUILDER
                .gameRestart()
                .comment("Width of the hitbox, in blocks before the scale (so 1.5 at scale 3 is 4.5 blocks wide). It is a square: it is both the width and the length. The model is far longer than this (head and tail stick out), on purpose: a narrow hitbox gets through trees and gaps.")
                .defineInRange("hitboxWidth", 1.5, 0.25, 8.0);
        HITBOX_HEIGHT = BUILDER
                .gameRestart()
                .comment("Height of the hitbox, in blocks before the scale (2.2 at scale 3 is 6.6 blocks tall).")
                .defineInRange("hitboxHeight", 2.2, 0.25, 8.0);
        EYE_HEIGHT = BUILDER
                .gameRestart()
                .comment("Eye height, in blocks before the scale: where it sees from. Keep it at or below hitboxHeight.")
                .defineInRange("eyeHeight", 1.95, 0.1, 8.0);
        MODEL_OFFSET_BLOCKS = BUILDER
                .gameRestart()
                .comment("How far the model is shifted straight back from the hitbox centre, in blocks before the scale (0.5 at scale 3 is 1.5 blocks), so the hitbox sits over the chest and legs instead of the hips and little of it is wasted on the tail, which clips through walls.")
                .defineInRange("modelOffsetBlocks", 0.5, -2.0, 4.0);
        SWIM_SPEED_MULTIPLIER = BUILDER
                .gameRestart()
                .comment("How fast it moves in water: NeoForge's swim speed attribute, a multiplier on vanilla's water acceleration. A land mob crawls through water at under 1 block",
                        "a second at 1.0 (this T-rex measured 0.5 to 0.9). The swim acceleration also scales with its hunt speed, so it swims slower while stalking than while charging.",
                        "The default 8 measures about 6.3 blocks a second charging and 3.1 stalking: a swimming player is overtaken, but a boat (8 blocks a second) still escapes.")
                .defineInRange("swimSpeedMultiplier", 8.0, 0.1, 64.0);
        WATER_EXIT_HEIGHT = BUILDER
                .gameRestart()
                .comment("Highest ledge, in blocks, it can haul itself up onto when it swims into a bank (vanilla only manages about 0.6, so a lake whose shore is a block above the water was a wall).",
                        "0 turns this off and leaves only vanilla's short pull-out.")
                .defineInRange("waterExitHeight", 3.0, 0.0, 8.0);
        SWIM_DEPTH = BUILDER
                .gameRestart()
                .comment("How many blocks of its body are under the surface while it swims, in real blocks (its hitbox is 6.6 tall at the default scale). Vanilla mobs ride on top of the water",
                        "with their feet about 0.4 blocks under; at the default 3 the T-rex wades through deep water with its legs under and its head and back out. In water shallower than this it",
                        "wades along the bottom instead. It floats up to the vanilla level when it bumps a bank, so it can climb out. 0 turns this off.")
                .defineInRange("swimDepth", 3.0, 0.0, 6.0);
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
        WANDER_ARRIVAL_DISTANCE = BUILDER
                .comment("How close, in blocks, to its wander destination counts as having arrived.")
                .defineInRange("wanderArrivalDistance", 6.0, 1.0, 32.0);
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
        INVESTIGATE_REACH_FROM_EDGE = BUILDER
                .comment("How close to a heard noise counts as having arrived, measured from the edge of the T-rex's hitbox (so it does not depend on how wide the body is).",
                        "Replaces the old investigateReachDistance, which was measured from its centre: it stopped far short of the noise once the body got narrower.")
                .defineInRange("investigateReachFromEdge", 1.5, 0.0, 32.0);
        BITE_REACH = BUILDER
                .comment("Bite reach in blocks, measured from the edge of the T-rex's hitbox (so it stays correct at any scale).")
                .defineInRange("biteReach", 3.0, 0.5, 16.0);
        BITE_REACH_BELOW = BUILDER
                .comment("How far below its own feet, in blocks, the T-rex can bite down at a target (on top of biteReach). A body this wide cannot get into a narrow valley or pit, but the head can reach down into it.")
                .defineInRange("biteReachBelow", 8.0, 0.0, 32.0);
        BITE_WINDUP_TICKS = BUILDER
                .comment("Ticks between starting a bite and the damage landing (telegraph).")
                .defineInRange("biteWindupTicks", 10, 0, 100);
        BITE_COOLDOWN_TICKS = BUILDER
                .comment("Ticks between bites.")
                .defineInRange("biteCooldownTicks", 40, 5, 400);
        BUILDER.pop();

        BUILDER.push("obstacles");
        GRIEFING_ENABLED = BUILDER
                .comment("Master switch for block breaking. Off = the T-rex never breaks any block. The mobGriefing game rule is honored too (rule off = no breaking).")
                .define("griefingEnabled", true);
        UNBREAKABLE_BLOCKS = BUILDER
                .comment("Blocks the T-rex never breaks. Everything else it pushes against (up to maxBreakHardness) it may break, unless it holds data (see breakBlockEntities). Entries are block ids (minecraft:obsidian) or block tags with a leading # (#minecraft:dirt). Default: natural terrain (so it walks over the world instead of tunnelling through it), smooth stone, obsidian, iron blocks, concrete and End blocks.")
                .defineListAllowEmpty("unbreakableBlocks",
                        List.of("#minecraft:base_stone_overworld", "#minecraft:base_stone_nether", "#minecraft:dirt", "#minecraft:grass_blocks",
                                "#minecraft:mud", "#minecraft:moss_blocks", "#minecraft:sand", "#c:gravels", "#c:ores", "#minecraft:snow",
                                "#minecraft:ice", "#minecraft:nylium", "minecraft:dirt_path", "minecraft:farmland", "minecraft:clay",
                                "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:smooth_stone", "minecraft:smooth_stone_slab",
                                "#c:obsidians", "#minecraft:concrete", "#minecraft:concrete_slabs", "#minecraft:concrete_stairs",
                                "minecraft:iron_block", "minecraft:raw_iron_block", "#c:end_stones", "minecraft:end_stone_bricks",
                                "minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_slab", "minecraft:end_stone_brick_wall",
                                "minecraft:purpur_block", "minecraft:purpur_pillar", "minecraft:purpur_stairs", "minecraft:purpur_slab",
                                "minecraft:end_rod", "minecraft:end_portal_frame", "minecraft:end_portal", "minecraft:end_gateway",
                                "minecraft:chorus_plant", "minecraft:chorus_flower", "minecraft:dragon_egg"),
                        () -> "minecraft:obsidian",
                        element -> element instanceof String);
        FOLIAGE_BLOCKS = BUILDER
                .comment("Trees and the like. While the T-rex is not hunting a player (wandering, or going for an animal) these are the only blocks it breaks, so a tree line is never a trap and everything else is left alone then. Hunting a player uses unbreakableBlocks instead. Entries are block ids (minecraft:oak_leaves) or block tags with a leading # (#minecraft:leaves).")
                .defineListAllowEmpty("foliageBlocks",
                        List.of("#minecraft:leaves", "#minecraft:logs"),
                        () -> "minecraft:oak_leaves",
                        element -> element instanceof String);
        BREAK_BLOCK_ENTITIES = BUILDER
                .comment("Allow breaking blocks that hold data (chests, furnaces, signs...) when they match the list above. Off = those blocks are never broken.")
                .define("breakBlockEntities", false);
        MAX_BREAK_HARDNESS = BUILDER
                .comment("Blocks harder than this (vanilla hardness: planks 2, logs 2, glass 0.3, obsidian 50) are never broken, even if listed.")
                .defineInRange("maxBreakHardness", 5.0, 0.0, 3600000.0);
        BREAK_INTERVAL_TICKS = BUILDER
                .comment("Ticks between break actions while the T-rex is pushing against obstacles.")
                .defineInRange("breakIntervalTicks", 2, 1, 200);
        BLOCKS_PER_BREAK = BUILDER
                .comment("Most blocks removed in one break action. With the defaults (24 blocks every 3 ticks, about 8 per tick) a house wall or a tree line goes in a second or two; lower this and raise breakIntervalTicks for a slower, easier-on-the-server rex.")
                .defineInRange("blocksPerBreak", 24, 1, 256);
        BREAK_REACH = BUILDER
                .comment("How far in front of its hitbox, in blocks, the T-rex breaks blocks.")
                .defineInRange("breakReach", 1.5, 0.5, 16.0);
        BREAK_DROPS_ITEMS = BUILDER
                .comment("Broken blocks drop their items. Off by default: the T-rex leaves rubble (the break particles and sound, a wrecked structure) and no loot, which is also far kinder to the server when a whole forest goes.")
                .define("breakDropsItems", false);
        BUILDER.pop();

        BUILDER.push("stuck");
        STUCK_CHECK_INTERVAL_TICKS = BUILDER
                .comment("How often, in ticks, the T-rex checks whether it is getting closer to where it is going.")
                .defineInRange("stuckCheckIntervalTicks", 10, 2, 100);
        STUCK_MIN_PROGRESS = BUILDER
                .comment("Blocks it must have closed in on its destination since the last check to count as making progress.")
                .defineInRange("stuckMinProgress", 0.5, 0.05, 10.0);
        STUCK_BREAK_AFTER_TICKS = BUILDER
                .comment("Ticks without progress before it starts breaking blocks even when it is not touching a wall.")
                .defineInRange("stuckBreakAfterTicks", 20, 1, 1200);
        STUCK_HOP_AFTER_TICKS = BUILDER
                .comment("Ticks without progress before it starts hopping (to get over ledges). Should be above the break value.")
                .defineInRange("stuckHopAfterTicks", 60, 1, 1200);
        STUCK_SIDESTEP_AFTER_TICKS = BUILDER
                .comment("Ticks without progress before it starts walking sideways to go round the obstacle. Should be above the hop value.")
                .defineInRange("stuckSidestepAfterTicks", 100, 1, 1200);
        STUCK_GIVE_UP_AFTER_TICKS = BUILDER
                .comment("Ticks without progress before it gives up on its current target. Should be above the sidestep value.")
                .defineInRange("stuckGiveUpAfterTicks", 220, 1, 6000);
        STUCK_SIDESTEP_SWITCH_TICKS = BUILDER
                .comment("While sidestepping it switches between left and right every this many ticks.")
                .defineInRange("stuckSidestepSwitchTicks", 40, 5, 400);
        STUCK_SIDESTEP_DISTANCE = BUILDER
                .comment("How far to the side, in blocks, a sidestep aims.")
                .defineInRange("stuckSidestepDistance", 12.0, 2.0, 64.0);
        STUCK_HOP_VELOCITY = BUILDER
                .comment("Upward speed of a hop in blocks per tick (0.42 is a player jump; 0.9 clears roughly 4 blocks).")
                .defineInRange("stuckHopVelocity", 0.9, 0.1, 3.0);
        STUCK_HOP_FORWARD_VELOCITY = BUILDER
                .comment("Forward speed of a hop in blocks per tick.")
                .defineInRange("stuckHopForwardVelocity", 0.3, 0.0, 2.0);
        BUILDER.pop();

        BUILDER.push("prey");
        PREY_ENABLED = BUILDER
                .comment("Whether the T-rex occasionally goes for animals (pigs, cows, sheep, chickens, ...) and eats them while no player is detected.")
                .define("preyEnabled", true);
        PREY_INTERVAL_TICKS = BUILDER
                .comment("Average ticks between the T-rex noticing a nearby animal to eat while no player is detected.")
                .defineInRange("preyIntervalTicks", 900, 20, 72000);
        PREY_RANGE = BUILDER
                .comment("How far, in blocks, from the T-rex it notices animals to eat. Tamed, leashed and name-tagged animals are never eaten.")
                .defineInRange("preyRange", 32.0, 4.0, 128.0);
        PREY_SPEED_MULTIPLIER = BUILDER
                .comment("Speed multiplier while going for an animal.")
                .defineInRange("preySpeedMultiplier", 1.0, 0.1, 4.0);
        PREY_EAT_TICKS = BUILDER
                .comment("Ticks it stands over a kill 'eating' before it wanders on.")
                .defineInRange("preyEatTicks", 100, 0, 2400);
        PREY_MAX_CHASE_TICKS = BUILDER
                .comment("Longest it chases one animal before losing interest.")
                .defineInRange("preyMaxChaseTicks", 400, 20, 2400);
        BUILDER.pop();

        BUILDER.push("audio");
        FOOTSTEP_VOLUME = BUILDER
                .comment("Volume of its footsteps. A sound carries about 16 blocks per 1.0 of volume, so 10.0 is heard out to roughly 160 blocks, well past render distance.",
                        "The game never plays anything louder than 1.0 at the listener's ear: above 1.0 this only makes the footsteps reach further (and fade out later).")
                .defineInRange("footstepVolume", 10.0, 0.1, 16.0);
        FOOTSTEP_STRIDE_BLOCKS = BUILDER
                .comment("Blocks it walks between two footsteps. Vanilla steps every ~1.7 blocks, far too fast for a body this big.")
                .defineInRange("footstepStrideBlocks", 7.0, 1.0, 20.0);
        BITE_VOLUME = BUILDER
                .comment("Volume of its bite (audible out to about 16 blocks per 1.0).")
                .defineInRange("biteVolume", 3.0, 0.1, 16.0);
        ROAR_VOLUME = BUILDER
                .comment("Volume of the roar when it starts hunting a player (audible out to about 16 blocks per 1.0).")
                .defineInRange("roarVolume", 5.0, 0.1, 16.0);
        ROAR_COOLDOWN_TICKS = BUILDER
                .comment("Minimum ticks between two detection roars, so a player who keeps ducking in and out of sight does not trigger a roar every few seconds.")
                .defineInRange("roarCooldownTicks", 600, 0, 72000);
        DISTANT_ROAR_ENABLED = BUILDER
                .comment("Whether it occasionally roars at night while it is not hunting, to be heard from far away.")
                .define("distantRoarEnabled", true);
        DISTANT_ROAR_INTERVAL_TICKS = BUILDER
                .comment("Average ticks between distant roars at night while it is not hunting (6000 ticks = 5 minutes).")
                .defineInRange("distantRoarIntervalTicks", 6000, 200, 720000);
        DISTANT_ROAR_VOLUME = BUILDER
                .comment("Volume of the distant roar (audible out to about 16 blocks per 1.0, so 12.0 is about 192 blocks).")
                .defineInRange("distantRoarVolume", 12.0, 0.1, 16.0);
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
