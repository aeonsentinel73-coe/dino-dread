package com.dinodread.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.dinodread.DinoDread;
import com.dinodread.config.RaptorConfig;
import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
import com.dinodread.entity.Velociraptor;
import com.dinodread.entity.ai.HuntState;
import com.dinodread.gametest.RexGameTests.Run;
import com.dinodread.registry.ModEntities;
import com.dinodread.spawn.RaptorRules;
import com.dinodread.spawn.RaptorSpawner;
import com.dinodread.spawn.RexSpawner;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.dinodread.gametest.RexGameTests.FACING_SOUTH;
import static com.dinodread.gametest.RexGameTests.MIDNIGHT_TICKS;
import static com.dinodread.gametest.RexGameTests.TEST_TIMEOUT_TICKS;
import static com.dinodread.gametest.RexGameTests.scenario;

/**
 * Velociraptor pack tests: natural packs spawn together under one pack tag, a pack whose member died flees and despawns,
 * raptors hunt hostile mobs the T-rex ignores, and the hunt goal never enters CHARGE for a raptor.
 */
public final class RexRaptorGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);
    private static final List<String> NAMES = new ArrayList<>();

    private static final Vec3 PLAYER_AT = new Vec3(0.5, 0, -8.5);
    private static final Vec3 FLANK_PLAYER_AT = new Vec3(0.5, 0, 8.5);
    private static final double RING_MIN = 24.0;
    private static final double RING_MAX = 40.0;
    private static final int RING_ATTEMPTS = 400;
    private static final int BACKSTOP_TICKS = 40;
    private static final int FLEE_TEST_TICKS = 120;
    private static final int FLEE_WATCH_TICKS = 1200;
    private static final int HUNT_STABLE_TICKS = 60;
    private static final int IGNORE_WATCH_TICKS = 100;
    private static final double PACK_SPREAD = 6.0;
    private static final double RING_EPSILON = 0.01;
    private static final int FLANK_WATCH_TICKS = 600;
    private static final int SCATTER_WATCH_TICKS = 900;

    static {
        test("raptor_pack_spawns_together_under_one_tag", RexRaptorGameTests::packSpawnsTogetherUnderOneTag);
        test("raptor_pack_flees_and_despawns_when_one_dies", RexRaptorGameTests::packFleesAndDespawnsWhenOneDies);
        test("raptor_hunts_a_zombie_and_never_charges", RexRaptorGameTests::raptorHuntsZombieAndNeverCharges);
        test("raptor_pack_flanks_its_quarry", RexRaptorGameTests::raptorPackFlanksItsQuarry);
        test("raptor_scatters_without_despawning", RexRaptorGameTests::raptorScattersWithoutDespawning);
        test("trex_ignores_hostile_mobs", RexRaptorGameTests::trexIgnoresHostileMobs);
        test("raptor_defaults_are_small_slow_and_quiet", RexRaptorGameTests::defaultsAreSmallSlowAndQuiet);
    }

    private RexRaptorGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        NAMES.add(name);
        FUNCTIONS.register(name, () -> body);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexRaptorGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        for (String name : NAMES) {
            RexGameTests.addTest(event, name, TEST_TIMEOUT_TICKS);
        }
    }

    /** Rules that let a pack through in the test arena. */
    private static RaptorRules baseline() {
        return new RaptorRules(true, true, 0.5, 1200, 1.0, List.of("minecraft:overworld"),
                RING_MIN, RING_MAX, RING_ATTEMPTS, 3, 4, (int) PACK_SPREAD, 8, 160.0, 12, true, 128.0);
    }

    /** As baseline(), but the pack lands on the flat arena floor around the player (the natural ring is off-platform terrain). */
    private static RaptorRules closeRing() {
        return new RaptorRules(true, true, 0.5, 1200, 1.0, List.of("minecraft:overworld"),
                6.0, 9.0, RING_ATTEMPTS, 3, 4, (int) PACK_SPREAD, 8, 160.0, 12, true, 128.0);
    }

    // ---------------------------------------------------------------- pack spawning

    /** A natural pack is 3-4 raptors, close together, sharing one pack tag and the natural tag. */
    private static void packSpawnsTogetherUnderOneTag(GameTestHelper helper) {
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, PLAYER_AT, false, BACKSTOP_TICKS, origin -> {}, run -> {
            run.rex.discard();
            ServerLevel level = helper.getLevel();
            java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
            for (Velociraptor existing : level.getEntities(EntityTypeTest.forClass(Velociraptor.class), raptor -> true)) {
                before.add(existing.getUUID());
            }
            int spawned = withMobSpawning(helper, () -> RaptorSpawner.trySpawnPack(level, run.player, baseline(), level.getRandom()));
            run.check(spawned >= 3 && spawned <= 4, "a natural pack must be packMinSize..packMaxSize (3..4) raptors, got " + spawned);

            // Only this test's pack: neighbouring test arenas can hold their own raptors.
            List<Velociraptor> pack = new ArrayList<>();
            for (Velociraptor raptor : level.getEntities(EntityTypeTest.forClass(Velociraptor.class), raptor -> true)) {
                if (!before.contains(raptor.getUUID())) {
                    pack.add(raptor);
                    run.cleanups.add(raptor::discard);
                }
            }
            run.check(pack.size() == spawned, "the level gained " + pack.size() + " raptors but the spawner reported " + spawned);

            String tag = pack.get(0).packTag();
            run.check(tag != null && tag.startsWith(Velociraptor.PACK_TAG_PREFIX), "the first pack member carries no pack tag: " + tag);
            for (Velociraptor raptor : pack) {
                run.check(tag.equals(raptor.packTag()), "a pack member is missing the shared pack tag");
                run.check(raptor.entityTags().contains(RexSpawner.NATURAL_TAG), "a natural raptor must carry the natural tag");
                run.check(raptor.isPersistenceRequired(), "a natural raptor must be persistent");
                double distance = Math.hypot(raptor.getX() - run.player.getX(), raptor.getZ() - run.player.getZ());
                run.check(distance >= RING_MIN - PACK_SPREAD - RING_EPSILON && distance <= RING_MAX + PACK_SPREAD + RING_EPSILON,
                        "a pack member spawned " + String.format("%.1f", distance) + " blocks from the player, outside the ring plus spread");
                double spread = raptor.position().subtract(pack.get(0).position()).horizontalDistance();
                run.check(spread <= PACK_SPREAD * 2.0 + 2.0, "pack members are " + String.format("%.1f", spread) + " blocks apart, looser than the configured spread");
            }
            run.pass();
        }, run -> {});
    }

    // ---------------------------------------------------------------- pack death -> flight -> despawn

    /**
     * Kill one member of a natural pack: every other member of that pack starts fleeing, and the whole pack despawns when
     * the flee runs out. An untagged raptor far away (an egg spawn) neither flees nor despawns.
     */
    private static void packFleesAndDespawnsWhenOneDies(GameTestHelper helper) {
        List<Velociraptor> survivors = new ArrayList<>();
        Velociraptor[] bystander = new Velociraptor[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, PLAYER_AT, false, FLEE_WATCH_TICKS, origin -> {}, run -> {
            run.rex.discard();
            ServerLevel level = helper.getLevel();
            java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
            for (Velociraptor existing : level.getEntities(EntityTypeTest.forClass(Velociraptor.class), raptor -> true)) {
                before.add(existing.getUUID());
            }
            int spawned = withMobSpawning(helper, () -> RaptorSpawner.trySpawnPack(level, run.player, baseline(), level.getRandom()));
            run.check(spawned >= 3, "test setup: the pack did not spawn (got " + spawned + ")");

            List<Velociraptor> pack = new ArrayList<>();
            for (Velociraptor raptor : level.getEntities(EntityTypeTest.forClass(Velociraptor.class), raptor -> true)) {
                if (!before.contains(raptor.getUUID())) {
                    pack.add(raptor);
                }
            }
            run.check(pack.size() == spawned, "test setup: expected " + spawned + " new raptors, found " + pack.size());

            // A lone untagged raptor (like a spawn-egg one), far from the pack: must neither flee nor despawn.
            bystander[0] = helper.spawnEntity(ModEntities.VELOCIRAPTOR.get(), new Vec3(0.5, 0, 50.5)).requirePersistence(true).spawn();
            run.cleanups.add(bystander[0]::discard);

            int fleeTicksWere = RaptorConfig.FLEE_TICKS.get();
            RaptorConfig.FLEE_TICKS.set(FLEE_TEST_TICKS);
            run.cleanups.add(() -> RaptorConfig.FLEE_TICKS.set(fleeTicksWere));

            Velociraptor victim = pack.get(0);
            victim.hurt(level.damageSources().generic(), 1000.0F);
            run.check(!victim.isAlive(), "test setup: the victim did not take the killing blow");
            for (Velociraptor survivor : pack) {
                if (survivor != victim) {
                    run.check(survivor.isFleeing(), "a pack member did not start fleeing after the pack death");
                    survivors.add(survivor);
                }
            }
            run.check(!bystander[0].isFleeing(), "an untagged raptor far from the death must not join the flight");
        }, run -> {
            boolean allGone = survivors.stream().allMatch(raptor -> raptor.isRemoved() || !raptor.isAlive());
            run.status = survivors.stream().filter(raptor -> !raptor.isRemoved() && raptor.isAlive()).count() + " of the pack still loaded";
            if (allGone) {
                run.check(bystander[0] != null && !bystander[0].isRemoved() && !bystander[0].isFleeing(),
                        "the untagged bystander fled or despawned with the pack: only natural raptors may");
                run.pass();
            }
        });
    }

    // ---------------------------------------------------------------- hunting hostiles, no charge

    /** A raptor that sees a zombie makes it its quarry and walks it down: the hunt state never becomes CHARGE. */
    private static void raptorHuntsZombieAndNeverCharges(GameTestHelper helper) {
        Zombie[] zombie = new Zombie[1];
        Vec3[] pinned = new Vec3[1];
        long[] firstSeenTick = new long[]{-1};
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, 900, origin -> {}, run -> {
            run.rex.discard();
            run.rex = spawnRaptor(helper, FACING_SOUTH, new Vec3(0.5, 0, 0.5));
            zombie[0] = helper.spawnEntity(EntityTypes.ZOMBIE, new Vec3(0.5, 0, 14.5)).requirePersistence(true).spawn();
            run.cleanups.add(zombie[0]::discard);
            pinned[0] = helper.absoluteVec(new Vec3(0.5, 0, 14.5));
        }, run -> {
            Zombie target = zombie[0];
            if (target.isRemoved() || !target.isAlive()) {
                run.fail("test setup: the zombie died before the watch window ended (firstSeen=" + firstSeenTick[0] + ")");
                return;
            }
            // Pin and heal the zombie: a wandering target can leave the arena and die on the void, and a dead target ends the hunt.
            target.setHealth(target.getMaxHealth());
            target.snapTo(pinned[0].x, pinned[0].y, pinned[0].z, 180.0F, 0.0F);
            TRex raptor = run.rex;
            if (raptor.getHuntState() == HuntState.CHARGE) {
                run.fail("a raptor entered the CHARGE state: raptors have no charge");
                return;
            }
            if (raptor.getTarget() == target && firstSeenTick[0] < 0) {
                firstSeenTick[0] = run.age();
            }
            run.status = "raptor target=" + (raptor.getTarget() == null ? "none" : raptor.getTarget().getType().getDescriptionId())
                    + " state=" + raptor.getHuntState() + " firstSeen=" + firstSeenTick[0] + " distance=" + String.format("%.1f", raptor.distanceTo(target));
            if (firstSeenTick[0] >= 0 && raptor.getTarget() == target && run.age() - firstSeenTick[0] >= HUNT_STABLE_TICKS) {
                run.pass();
            }
        });
    }

    /** The T-rex's senses must not pick up hostile mobs: a zombie nearby stays none of its business. */
    private static void trexIgnoresHostileMobs(GameTestHelper helper) {
        Zombie[] zombie = new Zombie[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, IGNORE_WATCH_TICKS + 40, origin -> {}, run -> {
            zombie[0] = helper.spawnEntity(EntityTypes.ZOMBIE, new Vec3(0.5, 0, 14.5)).requirePersistence(true).spawn();
            zombie[0].setHealth(zombie[0].getMaxHealth());
            run.cleanups.add(zombie[0]::discard);
        }, run -> {
            run.status = "the T-rex took a zombie as quarry";
            run.check(!(run.rex.getTarget() instanceof Zombie), "the T-rex must not target hostile mobs");
            run.check(!run.rex.getSenses().hasQuarry(), "the T-rex must not gain quarry from a hostile mob");
            if (run.age() >= IGNORE_WATCH_TICKS) {
                run.pass();
            }
        });
    }

    // ---------------------------------------------------------------- flanking

    /** A pack that hunts the player puts flankers on the circle: one in a pack of three, two in a pack of four. */
    private static void raptorPackFlanksItsQuarry(GameTestHelper helper) {
        java.util.Set<java.util.UUID> packIds = new java.util.HashSet<>();
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, FLANK_WATCH_TICKS, origin -> {}, run -> {
            run.rex.discard();
            ServerLevel level = helper.getLevel();
            java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
            for (Velociraptor existing : level.getEntities(EntityTypeTest.forClass(Velociraptor.class), raptor -> true)) {
                before.add(existing.getUUID());
            }
            int spawned = withMobSpawning(helper, () -> RaptorSpawner.trySpawnPack(level, run.player, closeRing(), level.getRandom()));
            run.check(spawned >= 3, "test setup: the pack did not spawn (got " + spawned + ")");
            for (Velociraptor raptor : level.getEntities(EntityTypeTest.forClass(Velociraptor.class), raptor -> true)) {
                if (!before.contains(raptor.getUUID())) {
                    packIds.add(raptor.getUUID());
                    run.cleanups.add(raptor::discard);
                }
            }
        }, run -> {
            // Neighbouring arenas can hold their own raptors: watch only this test's pack.
            List<? extends Velociraptor> pack = helper.getLevel().getEntities(EntityTypeTest.forClass(Velociraptor.class),
                    raptor -> raptor.isAlive() && packIds.contains(raptor.getUUID()));
            int hunting = 0;
            int flankers = 0;
            for (Velociraptor raptor : pack) {
                if (raptor.getTarget() != null) {
                    hunting++;
                }
                if (raptor.flankingThisTick()) {
                    flankers++;
                }
            }
            run.status = "pack=" + pack.size() + " hunting=" + hunting + " flanking=" + flankers;
            if (pack.size() >= 3 && hunting == pack.size()) {
                int expected = pack.size() >= 4 ? 2 : 1;
                if (flankers == expected) {
                    run.pass();
                } else if (run.age() > 200) {
                    run.fail("a pack of " + pack.size() + " hunting together should keep " + expected
                            + " raptor(s) on the flanks, saw " + flankers);
                }
            }
        });
    }

    // ---------------------------------------------------------------- scatter

    /** A raptor that loses its quarry runs away for a while - and, unlike after a pack death, stays in the world. */
    private static void raptorScattersWithoutDespawning(GameTestHelper helper) {
        Velociraptor[] lone = new Velociraptor[1];
        long[] scatterStartedAt = new long[]{-1};
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, PLAYER_AT, false, SCATTER_WATCH_TICKS, origin -> {}, run -> {
            run.rex.discard();
            lone[0] = spawnRaptor(helper, FACING_SOUTH, new Vec3(0.5, 0, 0.5));
            RexSpawner.markNatural(lone[0]); // natural-tagged: it despawns if any despawn rule fires during the run
            run.cleanups.add(lone[0]::discard);
        }, run -> {
            Velociraptor raptor = lone[0];
            if (raptor.isRemoved()) {
                run.fail("the scattering raptor was removed from the world");
                return;
            }
            if (scatterStartedAt[0] < 0) {
                if (raptor.getTarget() != null && isHuntGoalRunning(raptor)) {
                    raptor.getSenses().forget(); // the hunt goal stops: the quarry "got away"
                } else if (raptor.isScattering()) {
                    scatterStartedAt[0] = run.age();
                    run.check(!raptor.isFleeing(), "a lost prey must trigger the scatter, not the pack-death flight");
                }
            } else if (!raptor.isScattering() && run.age() - scatterStartedAt[0] > 20) {
                if (run.age() - scatterStartedAt[0] >= RaptorConfig.SCATTER_FLEE_TICKS.get()) {
                    run.check(!raptor.isFleeing(), "the scatter must not turn into the pack-death flight");
                    run.pass();
                } else {
                    run.fail("the scatter ended after " + (run.age() - scatterStartedAt[0]) + " ticks, before its clock ran out");
                }
            }
            run.status = "scatter start=" + scatterStartedAt[0] + " age=" + run.age() + " target=" + (raptor.getTarget() != null)
                    + " aggressive=" + raptor.isAggressive() + " state=" + raptor.getHuntState() + " fleeing=" + raptor.isFleeing();
        });
    }

    /**
     * True while the hunt goal holds its MOVE flags: the goal sets aggressive on every tick and clears it only in
     * stop(). Dropping the target before this point would be dropping it before the goal ever started - no stop(),
     * no scatter.
     */
    private static boolean isHuntGoalRunning(Velociraptor raptor) {
        return raptor.isAggressive();
    }

    // ---------------------------------------------------------------- config direction

    /** The shipped raptor defaults point the right way: smaller, terrible in water, quiet, packs of 3-4 that flee 30 s. */
    private static void defaultsAreSmallSlowAndQuiet(GameTestHelper helper) {
        if (RaptorConfig.SCALE.get() >= RexConfig.SCALE.get()) {
            helper.fail("raptors must be smaller than the T-rex");
        } else if (RaptorConfig.SWIM_SPEED_MULTIPLIER.get() >= 1.0 || RaptorConfig.SWIM_SPEED_MULTIPLIER.get() >= RexConfig.SWIM_SPEED_MULTIPLIER.get()) {
            helper.fail("raptors must be much slower swimmers than the T-rex");
        } else if (RaptorConfig.SWIM_DEPTH.get() != 0.0) {
            helper.fail("raptors must not swim submerged like the T-rex");
        } else if (RaptorConfig.PACK_MIN_SIZE.get() != 3 || RaptorConfig.PACK_MAX_SIZE.get() != 4) {
            helper.fail("natural packs must be 3-4");
        } else if (RaptorConfig.FLEE_TICKS.get() != 600) {
            helper.fail("the flee must default to 30 seconds");
        } else if (!RaptorConfig.HUNT_HOSTILE_MOBS.get()) {
            helper.fail("raptors must hunt hostile mobs by default");
        } else if (!RaptorConfig.NIGHT_ONLY.get()) {
            helper.fail("natural packs must be a night event by default");
        } else {
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Velociraptor spawnRaptor(GameTestHelper helper, float yRot, Vec3 pos) {
        Velociraptor raptor = helper.spawnEntity(ModEntities.VELOCIRAPTOR.get(), pos).requirePersistence(true).spawn();
        raptor.setYRot(yRot);
        raptor.setYHeadRot(yRot);
        raptor.setYBodyRot(yRot);
        return raptor;
    }

    /** Runs the body with the spawn_mobs game rule on, and puts it back afterwards (the game test server turns it off). */
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
}
