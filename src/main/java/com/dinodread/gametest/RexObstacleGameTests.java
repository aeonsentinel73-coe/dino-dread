package com.dinodread.gametest;

import static com.dinodread.gametest.RexGameTests.FACING_SOUTH;
import static com.dinodread.gametest.RexGameTests.MIDNIGHT_TICKS;
import static com.dinodread.gametest.RexGameTests.TEST_TIMEOUT_TICKS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import com.dinodread.DinoDread;
import com.dinodread.entity.ai.RexBlockBreaker;
import com.dinodread.entity.ai.RexStuckDetector;
import com.dinodread.entity.ai.RexStuckDetector.Level;
import com.dinodread.config.RexConfig;
import com.dinodread.gametest.RexGameTests.Run;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Milestone 4 acceptance tests ("big body"): the 9 wide x 12 tall T-rex must get at a survival player through wooden
 * walls and tree lines, climb a ledge, leave stone alone, and respect the mobGriefing game rule. The step functions are
 * run by the same scenario helper as {@link RexGameTests} (own batch per test, arena readiness waits, cleanup).
 * <p>
 * The player "paces" (a step game event every {@link #NOISE_INTERVAL_TICKS}) so the rex keeps hearing where the player is
 * even when walls or leaves block its line of sight; these tests are about getting through obstacles, not about senses.
 */
public final class RexObstacleGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);

    private static final String WOOD_HOUSE = "trex_breaks_into_wooden_house";
    private static final String CONCRETE_HOUSE = "trex_leaves_concrete_house_alone";
    private static final String STONE_BRICK_HOUSE = "trex_breaks_into_stone_brick_house";
    private static final String SAND_HILL = "trex_climbs_sand_hill";
    private static final String PREY = "trex_eats_a_pig_when_no_player_is_near";
    private static final String PREY_YIELDS = "trex_prefers_the_player_to_a_pig";
    private static final String NO_GRIEF = "trex_respects_mob_griefing_off";
    private static final String TREES = "trex_pushes_through_trees";
    private static final String LEDGE = "trex_climbs_ledge";
    private static final String BREAKER = "trex_block_breaker_follows_the_rules";
    private static final String STUCK = "trex_stuck_detector_escalates";
    private static final String TALL_LEDGE = "trex_hops_over_tall_ledge";
    private static final String FOREST = "trex_crosses_a_forest";
    private static final String ANGLE = "trex_breaks_into_house_from_an_angle";
    private static final String FOREST_WANDER = "trex_wanders_through_a_forest";

    // House: a hollow box with a flat roof, door gap in the wall facing the rex. Offsets are from the arena origin;
    // the rex starts at z=0 and comes from lower z. The door is 1 wide, far too narrow for the 9 wide body.
    private static final int HOUSE_HALF_WIDTH = 6;
    private static final int HOUSE_FRONT_Z = 24;
    private static final int HOUSE_BACK_Z = 36;
    private static final int HOUSE_WALL_HEIGHT = 4;
    private static final int HOUSE_DOOR_HEIGHT = 2;
    private static final double PLAYER_Z = 30.5;

    // Tree line: trunks every TREE_SPACING blocks along x, canopy of leaves around the top of each trunk.
    private static final int TREE_ROW_Z = 14;
    private static final int TREE_SPACING = 4;
    private static final int TREE_COUNT_EACH_SIDE = 2;
    private static final int TREE_TRUNK_HEIGHT = 8;
    private static final int TREE_CANOPY_BOTTOM = 5;
    private static final int TREE_CANOPY_RADIUS = 2;

    // Ledge: a stone plateau across the whole arena, starting at LEDGE_FRONT_Z, LEDGE_HEIGHT blocks high.
    private static final int LEDGE_FRONT_Z = 14;
    private static final int LEDGE_HEIGHT = 3;
    private static final int TALL_LEDGE_HEIGHT = 4;

    private static final double ANGLE_REX_X = -7.5;
    private static final double ANGLE_PLAYER_X = 4.5;
    private static final double REX_START_Z = 0.5;
    // Forest: a jittered grid of trees; the canopy is a block of leaves around the top FOREST_CANOPY_DEPTH layers of each trunk.
    private static final long FOREST_SEED = 20261017L;
    private static final int FOREST_FIRST_Z = 8;
    private static final int FOREST_LAST_Z = 50;
    private static final int FOREST_SPACING = 5;
    private static final int FOREST_JITTER = 1;
    private static final int FOREST_MIN_TRUNK = 5;
    private static final int FOREST_TRUNK_VARIATION = 3;
    private static final int FOREST_CANOPY_DEPTH = 3;
    private static final int FOREST_PLAYER_CLEARANCE = 3;
    private static final int FOREST_MAX_TICKS = 2400;
    private static final int FOREST_WANDER_TICKS = 12000;
    private static final int FENCE_HEIGHT = 14;
    private static final int FOREST_WANDER_MIN_CELLS = 4;
    private static final int WANDER_CELL_SIZE = 6;
    private static final long WANDER_CELL_KEY = 1000L;
    private static final int NOISE_INTERVAL_TICKS = 10;
    private static final int LOG_INTERVAL_TICKS = 40;
    private static final int HOUSE_MAX_TICKS = 1500;
    private static final int TREES_MAX_TICKS = 1500;
    private static final int LEDGE_MAX_TICKS = 1500;
    private static final int STONE_WATCH_TICKS = 1200;
    private static final int STONE_SETTLE_TICKS = 300;
    private static final double STONE_MIN_TRAVEL = 20.0;
    private static final int NO_GRIEF_WATCH_TICKS = 600;
    private static final int BREAKER_WATCH_TICKS = 30;
    private static final int BREAKER_MAX_TICKS = 60;
    private static final double PIG_X = 0.5;
    private static final double PIG_Z = 20.5;
    private static final int PREY_MAX_TICKS = 20000;
    private static final int HUNT_PREY_MAX_TICKS = 900;
    private static final int BREAKER_ROW_LAYERS = 2;
    private static final int BREAKER_FIRST_ROW_Z = 5;
    private static final int BREAKER_ROW_HALF_WIDTH = 4;
    private static final double STUCK_TEST_DISTANCE = 50.0;
    // Rex centre z at which its front edge (4.5 blocks ahead) touches the house wall, with a little slack.
    private static final double HOUSE_WALL_CONTACT_Z = HOUSE_FRONT_Z - 4.5 - 1.5;

    static {
        FUNCTIONS.register(WOOD_HOUSE, () -> RexObstacleGameTests::breaksIntoWoodenHouse);
        FUNCTIONS.register(CONCRETE_HOUSE, () -> RexObstacleGameTests::leavesConcreteHouseAlone);
        FUNCTIONS.register(STONE_BRICK_HOUSE, () -> RexObstacleGameTests::breaksIntoStoneBrickHouse);
        FUNCTIONS.register(SAND_HILL, () -> RexObstacleGameTests::climbsSandHill);
        FUNCTIONS.register(PREY, () -> RexObstacleGameTests::eatsAPig);
        FUNCTIONS.register(PREY_YIELDS, () -> RexObstacleGameTests::prefersThePlayerToAPig);
        FUNCTIONS.register(NO_GRIEF, () -> RexObstacleGameTests::respectsMobGriefingOff);
        FUNCTIONS.register(TREES, () -> RexObstacleGameTests::pushesThroughTrees);
        FUNCTIONS.register(LEDGE, () -> RexObstacleGameTests::climbsLedge);
        FUNCTIONS.register(BREAKER, () -> RexObstacleGameTests::blockBreakerFollowsTheRules);
        FUNCTIONS.register(STUCK, () -> RexObstacleGameTests::stuckDetectorEscalates);
        FUNCTIONS.register(TALL_LEDGE, () -> RexObstacleGameTests::hopsOverTallLedge);
        FUNCTIONS.register(FOREST, () -> RexObstacleGameTests::crossesAForest);
        FUNCTIONS.register(ANGLE, () -> RexObstacleGameTests::breaksIntoHouseFromAnAngle);
        FUNCTIONS.register(FOREST_WANDER, () -> RexObstacleGameTests::wandersThroughAForest);
    }

    private RexObstacleGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexObstacleGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        RexGameTests.addTest(event, WOOD_HOUSE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, CONCRETE_HOUSE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, STONE_BRICK_HOUSE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, SAND_HILL, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, PREY, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, PREY_YIELDS, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, NO_GRIEF, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, TREES, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, LEDGE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, BREAKER, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, STUCK, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, TALL_LEDGE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, FOREST, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, ANGLE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, FOREST_WANDER, TEST_TIMEOUT_TICKS);
    }

    /** Wooden house with a too-narrow door, player inside: the rex breaks wall blocks and bites the player. */
    private static void breaksIntoWoodenHouse(GameTestHelper helper) {
        enterHouse(helper, 0.5, 0.5, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState());
    }

    /** Same house, but the rex starts off to one side and the player stands off-centre, so it arrives at an angle and not at the door. */
    private static void breaksIntoHouseFromAnAngle(GameTestHelper helper) {
        enterHouse(helper, ANGLE_REX_X, ANGLE_PLAYER_X, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState());
    }

    /** Cobblestone / stone brick houses are ordinary player structures (only terrain and a few strong materials are unbreakable). */
    private static void breaksIntoStoneBrickHouse(GameTestHelper helper) {
        enterHouse(helper, 0.5, 0.5, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
    }

    private static void enterHouse(GameTestHelper helper, double rexX, double playerX, BlockState wall, BlockState post) {
        List<BlockPos> house = new ArrayList<>();
        float[] startHealth = new float[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(playerX, 0, PLAYER_Z), false, HOUSE_MAX_TICKS,
            origin -> house.addAll(buildHouse(helper.getLevel(), origin, wall, post)),
            run -> {
                startHealth[0] = run.player.getHealth();
                run.rex.snapTo(run.helper.absoluteVec(new Vec3(rexX, 0, REX_START_Z)));
                DinoDread.LOGGER.info("[gametest] rex body: width={} height={} scale={} stepHeight={}", run.rex.getBbWidth(), run.rex.getBbHeight(), run.rex.getScale(), run.rex.maxUpStep());
            },
            run -> {
                paceTheFloor(run);
                int broken = countBroken(run, house);
                run.status = describe(run, "no wall block broken or player untouched", broken, startHealth[0]);
                logProgress(run, broken);
                if (broken > 0 && bitten(run, startHealth[0])) {
                    DinoDread.LOGGER.info("[gametest] wooden house: {} blocks broken, player bitten {} ticks after the start", broken, run.age());
                    run.pass();
                }
            });
    }

    /** A concrete house with iron block corners: both are on the unbreakable list, so nothing may break, and the rex must not just freeze at the wall. */
    private static void leavesConcreteHouseAlone(GameTestHelper helper) {
        List<BlockPos> house = new ArrayList<>();
        double[] travelled = new double[1];
        Vec3[] last = new Vec3[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z), false, STONE_WATCH_TICKS + 1,
            origin -> house.addAll(buildHouse(helper.getLevel(), origin, Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState())),
            run -> last[0] = run.rex.position(),
            run -> {
                paceTheFloor(run);
                int broken = countBroken(run, house);
                run.check(broken == 0, broken + " stone house blocks were broken; concrete and iron blocks are unbreakable");
                if (run.age() > STONE_SETTLE_TICKS) {
                    travelled[0] += run.rex.position().subtract(last[0]).horizontalDistance();
                }
                last[0] = run.rex.position();
                run.status = "rex froze at the concrete wall: travelled only " + String.format("%.1f", travelled[0]) + " blocks since tick " + STONE_SETTLE_TICKS;
                logProgress(run, broken);
                if (run.age() >= STONE_WATCH_TICKS) {
                    run.check(run.rex.isAlive(), "rex died");
                    run.check(travelled[0] >= STONE_MIN_TRAVEL, run.status);
                    run.pass();
                }
            });
    }

    /** Same wooden house as the first test, but mobGriefing is off: the rex reaches the wall and breaks nothing. */
    private static void respectsMobGriefingOff(GameTestHelper helper) {
        List<BlockPos> house = new ArrayList<>();
        boolean[] reachedWall = new boolean[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z), false, NO_GRIEF_WATCH_TICKS + 1,
            origin -> house.addAll(buildHouse(helper.getLevel(), origin, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState())),
            run -> {
                ServerLevel level = run.helper.getLevel();
                level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
                run.cleanups.add(() -> level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer()));
                run.check(!level.getGameRules().get(GameRules.MOB_GRIEFING), "test setup: mobGriefing must be off");
            },
            run -> {
                paceTheFloor(run);
                int broken = countBroken(run, house);
                run.check(broken == 0, broken + " house blocks were broken with mobGriefing off");
                reachedWall[0] |= rexZ(run) >= HOUSE_WALL_CONTACT_Z;
                run.status = "rex never reached the wall (rex z=" + String.format("%.1f", rexZ(run)) + "), so the test proves nothing";
                logProgress(run, broken);
                if (run.age() >= NO_GRIEF_WATCH_TICKS) {
                    run.check(reachedWall[0], run.status);
                    run.pass();
                }
            });
    }

    /** A dense line of trees (log trunks + leaf canopies) across the arena: the rex plows through and bites the player. */
    private static void pushesThroughTrees(GameTestHelper helper) {
        List<BlockPos> trees = new ArrayList<>();
        float[] startHealth = new float[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z), false, TREES_MAX_TICKS,
            origin -> trees.addAll(buildTreeLine(helper.getLevel(), origin)),
            run -> startHealth[0] = run.player.getHealth(),
            run -> {
                paceTheFloor(run);
                int broken = countBroken(run, trees);
                run.status = describe(run, "rex did not get through the trees to the player", broken, startHealth[0]);
                logProgress(run, broken);
                if (broken > 0 && bitten(run, startHealth[0])) {
                    DinoDread.LOGGER.info("[gametest] trees: {} blocks broken, player bitten {} ticks after the start", broken, run.age());
                    run.pass();
                }
            });
    }

    /** A 3 block stone ledge (not breakable) between rex and player: the rex has to climb it, and must not dig it away. */
    private static void climbsLedge(GameTestHelper helper) {
        climbLedge(helper, LEDGE_HEIGHT, Blocks.STONE.defaultBlockState());
    }

    /** A ledge taller than the step height (3): stepping is not enough, the stuck recovery has to hop. */
    private static void hopsOverTallLedge(GameTestHelper helper) {
        climbLedge(helper, TALL_LEDGE_HEIGHT, Blocks.STONE.defaultBlockState());
    }

    /** A hill of sand (terrain, so unbreakable): the rex climbs it, it does not tunnel through it. */
    private static void climbsSandHill(GameTestHelper helper) {
        climbLedge(helper, LEDGE_HEIGHT, Blocks.SAND.defaultBlockState());
    }

    private static void climbLedge(GameTestHelper helper, int height, BlockState material) {
        List<BlockPos> ledge = new ArrayList<>();
        float[] startHealth = new float[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, height, PLAYER_Z), false, LEDGE_MAX_TICKS,
            origin -> ledge.addAll(buildLedge(helper.getLevel(), origin, height, material)),
            run -> startHealth[0] = run.player.getHealth(),
            run -> {
                paceTheFloor(run);
                int broken = countBroken(run, ledge);
                run.check(broken == 0, broken + " ledge blocks were broken; the rex must climb, not dig through terrain");
                run.status = describe(run, "rex did not climb the ledge to the player (rex y=" + String.format("%.1f", run.rex.getY() - run.origin.getY()) + ")", broken, startHealth[0]);
                logProgress(run, broken);
                if (bitten(run, startHealth[0])) {
                    DinoDread.LOGGER.info("[gametest] ledge: player bitten {} ticks after the start, rex y={}", run.age(), run.rex.getY() - run.origin.getY());
                    run.pass();
                }
            });
    }

    /** A dense, randomly jittered forest (fixed seed) between rex and player: the rex gets through and bites the player. */
    private static void crossesAForest(GameTestHelper helper) {
        List<BlockPos> forest = new ArrayList<>();
        float[] startHealth = new float[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z), false, FOREST_MAX_TICKS,
            origin -> forest.addAll(buildForest(helper.getLevel(), origin)),
            run -> startHealth[0] = run.player.getHealth(),
            run -> {
                paceTheFloor(run);
                int broken = countBroken(run, forest);
                run.status = describe(run, "rex did not get through the forest to the player", broken, startHealth[0]);
                logProgress(run, broken);
                if (bitten(run, startHealth[0])) {
                    DinoDread.LOGGER.info("[gametest] forest: {} of {} blocks broken, player bitten {} ticks after the start", broken, forest.size(), run.age());
                    run.pass();
                }
            });
    }

    /**
     * No player: the wander goal alone must keep the 9 wide rex moving around a dense fenced forest. It passes once the rex has been
     * in {@link #FOREST_WANDER_MIN_CELLS} different {@link #WANDER_CELL_SIZE}-block squares (so it left its start and made several
     * separate trips), within a budget that is over 4x the slowest of 29 measured runs (2837 ticks; the median is ~450); a rex
     * whose wandering froze (vanilla pathfinding could not path it at all) stays in its one start square and fails.
     */
    private static void wandersThroughAForest(GameTestHelper helper) {
        Set<Long> visited = new HashSet<>();
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, FOREST_WANDER_TICKS,
            origin -> {
                fenceArena(helper.getLevel(), origin);
                buildForest(helper.getLevel(), origin);
            },
            run -> {},
            run -> {
                int cellX = Math.floorDiv((int) Math.floor(run.rex.getX() - run.origin.getX()), WANDER_CELL_SIZE);
                int cellZ = Math.floorDiv((int) Math.floor(rexZ(run)), WANDER_CELL_SIZE);
                visited.add((long) cellX * WANDER_CELL_KEY + cellZ);
                run.status = "rex only visited " + visited.size() + " of the " + FOREST_WANDER_MIN_CELLS + " areas it needed (alive=" + run.rex.isAlive()
                    + ", x=" + String.format("%.1f", run.rex.getX() - run.origin.getX()) + ", z=" + String.format("%.1f", rexZ(run)) + ")";
                if (visited.size() >= FOREST_WANDER_MIN_CELLS) {
                    DinoDread.LOGGER.info("[gametest] forest wander: {} different areas visited {} ticks after the start", visited.size(), run.age());
                    run.pass();
                }
            });
    }

    /**
     * Unit-level check of {@link RexBlockBreaker} on a rex with its AI off. In two rows (z=5 and z=6) just in front of it, player-structure
     * materials (wood, glass, leaves, cobblestone, bricks, wool, ...) go, while terrain (stone, dirt, ore, snow, natural sandstone; sand falls when placed in mid-air, so it has its own hill test),
     * the deliberately unbreakable materials (smooth stone, obsidian, iron block, concrete, End blocks), bedrock, a chest (block entity)
     * stay; replaceable blocks (plants, snow layers, fluids) are never breakable; so is wood beyond the break reach and a plank under its feet.
     */
    private static void blockBreakerFollowsTheRules(GameTestHelper helper) {
        List<BlockState> breakables = List.of(
            Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
            Blocks.OAK_LOG.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState(),
            Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.CRAFTING_TABLE.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState());
        List<BlockState> unbreakables = List.of(
            Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.SANDSTONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(),
            Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState(), Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState(),
            Blocks.END_STONE.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.CHEST.defaultBlockState(), Blocks.BEDROCK.defaultBlockState());
        // Offsets from the arena origin; the rex (9 wide) stands at (0.5, 0, 0.5), so its front edge is at z=5 and the default reach is 1.5.
        List<BlockPos> slots = new ArrayList<>();
        for (int y = 1; y <= BREAKER_ROW_LAYERS; y++) {
            for (int z = BREAKER_FIRST_ROW_Z; z <= BREAKER_FIRST_ROW_Z + 1; z++) {
                for (int x = -BREAKER_ROW_HALF_WIDTH; x <= BREAKER_ROW_HALF_WIDTH; x++) {
                    slots.add(new BlockPos(x, y, z));
                }
            }
        }
        Map<BlockPos, BlockState> shouldBreak = new LinkedHashMap<>();
        Map<BlockPos, BlockState> shouldStay = new LinkedHashMap<>();
        for (int i = 0; i < breakables.size(); i++) {
            shouldBreak.put(slots.get(i), breakables.get(i));
        }
        for (int i = 0; i < unbreakables.size(); i++) {
            shouldStay.put(slots.get(breakables.size() + i), unbreakables.get(i));
        }
        if (breakables.size() + unbreakables.size() > slots.size()) {
            helper.fail("test setup: more materials than slots");
        }
        shouldStay.put(new BlockPos(0, 1, BREAKER_FIRST_ROW_Z + 2), Blocks.OAK_PLANKS.defaultBlockState());
        shouldStay.put(new BlockPos(-BREAKER_ROW_HALF_WIDTH, -1, 0), Blocks.OAK_PLANKS.defaultBlockState());
        RexBlockBreaker[] breaker = new RexBlockBreaker[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, BREAKER_MAX_TICKS,
            origin -> {
                ServerLevel level = helper.getLevel();
                shouldBreak.forEach((offset, state) -> level.setBlock(origin.offset(offset), state, Block.UPDATE_CLIENTS));
                shouldStay.forEach((offset, state) -> level.setBlock(origin.offset(offset), state, Block.UPDATE_CLIENTS));
            },
            run -> {
                run.rex.setNoAi(true);
                breaker[0] = new RexBlockBreaker(run.rex);
            },
            run -> {
                breaker[0].tick(new Vec3(0.0, 0.0, 1.0), true);
                run.status = "breaker test still running";
                if (run.age() >= BREAKER_WATCH_TICKS) {
                    ServerLevel level = run.helper.getLevel();
                    shouldBreak.forEach((offset, state) ->
                        run.check(level.getBlockState(run.origin.offset(offset)).isAir(), state.getBlock() + " at offset " + offset + " should have been broken"));
                    shouldStay.forEach((offset, state) ->
                        run.check(level.getBlockState(run.origin.offset(offset)).is(state.getBlock()), state.getBlock() + " at offset " + offset + " should NOT have been broken"));
                    for (BlockState replaceable : List.of(Blocks.SHORT_GRASS.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.SNOW.defaultBlockState(), Blocks.FIRE.defaultBlockState())) {
                        run.check(!breaker[0].isBreakable(level, run.origin, replaceable), replaceable.getBlock() + " is replaceable and must never be a break candidate");
                    }
                    run.pass();
                }
            });
    }

    /**
     * Pure logic test of {@link RexStuckDetector}, no entity: a destination that never gets closer escalates FREE, BREAK, HOP, SIDESTEP,
     * GIVE_UP at the configured thresholds (levels only change on check ticks); the sidestep direction alternates; real progress,
     * {@code markProgress()} and {@code reset()} all bring it back to FREE.
     */
    private static void stuckDetectorEscalates(GameTestHelper helper) {
        int check = RexConfig.STUCK_CHECK_INTERVAL_TICKS.get();
        int breakAfter = RexConfig.STUCK_BREAK_AFTER_TICKS.get();
        int hopAfter = RexConfig.STUCK_HOP_AFTER_TICKS.get();
        int sidestepAfter = RexConfig.STUCK_SIDESTEP_AFTER_TICKS.get();
        int giveUpAfter = RexConfig.STUCK_GIVE_UP_AFTER_TICKS.get();
        int switchTicks = RexConfig.STUCK_SIDESTEP_SWITCH_TICKS.get();
        if (!(breakAfter < hopAfter && hopAfter < sidestepAfter && sidestepAfter < giveUpAfter)) {
            helper.fail("test setup: stuck thresholds must be increasing in the config");
        }

        RexStuckDetector detector = new RexStuckDetector();
        for (int tick = 1; tick <= giveUpAfter + 2 * check; tick++) {
            Level level = detector.tick(STUCK_TEST_DISTANCE);
            int stuckTicks = tick / check * check;
            Level expected = stuckTicks >= giveUpAfter ? Level.GIVE_UP : stuckTicks >= sidestepAfter ? Level.SIDESTEP
                : stuckTicks >= hopAfter ? Level.HOP : stuckTicks >= breakAfter ? Level.BREAK : Level.FREE;
            if (level != expected) {
                helper.fail("tick " + tick + ": expected " + expected + " but got " + level);
            }
            if (level == Level.SIDESTEP) {
                double expectedSign = (stuckTicks - sidestepAfter) / switchTicks % 2 == 0 ? 1.0 : -1.0;
                if (detector.getSidestepSign() != expectedSign) {
                    helper.fail("tick " + tick + ": sidestep sign should be " + expectedSign + " but is " + detector.getSidestepSign());
                }
            }
        }
        if (detector.getLevel() != Level.GIVE_UP) {
            helper.fail("getLevel() should report GIVE_UP after being stuck for a long time, got " + detector.getLevel());
        }

        // Closing in by clearly more than the minimum progress at every check never counts as stuck.
        RexStuckDetector moving = new RexStuckDetector();
        double distance = STUCK_TEST_DISTANCE;
        for (int tick = 1; tick <= giveUpAfter * 2; tick++) {
            if (tick % check == 0) {
                distance -= 2.0 * RexConfig.STUCK_MIN_PROGRESS.get();
            }
            if (moving.tick(distance) != Level.FREE) {
                helper.fail("tick " + tick + ": a rex that keeps closing in must stay FREE, got " + moving.getLevel());
            }
        }

        // markProgress() and reset() both drop straight back to FREE and need a fresh stretch of being stuck to escalate again.
        RexStuckDetector other = new RexStuckDetector();
        for (int tick = 1; tick <= hopAfter; tick++) {
            other.tick(STUCK_TEST_DISTANCE);
        }
        if (other.getLevel().ordinal() < Level.HOP.ordinal()) {
            helper.fail("test setup: should be at least HOP after " + hopAfter + " stuck ticks, got " + other.getLevel());
        }
        other.markProgress();
        if (other.tick(STUCK_TEST_DISTANCE) != Level.FREE) {
            helper.fail("markProgress() should bring the level back to FREE, got " + other.getLevel());
        }
        for (int tick = 1; tick <= hopAfter; tick++) {
            other.tick(STUCK_TEST_DISTANCE);
        }
        other.reset();
        if (other.getLevel() != Level.FREE || other.tick(STUCK_TEST_DISTANCE) != Level.FREE) {
            helper.fail("reset() should bring the level back to FREE, got " + other.getLevel());
        }
        helper.succeed();
    }

    /**
     * No player anywhere: sooner or later the rex goes for a pig and kills it (it is "eating"). The arena is fenced so neither can wander off
     * into unloaded chunks (a rex out there is frozen). Measured over 13 samples: eaten after 94-4711 ticks (median ~850), so 20000 is over 4x the worst.
     */
    private static void eatsAPig(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, PREY_MAX_TICKS,
            origin -> fenceArena(helper.getLevel(), origin),
            run -> pig[0] = spawnPig(run),
            run -> {
                run.status = "rex never ate the pig (pig alive=" + pig[0].isAlive() + ", distance=" + String.format("%.1f", run.rex.distanceTo(pig[0])) + ")";
                if (!pig[0].isAlive()) {
                    DinoDread.LOGGER.info("[gametest] prey: pig eaten {} ticks after the start", run.age());
                    run.pass();
                }
            });
    }

    /** With a player in view the rex hunts the player, not the pig standing between them: the pig is still alive when the player is bitten. */
    private static void prefersThePlayerToAPig(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        float[] startHealth = new float[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_Z), false, HUNT_PREY_MAX_TICKS,
            origin -> {},
            run -> {
                startHealth[0] = run.player.getHealth();
                pig[0] = spawnPig(run);
            },
            run -> {
                run.status = "player untouched after " + run.age() + " ticks";
                if (!pig[0].isAlive()) {
                    run.fail("the rex ate the pig instead of hunting the player");
                }
                if (bitten(run, startHealth[0])) {
                    run.check(pig[0].isAlive(), "the rex killed the pig before reaching the player");
                    run.pass();
                }
            });
    }

    private static Pig spawnPig(Run run) {
        return run.helper.spawnEntity(EntityTypes.PIG, new Vec3(PIG_X, 0, PIG_Z)).requirePersistence(true).spawn();
    }

    // ---- scenario helpers ----

    /** The player "walks around": a step noise now and then keeps the rex informed even when it cannot see the player. */
    private static void paceTheFloor(Run run) {
        if (run.age() % NOISE_INTERVAL_TICKS == 0) {
            RexGameTests.stepNoise(run);
        }
    }

    private static boolean bitten(Run run, float startHealth) {
        return run.player.getHealth() < startHealth || !run.player.isAlive();
    }

    private static double rexZ(Run run) {
        return run.rex.getZ() - run.origin.getZ();
    }

    private static int countBroken(Run run, List<BlockPos> structure) {
        ServerLevel level = run.helper.getLevel();
        int broken = 0;
        for (BlockPos pos : structure) {
            if (level.getBlockState(pos).isAir()) {
                broken++;
            }
        }
        return broken;
    }

    private static String describe(Run run, String problem, int broken, float startHealth) {
        return problem + " (broken=" + broken + ", playerHealth=" + run.player.getHealth() + "/" + startHealth + ", rex z=" + String.format("%.1f", rexZ(run)) + ")";
    }

    private static void logProgress(Run run, int broken) {
        if (run.age() % LOG_INTERVAL_TICKS == 0) {
            DinoDread.LOGGER.info("[gametest] tick {} state={} rexZ={} rexY={} broken={} playerHealth={}", run.age(), run.rex.getHuntState(),
                String.format("%.1f", rexZ(run)), String.format("%.1f", run.rex.getY() - run.origin.getY()), broken, run.player.getHealth());
        }
    }

    // ---- structures; every returned list holds the positions of blocks that were placed and may later be broken ----

    private static void place(ServerLevel level, List<BlockPos> placed, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        placed.add(pos);
    }

    /** Hollow box with a flat roof; corner posts use {@code post}, walls and roof {@code wall}; 1 wide door gap in the front wall. */
    private static List<BlockPos> buildHouse(ServerLevel level, BlockPos origin, BlockState wall, BlockState post) {
        List<BlockPos> placed = new ArrayList<>();
        for (int x = -HOUSE_HALF_WIDTH; x <= HOUSE_HALF_WIDTH; x++) {
            for (int z = HOUSE_FRONT_Z; z <= HOUSE_BACK_Z; z++) {
                boolean edgeX = Math.abs(x) == HOUSE_HALF_WIDTH;
                boolean edgeZ = z == HOUSE_FRONT_Z || z == HOUSE_BACK_Z;
                if (edgeX || edgeZ) {
                    for (int y = 0; y < HOUSE_WALL_HEIGHT; y++) {
                        boolean door = x == 0 && z == HOUSE_FRONT_Z && y < HOUSE_DOOR_HEIGHT;
                        if (!door) {
                            place(level, placed, origin.offset(x, y, z), edgeX && edgeZ ? post : wall);
                        }
                    }
                }
                place(level, placed, origin.offset(x, HOUSE_WALL_HEIGHT, z), wall);
            }
        }
        return placed;
    }

    private static List<BlockPos> buildTreeLine(ServerLevel level, BlockPos origin) {
        List<BlockPos> placed = new ArrayList<>();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int i = -TREE_COUNT_EACH_SIDE; i <= TREE_COUNT_EACH_SIDE; i++) {
            int trunkX = i * TREE_SPACING;
            for (int y = 0; y < TREE_TRUNK_HEIGHT; y++) {
                place(level, placed, origin.offset(trunkX, y, TREE_ROW_Z), log);
            }
            for (int y = TREE_CANOPY_BOTTOM; y < TREE_TRUNK_HEIGHT + 1; y++) {
                for (int dx = -TREE_CANOPY_RADIUS; dx <= TREE_CANOPY_RADIUS; dx++) {
                    for (int dz = -TREE_CANOPY_RADIUS; dz <= TREE_CANOPY_RADIUS; dz++) {
                        BlockPos pos = origin.offset(trunkX + dx, y, TREE_ROW_Z + dz);
                        if (!placed.contains(pos)) {
                            place(level, placed, pos, leaves);
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** Jittered grid of trees (trunk height and canopy vary), none within clear radius of the rex start or the player. */
    private static List<BlockPos> buildForest(ServerLevel level, BlockPos origin) {
        List<BlockPos> placed = new ArrayList<>();
        Random random = new Random(FOREST_SEED);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int gridZ = FOREST_FIRST_Z; gridZ <= FOREST_LAST_Z; gridZ += FOREST_SPACING) {
            for (int gridX = -RexGameTests.ARENA_HALF_WIDTH + 1; gridX <= RexGameTests.ARENA_HALF_WIDTH - 1; gridX += FOREST_SPACING) {
                int x = gridX + random.nextInt(2 * FOREST_JITTER + 1) - FOREST_JITTER;
                int z = gridZ + random.nextInt(2 * FOREST_JITTER + 1) - FOREST_JITTER;
                int trunk = FOREST_MIN_TRUNK + random.nextInt(FOREST_TRUNK_VARIATION + 1);
                if (Math.abs(z - PLAYER_Z) < FOREST_PLAYER_CLEARANCE && Math.abs(x) < FOREST_PLAYER_CLEARANCE) {
                    continue;
                }
                for (int y = 0; y < trunk; y++) {
                    place(level, placed, origin.offset(x, y, z), log);
                }
                for (int y = trunk - FOREST_CANOPY_DEPTH; y <= trunk; y++) {
                    for (int dx = -TREE_CANOPY_RADIUS; dx <= TREE_CANOPY_RADIUS; dx++) {
                        for (int dz = -TREE_CANOPY_RADIUS; dz <= TREE_CANOPY_RADIUS; dz++) {
                            BlockPos pos = origin.offset(x + dx, y, z + dz);
                            if (level.getBlockState(pos).isAir() && Math.abs(x + dx) <= RexGameTests.ARENA_HALF_WIDTH) {
                                place(level, placed, pos, leaves);
                            }
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** Stone walls just outside the arena so a wandering rex stays inside it (beyond the arena is ordinary generated terrain). */
    private static void fenceArena(ServerLevel level, BlockPos origin) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        int minX = -RexGameTests.ARENA_HALF_WIDTH - 1;
        int maxX = RexGameTests.ARENA_HALF_WIDTH + 1;
        int minZ = -RexGameTests.ARENA_BACK - 1;
        int maxZ = RexGameTests.ARENA_FRONT + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x == minX || x == maxX || z == minZ || z == maxZ) {
                    for (int y = -1; y < FENCE_HEIGHT; y++) {
                        level.setBlock(origin.offset(x, y, z), stone, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static List<BlockPos> buildLedge(ServerLevel level, BlockPos origin, int height, BlockState material) {
        List<BlockPos> placed = new ArrayList<>();
        for (int x = -RexGameTests.ARENA_HALF_WIDTH; x <= RexGameTests.ARENA_HALF_WIDTH; x++) {
            for (int z = LEDGE_FRONT_Z; z <= RexGameTests.ARENA_FRONT; z++) {
                for (int y = 0; y < height; y++) {
                    place(level, placed, origin.offset(x, y, z), material);
                }
            }
        }
        return placed;
    }
}
