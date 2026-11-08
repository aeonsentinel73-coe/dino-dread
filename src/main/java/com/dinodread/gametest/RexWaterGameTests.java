package com.dinodread.gametest;

import static com.dinodread.gametest.RexGameTests.FACING_SOUTH;
import static com.dinodread.gametest.RexGameTests.MIDNIGHT_TICKS;
import static com.dinodread.gametest.RexGameTests.TEST_TIMEOUT_TICKS;

import java.util.function.Consumer;

import com.dinodread.DinoDread;
import com.dinodread.entity.ai.HuntState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Water: a lake between the T-rex and a player standing on the far bank. Raised after the second playtest ("he seemed to get stuck in the water").
 * Two things were wrong, both reproduced here before any fix:
 * <ul>
 * <li>Speed: a land mob crawls through water (vanilla's swim acceleration is 0.02 with drag 0.8; this T-rex measured 0.5 to 0.9 blocks a second, so a
 *     lake took over a minute). The design asked for: no hiding from the T-rex in the water, but a boat still escapes it, so while it is in the water it
 *     must be clearly faster than a swimming player (roughly 2 to 4 blocks a second) and clearly slower than a boat (8). The fix is NeoForge's swim speed
 *     attribute, plus keeping vanilla's small float nudge: NeoForge scales that by the same attribute, and at the speed needed the T-rex bounced across the
 *     lake like a dolphin (it spent most of the crossing in the air).</li>
 * <li>Getting out: the bank is one block above the water, as on a real lake. Vanilla only lets a swimmer pull itself out onto a ledge about 0.6 blocks
 *     up, so the T-rex sat against the far bank for the rest of the test, bobbing a quarter of a block short of the top.</li>
 * <li>Riding on top (the third playtest: "he's doing a Jesus"): vanilla floats a mob once more than 0.4 blocks of it is under water, so the T-rex walked on the surface.
 *     The swim depth (3 blocks of body under water) fixes that; it wades along the bottom where the water is shallower, floats up when it bumps a bank (so it can climb out), and
 *     is pushed down to its depth faster than the water alone would sink it.</li>
 * </ul>
 */
