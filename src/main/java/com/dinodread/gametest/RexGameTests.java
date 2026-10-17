package com.dinodread.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.dinodread.DinoDread;
import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
import com.dinodread.entity.ai.HuntState;
import com.dinodread.registry.ModEntities;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Server-side T-rex behavior tests, run by {@code .\gradlew runGameTestServer} (the {@code gameTestServer} run).
 * They only fire when game tests are enabled, so this costs nothing in a normal game.
 * <p>
 * Each test gets its own environment so the test runner puts it in its own batch: tests in one batch run side by
 * side in the same world, and these arenas are large and must not see each other's players.
 * <p>
 * Chunk readiness: the test world is generated on demand at random far-away coordinates, and the tick loop runs flat
 * out, so a chunk can be loaded and "forced" yet still not have its entities ticking for a while. A T-rex standing
 * in such a chunk is frozen (nothing, including its AI, runs). An earlier version of these tests spawned the rex
 * straight away and failed about one run in ten when the rex walked into such a chunk. So the arena's chunks are
 * forced before anything is built, and a scenario only starts once every arena chunk is entity-ticking.
 */
public final class RexGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);

    private static final String HUNT = "trex_hunts_player";
    private static final String WANDER = "trex_wanders";
    private static final String HEARS = "trex_hears_footsteps";
    private static final String SNEAK = "trex_barely_hears_sneaking";
    private static final String DAYLIGHT = "trex_dulled_by_daylight";

    // Flat stone arena, in blocks around the test origin: x in [-ARENA_HALF_WIDTH, +], z in [-ARENA_BACK, ARENA_FRONT].
    static final int ARENA_HALF_WIDTH = 12;
    static final int ARENA_BACK = 12;
    static final int ARENA_FRONT = 60;
    private static final int ARENA_HEIGHT = 16;
    private static final int LOG_INTERVAL_TICKS = 20;
    private static final double WANDER_MIN_DISTANCE = 6.0;
    static final float FACING_SOUTH = 0.0F;
    private static final float FACING_NORTH = 180.0F;
    // The tick loop runs flat out (thousands of ticks a second) while chunk entities load on an IO thread, so the wait
    // for the arena is paced and limited in wall-clock time, not ticks.
    static final int TEST_TIMEOUT_TICKS = 200_000;
    private static final long ARENA_READY_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long ARENA_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static final int HUNT_MAX_TICKS = 900;
    private static final int WANDER_MAX_TICKS = 2400;
    private static final int LISTEN_MAX_TICKS = 200;
    private static final double HEARS_MIN_APPROACH = 3.0;
    private static final double PLAYER_BEHIND_DISTANCE = 40.0;
    private static final double PLAYER_NEAR_DISTANCE = 15.0;
    // Overworld day cycle clock values: 6000 is noon, 18000 is midnight (data/minecraft/timeline/day.json).
    private static final long NOON_TICKS = 6000L;
    static final long MIDNIGHT_TICKS = 18000L;
    // After the clock is set, the environment attributes need a tick or two before the sky light factor follows.
    private static final int CLOCK_SETTLE_TICKS = 3;
    private static final double DAYLIGHT_TEST_DISTANCE = 35.0;
    private static final int DAYLIGHT_WATCH_TICKS = 20;
    private static final int DAYLIGHT_MAX_TICKS = 60;

    static {
        FUNCTIONS.register(HUNT, () -> RexGameTests::huntsPlayer);
        FUNCTIONS.register(WANDER, () -> RexGameTests::wanders);
        FUNCTIONS.register(HEARS, () -> RexGameTests::hearsFootsteps);
        FUNCTIONS.register(SNEAK, () -> RexGameTests::barelyHearsSneaking);
        FUNCTIONS.register(DAYLIGHT, () -> RexGameTests::daylightDullsSenses);
    }

    private RexGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Only a backstop: the wait for the arena is limited in wall-clock time and each scenario by its own tick budget.
        addTest(event, HUNT, TEST_TIMEOUT_TICKS);
        addTest(event, WANDER, TEST_TIMEOUT_TICKS);
        addTest(event, HEARS, TEST_TIMEOUT_TICKS);
        addTest(event, SNEAK, TEST_TIMEOUT_TICKS);
        addTest(event, DAYLIGHT, TEST_TIMEOUT_TICKS);
    }

    static void addTest(RegisterGameTestsEvent event, String name, int maxTicks) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id(name));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Identifier.withDefaultNamespace("empty"), maxTicks, 0, true);
        event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)), data));
    }

    static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(DinoDread.MODID, name);
    }

    /** On flat open ground a T-rex that can see a survival player stalks, charges and bites it. */
    private static void huntsPlayer(GameTestHelper helper) {
        float[] startHealth = new float[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, 30.5), false, HUNT_MAX_TICKS,
            run -> startHealth[0] = run.player.getHealth(),
            run -> {
                run.status = "player untouched; rex state=" + run.rex.getHuntState() + " distance=" + String.format("%.1f", run.rex.distanceTo(run.player));
                if (run.age() % LOG_INTERVAL_TICKS == 0) {
                    DinoDread.LOGGER.info("[gametest] tick {} state={} distance={} playerHealth={} rexAge={} entityTicking={}", run.age(), run.rex.getHuntState(),
                        String.format("%.1f", run.rex.distanceTo(run.player)), run.player.getHealth(), run.rex.tickCount, run.isRexChunkTicking());
                }
                if (run.player.getHealth() < startHealth[0] || !run.player.isAlive()) {
                    DinoDread.LOGGER.info("[gametest] hunt finished {} ticks after the start: playerHealth={} alive={}", run.age(), run.player.getHealth(), run.player.isAlive());
                    run.pass();
                }
            });
    }

    /** With nobody around, the T-rex still roams. */
    private static void wanders(GameTestHelper helper) {
        Vec3[] start = new Vec3[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, WANDER_MAX_TICKS,
            run -> start[0] = run.rex.position(),
            run -> {
                double moved = run.rex.position().subtract(start[0]).horizontalDistance();
                run.status = "rex only wandered " + String.format("%.1f", moved) + " blocks";
                if (moved >= WANDER_MIN_DISTANCE) {
                    DinoDread.LOGGER.info("[gametest] wander moved {} blocks {} ticks after the start", String.format("%.1f", moved), run.age());
                    run.pass();
                }
            });
    }

    /**
     * A rex facing away cannot see a player behind it, but hears them walk. The noise is made in the same tick the rex
     * spawns, before its AI has run once, so the first checks cannot race the rex turning round and spotting the player.
     * It must then go and investigate: head for the noise from a standing start.
     */
    private static void hearsFootsteps(GameTestHelper helper) {
        double[] startDistance = new double[1];
        boolean[] investigated = new boolean[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_NORTH, new Vec3(0.5, 0, PLAYER_BEHIND_DISTANCE + 0.5), false, LISTEN_MAX_TICKS,
            run -> {
                run.check(run.rex.distanceTo(run.player) < RexConfig.HEARING_RADIUS.get(), "test setup: player must be inside the hearing radius");
                run.check(!run.rex.getSenses().hasQuarry(), "test setup: rex must start with no quarry");
                stepNoise(run);
                run.check(run.rex.getTarget() == null, "rex should not have seen the player behind it");
                run.check(run.rex.getSenses().hasQuarry(), "rex should have heard the footstep");
                startDistance[0] = run.rex.distanceTo(run.player);
            },
            run -> {
                investigated[0] |= run.rex.getHuntState() == HuntState.INVESTIGATE;
                double approached = startDistance[0] - run.rex.distanceTo(run.player);
                run.status = "heard the noise but did not go for it: investigated=" + investigated[0] + " approached=" + String.format("%.1f", approached) + " blocks";
                if (investigated[0] && approached >= HEARS_MIN_APPROACH) {
                    run.pass();
                }
            });
    }

    /** Sneaking shrinks the hearing radius: inaudible at 40 blocks, audible at 15. Both noises are made in the spawn tick. */
    private static void barelyHearsSneaking(GameTestHelper helper) {
        scenario(helper, MIDNIGHT_TICKS, FACING_NORTH, new Vec3(0.5, 0, PLAYER_BEHIND_DISTANCE + 0.5), true, LISTEN_MAX_TICKS,
            run -> {
                double sneakRadius = RexConfig.HEARING_RADIUS.get() * RexConfig.SNEAK_HEARING_MULTIPLIER.get();
                run.check(PLAYER_BEHIND_DISTANCE > sneakRadius, "test setup: the far position must be outside the sneak hearing radius");
                run.check(PLAYER_NEAR_DISTANCE < sneakRadius, "test setup: the near position must be inside the sneak hearing radius");
                run.check(!run.rex.getSenses().hasQuarry(), "test setup: rex must start with no quarry");
                stepNoise(run);
                run.check(!run.rex.getSenses().hasQuarry(), "a sneaking player 40 blocks away should not be heard");
                run.player.snapTo(helper.absoluteVec(new Vec3(0.5, 0, PLAYER_NEAR_DISTANCE + 0.5)));
                stepNoise(run);
                run.check(run.rex.getTarget() == null, "rex should not have seen the player behind it");
                run.check(run.rex.getSenses().hasQuarry(), "a sneaking player 15 blocks away should be heard");
                run.pass();
            },
            run -> {});
    }

    /**
     * In daylight the T-rex is calmer: a player in view but beyond the reduced daytime sight range is not seen, and a
     * noise beyond the reduced daytime hearing radius is not heard, although both would be in range at night.
     */
    private static void daylightDullsSenses(GameTestHelper helper) {
        scenario(helper, NOON_TICKS, FACING_SOUTH, new Vec3(0.5, 0, DAYLIGHT_TEST_DISTANCE + 0.5), false, DAYLIGHT_MAX_TICKS,
            run -> {
                double dayAggression = RexConfig.DAY_AGGRESSION.get();
                run.check(DAYLIGHT_TEST_DISTANCE < RexConfig.SIGHT_RANGE.get(), "test setup: the player must be inside the night sight range");
                run.check(DAYLIGHT_TEST_DISTANCE > RexConfig.SIGHT_RANGE.get() * dayAggression, "test setup: the player must be outside the daytime sight range");
                run.check(DAYLIGHT_TEST_DISTANCE < RexConfig.HEARING_RADIUS.get(), "test setup: the noise must be inside the night hearing radius");
                run.check(DAYLIGHT_TEST_DISTANCE > RexConfig.HEARING_RADIUS.get() * dayAggression, "test setup: the noise must be outside the daytime hearing radius");
                run.check(!run.rex.getSenses().hasQuarry(), "test setup: rex must start with no quarry");
                stepNoise(run);
                run.check(!run.rex.getSenses().hasQuarry(), "in daylight a noise beyond the reduced hearing radius should not be heard");
            },
            run -> {
                run.status = "daytime sight check";
                run.check(!run.rex.getSenses().hasQuarry(), "in daylight a player beyond the reduced sight range should not be seen");
                if (run.age() >= DAYLIGHT_WATCH_TICKS) {
                    run.pass();
                }
            });
    }

    static void stepNoise(Run run) {
        run.helper.getLevel().gameEvent(GameEvent.STEP, run.player.position(), GameEvent.Context.of(run.player));
    }

    /**
     * Builds the arena, waits (in real time) until every arena chunk is entity-ticking, spawns the rex (and the player, if
     * given), runs {@code onStart} once, then runs {@code step} every tick until it calls {@link Run#pass()} or
     * {@code maxTicks} ticks have passed since the start.
     */
    private static void scenario(GameTestHelper helper, long clockTicks, float rexYaw, @Nullable Vec3 playerPos, boolean playerSneaking, int maxTicks, Consumer<Run> onStart, Consumer<Run> step) {
        scenario(helper, clockTicks, rexYaw, playerPos, playerSneaking, maxTicks, origin -> {}, onStart, step);
    }

    /** As above, but {@code structure} gets the arena origin right after the flat arena is built, to add obstacles. */
    static void scenario(GameTestHelper helper, long clockTicks, float rexYaw, @Nullable Vec3 playerPos, boolean playerSneaking, int maxTicks, Consumer<BlockPos> structure, Consumer<Run> onStart, Consumer<Run> step) {
        BlockPos origin = buildArena(helper);
        structure.accept(origin);
        helper.setTime(clockTicks);
        Run run = new Run(helper, origin);
        long waitStart = System.nanoTime();
        helper.onEachTick(() -> {
            if (run.done) {
                return;
            }

            if (run.rex == null) {
                if (helper.getTick() < CLOCK_SETTLE_TICKS) {
                    return;
                }
                if (!arenaReady(helper.getLevel(), origin)) {
                    if (System.nanoTime() - waitStart > ARENA_READY_TIMEOUT_NANOS) {
                        run.fail("arena chunks never became entity-ticking: " + describeArena(helper.getLevel(), origin));
                    }
                    LockSupport.parkNanos(ARENA_POLL_NANOS);
                    return;
                }

                DinoDread.LOGGER.info("[gametest] arena ready after {} ticks, {} ms", helper.getTick(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStart));
                run.rex = spawnRex(helper, rexYaw);
                if (playerPos != null) {
                    run.player = spawnPlayer(helper, playerPos);
                    run.player.setShiftKeyDown(playerSneaking);
                }
                run.startTick = helper.getTick();
                onStart.accept(run);
                return;
            }

            if (run.age() > maxTicks) {
                run.fail(run.status);
                return;
            }

            step.accept(run);
        });
        helper.runBeforeTestEnd(() -> {
            if (!run.done) {
                run.fail("test backstop timeout; " + run.status);
            }
        });
    }

    /** State of one running test. */
    static final class Run {
        final List<Runnable> cleanups = new ArrayList<>();
        final GameTestHelper helper;
        final BlockPos origin;
        @Nullable TRex rex;
        @Nullable ServerPlayer player;
        long startTick;
        boolean done;
        String status = "timed out";

        Run(GameTestHelper helper, BlockPos origin) {
            this.helper = helper;
            this.origin = origin;
        }

        long age() {
            return helper.getTick() - startTick;
        }

        boolean isRexChunkTicking() {
            return helper.getLevel().isPositionEntityTicking(rex.blockPosition());
        }

        void check(boolean condition, String message) {
            if (!condition) {
                fail(message);
            }
        }

        void pass() {
            cleanUp();
            helper.succeed();
        }

        void fail(String message) {
            cleanUp();
            helper.fail(message);
        }

        private void cleanUp() {
            done = true;
            cleanups.forEach(Runnable::run);
            if (rex != null) {
                rex.discard();
            }
            if (player != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
    }

    private static List<ChunkPos> arenaChunks(BlockPos origin) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int chunkX = SectionPos.blockToSectionCoord(origin.getX() - ARENA_HALF_WIDTH); chunkX <= SectionPos.blockToSectionCoord(origin.getX() + ARENA_HALF_WIDTH); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(origin.getZ() - ARENA_BACK); chunkZ <= SectionPos.blockToSectionCoord(origin.getZ() + ARENA_FRONT); chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    /** True once every arena chunk is loaded, entity-ticking, and has its entities loaded. */
    private static boolean arenaReady(ServerLevel level, BlockPos origin) {
        for (ChunkPos chunk : arenaChunks(origin)) {
            if (!level.isPositionEntityTicking(chunk.getMiddleBlockPosition(origin.getY())) || !level.areEntitiesActuallyLoadedAndTicking(chunk)) {
                return false;
            }
        }
        return true;
    }

    private static String describeArena(ServerLevel level, BlockPos origin) {
        StringBuilder text = new StringBuilder();
        for (ChunkPos chunk : arenaChunks(origin)) {
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            text.append(chunk).append('=').append(loaded == null ? "NOT LOADED" : loaded.getFullStatus().name())
                .append(" forced=").append(level.getForceLoadedChunks().contains(chunk.pack()))
                .append(" entitiesTicking=").append(level.areEntitiesActuallyLoadedAndTicking(chunk)).append("; ");
        }
        return text.toString();
    }

    /** Flat stone arena. The chunks are forced first, then built, so they are never loaded without a ticket. */
    private static BlockPos buildArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (ChunkPos chunk : arenaChunks(origin)) {
            level.setChunkForced(chunk.x(), chunk.z(), true);
        }

        for (int x = -ARENA_HALF_WIDTH; x <= ARENA_HALF_WIDTH; x++) {
            for (int z = -ARENA_BACK; z <= ARENA_FRONT; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y < ARENA_HEIGHT; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return origin;
    }

    private static TRex spawnRex(GameTestHelper helper, float yRot) {
        TRex rex = helper.spawnEntity(ModEntities.TREX.get(), new Vec3(0.5, 0, 0.5)).requirePersistence(true).spawn();
        rex.setYRot(yRot);
        rex.setYHeadRot(yRot);
        rex.setYBodyRot(yRot);
        return rex;
    }

    /** A real survival ServerPlayer on a dummy connection (same recipe the vanilla test helper uses for its mock player). */
    private static ServerPlayer spawnPlayer(GameTestHelper helper, Vec3 relativePos) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "dread-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(relativePos));
        return player;
    }
}
