package com.dinodread.gametest;

import static com.dinodread.gametest.RexGameTests.FACING_SOUTH;
import static com.dinodread.gametest.RexGameTests.MIDNIGHT_TICKS;
import static com.dinodread.gametest.RexGameTests.TEST_TIMEOUT_TICKS;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;

import com.dinodread.DinoDread;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Terrain and tree acceptance tests found missing after the first real playtest: "his pathing really struggles with hills, and he has a
 * hard time escaping trees; he started to break the tree in front of him, but when it was just a few floating leaves left he got stuck".
 * <ul>
 * <li>Hills: the M4 "sand hill" test was a straight 3-block ledge, never a slope. These hunt a player on top of real slopes (a ramp at
 *     three steepnesses, and a landscape of rolling hills), over unbreakable grass and dirt.</li>
 * <li>Trees: a rex that has lost its target is no longer hunting, and used to be unable to break anything, so the last leaves
 *     around it were a trap. A rex boxed in by leaves and log posts, with nobody to hunt, must wander out; one boxed in by planks (a
 *     player's build) must still leave them alone.</li>
 * </ul>
 */
public final class RexTerrainGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);

    private static final String GENTLE_HILL = "trex_hunts_up_a_gentle_hill";
    private static final String HILL_45 = "trex_hunts_up_a_45_degree_hill";
    private static final String STEEP_HILL = "trex_hunts_up_a_steep_hill";
    private static final String ROLLING_HILLS = "trex_hunts_across_rolling_hills";
    private static final String LEAF_CAGE = "trex_wanders_out_of_a_leaf_cage";
    private static final String PLANK_CAGE = "trex_wandering_leaves_a_plank_cage_alone";
    private static final String FOREST_GROUND = "trex_goes_through_a_dense_forest_not_over_it";
    private static final String TREETOP = "trex_falls_through_a_treetop_it_stands_on";

    // Hills: the ground starts rising at HILL_START_Z, blocks gained per block walked is the slope, up to the plateau height.
    private static final int HILL_START_Z = 6;
    private static final int HILL_PLATEAU = 10;
    private static final int STEEP_PLATEAU = 12;
    private static final int PLAYER_Z = 34;
    private static final double GENTLE_SLOPE = 0.5;
    private static final double SLOPE_45 = 1.0;
    private static final double STEEP_SLOPE = 2.0;
    private static final int HILL_MAX_TICKS = 2400;
    private static final int NOISE_INTERVAL_TICKS = 10;
    private static final int LOG_INTERVAL_TICKS = 40;

    // Rolling hills: smooth bumps in both directions (about 1 block of rise per block at the steepest), faded in from flat ground at the rex.
    private static final int ROLLING_FADE_IN_BLOCKS = 12;
    private static final double ROLLING_BASE = 6.0;
    private static final double ROLLING_X_AMPLITUDE = 3.0;
    private static final double ROLLING_X_FREQUENCY = 0.21;
    private static final double ROLLING_Z_AMPLITUDE = 3.0;
    private static final double ROLLING_Z_FREQUENCY = 0.16;
    private static final double ROLLING_DIAGONAL_AMPLITUDE = 2.0;
    private static final double ROLLING_DIAGONAL_FREQUENCY = 0.11;

    // Cage: a ring two blocks thick whose inner face is one block clear of the 9 wide rex at its start; the walls are taller than the rex.
    private static final int CAGE_INNER_RADIUS = 6;
    private static final int CAGE_OUTER_RADIUS = 7;
    private static final int CAGE_HEIGHT = 10;
    private static final int LOG_POST_SPACING = 3;
    // The wall's outer face is 8 blocks from the origin, 7.5 from the rex's centre at its start. A centre that far out has pushed through the whole
    // wall; much further is not possible here, because the arena floor ends 13 blocks out and beyond it is natural terrain.
    private static final double ESCAPE_DISTANCE = 7.5;
    // The wander direction is random. Measured over 6 full runs: out after 375, 432, 452, 560, 750 and 1017 ticks (median ~500); 4000 is
    // about 4x the slowest. (Before wandering could break foliage it never got out at all; before it ticked every tick it took ~2000.)
    private static final int ESCAPE_MAX_TICKS = 4000;
    private static final int PLANK_WATCH_TICKS = 1500;
    private static final int LEAF_CAGE_LOG_DENSE_TICKS = 600;

    // Dense oak forest: a jittered grid of trees (trunks 4 to 7 high, a real oak canopy of two wide and two narrow leaf layers) close enough that the canopies touch.
    private static final long OAK_FOREST_SEED = 20261020L;
    private static final int OAK_FIRST_Z = 8;
    private static final int OAK_LAST_Z = 30;
    private static final int OAK_SPACING = 4;
    private static final int OAK_JITTER = 1;
    private static final int OAK_MIN_TRUNK = 4;
    private static final int OAK_TRUNK_VARIATION = 3;
    private static final int OAK_PLAYER_CLEARANCE = 4;
    private static final int OAK_WIDE_RADIUS = 2;
    private static final int OAK_NARROW_RADIUS = 1;
    private static final int FOREST_MAX_TICKS = 2400;
    // Treetop: a one block thick leaf platform this high, wider than the rex, which is put on top of it, inside a tall stone ring.
    private static final int TREETOP_HEIGHT = 3;
    private static final int TREETOP_RING_RADIUS = 8;
    private static final int TREETOP_RING_TOP = 16;
    private static final int TREETOP_MAX_TICKS = 600;
    // On a flat floor, a rex standing on anything higher than this is standing on a tree.
    private static final double STANDING_LIMIT = 1.0;

    static {
        FUNCTIONS.register(GENTLE_HILL, () -> RexTerrainGameTests::huntsUpAGentleHill);
        FUNCTIONS.register(HILL_45, () -> RexTerrainGameTests::huntsUpA45DegreeHill);
        FUNCTIONS.register(STEEP_HILL, () -> RexTerrainGameTests::huntsUpASteepHill);
        FUNCTIONS.register(ROLLING_HILLS, () -> RexTerrainGameTests::huntsAcrossRollingHills);
        FUNCTIONS.register(LEAF_CAGE, () -> RexTerrainGameTests::wandersOutOfALeafCage);
        FUNCTIONS.register(PLANK_CAGE, () -> RexTerrainGameTests::wanderingLeavesAPlankCageAlone);
        FUNCTIONS.register(FOREST_GROUND, () -> RexTerrainGameTests::goesThroughADenseForestNotOverIt);
        FUNCTIONS.register(TREETOP, () -> RexTerrainGameTests::fallsThroughATreetopItStandsOn);
    }

    private RexTerrainGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexTerrainGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        RexGameTests.addTest(event, GENTLE_HILL, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, HILL_45, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, STEEP_HILL, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, ROLLING_HILLS, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, LEAF_CAGE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, PLANK_CAGE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, FOREST_GROUND, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, TREETOP, TEST_TIMEOUT_TICKS);
    }

    /** A ramp rising one block every two blocks (about 27 degrees). */
    private static void huntsUpAGentleHill(GameTestHelper helper) {
        huntsUp(helper, ramp(GENTLE_SLOPE, HILL_PLATEAU));
    }

    /** A 45 degree ramp: one block up for every block forward. */
    private static void huntsUpA45DegreeHill(GameTestHelper helper) {
        huntsUp(helper, ramp(SLOPE_45, HILL_PLATEAU));
    }

    /** Steps two blocks high every block: still within the step height of 3, but a staircase for a body this wide. */
    private static void huntsUpASteepHill(GameTestHelper helper) {
        huntsUp(helper, ramp(STEEP_SLOPE, STEEP_PLATEAU));
    }

    private static void huntsAcrossRollingHills(GameTestHelper helper) {
        huntsUp(helper, RexTerrainGameTests::rollingHeight);
    }

    /** The rex hunts a player standing on the highest ground at PLAYER_Z, over grass and dirt it cannot (and must not) dig through. */
    private static void huntsUp(GameTestHelper helper, IntBinaryOperator terrain) {
        float[] startHealth = new float[1];
        int playerY = terrain.applyAsInt(0, PLAYER_Z);
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, playerY, PLAYER_Z + 0.5), false, HILL_MAX_TICKS,
            origin -> buildTerrain(helper.getLevel(), origin, terrain),
            run -> startHealth[0] = run.player.getHealth(),
            run -> {
                if (run.age() % NOISE_INTERVAL_TICKS == 0) {
                    RexGameTests.stepNoise(run);
                }
                double rexY = run.rex.getY() - run.origin.getY();
                run.status = "rex never got to the player on the hill: rex z=" + String.format("%.1f", run.rex.getZ() - run.origin.getZ()) + " y=" + String.format("%.1f", rexY)
                    + " (player y=" + playerY + "), state=" + run.rex.getHuntState() + ", hasQuarry=" + run.rex.getSenses().hasQuarry();
                if (run.age() % LOG_INTERVAL_TICKS == 0) {
                    DinoDread.LOGGER.info("[gametest] hill tick {}: {}", run.age(), run.status);
                }
                if (run.player.getHealth() < startHealth[0] || !run.player.isAlive()) {
                    DinoDread.LOGGER.info("[gametest] hill: player bitten {} ticks after the start", run.age());
                    run.pass();
                }
            });
    }

    /** No target at all and walls of leaves with log posts all around: wandering has to break through, as the hunt would. */
    private static void wandersOutOfALeafCage(GameTestHelper helper) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        List<BlockPos> cage = new ArrayList<>();
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, ESCAPE_MAX_TICKS,
            origin -> cage.addAll(buildCage(helper.getLevel(), origin, (x, z) -> x % LOG_POST_SPACING == 0 && z % LOG_POST_SPACING == 0 ? log : leaves)),
            run -> {},
            run -> {
                double fromStart = Math.max(Math.abs(run.rex.getX() - run.origin.getX() - 0.5), Math.abs(run.rex.getZ() - run.origin.getZ() - 0.5));
                long broken = cage.stream().filter(pos -> run.helper.getLevel().getBlockState(pos).isAir()).count();
                run.status = "rex never got out of the leaf cage (distance from start: " + String.format("%.1f", fromStart) + " blocks, needs " + ESCAPE_DISTANCE
                    + "; " + broken + " of " + cage.size() + " cage blocks broken; collided=" + run.rex.horizontalCollision + ")";
                if (run.age() % LOG_INTERVAL_TICKS == 0 && (run.age() < LEAF_CAGE_LOG_DENSE_TICKS || run.age() % (LOG_INTERVAL_TICKS * 5) == 0)) {
                    DinoDread.LOGGER.info("[gametest] leaf cage tick {}: {} state={}", run.age(), run.status, run.rex.getHuntState());
                }
                if (fromStart >= ESCAPE_DISTANCE) {
                    DinoDread.LOGGER.info("[gametest] leaf cage: out {} ticks after the start", run.age());
                    run.pass();
                }
            });
    }

    /** A cage of planks is somebody's build: a rex that is only wandering must not break any of it, however long it pushes. */
    private static void wanderingLeavesAPlankCageAlone(GameTestHelper helper) {
        List<BlockPos> planks = new ArrayList<>();
        BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, PLANK_WATCH_TICKS + 1,
            origin -> planks.addAll(buildCage(helper.getLevel(), origin, (x, z) -> plank)),
            run -> {},
            run -> {
                ServerLevel level = run.helper.getLevel();
                for (BlockPos pos : planks) {
                    run.check(level.getBlockState(pos).is(plank.getBlock()), "a wandering rex broke the plank at " + pos.subtract(run.origin));
                }
                run.status = "watching a wandering rex push at planks";
                if (run.age() >= PLANK_WATCH_TICKS) {
                    run.pass();
                }
            });
    }

    /**
     * A dense oak forest on flat ground, canopies touching, between the rex and the player: it has to break its way through at ground level.
     * A rex that is ever standing on the ground-level-plus-something (it only counts when it is on a surface, not hopping) has climbed onto the trees,
     * which is what the playtest showed: it walked around on top of the canopy instead of going through it.
     */
    private static void goesThroughADenseForestNotOverIt(GameTestHelper helper) {
        float[] startHealth = new float[1];
        double[] highestStanding = new double[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z + 0.5), false, FOREST_MAX_TICKS,
            origin -> buildOakForest(helper.getLevel(), origin),
            run -> startHealth[0] = run.player.getHealth(),
            run -> {
                if (run.age() % NOISE_INTERVAL_TICKS == 0) {
                    RexGameTests.stepNoise(run);
                }
                double feetY = run.rex.getY() - run.origin.getY();
                if (run.rex.onGround()) {
                    highestStanding[0] = Math.max(highestStanding[0], feetY);
                }
                run.check(highestStanding[0] <= STANDING_LIMIT, "the rex climbed onto the trees: it stood " + String.format("%.1f", highestStanding[0]) + " blocks above the ground (limit "
                    + STANDING_LIMIT + ") at tick " + run.age());
                run.status = "rex never got through the forest to the player: z=" + String.format("%.1f", run.rex.getZ() - run.origin.getZ()) + " y=" + String.format("%.1f", feetY);
                if (run.age() % LOG_INTERVAL_TICKS == 0) {
                    DinoDread.LOGGER.info("[gametest] oak forest tick {}: {} (highest standing {})", run.age(), run.status, String.format("%.1f", highestStanding[0]));
                }
                if (run.player.getHealth() < startHealth[0] || !run.player.isAlive()) {
                    DinoDread.LOGGER.info("[gametest] oak forest: player bitten {} ticks after the start, highest standing {}", run.age(), String.format("%.1f", highestStanding[0]));
                    run.pass();
                }
            });
    }

    /**
     * Treetops are not ground: put the rex on a platform of leaves three blocks up (as if it had hopped or stepped onto a canopy) and it must break
     * its way down through it and end up standing on the real floor, instead of walking around up there. A ring of stone, tall and unbreakable, keeps it
     * from simply walking off the edge, so the only way down is through the leaves under its feet.
     */
    private static void fallsThroughATreetopItStandsOn(GameTestHelper helper) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState stone = Blocks.STONE.defaultBlockState();
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, TREETOP_MAX_TICKS,
            origin -> {
                for (int x = -TREETOP_RING_RADIUS; x <= TREETOP_RING_RADIUS; x++) {
                    for (int z = -TREETOP_RING_RADIUS; z <= TREETOP_RING_RADIUS; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) == TREETOP_RING_RADIUS) {
                            for (int y = TREETOP_HEIGHT; y < TREETOP_RING_TOP; y++) {
                                helper.getLevel().setBlock(origin.offset(x, y, z), stone, Block.UPDATE_CLIENTS);
                            }
                        } else {
                            helper.getLevel().setBlock(origin.offset(x, TREETOP_HEIGHT, z), leaves, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            },
            run -> run.rex.snapTo(run.helper.absoluteVec(new Vec3(0.5, TREETOP_HEIGHT + 1, 0.5))),
            run -> {
                double feetY = run.rex.getY() - run.origin.getY();
                run.status = "the rex is still standing on the treetop (y=" + String.format("%.1f", feetY) + ", onGround=" + run.rex.onGround() + ")";
                if (run.age() % LOG_INTERVAL_TICKS == 0) {
                    DinoDread.LOGGER.info("[gametest] treetop tick {}: {}", run.age(), run.status);
                }
                if (run.rex.onGround() && feetY <= STANDING_LIMIT) {
                    DinoDread.LOGGER.info("[gametest] treetop: back on the ground {} ticks after the start", run.age());
                    run.pass();
                }
            });
    }

    // ---- structures ----

    private static IntBinaryOperator ramp(double slope, int plateau) {
        return (x, z) -> Mth.clamp((int) Math.floor((z - HILL_START_Z) * slope), 0, plateau);
    }

    private static int rollingHeight(int x, int z) {
        double fade = Mth.clamp((z - HILL_START_Z) / (double) ROLLING_FADE_IN_BLOCKS, 0.0, 1.0);
        double bumps = ROLLING_BASE + ROLLING_X_AMPLITUDE * Math.sin(ROLLING_X_FREQUENCY * x) + ROLLING_Z_AMPLITUDE * Math.sin(ROLLING_Z_FREQUENCY * z)
            + ROLLING_DIAGONAL_AMPLITUDE * Math.sin(ROLLING_DIAGONAL_FREQUENCY * (x + z));
        return Math.max(0, (int) Math.round(fade * bumps));
    }

    /** Columns of dirt topped with grass from the arena floor up to {@code terrain(x, z)}: natural terrain, which the rex must climb. */
    private static void buildTerrain(ServerLevel level, BlockPos origin, IntBinaryOperator terrain) {
        for (int x = -RexGameTests.ARENA_HALF_WIDTH; x <= RexGameTests.ARENA_HALF_WIDTH; x++) {
            for (int z = 0; z <= RexGameTests.ARENA_FRONT; z++) {
                int height = terrain.applyAsInt(x, z);
                for (int y = 0; y < height; y++) {
                    BlockState state = y == height - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    level.setBlock(origin.offset(x, y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void buildOakForest(ServerLevel level, BlockPos origin) {
        Random random = new Random(OAK_FOREST_SEED);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int gridZ = OAK_FIRST_Z; gridZ <= OAK_LAST_Z; gridZ += OAK_SPACING) {
            for (int gridX = -RexGameTests.ARENA_HALF_WIDTH + 1; gridX <= RexGameTests.ARENA_HALF_WIDTH - 1; gridX += OAK_SPACING) {
                int x = gridX + random.nextInt(2 * OAK_JITTER + 1) - OAK_JITTER;
                int z = gridZ + random.nextInt(2 * OAK_JITTER + 1) - OAK_JITTER;
                int trunk = OAK_MIN_TRUNK + random.nextInt(OAK_TRUNK_VARIATION + 1);
                if (Math.abs(x) < OAK_PLAYER_CLEARANCE && Math.abs(z - PLAYER_Z) < OAK_PLAYER_CLEARANCE) {
                    continue;
                }
                for (int y = 0; y < trunk; y++) {
                    level.setBlock(origin.offset(x, y, z), log, Block.UPDATE_CLIENTS);
                }
                for (int layer = -2; layer <= 1; layer++) {
                    int radius = layer <= -1 ? OAK_WIDE_RADIUS : OAK_NARROW_RADIUS;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            boolean trimmedCorner = radius == OAK_WIDE_RADIUS && Math.abs(dx) == radius && Math.abs(dz) == radius;
                            BlockPos pos = origin.offset(x + dx, trunk + layer, z + dz);
                            if (!trimmedCorner && Math.abs(x + dx) <= RexGameTests.ARENA_HALF_WIDTH && level.getBlockState(pos).isAir()) {
                                level.setBlock(pos, leaves, Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
            }
        }
    }

    /** A square ring around the rex's start, {@link #CAGE_OUTER_RADIUS} - {@link #CAGE_INNER_RADIUS} + 1 blocks thick; {@code material} picks the block at each column. */
    private static List<BlockPos> buildCage(ServerLevel level, BlockPos origin, BiFunction<Integer, Integer, BlockState> material) {
        List<BlockPos> placed = new ArrayList<>();
        for (int x = -CAGE_OUTER_RADIUS; x <= CAGE_OUTER_RADIUS; x++) {
            for (int z = -CAGE_OUTER_RADIUS; z <= CAGE_OUTER_RADIUS; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) < CAGE_INNER_RADIUS) {
                    continue;
                }

                BlockState state = material.apply(x, z);
                for (int y = 0; y < CAGE_HEIGHT; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    placed.add(pos);
                }
            }
        }
        return placed;
    }
}