public final class RexWaterGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);

    private static final String SHALLOW = "trex_crosses_a_shallow_lake";
    private static final String DEEP = "trex_swims_across_a_deep_lake";
    private static final String SINKS = "trex_sinks_to_its_swim_depth";

    private static final int SHALLOW_DEPTH = 3;
    private static final int DEEP_DEPTH = 9;
    private static final int LAKE_FIRST_Z = 8;
    private static final int LAKE_LAST_Z = 34;
    private static final double PLAYER_Z = 40.5;
    private static final int WATER_MAX_TICKS = 3000;
    private static final int NOISE_INTERVAL_TICKS = 10;
    private static final int LOG_INTERVAL_TICKS = 100;
    private static final double TICKS_PER_SECOND = 20.0;
    // What the design asks of the speed in the water (blocks a second). A boat tops out at 0.04 / (1 - 0.9) = 8 per second, a swimming player at roughly 2 to 4
    // (sprint-swimming is the fast end). A charging rex must be well above a swimmer, so there is no hiding in the water, and clearly below the boat, so a boat still
    // escapes; the stalk speed is lower still (the swim acceleration scales with the hunt speed), so the average only has to beat a plain swimmer.
    private static final double MIN_CHARGE_WATER_SPEED = 5.0;
    private static final double MAX_CHARGE_WATER_SPEED = 6.8;
    private static final double MIN_AVERAGE_WATER_SPEED = 3.5;
    // Fewer in-water ticks than this and the speeds say nothing (they would be the bank, not the lake).
    private static final int MIN_WATER_TICKS = 30;
    // Where the sinking is measured: far enough from both banks that it has settled (it dives on entering the lake and rises to climb out).
    private static final int MID_LAKE_FIRST_Z = 16;
    private static final int MID_LAKE_LAST_Z = 28;
    private static final int MIN_MID_LAKE_TICKS = 10;
    // The default swim depth is 3 blocks of body under water; the average over the middle of the lake has to show most of that (it bobs a little).
    private static final double MIN_SUBMERGED_BLOCKS = 2.0;
    // Sinking: placed at rest on the surface in the middle of the lake (nothing carries it down), it has to be most of the way to its depth within 2.5 seconds. Water alone
    // sinks a mob by about half a block a second, which would leave it riding the surface for seconds after every time it rose to climb out.
    private static final int SINK_START_Z = 14;
    private static final double SINK_START_BODY_UNDER_WATER = 0.4;
    private static final double SINK_TARGET_BLOCKS = 2.5;
    private static final int SINK_MAX_TICKS = 50;
    // The top of a source block of water is 8/9 of the block.
    private static final double WATER_SURFACE_IN_BLOCK = 8.0 / 9.0;

    static {
        FUNCTIONS.register(SHALLOW, () -> RexWaterGameTests::crossesAShallowLake);
        FUNCTIONS.register(DEEP, () -> RexWaterGameTests::swimsAcrossADeepLake);
        FUNCTIONS.register(SINKS, () -> RexWaterGameTests::sinksToItsSwimDepth);
    }

    private RexWaterGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexWaterGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        RexGameTests.addTest(event, SHALLOW, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, DEEP, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, SINKS, TEST_TIMEOUT_TICKS);
    }

    /** A lake with 2 blocks of water: shallower than the swim depth, so the T-rex wades along the bed and steps out; only the speed is checked. */
    private static void crossesAShallowLake(GameTestHelper helper) {
        crossesALake(helper, SHALLOW_DEPTH, false);
    }

    /** Deep water: the T-rex swims with its legs under the surface (not on top of it) and its head clear of the water. */
    private static void swimsAcrossADeepLake(GameTestHelper helper) {
        crossesALake(helper, DEEP_DEPTH, true);
    }

    private static void sinksToItsSwimDepth(GameTestHelper helper) {
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z), false, SINK_MAX_TICKS,
            origin -> buildLake(helper.getLevel(), origin, DEEP_DEPTH),
            run -> {
                run.cleanups.add(() -> removeLake(helper.getLevel(), run.origin, DEEP_DEPTH));
                // Water surface, minus the little of the body that floats under it; at rest.
                double surfaceY = run.origin.getY() - 2 + WATER_SURFACE_IN_BLOCK;
                run.rex.setPos(run.origin.getX() + 0.5, surfaceY - SINK_START_BODY_UNDER_WATER, run.origin.getZ() + SINK_START_Z + 0.5);
                run.rex.setDeltaMovement(Vec3.ZERO);
            },
            run -> {
                double submerged = run.rex.getFluidHeight(FluidTags.WATER);
                run.status = "it has not sunk to its swim depth: " + String.format("%.2f", submerged) + " blocks of its body under water after " + run.age()
                    + " ticks, needs " + SINK_TARGET_BLOCKS + " within " + SINK_MAX_TICKS;
                if (submerged >= SINK_TARGET_BLOCKS) {
                    DinoDread.LOGGER.info("[gametest] sink: {} blocks of body under water after {} ticks", String.format("%.2f", submerged), run.age());
                    run.pass();
                }
            });
    }

    private static void crossesALake(GameTestHelper helper, int depth, boolean expectSubmerged) {
        float[] startHealth = new float[1];
        Vec3[] last = new Vec3[1];
        // Blocks of body under water (Entity.getFluidHeight, what the float logic measures) summed over the middle of the lake, and whether the eyes ever went under.
        double[] submergedSum = new double[1];
        int[] midLakeTicks = new int[1];
        boolean[] eyesUnderWater = new boolean[1];
        // Horizontal distance and ticks spent in the water, in total and per hunt state (the speed multipliers differ per state on land).
        double[] distanceByState = new double[HuntState.values().length];
        int[] ticksByState = new int[HuntState.values().length];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z), false, WATER_MAX_TICKS,
            origin -> buildLake(helper.getLevel(), origin, depth),
            run -> {
                startHealth[0] = run.player.getHealth();
                last[0] = run.rex.position();
                // The basin is dug out of ground other tests reuse, so put the arena floor back whatever happens.
                run.cleanups.add(() -> removeLake(helper.getLevel(), run.origin, depth));
            },
            run -> {
                if (run.age() % NOISE_INTERVAL_TICKS == 0) {
                    RexGameTests.stepNoise(run);
                }
                Vec3 now = run.rex.position();
                if (run.rex.isInWater()) {
                    int state = run.rex.getHuntState().ordinal();
                    ticksByState[state]++;
                    distanceByState[state] += now.subtract(last[0]).horizontalDistance();
                }
                last[0] = now;
                double lakeZ = now.z - run.origin.getZ();
                if (run.rex.isInWater() && lakeZ >= MID_LAKE_FIRST_Z && lakeZ <= MID_LAKE_LAST_Z) {
                    midLakeTicks[0]++;
                    submergedSum[0] += run.rex.getFluidHeight(FluidTags.WATER);
                }
                eyesUnderWater[0] |= run.rex.isEyeInFluid(FluidTags.WATER);
                int waterTicks = total(ticksByState);
                double averageSpeed = waterTicks == 0 ? 0.0 : total(distanceByState) / waterTicks * TICKS_PER_SECOND;
                run.status = "rex never got across the lake and bit the player: z=" + String.format("%.1f", now.z - run.origin.getZ()) + " y=" + String.format("%.1f", now.y - run.origin.getY())
                    + " inWater=" + run.rex.isInWater() + " state=" + run.rex.getHuntState() + " average speed in water " + String.format("%.2f", averageSpeed) + " blocks/s";
                if (run.age() % LOG_INTERVAL_TICKS == 0) {
                    DinoDread.LOGGER.info("[gametest] lake(depth {}) tick {}: {}", depth, run.age(), run.status);
                }
                if (run.player.getHealth() < startHealth[0] || !run.player.isAlive()) {
                    DinoDread.LOGGER.info("[gametest] lake(depth {}): player bitten {} ticks after the start; {} ticks in water, average speed {} blocks/s ({})", depth, run.age(), waterTicks,
                        String.format("%.2f", averageSpeed), describeStates(distanceByState, ticksByState));
                    int chargeIndex = HuntState.CHARGE.ordinal();
                    double chargeSpeed = ticksByState[chargeIndex] == 0 ? 0.0 : distanceByState[chargeIndex] / ticksByState[chargeIndex] * TICKS_PER_SECOND;
                    run.check(waterTicks >= MIN_WATER_TICKS && ticksByState[chargeIndex] >= MIN_WATER_TICKS, "the rex charged in the water for only " + ticksByState[chargeIndex]
                        + " ticks (" + waterTicks + " in all): the lake did not test anything");
                    run.check(chargeSpeed >= MIN_CHARGE_WATER_SPEED, "too slow when charging in the water: " + String.format("%.2f", chargeSpeed)
                        + " blocks/s, a swimming player could almost keep up; needs at least " + MIN_CHARGE_WATER_SPEED);
                    run.check(chargeSpeed <= MAX_CHARGE_WATER_SPEED, "too fast when charging in the water: " + String.format("%.2f", chargeSpeed)
                        + " blocks/s, a boat (8) would be no escape; needs at most " + MAX_CHARGE_WATER_SPEED);
                    run.check(averageSpeed >= MIN_AVERAGE_WATER_SPEED, "too slow on average in the water: " + String.format("%.2f", averageSpeed) + " blocks/s; needs at least " + MIN_AVERAGE_WATER_SPEED);
                    double averageSubmerged = midLakeTicks[0] == 0 ? 0.0 : submergedSum[0] / midLakeTicks[0];
                    DinoDread.LOGGER.info("[gametest] lake(depth {}): {} ticks in mid-lake, {} blocks of body under water on average, eyes under water at some point: {}", depth, midLakeTicks[0],
                        String.format("%.2f", averageSubmerged), eyesUnderWater[0]);
                    run.check(!eyesUnderWater[0], "the head went under the water");
                    if (expectSubmerged) {
                        run.check(midLakeTicks[0] >= MIN_MID_LAKE_TICKS, "only " + midLakeTicks[0] + " ticks in the middle of the lake: the depth says nothing");
                        run.check(averageSubmerged >= MIN_SUBMERGED_BLOCKS, "it rides on top of the water: only " + String.format("%.2f", averageSubmerged)
                            + " blocks of its body are under the surface in the middle of the lake, needs at least " + MIN_SUBMERGED_BLOCKS);
                    }
                    run.pass();
                }
            });
    }

    private static int total(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    private static double total(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    private static String describeStates(double[] distanceByState, int[] ticksByState) {
        StringBuilder text = new StringBuilder();
        for (HuntState state : HuntState.values()) {
            int ticks = ticksByState[state.ordinal()];
            if (ticks > 0) {
                text.append(state).append(' ').append(String.format("%.2f", distanceByState[state.ordinal()] / ticks * TICKS_PER_SECOND)).append(" over ").append(ticks).append(" ticks; ");
            }
        }
        return text.toString();
    }

    /** Puts the arena back as {@code buildArena} makes it, so the next test that reuses this ground finds a stone floor and air, not a basin. */
    static void removeLake(ServerLevel level, BlockPos origin, int depth) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int wallX = RexGameTests.ARENA_HALF_WIDTH + 1;
        for (int x = -wallX; x <= wallX; x++) {
            for (int z = LAKE_FIRST_Z - 1; z <= LAKE_LAST_Z + 1; z++) {
                for (int y = -depth - 1; y <= -1; y++) {
                    boolean arenaFloor = y == -1 && Math.abs(x) <= RexGameTests.ARENA_HALF_WIDTH;
                    level.setBlock(origin.offset(x, y, z), arenaFloor ? stone : air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * A basin sunk into the arena floor between z=LAKE_FIRST_Z and z=LAKE_LAST_Z: stone bed and walls (the arena is narrower than the world, so the
     * sides are walled too), water up to one block below the bank top, so leaving the lake means climbing a block, as on a real shore.
     */
    static void buildLake(ServerLevel level, BlockPos origin, int depth) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int bedY = -depth - 1;
        int wallX = RexGameTests.ARENA_HALF_WIDTH + 1;
        for (int x = -wallX; x <= wallX; x++) {
            for (int z = LAKE_FIRST_Z - 1; z <= LAKE_LAST_Z + 1; z++) {
                boolean wall = Math.abs(x) == wallX || z == LAKE_FIRST_Z - 1 || z == LAKE_LAST_Z + 1;
                for (int y = bedY; y <= -1; y++) {
                    BlockState state;
                    if (y == bedY || wall) {
                        state = stone;
                    } else if (y == -1) {
                        state = air;
                    } else {
                        state = water;
                    }
                    level.setBlock(origin.offset(x, y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
