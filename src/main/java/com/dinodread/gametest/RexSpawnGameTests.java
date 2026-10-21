package com.dinodread.gametest;

import static com.dinodread.gametest.RexGameTests.FACING_SOUTH;
import static com.dinodread.gametest.RexGameTests.MIDNIGHT_TICKS;
import static com.dinodread.gametest.RexGameTests.NOON_TICKS;
import static com.dinodread.gametest.RexGameTests.TEST_TIMEOUT_TICKS;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.dinodread.DinoDread;
import com.dinodread.config.RexConfig;
import com.dinodread.config.SpawnConfig;
import com.dinodread.entity.TRex;
import com.dinodread.gametest.RexGameTests.Run;
import com.dinodread.registry.ModEntities;
import com.dinodread.spawn.HostileBlocker;
import com.dinodread.spawn.RexSpawner;
import com.dinodread.spawn.SpawnRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Milestone 6 acceptance tests (spawning and balance), server side. The spawner and the hostile-mob blocker are driven with
 * explicit {@link SpawnRules} / {@link HostileBlocker.Rules} (not the config file), so each rule can be switched on and off
 * inside one test; the config defaults get a test of their own. Every body runs in the first tick of its scenario, so the
 * real spawner that ticks with the game (default config, one T-rex per dimension) never interleaves with a test.
 * <p>
 * Geometry: the player stands at the back of the flat arena and the spawn ring is 24 to 60 blocks from there, so only the
 * stretch of arena in front of the player can host a body this big; the tests ask for many placement attempts to find it.
 * The T-rex's size is always read from the entity, never assumed, because the model is still being resized.
 */
