package com.dinodread.gametest;

import static com.dinodread.gametest.RexGameTests.FACING_SOUTH;
import static com.dinodread.gametest.RexGameTests.MIDNIGHT_TICKS;
import static com.dinodread.gametest.RexGameTests.TEST_TIMEOUT_TICKS;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.dinodread.DinoDread;
import com.dinodread.config.RexConfig;
import com.dinodread.config.SpawnConfig;
import com.dinodread.config.SuperRexConfig;
import com.dinodread.entity.SuperRex;
import com.dinodread.entity.TRex;
import com.dinodread.gametest.RexGameTests.Run;
import com.dinodread.registry.ModEntities;
import com.dinodread.spawn.RexSpawner;
import com.dinodread.spawn.SpawnRules;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Super rex acceptance tests: the spawn substitution (one in superRexChance), the attribute differences
 * that define the variant, and the timed hunt (a natural super rex leaves a fixed time after a player
 * first gets it in view). Same arena and scenario machinery as the other rex tests.
 */
public final class RexSuperRexGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);
    private static final List<String> NAMES = new ArrayList<>();

    private static final int BACKSTOP_TICKS = 40;
    private static final Vec3 PLAYER_AT = new Vec3(0.5, 0, -8.5);
    private static final double RING_MIN = 24.0;
    private static final double RING_MAX = 60.0;
    private static final int RING_ATTEMPTS = 400;
    private static final int CHANCE_TRIALS = 100;
    private static final double SUBSTITUTION_UNDER_TEST = 0.25;
    // Mean 25 and standard deviation 4.3 over 100 trials: these bounds are about four deviations out
    // (same bounds the spawn-chance test uses).
    private static final int SUBSTITUTION_LOWEST = 8;
    private static final int SUBSTITUTION_HIGHEST = 42;

    // The timed-hunt test: rex and player 40 blocks apart on flat ground, clock shortened to 100 ticks.
    private static final int HUNT_TEST_LIMIT_TICKS = 100;
    private static final Vec3 HUNT_PLAYER_AT = new Vec3(0.5, 0, 60.5);
    private static final Vec3 HUNT_REX_AT = new Vec3(0.5, 0, 20.5);
    private static final double HUNT_CONTROL_AT_Z = 26.5;
    private static final int DESPAWN_MAX_TICKS = 400;
    // The clock starts on a tickCount that is a multiple of the 10-tick sight check, so removal cannot happen before
    // roughly the limit; allow for that alignment.
    private static final int DESPAWN_EARLIEST_TICKS = 80;

    static {
        test("superrex_spawner_substitutes_by_chance", RexSuperRexGameTests::substitutesByChance);
        test("superrex_always_counts_against_the_rex_caps", RexSuperRexGameTests::countsAgainstTheCaps);
        test("superrex_is_faster_bigger_but_slower_in_water", RexSuperRexGameTests::fasterBiggerSlowerInWater);
        test("superrex_despawns_after_being_seen", RexSuperRexGameTests::despawnsAfterBeingSeen);
    }

    private RexSuperRexGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        NAMES.add(name);
        FUNCTIONS.register(name, () -> body);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexSuperRexGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        for (String name : NAMES) {
            RexGameTests.addTest(event, name, TEST_TIMEOUT_TICKS);
        }
    }

    /** Rules that let a spawn through in the test arena (same shape as the spawner tests' baseline). */
    private static SpawnRules baseline() {
        return new SpawnRules(true, true, 0.5, 0, 1, 1.0, 0.0, List.of("minecraft:overworld"), List.of(), RING_MIN, RING_MAX, RING_ATTEMPTS, 1, 160.0, 1, true, 128.0, 0, false);
    }

    /** One spawner scenario: the player at the back of the arena, the body run once in the first tick. */
    private static void spawnerScenario(GameTestHelper helper, Consumer<Run> body) {
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, PLAYER_AT, false, BACKSTOP_TICKS, origin -> {}, run -> {
            run.rex.discard();
            withMobSpawning(helper, () -> {
                body.accept(run);
                return null;
            });
            run.pass();
        }, run -> {});
    }

    private static <T> @Nullable T withMobSpawning(GameTestHelper helper, Supplier<@Nullable T> body) {
        GameRules rules = helper.getLevel().getGameRules();
        MinecraftServer server = helper.getLevel().getServer();
        boolean mobsWere = rules.get(GameRules.SPAWN_MOBS);
        rules.set(GameRules.SPAWN_MOBS, true, server);
        try {
            return body.get();
        } finally {
            rules.set(GameRules.SPAWN_MOBS, mobsWere, server);
        }
    }

    /** Spawn, count it against the rules, and discard it again straight away. */
    private static @Nullable TRex spawn(Run run, SpawnRules rules) {
        ServerLevel level = run.helper.getLevel();
        TRex rex = RexSpawner.trySpawn(level, run.player, rules, level.getRandom());
        if (rex != null) {
            run.cleanups.add(rex::discard);
        }
        return rex;
    }

    // ----------------------------------------------------------------

    /** chance 0 never substitutes, chance 1 always does, and in between it is a per-spawn probability. */
    private static void substitutesByChance(GameTestHelper helper) {
        spawnerScenario(helper, run -> {
            for (int roll = 0; roll < 5; roll++) {
                TRex rex = spawn(run, baseline());
                run.check(rex != null, "test setup: no spawn with chance 1 and superRexChance 0");
                run.check(!(rex instanceof SuperRex), "a super rex spawned with superRexChance 0");
                rex.discard();
            }

            for (int roll = 0; roll < 5; roll++) {
                TRex rex = spawn(run, baseline().withSuperRexChance(1.0));
                run.check(rex != null, "test setup: no spawn with superRexChance 1");
                run.check(rex instanceof SuperRex, "superRexChance 1 produced a plain T-rex");
                rex.discard();
            }

            int supers = 0;
            for (int roll = 0; roll < CHANCE_TRIALS; roll++) {
                TRex rex = spawn(run, baseline().withSuperRexChance(SUBSTITUTION_UNDER_TEST));
                run.check(rex != null, "test setup: spawn failed mid-trial (" + roll + ")");
                if (rex instanceof SuperRex) {
                    supers++;
                }
                rex.discard();
            }
            run.check(supers >= SUBSTITUTION_LOWEST && supers <= SUBSTITUTION_HIGHEST,
                CHANCE_TRIALS + " spawns at superRexChance " + SUBSTITUTION_UNDER_TEST + " produced " + supers + " super rexes, expected about " + (int) (CHANCE_TRIALS * SUBSTITUTION_UNDER_TEST));
        });
    }

    /** A super rex is a TRex for the spawner's purposes: it counts against maxRexPerPlayer and maxRexInWorld. */
    private static void countsAgainstTheCaps(GameTestHelper helper) {
        spawnerScenario(helper, run -> {
            TRex superRex = spawn(run, baseline().withSuperRexChance(1.0));
            run.check(superRex instanceof SuperRex, "test setup: the first spawn must be a super rex");
            // The caps are already reached (1 per player, 1 in world) with the super rex still in the level.
            run.check(!spawnExists(run, baseline()), "a second rex spawned although the super rex counts against the caps of 1");
            run.check(!spawnExists(run, baseline().withMaxRexPerPlayer(2)), "the super rex did not count against maxRexInWorld (1)");
            superRex.discard();
            run.check(spawnExists(run, baseline()), "with the super rex gone a spawn must work again");
        });
    }

    private static boolean spawnExists(Run run, SpawnRules rules) {
        TRex rex = spawn(run, rules);
        if (rex == null) {
            return false;
        }
        rex.discard();
        return true;
    }

    /** The definition of the variant, straight from the shipped config: faster on land, slower in water, bigger, harder hits. */
    private static void fasterBiggerSlowerInWater(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (SuperRexConfig.MOVEMENT_SPEED.get() <= RexConfig.MOVEMENT_SPEED.get()) {
            problems.add("super rex movementSpeed " + SuperRexConfig.MOVEMENT_SPEED.get() + " is not above the T-rex's " + RexConfig.MOVEMENT_SPEED.get());
        }
        if (SuperRexConfig.SWIM_SPEED_MULTIPLIER.get() >= RexConfig.SWIM_SPEED_MULTIPLIER.get()) {
            problems.add("super rex swimSpeedMultiplier " + SuperRexConfig.SWIM_SPEED_MULTIPLIER.get() + " is not below the T-rex's " + RexConfig.SWIM_SPEED_MULTIPLIER.get());
        }
        if (SuperRexConfig.SCALE.get() <= RexConfig.SCALE.get()) {
            problems.add("super rex scale " + SuperRexConfig.SCALE.get() + " is not above the T-rex's " + RexConfig.SCALE.get());
        }
        if (SuperRexConfig.ATTACK_DAMAGE.get() < RexConfig.ATTACK_DAMAGE.get()) {
            problems.add("super rex attackDamage " + SuperRexConfig.ATTACK_DAMAGE.get() + " is below the T-rex's " + RexConfig.ATTACK_DAMAGE.get());
        }
        if (SpawnConfig.SUPER_REX_CHANCE.get() != 0.05) {
            problems.add("superRexChance must default to 0.05 (one in 20), is " + SpawnConfig.SUPER_REX_CHANCE.get());
        }
        if (SuperRexConfig.DESPAWN_AFTER_SPOTTED_TICKS.get() < 1200 || SuperRexConfig.DESPAWN_AFTER_SPOTTED_TICKS.get() > 3600) {
            problems.add("despawnAfterSpottedTicks " + SuperRexConfig.DESPAWN_AFTER_SPOTTED_TICKS.get() + " is not in the 1-3 minute band");
        }
        if (problems.isEmpty()) {
            helper.succeed();
        } else {
            helper.fail(String.join("; ", problems));
        }
    }

    /**
     * The timed hunt: a natural super rex a survival player can see is gone about despawnAfterSpottedTicks
     * after the start (never before the clock could have run out), and an otherwise identical super rex
     * without the natural tag stays.
     */
    private static void despawnsAfterBeingSeen(GameTestHelper helper) {
        SuperRex[] timed = new SuperRex[1];
        SuperRex[] untagged = new SuperRex[1];
        int[] limit = new int[1];
        boolean[] sawClockRun = new boolean[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, HUNT_PLAYER_AT, false, DESPAWN_MAX_TICKS, origin -> {}, run -> {
            limit[0] = HUNT_TEST_LIMIT_TICKS;
            SuperRexConfig.DESPAWN_AFTER_SPOTTED_TICKS.set(HUNT_TEST_LIMIT_TICKS);
            run.cleanups.add(() -> SuperRexConfig.DESPAWN_AFTER_SPOTTED_TICKS.set(2400));
            run.check(SuperRexConfig.SPOTTED_RANGE.get() > HUNT_PLAYER_AT.distanceTo(HUNT_REX_AT), "test setup: the rex must start within spottedRange");

            run.rex.discard();
            ServerLevel level = helper.getLevel();
            timed[0] = ModEntities.SUPER_REX.get().spawn(level, run.origin.offset(0, 0, 21), EntitySpawnReason.COMMAND);
            untagged[0] = ModEntities.SUPER_REX.get().spawn(level, run.origin.offset(3, 0, 27), EntitySpawnReason.COMMAND);
            run.check(timed[0] != null && untagged[0] != null, "test setup: could not spawn the super rexes");
            run.cleanups.add(() -> {
                timed[0].discard();
                untagged[0].discard();
            });
            RexSpawner.markNatural(timed[0]);
        }, run -> {
            run.status = "timed super rex removed=" + timed[0].isRemoved() + " countdown=" + timed[0].despawnCountdownTicks() + " age=" + timed[0].tickCount;
            sawClockRun[0] |= timed[0].despawnCountdownTicks() >= 0;
            if (timed[0].isRemoved()) {
                run.check(sawClockRun[0], "the timed super rex vanished without its despawn clock ever starting");
                run.check(timed[0].tickCount >= DESPAWN_EARLIEST_TICKS,
                    "the timed super rex despawned after only " + timed[0].tickCount + " ticks, before the " + limit[0] + "-tick clock could run out");
                run.check(!untagged[0].isRemoved(), "a super rex without the natural tag was despawned by the timed-hunt rule");
                run.pass();
            }
        });
    }

    // Distance sanity for the hunt test arena (z 20.5 to 60.5 is 40 blocks, inside spottedRange 64 and inside the arena).
    static {
        if (HUNT_PLAYER_AT.distanceTo(HUNT_REX_AT) > 64.0 || Mth.floor(HUNT_REX_AT.z) > RexGameTests.ARENA_FRONT) {
            throw new IllegalStateException("super rex hunt test geometry is outside the arena/spottedRange");
        }
    }
}
