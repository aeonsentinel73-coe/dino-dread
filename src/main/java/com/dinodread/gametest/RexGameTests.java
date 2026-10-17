package com.dinodread.gametest;

import java.util.UUID;
import java.util.function.Consumer;

import com.dinodread.DinoDread;
import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Server-side T-rex behavior tests, run by {@code .\gradlew runGameTestServer} (the {@code gameTestServer} run).
 * They only fire when game tests are enabled, so this costs nothing in a normal game.
 * Each test gets its own environment so the test runner puts it in its own batch: tests in one batch run side by
 * side in the same world, and these arenas are large and must not see each other's players.
 */
public final class RexGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);

    private static final String HUNT = "trex_hunts_player";
    private static final String WANDER = "trex_wanders";
    private static final String HEARS = "trex_hears_footsteps";
    private static final String SNEAK = "trex_barely_hears_sneaking";

    // Flat stone arena, in blocks around the test origin: x in [-ARENA_HALF_WIDTH, +], z in [-ARENA_BACK, ARENA_FRONT].
    private static final int ARENA_HALF_WIDTH = 12;
    private static final int ARENA_BACK = 12;
    private static final int ARENA_FRONT = 60;
    private static final int ARENA_HEIGHT = 16;
    private static final int LOG_INTERVAL_TICKS = 20;
    private static final double WANDER_MIN_DISTANCE = 6.0;
    private static final float FACING_SOUTH = 0.0F;
    private static final float FACING_NORTH = 180.0F;

    static {
        FUNCTIONS.register(HUNT, () -> RexGameTests::huntsPlayer);
        FUNCTIONS.register(WANDER, () -> RexGameTests::wanders);
        FUNCTIONS.register(HEARS, () -> RexGameTests::hearsFootsteps);
        FUNCTIONS.register(SNEAK, () -> RexGameTests::barelyHearsSneaking);
    }

    private RexGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        addTest(event, HUNT, 900);
        addTest(event, WANDER, 2400);
        addTest(event, HEARS, 100);
        addTest(event, SNEAK, 100);
    }

    private static void addTest(RegisterGameTestsEvent event, String name, int maxTicks) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id(name));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Identifier.withDefaultNamespace("empty"), maxTicks, 0, true);
        event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)), data));
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(DinoDread.MODID, name);
    }

    /** On flat open ground a T-rex that can see a survival player stalks, charges and bites it. */
    private static void huntsPlayer(GameTestHelper helper) {
        buildArena(helper);
        TRex rex = spawnRex(helper, FACING_SOUTH);
        ServerPlayer player = spawnPlayer(helper, new Vec3(0.5, 0, 30.5));
        float startHealth = player.getHealth();

        helper.onEachTick(() -> {
            if (helper.getTick() % LOG_INTERVAL_TICKS == 0) {
                DinoDread.LOGGER.info("[gametest] tick {} state={} distance={} playerHealth={}",
                    helper.getTick(), rex.getHuntState(), String.format("%.1f", rex.distanceTo(player)), player.getHealth());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(player.getHealth() < startHealth || !player.isAlive(),
                "player untouched; rex state=" + rex.getHuntState() + " distance=" + rex.distanceTo(player));
            DinoDread.LOGGER.info("[gametest] hunt finished at tick {}: playerHealth={} alive={}", helper.getTick(), player.getHealth(), player.isAlive());
            cleanUp(helper, rex, player);
        });
    }

    /** With nobody around, the T-rex still roams. */
    private static void wanders(GameTestHelper helper) {
        buildArena(helper);
        TRex rex = spawnRex(helper, FACING_SOUTH);
        Vec3 start = rex.position();
        helper.succeedWhen(() -> {
            double moved = rex.position().subtract(start).horizontalDistance();
            helper.assertTrue(moved >= WANDER_MIN_DISTANCE, "rex only wandered " + String.format("%.1f", moved) + " blocks");
            DinoDread.LOGGER.info("[gametest] wander moved {} blocks by tick {}", String.format("%.1f", moved), helper.getTick());
            rex.discard();
        });
    }

    /** A rex facing away cannot see a player behind it, but hears them walk: it investigates without a target. */
    private static void hearsFootsteps(GameTestHelper helper) {
        buildArena(helper);
        TRex rex = spawnRex(helper, FACING_NORTH);
        ServerPlayer player = spawnPlayer(helper, new Vec3(0.5, 0, 40.5));
        float hearingRadius = RexConfig.HEARING_RADIUS.get().floatValue();
        helper.assertTrue(rex.distanceTo(player) < hearingRadius, "test setup: player must be inside the hearing radius");

        helper.runAtTickTime(3, () -> stepNoise(helper, player));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(rex.getTarget() == null, "rex should not have seen the player behind it");
            helper.assertTrue(rex.getSenses().hasQuarry(), "rex should have heard the footstep");
            cleanUp(helper, rex, player);
            helper.succeed();
        });
    }

    /** Sneaking shrinks the hearing radius: inaudible at 40 blocks, audible at 15. */
    private static void barelyHearsSneaking(GameTestHelper helper) {
        buildArena(helper);
        TRex rex = spawnRex(helper, FACING_NORTH);
        ServerPlayer player = spawnPlayer(helper, new Vec3(0.5, 0, 40.5));
        player.setShiftKeyDown(true);
        double sneakRadius = RexConfig.HEARING_RADIUS.get() * RexConfig.SNEAK_HEARING_MULTIPLIER.get();
        helper.assertTrue(rex.distanceTo(player) > sneakRadius, "test setup: player must start outside the sneak hearing radius");
        helper.assertTrue(15.0 < sneakRadius, "test setup: the near position must be inside the sneak hearing radius");

        helper.runAtTickTime(3, () -> stepNoise(helper, player));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!rex.getSenses().hasQuarry(), "a sneaking player 40 blocks away should not be heard");
            player.snapTo(helper.absoluteVec(new Vec3(0.5, 0, 15.5)));
        });
        helper.runAtTickTime(6, () -> stepNoise(helper, player));
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(rex.getTarget() == null, "rex should not have seen the player behind it");
            helper.assertTrue(rex.getSenses().hasQuarry(), "a sneaking player 15 blocks away should be heard");
            cleanUp(helper, rex, player);
            helper.succeed();
        });
    }

    private static void stepNoise(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().gameEvent(GameEvent.STEP, player.position(), GameEvent.Context.of(player));
    }

    private static void buildArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = -ARENA_HALF_WIDTH; x <= ARENA_HALF_WIDTH; x++) {
            for (int z = -ARENA_BACK; z <= ARENA_FRONT; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y < ARENA_HEIGHT; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        // The arena is far bigger than the (1x1x1) test structure: keep its chunks loaded and ticking.
        for (int chunkX = SectionPos.blockToSectionCoord(origin.getX() - ARENA_HALF_WIDTH); chunkX <= SectionPos.blockToSectionCoord(origin.getX() + ARENA_HALF_WIDTH); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(origin.getZ() - ARENA_BACK); chunkZ <= SectionPos.blockToSectionCoord(origin.getZ() + ARENA_FRONT); chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true);
            }
        }
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

    private static void cleanUp(GameTestHelper helper, TRex rex, ServerPlayer player) {
        rex.discard();
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