public final class RexSpawnGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);
    private static final List<String> NAMES = new ArrayList<>();

    // The body of every test finishes in the first tick; this is only the backstop for the scenario.
    private static final int BACKSTOP_TICKS = 40;
    private static final Vec3 PLAYER_AT = new Vec3(0.5, 0, -8.5);
    private static final double RING_MIN = 24.0;
    private static final double RING_MAX = 60.0;
    private static final int RING_ATTEMPTS = 400;
    private static final double RING_EPSILON = 0.01;
    private static final int RING_SAMPLES = 6;
    private static final int NO_LIMIT = 1000;
    private static final int CHANCE_TRIALS = 100;
    private static final double CHANCE_UNDER_TEST = 0.25;
    // Mean 25 and standard deviation 4.3 over 100 trials: these bounds are about four deviations out.
    private static final int CHANCE_LOWEST = 8;
    private static final int CHANCE_HIGHEST = 42;
    private static final double ARENA_CENTER_Z = 35.0;
    private static final double CROWDED_RING_MIN = 40.0;
    private static final double WALL_FRACTION = 0.25;
    private static final List<String> DEFAULT_BLOCKED_REASONS = List.of("NATURAL", "CHUNK_GENERATION", "PATROL", "STRUCTURE");
    private static final double SPAWN_DISTANCE_FAR_AWAY = 1.0E6;

    static {
        test("spawner_spawns_a_rex_at_night_inside_the_ring", RexSpawnGameTests::spawnsAtNightInsideTheRing);
        test("spawner_spawns_only_in_the_dark_when_night_only", RexSpawnGameTests::spawnsOnlyInTheDark);
        test("spawner_spawn_chance_is_the_chance_per_roll", RexSpawnGameTests::chanceIsPerRoll);
        test("spawner_respects_the_biome_list", RexSpawnGameTests::respectsTheBiomeList);
        test("spawner_respects_dimension_delay_and_master_switch", RexSpawnGameTests::respectsDimensionDelayAndMasterSwitch);
        test("spawner_respects_the_per_player_and_world_caps", RexSpawnGameTests::respectsTheCaps);
        test("spawner_needs_room_for_the_real_hitbox", RexSpawnGameTests::needsRoomForTheRealHitbox);
        test("spawner_keeps_inside_the_distance_ring", RexSpawnGameTests::staysInsideTheRing);
        test("spawner_rolls_once_per_interval", RexSpawnGameTests::rollsOncePerInterval);
        test("spawner_despawns_natural_rexes_by_the_rules", RexSpawnGameTests::despawnsNaturalRexesByTheRules);
        test("spawner_respects_the_mob_spawning_game_rules", RexSpawnGameTests::respectsTheMobSpawningGameRules);
        test("spawner_keeps_its_distance_from_every_player", RexSpawnGameTests::keepsItsDistanceFromEveryPlayer);
        test("spawner_defaults_are_one_rare_rex_far_away", RexSpawnGameTests::defaultsAreOneRareRexFarAway);
        test("hostile_blocker_stops_natural_hostiles", RexSpawnGameTests::blocksNaturalHostiles);
        test("hostile_blocker_spares_eggs_commands_and_spawners", RexSpawnGameTests::sparesEggsCommandsAndSpawners);
        test("hostile_blocker_honors_exceptions_and_reasons", RexSpawnGameTests::honorsExceptionsAndReasons);
        test("hostile_blocker_never_touches_the_rex_or_peaceful_mobs", RexSpawnGameTests::neverTouchesTheRexOrPeacefulMobs);
        test("hostile_blocker_is_off_by_default", RexSpawnGameTests::isOffByDefault);
        test("hostile_blocker_fails_the_placement_check", RexSpawnGameTests::failsThePlacementCheck);
    }

    private RexSpawnGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        NAMES.add(name);
        FUNCTIONS.register(name, () -> body);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexSpawnGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        for (String name : NAMES) {
            RexGameTests.addTest(event, name, TEST_TIMEOUT_TICKS);
        }
    }

    /** Rules that let a spawn through in the test arena; each test switches off or changes the one rule it is about. */
    private static SpawnRules baseline() {
        return new SpawnRules(true, true, 0.5, 0, 1, 1.0, List.of("minecraft:overworld"), List.of(), RING_MIN, RING_MAX, RING_ATTEMPTS, 1, 160.0, 1, true, 128.0, 0);
    }

    /**
     * One spawner scenario: the player at the back of the arena, the body run once in the first tick, then the test passes.
     * The game test server turns the spawn_mobs game rule off (so nothing random wanders into the tests); the body runs with it
     * back on, and only for that one tick, so vanilla's own spawner never gets a turn.
     */
    private static void spawnerScenario(GameTestHelper helper, long clockTicks, boolean keepScenarioRex, Consumer<Run> body) {
        RexGameTests.scenario(helper, clockTicks, FACING_SOUTH, PLAYER_AT, false, BACKSTOP_TICKS, origin -> {}, run -> {
            if (!keepScenarioRex) {
                run.rex.discard();
            }
            GameRules rules = helper.getLevel().getGameRules();
            MinecraftServer server = helper.getLevel().getServer();
            boolean mobsWere = rules.get(GameRules.SPAWN_MOBS);
            rules.set(GameRules.SPAWN_MOBS, true, server);
            try {
                body.accept(run);
            } finally {
                rules.set(GameRules.SPAWN_MOBS, mobsWere, server);
            }
            run.pass();
        }, run -> {});
    }

    private static @Nullable TRex spawn(Run run, SpawnRules rules) {
        ServerLevel level = run.helper.getLevel();
        TRex rex = RexSpawner.trySpawn(level, run.player, rules, level.getRandom());
        if (rex != null) {
            run.cleanups.add(rex::discard);
        }
        return rex;
    }

    /** Spawns with the rules and, if it spawned, discards the rex again straight away. */
    private static boolean spawns(Run run, SpawnRules rules) {
        TRex rex = spawn(run, rules);
        if (rex == null) {
            return false;
        }
        rex.discard();
        return true;
    }

    private static void discardNatural(ServerLevel level) {
        for (TRex rex : level.getEntities(EntityTypeTest.forClass(TRex.class), candidate -> candidate.entityTags().contains(RexSpawner.NATURAL_TAG))) {
            rex.discard();
        }
    }

    private static double horizontalDistanceToPlayer(Run run, Entity entity) {
        return Math.hypot(entity.getX() - run.player.getX(), entity.getZ() - run.player.getZ());
    }

    private static String describe(SpawnRules rules) {
        return rules.toString();
    }

    // ---------------------------------------------------------------- the spawner

    /** The happy path: night, chance 1, any biome, room in the arena: a rex appears, standing on the ground, in the ring, with its real body. */
    private static void spawnsAtNightInsideTheRing(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            ServerLevel level = helper.getLevel();
            TRex rex = spawn(run, baseline());
            run.check(rex != null, "no T-rex spawned at night with chance 1, any biome and open ground in the ring");
            run.check(level.getEntity(rex.getUUID()) == rex, "the spawner returned a T-rex that is not in the level");
            run.check(rex.entityTags().contains(RexSpawner.NATURAL_TAG), "a natural spawn must carry the natural tag " + RexSpawner.NATURAL_TAG + ", despawning keys off it");
            run.check(rex.isPersistenceRequired(), "a natural spawn must be persistent, or vanilla despawns it within a minute of it spawning far from the player");

            double distance = horizontalDistanceToPlayer(run, rex);
            run.check(distance >= RING_MIN - RING_EPSILON && distance <= RING_MAX + RING_EPSILON,
                "spawned " + String.format("%.1f", distance) + " blocks away, outside the ring " + RING_MIN + " to " + RING_MAX);

            double expectedWidth = ModEntities.TREX.get().getWidth() * RexConfig.SCALE.get();
            run.check(Math.abs(rex.getBbWidth() - expectedWidth) < RING_EPSILON,
                "the spawned rex is " + rex.getBbWidth() + " wide, not the scaled " + expectedWidth + ": it would be placed with the wrong body");
            BlockPos below = BlockPos.containing(rex.getX(), rex.getY() - 0.5, rex.getZ());
            run.check(level.getBlockState(below).isFaceSturdy(level, below, Direction.UP), "the rex was placed with no solid ground under its feet");
            run.check(level.noCollision(rex), "the rex was placed inside blocks");
        });
    }

    /** nightOnly: no spawn at noon; the same rules with nightOnly off do spawn (so the daylight refusal is the rule, not the arena). */
    private static void spawnsOnlyInTheDark(GameTestHelper helper) {
        spawnerScenario(helper, NOON_TICKS, false, run -> {
            run.check(!spawns(run, baseline()), "spawned in full daylight although nightOnly is on");
            run.check(spawns(run, baseline().withNightOnly(false)), "nightOnly off must allow a daylight spawn (otherwise the arena, not the rule, refused the first one)");
        });
        // The dark side of the same rule is the "spawns at night" test above.
    }

    /** Chance 0 never spawns, chance 1 always does, and in between it is a per-roll probability, so rarity can be tuned by feel. */
    private static void chanceIsPerRoll(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            run.check(!spawns(run, baseline().withSpawnChance(0.0)), "spawned with spawnChance 0");
            run.check(spawns(run, baseline().withSpawnChance(1.0)), "did not spawn with spawnChance 1");
            int hits = 0;
            for (int roll = 0; roll < CHANCE_TRIALS; roll++) {
                if (spawns(run, baseline().withSpawnChance(CHANCE_UNDER_TEST))) {
                    hits++;
                }
            }
            run.check(hits >= CHANCE_LOWEST && hits <= CHANCE_HIGHEST,
                CHANCE_TRIALS + " rolls at chance " + CHANCE_UNDER_TEST + " spawned " + hits + " times, expected about " + (int) (CHANCE_TRIALS * CHANCE_UNDER_TEST));
        });
    }

    /** allowedBiomes takes ids and #tags, an empty list means any biome, and junk entries are ignored. */
    private static void respectsTheBiomeList(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            ServerLevel level = helper.getLevel();
            Holder<Biome> here = level.getBiome(run.origin.offset(0, 0, (int) ARENA_CENTER_Z));
            String hereId = here.unwrapKey().orElseThrow().identifier().toString();
            run.check(spawns(run, baseline().withAllowedBiomes(List.of(hereId))), "the biome id " + hereId + " (the arena's own biome) did not allow a spawn");
            run.check(spawns(run, baseline().withAllowedBiomes(List.of("#minecraft:is_overworld"))), "the tag #minecraft:is_overworld did not allow a spawn in the overworld");
            run.check(spawns(run, baseline().withAllowedBiomes(List.of())), "an empty biome list must mean any biome");
            run.check(spawns(run, baseline().withAllowedBiomes(List.of("not a valid id!!", "#also not valid!!", hereId))),
                "malformed entries in the biome list must be skipped, not break the valid ones");
            run.check(!spawns(run, baseline().withAllowedBiomes(List.of("minecraft:the_end"))), "spawned in the overworld with only minecraft:the_end allowed");
            run.check(!spawns(run, baseline().withAllowedBiomes(List.of("#minecraft:is_nether"))), "spawned in the overworld with only #minecraft:is_nether allowed");
        });
    }

    /** allowedDimensions, firstSpawnDelayDays and the master switch each stop a spawn that would otherwise happen. */
    private static void respectsDimensionDelayAndMasterSwitch(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            ServerLevel level = helper.getLevel();
            String thisDimension = level.dimension().identifier().toString();
            run.check(spawns(run, baseline().withAllowedDimensions(List.of(thisDimension))), "the level's own dimension " + thisDimension + " did not allow a spawn");
            run.check(!spawns(run, baseline().withAllowedDimensions(List.of("minecraft:the_nether"))), "spawned in " + thisDimension + " with only the nether allowed");
            run.check(!spawns(run, baseline().withFirstSpawnDelayDays(NO_LIMIT * NO_LIMIT)), "spawned although the world is younger than firstSpawnDelayDays");
            run.check(spawns(run, baseline().withFirstSpawnDelayDays(0)), "firstSpawnDelayDays 0 must allow a spawn");
            int spawned = RexSpawner.tickLevel(level, baseline().withSpawnEnabled(false), level.getRandom());
            discardNatural(level);
            run.check(spawned == 0, "the level tick spawned " + spawned + " rexes with spawnEnabled off");
        });
    }

    /** maxRexPerPlayer counts rexes within capRadius of the player, maxRexInWorld counts the whole level; both count rexes of any origin. */
    private static void respectsTheCaps(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, true, run -> {
            ServerLevel level = helper.getLevel();
            run.check(run.rex.distanceTo(run.player) < baseline().capRadius(), "test setup: the existing rex must be inside the cap radius");
            run.check(!spawns(run, baseline()), "spawned with one rex (the scenario's, spawned by egg) already near the player and caps of 1");
            run.check(!spawns(run, baseline().withMaxRexPerPlayer(2)), "spawned although the dimension cap (1) was reached");
            run.check(!spawns(run, baseline().withMaxRexInWorld(2)), "spawned although the per-player cap (1) was reached");

            SpawnRules two = baseline().withMaxRexPerPlayer(2).withMaxRexInWorld(2);
            TRex second = spawn(run, two);
            run.check(second != null, "caps of 2 with one rex around must allow a second");
            run.check(!spawns(run, two), "caps of 2 with two rexes around must stop a third");
            second.discard();

            // Move the existing rex out of the near-player radius: only the dimension cap still counts it.
            run.rex.snapTo(helper.absoluteVec(new Vec3(0.5, 0, 56.5)));
            SpawnRules smallRadius = baseline().withCapRadius(30.0);
            run.check(run.rex.distanceTo(run.player) > smallRadius.capRadius(), "test setup: the moved rex must be outside the small cap radius");
            run.check(!spawns(run, smallRadius), "spawned although the dimension cap (1) counts the far rex");
            run.check(spawns(run, smallRadius.withMaxRexInWorld(2)), "a rex outside capRadius must not count against maxRexPerPlayer");
        });
    }

    /** The placement check reads the rex's real hitbox: a block inside it, a ceiling inside it, missing ground or water refuse the spot; blocks just outside do not. */
    private static void needsRoomForTheRealHitbox(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, true, run -> {
            ServerLevel level = helper.getLevel();
            TRex rex = run.rex;
            double x = run.origin.getX() + 0.5;
            double y = run.origin.getY();
            double z = run.origin.getZ() + 30.5;
            int blockZ = Mth.floor(z);

            run.check(RexSpawner.canStandAt(level, rex, x, y, z), "flat open ground with room for the whole body was refused");

            double half = rex.getBbWidth() / 2.0;
            run.check(half < RexGameTests.ARENA_HALF_WIDTH - 2, "test setup: the arena is too narrow for a body " + rex.getBbWidth() + " wide");
            int insideX = Mth.floor(x + half - WALL_FRACTION);
            placeThenRemove(level, new BlockPos(insideX, run.origin.getY() + 1, blockZ), () ->
                run.check(!RexSpawner.canStandAt(level, rex, x, y, z), "a block poking " + WALL_FRACTION + " blocks into the side of the hitbox did not refuse the spot"));
            placeThenRemove(level, new BlockPos(insideX + 2, run.origin.getY() + 1, blockZ), () ->
                run.check(RexSpawner.canStandAt(level, rex, x, y, z), "a block a whole block outside the hitbox refused the spot: the check is not using the real body"));

            int insideY = Mth.floor(y + rex.getBbHeight() - WALL_FRACTION);
            placeThenRemove(level, new BlockPos(Mth.floor(x), insideY, blockZ), () ->
                run.check(!RexSpawner.canStandAt(level, rex, x, y, z), "a ceiling block inside the top of the hitbox did not refuse the spot"));
            placeThenRemove(level, new BlockPos(Mth.floor(x), insideY + 2, blockZ), () ->
                run.check(RexSpawner.canStandAt(level, rex, x, y, z), "a block above the hitbox refused the spot"));

            run.check(!RexSpawner.canStandAt(level, rex, x, y + 3, z), "a spot three blocks above the ground (no floor under the feet) was accepted");
            level.setBlock(new BlockPos(Mth.floor(x), run.origin.getY(), blockZ), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            run.check(!RexSpawner.canStandAt(level, rex, x, y, z), "a spot with water in the hitbox was accepted");
            level.setBlock(new BlockPos(Mth.floor(x), run.origin.getY(), blockZ), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        });
    }

    private static void placeThenRemove(ServerLevel level, BlockPos pos, Runnable whilePlaced) {
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        try {
            whilePlaced.run();
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Three different rings: every spawn lands inside the one asked for, so minSpawnDistance and maxSpawnDistance are both honored. */
    private static void staysInsideTheRing(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            double[][] rings = {{24.0, 28.0}, {40.0, 48.0}, {52.0, 60.0}};
            for (double[] ring : rings) {
                SpawnRules rules = baseline().withMinSpawnDistance(ring[0]).withMaxSpawnDistance(ring[1]).withMaxRexPerPlayer(NO_LIMIT).withMaxRexInWorld(NO_LIMIT);
                for (int sample = 0; sample < RING_SAMPLES; sample++) {
                    TRex rex = spawn(run, rules);
                    run.check(rex != null, "no spawn for the ring " + ring[0] + " to " + ring[1] + " (sample " + sample + "): " + describe(rules));
                    double distance = horizontalDistanceToPlayer(run, rex);
                    rex.discard();
                    run.check(distance >= ring[0] - RING_EPSILON && distance <= ring[1] + RING_EPSILON,
                        "spawned " + String.format("%.1f", distance) + " blocks away, outside the ring " + ring[0] + " to " + ring[1]);
                }
            }
            // A ring far outside every loaded chunk finds nothing: the spawner must not load or build chunks to find a spot.
            run.check(!spawns(run, baseline().withMinSpawnDistance(SPAWN_DISTANCE_FAR_AWAY).withMaxSpawnDistance(SPAWN_DISTANCE_FAR_AWAY + 1.0)),
                "spawned in a ring a million blocks away, in chunks that are not running");
        });
    }

    /** The level tick rolls once per attemptIntervalTicks (on ticks that are a multiple of it), and not at all when spawnEnabled is off. */
    private static void rollsOncePerInterval(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            ServerLevel level = helper.getLevel();
            run.check(level.getGameTime() > 0, "test setup: the world must have run for a tick");
            int onInterval = RexSpawner.tickLevel(level, baseline().withAttemptIntervalTicks(1), level.getRandom());
            discardNatural(level);
            run.check(onInterval == 1, "the level tick with an interval of 1 tick spawned " + onInterval + " rexes, expected 1");
            int gameTime = (int) level.getGameTime();
            int offInterval = RexSpawner.tickLevel(level, baseline().withAttemptIntervalTicks(gameTime + 1), level.getRandom());
            discardNatural(level);
            run.check(offInterval == 0, "the level tick rolled on a tick that is not a multiple of attemptIntervalTicks (" + (gameTime + 1) + ") at game time " + gameTime);
        });
    }

    /**
     * Only natural rexes are ever despawned: not eggs; not while a player is within despawnDistance; not with despawnWhenFar off.
     * The lifetime limit removes a natural rex too, but never one that is hunting right now.
     */
    private static void despawnsNaturalRexesByTheRules(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, true, run -> {
            ServerLevel level = helper.getLevel();
            TRex near = run.rex;
            TRex far = ModEntities.TREX.get().spawn(level, run.origin.offset(0, 0, 30), EntitySpawnReason.COMMAND);
            run.check(far != null, "test setup: could not spawn the second rex");
            run.cleanups.add(far::discard);
            SpawnRules rules = baseline();
            run.check(near.distanceTo(run.player) < 20.0 && far.distanceTo(run.player) > 30.0, "test setup: one rex must be near the player and one far");

            run.check(RexSpawner.despawnStrayRexes(level, rules.withDespawnDistance(5.0)) == 0 && !near.isRemoved() && !far.isRemoved(),
                "a spawn-egg rex (no natural tag) was despawned");

            RexSpawner.markNatural(near);
            RexSpawner.markNatural(far);
            run.check(RexSpawner.despawnStrayRexes(level, rules.withDespawnDistance(50.0).withDespawnWhenFar(false)) == 0 && !far.isRemoved(),
                "despawnWhenFar off must keep a natural rex that is far from every player");
            run.check(RexSpawner.despawnStrayRexes(level, rules.withDespawnDistance(50.0)) == 0 && !far.isRemoved(),
                "a natural rex was despawned although a player is within despawnDistance");
            int removed = RexSpawner.despawnStrayRexes(level, rules.withDespawnDistance(20.0));
            run.check(removed == 1 && far.isRemoved() && !near.isRemoved(), "with despawnDistance 20, only the rex 39 blocks from the player should go: removed=" + removed);

            near.tickCount = 1000;
            SpawnRules lifetime = rules.withDespawnWhenFar(false);
            run.check(RexSpawner.despawnStrayRexes(level, lifetime.withMaxLifetimeTicks(5000)) == 0 && !near.isRemoved(), "a rex younger than maxLifetimeTicks was despawned");
            run.check(RexSpawner.despawnStrayRexes(level, lifetime.withMaxLifetimeTicks(0)) == 0 && !near.isRemoved(), "maxLifetimeTicks 0 must mean no limit");
            near.getSenses().hearNoise(run.player, run.player.position());
            run.check(near.getSenses().hasQuarry(), "test setup: the rex must be hunting");
            run.check(RexSpawner.despawnStrayRexes(level, lifetime.withMaxLifetimeTicks(500)) == 0 && !near.isRemoved(), "a hunting rex was removed for outliving maxLifetimeTicks");
            near.getSenses().forget();
            run.check(RexSpawner.despawnStrayRexes(level, lifetime.withMaxLifetimeTicks(500)) == 1 && near.isRemoved(), "a rex that outlived maxLifetimeTicks and is not hunting was not removed");
        });
    }

    /** On a server a spawn is never conjured within the minimum distance of ANY player, not just the one who rolled. */
    private static void keepsItsDistanceFromEveryPlayer(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            // The ring is 40 to 60 blocks from the rolling player, and a second player stands in the middle of the only ground it can use (the
            // arena, in front of the rolling player). Everything the ring can reach in the arena's chunks is then within 40 blocks of the second player.
            SpawnRules rules = baseline().withMinSpawnDistance(CROWDED_RING_MIN);
            ServerPlayer other = RexGameTests.spawnPlayer(helper, new Vec3(0.5, 0, ARENA_CENTER_Z + 0.5));
            run.cleanups.add(() -> helper.getLevel().getServer().getPlayerList().remove(other));
            run.check(!spawns(run, rules), "spawned within " + CROWDED_RING_MIN + " blocks of a second player");
            other.snapTo(run.player.position());
            run.check(spawns(run, rules), "with the second player out of the way the same rules must spawn (otherwise something else refused the first one)");
        });
    }

    /** The spawn_mobs and spawn_monsters game rules stop natural rexes like any other natural spawn (they are not mine to override). */
    private static void respectsTheMobSpawningGameRules(GameTestHelper helper) {
        spawnerScenario(helper, MIDNIGHT_TICKS, false, run -> {
            ServerLevel level = helper.getLevel();
            GameRules rules = level.getGameRules();
            MinecraftServer server = level.getServer();
            run.check(spawns(run, baseline()), "test setup: with both rules on a spawn must work");
            rules.set(GameRules.SPAWN_MONSTERS, false, server);
            try {
                run.check(!spawns(run, baseline()), "spawned although the spawn_monsters game rule is off");
            } finally {
                rules.set(GameRules.SPAWN_MONSTERS, true, server);
            }
            rules.set(GameRules.SPAWN_MOBS, false, server);
            run.check(!spawns(run, baseline()), "spawned although the spawn_mobs game rule is off");
        });
    }

    /** The shipped defaults: one rex at a time, rare, never conjured in view, and the vanilla hostiles left alone until someone opts in. */
    private static void defaultsAreOneRareRexFarAway(GameTestHelper helper) {
        SpawnRules rules = SpawnRules.fromConfig();
        List<String> problems = new ArrayList<>();
        if (!rules.spawnEnabled()) {
            problems.add("natural spawning is off by default");
        }
        if (rules.maxRexInWorld() != 1 || rules.maxRexPerPlayer() != 1) {
            problems.add("v1 is ONE T-rex: caps are " + rules.maxRexPerPlayer() + " per player and " + rules.maxRexInWorld() + " per dimension");
        }
        if (!rules.nightOnly()) {
            problems.add("nightOnly is off by default");
        }
        if (rules.spawnChance() > 0.2 || rules.spawnChance() <= 0.0) {
            problems.add("spawnChance " + rules.spawnChance() + " is not rare");
        }
        if (rules.minSpawnDistance() <= RexConfig.SIGHT_RANGE.get()) {
            problems.add("minSpawnDistance " + rules.minSpawnDistance() + " is within the rex's sight range " + RexConfig.SIGHT_RANGE.get() + ": it would spawn in view");
        }
        if (rules.maxSpawnDistance() < rules.minSpawnDistance()) {
            problems.add("maxSpawnDistance is below minSpawnDistance");
        }
        if (rules.despawnDistance() <= rules.maxSpawnDistance()) {
            problems.add("despawnDistance " + rules.despawnDistance() + " is not above maxSpawnDistance " + rules.maxSpawnDistance() + ": a rex would vanish as it spawns");
        }
        if (!rules.allowedDimensions().contains("minecraft:overworld")) {
            problems.add("the overworld is not an allowed dimension by default");
        }
        if (SpawnConfig.DISABLE_VANILLA_HOSTILES.get() || !SpawnConfig.VANILLA_HOSTILE_EXCEPTIONS.get().isEmpty()) {
            problems.add("vanilla hostile mobs are disabled by default");
        }
        if (!SpawnConfig.BLOCKED_SPAWN_REASONS.get().containsAll(DEFAULT_BLOCKED_REASONS)) {
            problems.add("blockedSpawnReasons does not default to " + DEFAULT_BLOCKED_REASONS);
        }
        if (problems.isEmpty()) {
            helper.succeed();
        } else {
            helper.fail(String.join("; ", problems));
        }
    }

    // ---------------------------------------------------------------- the hostile-mob toggle

    private static HostileBlocker.Rules on() {
        return new HostileBlocker.Rules(true, List.of(), DEFAULT_BLOCKED_REASONS);
    }

    /** A hostile-mob scenario: no player, the body run once in the first tick, then the test passes. */
    private static void hostileScenario(GameTestHelper helper, Consumer<Run> body) {
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, BACKSTOP_TICKS, origin -> {}, run -> {
            body.accept(run);
            run.pass();
        }, run -> {});
    }

    /** Registers a blocker with these rules on the game bus for the rest of the test. */
    private static HostileBlocker block(Run run, HostileBlocker.Rules rules) {
        HostileBlocker blocker = new HostileBlocker(() -> rules);
        blocker.register(NeoForge.EVENT_BUS);
        run.cleanups.add(() -> blocker.unregister(NeoForge.EVENT_BUS));
        return blocker;
    }

    /** Spawns the mob the way the game would for this reason; true if it came into the world (and then removes it again). */
    private static boolean mobSpawns(Run run, EntityType<?> type, EntitySpawnReason reason) {
        Entity mob = type.spawn(run.helper.getLevel(), run.origin.offset(0, 0, 30), reason);
        if (mob == null) {
            return false;
        }
        boolean inLevel = run.helper.getLevel().getEntity(mob.getUUID()) == mob;
        mob.discard();
        return inLevel;
    }

    /** With the toggle on, a natural, chunk-generation, patrol or structure spawn of a hostile mob never happens. */
    private static void blocksNaturalHostiles(GameTestHelper helper) {
        hostileScenario(helper, run -> {
            run.check(mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL), "test setup: a natural zombie spawn must work with no blocker");
            block(run, on());
            run.check(!mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL), "a natural zombie spawned with disableVanillaHostiles on");
            run.check(!mobSpawns(run, EntityTypes.CREEPER, EntitySpawnReason.CHUNK_GENERATION), "a creeper made by chunk generation spawned with the toggle on");
            run.check(!mobSpawns(run, EntityTypes.PILLAGER, EntitySpawnReason.PATROL), "a pillager patrol spawned with the toggle on");
            run.check(!mobSpawns(run, EntityTypes.SKELETON, EntitySpawnReason.STRUCTURE), "a structure-placed skeleton spawned with the toggle on");
        });
    }

    /** Eggs, commands, summons, dispensers, trial spawners and old-style spawners are not in the default reason list: never blocked. */
    private static void sparesEggsCommandsAndSpawners(GameTestHelper helper) {
        hostileScenario(helper, run -> {
            block(run, on());
            EntitySpawnReason[] spared = {EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.COMMAND, EntitySpawnReason.MOB_SUMMONED, EntitySpawnReason.DISPENSER,
                EntitySpawnReason.SPAWNER, EntitySpawnReason.TRIAL_SPAWNER, EntitySpawnReason.EVENT, EntitySpawnReason.REINFORCEMENT, EntitySpawnReason.TRIGGERED};
            for (EntitySpawnReason reason : spared) {
                run.check(mobSpawns(run, EntityTypes.ZOMBIE, reason), "a zombie spawn with reason " + reason + " was blocked, but only the listed reasons may be");
            }
        });
    }

    /** vanillaHostileExceptions (ids and #tags) let mobs through; blockedSpawnReasons decides which spawns count, unknown names are skipped. */
    private static void honorsExceptionsAndReasons(GameTestHelper helper) {
        hostileScenario(helper, run -> {
            HostileBlocker with = block(run, new HostileBlocker.Rules(true, List.of("minecraft:zombie", "#minecraft:skeletons", "garbage id!!"), DEFAULT_BLOCKED_REASONS));
            run.check(mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL), "an excepted id (minecraft:zombie) was blocked");
            run.check(mobSpawns(run, EntityTypes.SKELETON, EntitySpawnReason.NATURAL), "an excepted tag (#minecraft:skeletons) did not let a skeleton through");
            run.check(mobSpawns(run, EntityTypes.STRAY, EntitySpawnReason.NATURAL), "an excepted tag did not let a stray (also a skeleton) through");
            run.check(!mobSpawns(run, EntityTypes.CREEPER, EntitySpawnReason.NATURAL), "a mob that is in no exception was let through");
            with.unregister(NeoForge.EVENT_BUS);

            block(run, new HostileBlocker.Rules(true, List.of(), List.of("SPAWNER", "NOT_A_REASON")));
            run.check(!mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER), "SPAWNER in blockedSpawnReasons did not block a spawner spawn");
            run.check(mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL), "NATURAL is not in this reason list but was blocked");
            run.check(mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWN_ITEM_USE), "an egg spawn was blocked");
        });
    }

    /** The rex (it is in the monster category too) and everything that is not hostile pass with the toggle on and nothing excepted. */
    private static void neverTouchesTheRexOrPeacefulMobs(GameTestHelper helper) {
        hostileScenario(helper, run -> {
            block(run, on());
            run.check(mobSpawns(run, ModEntities.TREX.get(), EntitySpawnReason.NATURAL), "the T-rex's own natural spawn was blocked by the hostile toggle");
            run.check(mobSpawns(run, ModEntities.TREX.get(), EntitySpawnReason.CHUNK_GENERATION), "the T-rex was blocked for a chunk-generation spawn");
            run.check(mobSpawns(run, EntityTypes.COW, EntitySpawnReason.NATURAL), "a cow (a peaceful mob) was blocked");
            run.check(mobSpawns(run, EntityTypes.BAT, EntitySpawnReason.NATURAL), "a bat (ambient) was blocked");
        });
    }

    /** With the toggle off, whether through explicit rules or the shipped config, a natural hostile spawn is untouched. */
    private static void isOffByDefault(GameTestHelper helper) {
        hostileScenario(helper, run -> {
            run.check(!SpawnConfig.DISABLE_VANILLA_HOSTILES.get(), "test setup: disableVanillaHostiles must default to false");
            run.check(mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL), "a natural zombie spawn failed with the shipped config");
            block(run, new HostileBlocker.Rules(false, List.of(), DEFAULT_BLOCKED_REASONS));
            run.check(mobSpawns(run, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL), "a natural zombie spawn failed with a registered blocker whose toggle is off");
            run.check(mobSpawns(run, EntityTypes.CREEPER, EntitySpawnReason.CHUNK_GENERATION), "a chunk-generation spawn failed with the toggle off");
        });
    }

    /**
     * The cheap early refusal: the placement check that vanilla's natural spawner asks (before it builds a mob) fails for a blocked hostile spawn.
     * The event is posted by hand with "vanilla would allow it" as its default answer, because vanilla's own answer for a zombie depends on a random light roll.
     */
    private static void failsThePlacementCheck(GameTestHelper helper) {
        hostileScenario(helper, run -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = run.origin.offset(0, 0, 30);
            run.check(placementPasses(level, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, pos), "test setup: with no blocker the placement check must keep vanilla's answer");
            block(run, on());
            run.check(!placementPasses(level, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, pos), "the placement check passed a natural zombie with the toggle on");
            run.check(!placementPasses(level, EntityTypes.CREEPER, EntitySpawnReason.CHUNK_GENERATION, pos), "the placement check passed a chunk-generation creeper with the toggle on");
            run.check(placementPasses(level, EntityTypes.ZOMBIE, EntitySpawnReason.TRIAL_SPAWNER, pos), "the placement check refused a reason that is not blocked");
            run.check(placementPasses(level, EntityTypes.COW, EntitySpawnReason.NATURAL, pos), "the placement check refused a cow");
            run.check(placementPasses(level, ModEntities.TREX.get(), EntitySpawnReason.NATURAL, pos), "the placement check refused the T-rex");
        });
    }

    private static boolean placementPasses(ServerLevel level, EntityType<?> type, EntitySpawnReason reason, BlockPos pos) {
        MobSpawnEvent.SpawnPlacementCheck check = new MobSpawnEvent.SpawnPlacementCheck(type, level, reason, pos, level.getRandom(), true);
        NeoForge.EVENT_BUS.post(check);
        return check.getPlacementCheckResult();
    }
}
