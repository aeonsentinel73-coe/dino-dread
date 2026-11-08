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

import net.minecraft.core.BlockPos;
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
    // Same upper-bound logic as SCATTER_WATCH_TICKS: target acquisition is variable, and a passing run
    // exits as soon as flanking is observed.
    // Upper bound only: a 20-block circle at body-capped arc speed plus the sweep of 180 degrees takes seconds,
    // and pack acquisition can burn most of a minute in a slow run.
    private static final int FLANK_WATCH_TICKS = 2400;
    // Retreat test: the walk-back caps at 32 blocks (~490 ticks); after that the pack just needs to close and
    // finish the circles. Acquisition is variable (observed up to ~700 ticks) and a flanker can spend time
    // re-climbing onto the ring after a knockback (observed one at 174 deg of 180 while hugging the quarry at
    // 14 blocks) - so the window mirrors the plain flank test's generosity.
    private static final int RETREAT_WATCH_TICKS = 2600;
    private static final int FLANK_GROUND_HALF = 48;
    // The scatter clock starts only once the raptor happens to spot the player and the hunt begins, which is
    // variable (observed up to ~700 ticks); the test then needs scatterFleeTicks + 20 more to see it end.
    // The window is an upper bound only - a passing run exits the moment the scatter completes.
    private static final int SCATTER_WATCH_TICKS = 2400;
    private static final int CHASE_WATCH_TICKS = 2400;
    // Water exit: the rex lake (its z span and surface are fixed by RexWaterGameTests' builder), shallow variant.
    private static final int WATER_LAKE_DEPTH = 3;
    private static final double WATER_SURFACE_Y = -2 + 8.0 / 9.0;
    private static final Vec3 WATER_PLAYER_AT = new Vec3(0.5, 0, 40.5);
    private static final double BANK_MIN_Z = 36.0;
    private static final int WATER_WATCH_TICKS = 1200;

    static {
        test("raptor_pack_spawns_together_under_one_tag", RexRaptorGameTests::packSpawnsTogetherUnderOneTag);
        test("raptor_pack_flees_and_despawns_when_one_dies", RexRaptorGameTests::packFleesAndDespawnsWhenOneDies);
        test("raptor_hunts_a_zombie_and_never_charges", RexRaptorGameTests::raptorHuntsZombieAndNeverCharges);
        test("raptor_pack_flanks_its_quarry", RexRaptorGameTests::raptorPackFlanksItsQuarry);
        test("raptor_flank_survives_slow_retreat", RexRaptorGameTests::raptorFlankSurvivesSlowRetreat);
        test("raptor_flank_gives_up_when_pinned_terrain_beats_it", RexRaptorGameTests::raptorFlankGivesUpWhenPinnedTerrainBeatsIt);
        test("raptor_pack_shares_the_hunt", RexRaptorGameTests::raptorPackSharesTheHunt);
        test("raptor_pack_holds_the_hunt_together", RexRaptorGameTests::raptorPackHoldsTheHuntTogether);
        test("raptor_pack_holds_hunt_across_a_stretched_pack", RexRaptorGameTests::raptorPackHoldsHuntAcrossStretchedPack);
        test("raptor_survival_timer_ends_a_staring_hunt", RexRaptorGameTests::raptorSurvivalTimerEndsAStaringHunt);
        test("raptor_scatters_without_despawning", RexRaptorGameTests::raptorScattersWithoutDespawning);
        test("raptor_chase_outruns_a_walking_player", RexRaptorGameTests::raptorChaseOutrunsWalkingPlayer);
        test("raptor_intercept_cutoff_catches_a_runner", RexRaptorGameTests::raptorInterceptCutoffCatchesARunner);
        test("raptor_intercept_turn_breaks_the_cutoff", RexRaptorGameTests::raptorInterceptTurnBreaksTheCutoff);
        test("raptor_climbs_out_of_water", RexRaptorGameTests::raptorClimbsOutOfWater);
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

            // Per-entity, NOT the static knob: arenas run concurrently and a global FLEE_TICKS edit would
            // silently change every other fleeing test's clock mid-run.
            for (Velociraptor member : pack) {
                member.debugOverride(null, null, FLEE_TEST_TICKS);
            }

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
        java.util.Set<java.util.UUID> everFlanked = new java.util.HashSet<>();
        java.util.Map<java.util.UUID, Double> sweepSeen = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Integer> lastMoving = new java.util.HashMap<>();
        Runnable[] undoGround = new Runnable[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, FLANK_WATCH_TICKS, origin -> {
            // The flank circle (radius from config, 20 by default) does not fit the standard arena: lay stone out
            // to the circle plus margin and force those chunks ticking. The test restores the ground itself.
            undoGround[0] = widenGround(helper.getLevel(), origin);
        }, run -> {
            run.rex.discard();
            run.cleanups.add(() -> undoGround[0].run());
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
            // The straight chasers would kill a plain fixture in two or three bites, long before the flankers
            // finish a 20-block circle (~250 ticks). Bites must land but never kill: the quarry lives to be circled.
            run.player.setHealth(run.player.getMaxHealth());
            // And the quarry must STAY put: every bite applies vanilla damage knockback (halved velocity +
            // 0.4 impulse + upward pop), and a healed fixture bitten forever eventually hops off the floor -
            // then the raptors legitimately cannot see it and INVESTIGATE forever (test flake, not entity bug).
            run.player.setPos(FLANK_PLAYER_AT.x + run.origin.getX(), FLANK_PLAYER_AT.y + run.origin.getY(),
                    FLANK_PLAYER_AT.z + run.origin.getZ());
            // Neighbouring arenas can hold their own raptors: watch only this test's pack.
            List<? extends Velociraptor> pack = helper.getLevel().getEntities(EntityTypeTest.forClass(Velociraptor.class),
                    raptor -> raptor.isAlive() && packIds.contains(raptor.getUUID()));
            int hunting = 0;
            int circling = 0;
            for (Velociraptor raptor : pack) {
                if (raptor.getTarget() != null) {
                    hunting++;
                }
                if (raptor.flankingThisTick()) {
                    everFlanked.add(raptor.getUUID());
                    circling++;
                }
            }
            run.status = "pack=" + pack.size() + " hunting=" + hunting + " flanking=" + circling
                    + " flankedEver=" + everFlanked.size();
            flankProgressVerdict(run, everFlanked, sweepSeen, lastMoving, pack,
                    expectedFlankers(pack.size()), FLANK_WATCH_TICKS,
                    "a pack of " + pack.size() + " hunting together: ");
        });
    }

    /**
     * Shared flank verdict: everyone who EVER flanked must COMPLETE their circle, and a circle only counts as
     * broken when it stops PROGRESSING (sweep gains < 30 deg over 600 ticks) or the window runs out. Timing is
     * variable (night acquisition up to ~700 ticks; a flanker knocked deep inside the ring by its own bite
     * spends real seconds re-climbing to r40 before its next degrees of sweep) - a plain elapsed-time fail
     * blamed the end hook for what was only a slow run.
     */
    private static void flankProgressVerdict(Run run, java.util.Set<java.util.UUID> everFlanked,
            java.util.Map<java.util.UUID, Double> sweepSeen, java.util.Map<java.util.UUID, Integer> lastMoving,
            List<? extends Velociraptor> pack, int expected, int windowEnd, String failPrefix) {
        int tick = (int) run.age();
        for (Velociraptor raptor : pack) {
            java.util.UUID id = raptor.getUUID();
            if (!everFlanked.contains(id)) {
                continue;
            }
            if (raptor.hasCompletedFlank()) {
                sweepSeen.remove(id);
                lastMoving.remove(id);
                continue;
            }
            double sweep = raptor.flankSweepDegrees();
            Integer moved = lastMoving.get(id);
            if (moved == null || sweep - sweepSeen.getOrDefault(id, 0.0) >= 30.0) {
                lastMoving.put(id, tick);
                sweepSeen.put(id, sweep);
            } else if (tick - moved > 600) {
                run.fail(failPrefix + "flank stalled at " + String.format("%.0f", sweep)
                        + " deg with no progress for 600 ticks [" + raptor.flankDebug() + "]"
                        + " origin=(" + run.origin.getX() + "," + run.origin.getY() + "," + run.origin.getZ() + ")"
                        + " quarry=(" + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f",
                                run.player.getX(), run.player.getY(), run.player.getZ())
                        + ") raptor=(" + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f",
                                raptor.getX(), raptor.getY(), raptor.getZ()) + ")"
                        + " raptorBlockBelow=" + run.helper.getLevel().getBlockState(
                                raptor.blockPosition().below()) + " " + run.status);
                return;
            }
        }
        boolean allFlanked = everFlanked.size() >= expected;
        boolean allDone = true;
        for (Velociraptor raptor : pack) {
            if (everFlanked.contains(raptor.getUUID()) && !raptor.hasCompletedFlank()) {
                allDone = false;
            }
        }
        if (allFlanked && allDone) {
            run.pass();
        } else if (tick > windowEnd - 200) {
            StringBuilder diag = new StringBuilder();
            for (Velociraptor raptor : pack) {
                if (everFlanked.contains(raptor.getUUID())) {
                    diag.append(" [").append(raptor.flankDebug()).append(']');
                }
            }
            run.fail(failPrefix + "flankers never finished their circles: " + run.status + diag);
        }
    }

    /** One flanker in a pack of three, two in a pack of four (mirrors the entity's slot assignment). */
    private static int expectedFlankers(int packSize) {
        return packSize >= 4 ? 2 : packSize == 3 ? 1 : 0;
    }

    /**
     * A quarry that BACKS AWAY at walking pace must not break the flank circles: retreating from a straight
     * chase is exactly the move flanking exists to punish. Live playtest at radius 40: the flankers started
     * their circles, then immediately broke out of them while the quarry walked backwards, lost the ~40 blocks
     * of ring they were standing on, and fell behind even the walkers. The stall valve must watch the QUARRY
     * FLEEING, not a momentarily flat bearing.
     */
    private static void raptorFlankSurvivesSlowRetreat(GameTestHelper helper) {
        java.util.Set<java.util.UUID> packIds = new java.util.HashSet<>();
        java.util.Set<java.util.UUID> everFlanked = new java.util.HashSet<>();
        java.util.Map<java.util.UUID, Double> sweepSeen = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Integer> lastMoving = new java.util.HashMap<>();
        Runnable[] undoGround = new Runnable[1];
        Vec3[] start = new Vec3[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, RETREAT_WATCH_TICKS, origin -> {
            undoGround[0] = widenGround(helper.getLevel(), origin);
        }, run -> {
            run.rex.discard();
            run.cleanups.add(() -> undoGround[0].run());
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
            run.player.setHealth(run.player.getMaxHealth());
            if (start[0] == null) {
                start[0] = run.player.position();
            }
            // Real back-walk pace: 0.065 blocks/tick ~ 1.3 blocks/s, straight line, capped so the test stays
            // inside the widened ground (the flank circle must hold while backing up, wherever it is).
            run.player.setPos(start[0].x, start[0].y, start[0].z - Math.min(32.0, 0.065 * run.age()));
            List<? extends Velociraptor> pack = helper.getLevel().getEntities(EntityTypeTest.forClass(Velociraptor.class),
                    raptor -> raptor.isAlive() && packIds.contains(raptor.getUUID()));
            int circling = 0;
            for (Velociraptor raptor : pack) {
                if (raptor.flankingThisTick()) {
                    everFlanked.add(raptor.getUUID());
                    circling++;
                }
            }
            run.status = "pack=" + pack.size() + " flanking=" + circling + " flankedEver=" + everFlanked.size()
                    + " backed=" + String.format("%.0f", Math.min(32.0, 0.065 * run.age()));
            flankProgressVerdict(run, everFlanked, sweepSeen, lastMoving, pack,
                    expectedFlankers(pack.size()), RETREAT_WATCH_TICKS, "walking backwards broke the flank: ");
        });
    }

    /**
     * The pack hunts as one: a raptor that can neither see nor hear the player must still join through a
     * pack-mate that can. Live playtest: raptors only chased after the player ran past each one individually.
     * A stone wall hides two of three raptors from the (motionless, therefore silent) player fixture; only the
     * third can see the player at all. All three must end up hunting the same quarry.
     */
    private static void raptorPackSharesTheHunt(GameTestHelper helper) {
        Velociraptor[] trio = new Velociraptor[3];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, SHARE_WATCH_TICKS, origin -> {
            // Wall across the arena at z = -4, three blocks tall: no line of sight between z < -4 and the player.
            net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            for (int x = -RexGameTests.ARENA_HALF_WIDTH; x <= RexGameTests.ARENA_HALF_WIDTH; x++) {
                for (int y = 0; y <= 2; y++) {
                    helper.getLevel().setBlock(origin.offset(x, y, -4), stone, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }, run -> {
            run.rex.discard();
            String packTag = Velociraptor.PACK_TAG_PREFIX + "gametest";
            // The caller stands in front of the wall, facing the player (yaw 0 = +z). The hidden pair wait behind it.
            trio[0] = spawnRaptor(helper, 0.0F, new Vec3(0.5, 0, 2.5));
            trio[1] = spawnRaptor(helper, 180.0F, new Vec3(-2.5, 0, -6.5));
            trio[2] = spawnRaptor(helper, 180.0F, new Vec3(2.5, 0, -9.5));
            for (Velociraptor raptor : trio) {
                raptor.addTag(packTag);
                run.cleanups.add(raptor::discard);
            }
            // The hidden pair must start blind: the wall, not luck, is what the test proves.
            run.check(!trio[1].getSenses().hasQuarry() && !trio[2].getSenses().hasQuarry(),
                    "test setup: a raptor behind the wall already has quarry");
        }, run -> {
            run.player.setHealth(run.player.getMaxHealth());
            int hunting = 0;
            StringBuilder diag = new StringBuilder();
            for (int i = 0; i < trio.length; i++) {
                if (trio[i] == null) {
                    continue;
                }
                if (trio[i].getTarget() == run.player) {
                    hunting++;
                }
                diag.append(" r").append(i).append(trio[i].getTarget() == null ? ":-" : ":+")
                        .append(String.format("%.0f", trio[i].distanceTo(run.player)));
            }
            run.status = "hunting=" + hunting + "/3" + diag;
            if (hunting == trio.length) {
                run.pass();
            } else if (run.age() > SHARE_WATCH_TICKS - 200) {
                run.fail("the pack must hunt as one; the pair behind the wall never got the shared quarry: " + run.status);
            }
        });
    }

    private static final int SHARE_WATCH_TICKS = 1200;

    /**
     * The post-flank cut-off surge: a flanker that finishes its circle closes on where the quarry is GOING at
     * flank speed, so running in a straight line gets you met ahead of your own nose. Live playtest design: the
     * front bite should knock the player back INTO the pack. This drives the surge directly (flank completion
     * is stochastic), with the quarry fleeing at 5.4 b/s - an ordinary chase (5.4) never closes that gap, the
     * surge (7.9) must close it and bite.
     */
    private static void raptorInterceptCutoffCatchesARunner(GameTestHelper helper) {
        Velociraptor[] striker = new Velociraptor[1];
        Runnable[] undoGround = new Runnable[1];
        double[] distanceAtStart = new double[]{-1};
        boolean[] everBitten = new boolean[]{false};
        boolean[] surgeArmed = new boolean[]{false};
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, 400, origin -> {
            // The runner needs a corridor to the arena front and past it (widenGround stops at z = player + 24).
            undoGround[0] = widenGround(helper.getLevel(), origin);
            net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            for (int x = -2; x <= 3; x++) {
                for (int z = 25; z <= 44; z++) {
                    helper.getLevel().setBlock(origin.offset(x, -1, z), stone, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
            helper.getLevel().setChunkForced(net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX()),
                    net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ() + 44), true);
        }, run -> {
            run.rex.discard();
            run.cleanups.add(() -> undoGround[0].run());
            // The striker waits 12 blocks off the runner's line, facing down the corridor.
            striker[0] = spawnRaptor(helper, 0.0F, new Vec3(12.5, 0, 8.5));
            run.cleanups.add(striker[0]::discard);
        }, run -> {
            run.player.setHealth(run.player.getMaxHealth());
            if (run.player.hurtTime > 0) {
                everBitten[0] = true;
            }
            if (run.age() >= 30) {
                // The runner flees straight down the corridor at ~5.4 b/s once the hunt is established.
                // Teleport-driven (mock players zero setDeltaMovement via friction: no client input).
                run.player.setPos(run.player.getX(), run.player.getY(), run.player.getZ() + 0.27);
                run.player.setDeltaMovement(0.0, run.player.getDeltaMovement().y, 0.27);
            }
            if (run.age() >= 40 && !surgeArmed[0] && striker[0].getTarget() == run.player) {
                // Arm once the hunt is real (acquisition is stochastic; a fixed tick could outrun it).
                striker[0].debugIntercept(120);
                surgeArmed[0] = true;
            }
            if (run.age() == 60) {
                distanceAtStart[0] = striker[0].distanceTo(run.player);
            }
            run.status = "gap=" + String.format("%.1f", striker[0].distanceTo(run.player))
                    + " at60=" + String.format("%.1f", distanceAtStart[0])
                    + " surging=" + striker[0].interceptingThisTick() + " bitten=" + everBitten[0]
                    + " armed=" + surgeArmed[0];
            if (run.age() == 60) {
                // Holding the line PAST the dodge window commits the surge: same straight line from the arm means
                // the turn check never fires and the striker is still surging half a second+ later.
                run.check(surgeArmed[0] && striker[0].interceptingThisTick(),
                        "a straight-running quarry lost the surge it should have committed: " + run.status);
            }
            if (run.age() >= 160) {
                run.check(surgeArmed[0], "test setup: the striker never took the runner as quarry: " + run.status);
                run.check(everBitten[0], "the cut-off surge never bit the straight-line runner: " + run.status);
                run.pass();
            }
        });
    }

    /**
     * The surge's dodge: the first half-second after the cut-off arms belongs to the quarry. Here the runner
     * swings 90 degrees off its line the tick after arming - the accumulated traveled direction passes the
     * 60-degree limit inside the window, the striker gives up the cut-off, and is no longer surging long
     * before the surge's own expiry (so "not surging" can only mean CANCELLED, not elapsed). The straight
     * sister test above proves the other half of the rule: hold the line and it stays committed.
     */
    private static void raptorInterceptTurnBreaksTheCutoff(GameTestHelper helper) {
        Velociraptor[] striker = new Velociraptor[1];
        Runnable[] undoGround = new Runnable[1];
        int[] armedAt = new int[]{-1};
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, 200, origin -> {
            undoGround[0] = widenGround(helper.getLevel(), origin);
        }, run -> {
            run.rex.discard();
            run.cleanups.add(() -> undoGround[0].run());
            striker[0] = spawnRaptor(helper, 0.0F, new Vec3(12.5, 0, 8.5));
            run.cleanups.add(striker[0]::discard);
        }, run -> {
            run.player.setHealth(run.player.getMaxHealth());
            // A mock ServerPlayer has no client input, so travel() zeroes its velocity every tick -
            // setDeltaMovement alone moves NOTHING (the retreat test walks with setPos for the same reason).
            // Walk by teleport, and mirror the walk into deltaMovement: that is what a real player's velocity
            // looks like, and it is exactly what the surge's arm snapshot and prediction read.
            var vel = run.player.getDeltaMovement();
            if (run.age() >= 30 && armedAt[0] < 0) {
                // Straight line down the corridor first, so the arm snapshots a real +z travel direction.
                run.player.setPos(run.player.getX(), run.player.getY(), run.player.getZ() + 0.27);
                run.player.setDeltaMovement(0.0, vel.y, 0.27);
                if (striker[0].getTarget() == run.player) {
                    striker[0].debugIntercept(160);
                    armedAt[0] = (int) run.age();
                }
            } else if (armedAt[0] >= 0) {
                // The turn: 90 degrees off the +z line the arm saw, held from the tick after arming.
                run.player.setPos(run.player.getX() + 0.27, run.player.getY(), run.player.getZ());
                run.player.setDeltaMovement(0.27, vel.y, 0.0);
            }
            run.status = "armedAt=" + armedAt[0] + " surging=" + striker[0].interceptingThisTick()
                    + " gap=" + String.format("%.1f", striker[0].distanceTo(run.player));
            if (run.age() >= 90) {
                run.check(armedAt[0] >= 0, "test setup: the striker never took the runner as quarry: " + run.status);
                // Target must STILL be the runner: "not surging" has to mean CANCELLED, never a lost target
                // quietly clearing the flag.
                run.check(striker[0].getTarget() == run.player && !striker[0].interceptingThisTick(),
                        "the 90-degree turn inside the dodge window did not break the cut-off: " + run.status);
                run.pass();
            }
        });
    }

    /**
     * The hills valve (live playtest, hilly worlds): a flanker the pathfinder cannot keep on the ring used to
     * haunt the quarry forever - the stall valve is ring-gated, so a raptor flung off-ring by cliffs, ridges
     * or washouts never counted stall, never completed, never abandoned (two hilly worlds: zero flankdiag
     * lines while the user watched circles evaporate). Here the whole pack is PINNED mid-hunt - bodies frozen,
     * bearings frozen, sweep can never earn a degree - so the 20 s unproductive ceiling is the only exit.
     * Every raptor that ever started a circle must give it up (done, no longer circling) while STILL hunting:
     * the circle ends and the ordinary chase takes over, instead of the silent periphery-haunting of before.
     */
    private static void raptorFlankGivesUpWhenPinnedTerrainBeatsIt(GameTestHelper helper) {
        java.util.Set<java.util.UUID> everFlanked = new java.util.HashSet<>();
        java.util.Set<java.util.UUID> packIds = new java.util.HashSet<>();
        java.util.Map<java.util.UUID, org.joml.Vector3d> pinned = new java.util.HashMap<>();
        Runnable[] undoGround = new Runnable[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, 1200, origin -> {
            undoGround[0] = widenGround(helper.getLevel(), origin);
        }, run -> {
            run.rex.discard();
            run.cleanups.add(() -> undoGround[0].run());
            int spawned = withMobSpawning(helper, () -> RaptorSpawner.trySpawnPack(helper.getLevel(), run.player, closeRing(), helper.getLevel().getRandom()));
            run.check(spawned >= 3, "test setup: the pack did not spawn (got " + spawned + ")");
            for (Velociraptor raptor : helper.getLevel().getEntities(EntityTypeTest.forClass(Velociraptor.class), r -> true)) {
                if (raptor.isAlive()) {
                    packIds.add(raptor.getUUID());
                    run.cleanups.add(raptor::discard);
                }
            }
        }, run -> {
            run.player.setHealth(run.player.getMaxHealth());
            List<? extends Velociraptor> pack = helper.getLevel().getEntities(EntityTypeTest.forClass(Velociraptor.class),
                    raptor -> raptor.isAlive() && packIds.contains(raptor.getUUID()));
            if (run.age() >= 30) {
                // Freeze every body from tick 30 on: bearings cannot move, so no circle can EVER earn a
                // degree - the only way out is the unproductive ceiling (400 ticks + slack below).
                for (Velociraptor raptor : pack) {
                    org.joml.Vector3d at = pinned.computeIfAbsent(raptor.getUUID(),
                            id -> new org.joml.Vector3d(raptor.getX(), raptor.getY(), raptor.getZ()));
                    raptor.setPos(at.x, at.y, at.z);
                    raptor.setDeltaMovement(0.0, 0.0, 0.0);
                }
            }
            boolean anyFlanked = false;
            boolean allGaveUp = true;
            int hunting = 0;
            for (Velociraptor raptor : pack) {
                if (raptor.getTarget() != null) {
                    hunting++;
                }
                if (raptor.flankingThisTick()) {
                    everFlanked.add(raptor.getUUID());
                }
                if (everFlanked.contains(raptor.getUUID())) {
                    anyFlanked = true;
                    if (raptor.flankingThisTick() || !raptor.hasCompletedFlank()) {
                        allGaveUp = false;
                    }
                }
            }
            run.status = "pack=" + pack.size() + " hunting=" + hunting + " flankedEver=" + everFlanked.size();
            if (anyFlanked && allGaveUp) {
                run.check(hunting == pack.size(), "the pack dropped the quarry instead of reverting to the chase: " + run.status);
                run.pass();
            } else if (run.age() > 900) {
                for (Velociraptor raptor : pack) {
                    run.status += " [" + raptor.flankDebug() + "]";
                }
                run.fail("the pinned pack never gave up its doomed circles within the ceiling: " + run.status);
            }
        });
    }

    /**
     * The pack holds the hunt together: a raptor that can no longer perceive the quarry stays on it while a
     * pack-mate can. Live playtest: a sprinting player watched the pack peel away one raptor at a time - the
     * give-up clocks were individual, so every straggler that fell out of sight and hearing scattered on its
     * own schedule. Here the sight range is cut to 16 and two of three tagged raptors sit 20+ blocks from the
     * (motionless, silent) player: they can NEVER perceive it, and before the fix they abandoned at the 12 s
     * give-up tick. They must still be hunting well past it.
     */
    private static void raptorPackHoldsTheHuntTogether(GameTestHelper helper) {
        Velociraptor[] trio = new Velociraptor[3];
        Runnable[] undoGround = new Runnable[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, 700, origin -> {
            // The blind pair need floor out to the arena corners (widenGround covers x +-24).
            undoGround[0] = widenGround(helper.getLevel(), origin);
        }, run -> {
            run.cleanups.add(undoGround[0]::run);
            run.rex.discard();
            String packTag = Velociraptor.PACK_TAG_PREFIX + "gametest";
            // The caller is 6 blocks from the player inside the 16-block sight; the pair are 20-29: blind, and
            // the motionless silent player gives them nothing to hear either.
            trio[0] = spawnRaptor(helper, 0.0F, new Vec3(0.5, 0, 2.5));
            trio[1] = spawnRaptor(helper, 180.0F, new Vec3(-20.5, 0, -12.5));
            trio[2] = spawnRaptor(helper, 180.0F, new Vec3(-14.5, 0, -12.5));
            for (Velociraptor raptor : trio) {
                // Sight 16 (the caller sees the player, the pair don't) and flank ring 8 (keep the ring inside
                // that sight so a circling caller stays visible) - per-entity so other arenas' flankers, which
                // run CONCURRENTLY in the same JVM, keep their real 32-block sight and 40-block ring.
                raptor.debugOverride(8.0, 16.0, null);
                raptor.addTag(packTag);
                run.cleanups.add(raptor::discard);
            }
        }, run -> {
            run.player.setHealth(run.player.getMaxHealth());
            int hunting = 0;
            boolean scattered = false;
            StringBuilder diag = new StringBuilder();
            for (int i = 0; i < trio.length; i++) {
                if (trio[i] == null) {
                    continue;
                }
                if (trio[i].getTarget() == run.player) {
                    hunting++;
                }
                scattered |= trio[i].isScattering();
                diag.append(" r").append(i).append(trio[i].getTarget() == null ? ":-" : ":+")
                        .append(String.format("%.0f", trio[i].distanceTo(run.player)));
            }
            run.status = "hunting=" + hunting + "/3 scattered=" + scattered + diag;
            // Past the give-up horizon (240 from the last shared contact) the blind pair must still be holding.
            if (run.age() > 300 && (hunting < trio.length || scattered)) {
                run.fail("the pack must hold the hunt while ANY member perceives the quarry: " + run.status);
            }
            if (run.age() >= 500 && hunting == trio.length && !scattered) {
                run.pass();
            }
        });
    }

    /**
     * The stragglers of a stretched pack: the same fixture as the holds-the-hunt test, but the blind pair sit
     * 36-39 blocks from the caller - OUTSIDE the old 32-block earshot that used to gate the whisper network,
     * inside the 72-block cohesion radius. A formation stretched by hills or a retreat, which is exactly how a
     * live pack runs: "one or two of the pack would lose me a little bit after the start" (live playtest).
     * They must be conscripted by the pack anyway and hold the hunt past the 12 s give-up clock. Pre-hardening
     * this failed: the pair never heard the hunt at all, wandered blind, and the caller held the player alone.
     */
    private static void raptorPackHoldsHuntAcrossStretchedPack(GameTestHelper helper) {
        Velociraptor[] trio = new Velociraptor[3];
        Runnable[] undoGround = new Runnable[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, 700, origin -> {
            undoGround[0] = widenGround(helper.getLevel(), origin);
        }, run -> {
            run.cleanups.add(undoGround[0]::run);
            run.rex.discard();
            String packTag = Velociraptor.PACK_TAG_PREFIX + "gametest_stretched";
            // The caller is 6 blocks from the player inside the 16-block sight; the pair are 33-44 from the
            // player (blind) and 36-39 from every packmate who holds the quarry (past the old earshot).
            trio[0] = spawnRaptor(helper, 0.0F, new Vec3(0.5, 0, 2.5));
            trio[1] = spawnRaptor(helper, 180.0F, new Vec3(-20.5, 0, -30.5));
            trio[2] = spawnRaptor(helper, 180.0F, new Vec3(-14.5, 0, -30.5));
            for (Velociraptor raptor : trio) {
                raptor.debugOverride(8.0, 16.0, null);
                raptor.addTag(packTag);
                run.cleanups.add(raptor::discard);
            }
        }, run -> {
            run.player.setHealth(run.player.getMaxHealth());
            int hunting = 0;
            StringBuilder diag = new StringBuilder();
            for (int i = 0; i < trio.length; i++) {
                if (trio[i] == null) {
                    continue;
                }
                if (trio[i].getTarget() == run.player) {
                    hunting++;
                }
                diag.append(" r").append(i).append(trio[i].getTarget() == null ? ":-" : ":+")
                        .append(String.format("%.0f", trio[i].distanceTo(run.player)));
            }
            run.status = "hunting=" + hunting + "/3" + diag;
            if (run.age() > 300 && hunting < trio.length) {
                run.fail("a pack stretched past earshot must still share one hunt: " + run.status);
            }
            if (run.age() >= 500 && hunting == trio.length) {
                run.pass();
            }
        });
    }

    /**
     * The survival timer (60 s by default; pinned to 10 s here via the entity-scoped override): a hunt against
     * ONE quarry ends even while that quarry stands in plain sight, because on rough terrain "can see you" and
     * "can reach you" are not the same thing and the silence-based give-up clock never fires on a staring
     * match. The capped raptor then ignores that quarry for a 30 s blackout, so standing exposed in the open
     * stops being a permanent death sentence; when the blackout ends it may pick the hunt back up (or has
     * wandered off, which is also survival).
     */
    private static void raptorSurvivalTimerEndsAStaringHunt(GameTestHelper helper) {
        Velociraptor[] lone = new Velociraptor[1];
        long[] huntStart = {-1};
        boolean[] teleported = {false};
        StringBuilder trace = new StringBuilder("trace:");
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, 1150, origin -> {
        }, run -> {
            run.rex.discard();
            lone[0] = spawnRaptor(helper, 180.0F, new Vec3(0.5, 0, 5.5));
            lone[0].debugOverride(null, null, null, 200); // 10 s cap; blackout stays the real 600 t
            run.cleanups.add(lone[0]::discard);
        }, run -> {
            run.player.setHealth(run.player.getMaxHealth());
            Velociraptor raptor = lone[0];
            if (raptor == null) {
                return;
            }
            boolean hunting = raptor.getTarget() == run.player;
            if (hunting && huntStart[0] < 0) {
                huntStart[0] = run.age(); // every window below is relative to when THIS arena's hunt began
            }
            long since = huntStart[0] < 0 ? -1 : run.age() - huntStart[0];
            run.status = "hunting=" + hunting + " start=" + huntStart[0] + " d="
                    + String.format("%.0f", raptor.distanceTo(run.player))
                    + " scatter=" + raptor.isScattering() + trace;
            if (run.age() % 50 == 0) {
                trace.append(' ').append(run.age()).append(":")
                        .append(hunting ? 'H' : '-').append(raptor.isScattering() ? 'S' : '-')
                        .append(String.format("%.0f", raptor.distanceTo(run.player)));
            }
            if (huntStart[0] < 0) {
                return;
            }
            // The cap fires ~200 t after the hunt begins; the 600 t blackout then forbids re-acquisition
            // until ~600 t after the cap. (Bite knockback can flicker the target for a tick or two at
            // melee, so the "must be quit" window starts a full 50 t past the cap.)
            if (since > 250 && since < 290 && hunting) {
                run.fail("the survival timer must end a hunt even with perfect line of sight: " + run.status);
            }
            if (since >= 300 && since < 700 && hunting) {
                run.fail("a survival-capped raptor must IGNORE the capped quarry for the whole blackout: " + run.status);
            }
            // When the blackout ends, stand the hunter back in front of the player: a scattered raptor can
            // freeze against an arena wall and never FOV-sweep the player again, which would make the
            // re-hunt depend on wander luck. Then it MUST take the hunt back up.
            if (since >= 820 && !hunting && !teleported[0]) {
                raptor.setPos(run.player.getX(), run.player.getY(), run.player.getZ() + 6.0);
                double dx = run.player.getX() - raptor.getX();
                double dz = run.player.getZ() - raptor.getZ();
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                raptor.setYRot(yaw);
                raptor.setYHeadRot(yaw);
                teleported[0] = true;
            }
            if (since >= 820 && hunting) {
                run.pass(); // reopened on its own, or after being stood back in front of the player
            }
            if (teleported[0] && since >= 950) {
                run.fail("a survival-capped raptor must take the hunt back up when the blackout ends: " + run.status);
            }
        });
    }

    /**
     * Extends the arena floor out to {@code FLANK_GROUND_HALF} blocks wide so a flank circle fits, forces those
     * chunks ticking, and returns the undo (back to air), same contract as the water tests' lake.
     */
    private static Runnable widenGround(ServerLevel level, BlockPos origin) {
        net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        int back = (int) FLANK_PLAYER_AT.z - 40 - 32 - 8; // ring (40) + retreat walk-back (32) + margin behind
        int front = (int) FLANK_PLAYER_AT.z + FLANK_GROUND_HALF + 8; // ...and ahead
        // (These used to be sized for the old radius-20 ring: flankers running the FRONT of the radius-40 ring
        // ran off the floor's edge, pinned there with a frozen bearing - the "stalled sweep" test flake.)
        for (int chunkX = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX() - FLANK_GROUND_HALF);
                chunkX <= net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX() + FLANK_GROUND_HALF); chunkX++) {
            for (int chunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ() + back);
                    chunkZ <= net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ() + front); chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true);
            }
        }
        for (int x = -FLANK_GROUND_HALF; x <= FLANK_GROUND_HALF; x++) {
            for (int z = back; z <= front; z++) {
                // Clear anything at walkable height inside the widened rectangle. The flank ring is radius 40
                // but the rex arena's perimeter walls stand ~24 out and 3 tall, and a flanker on the far side
                // legitimately loses line of sight through the wall, goes INVESTIGATE, and stalls (verified:
                // canSee=false at 23 blocks with the pack holding contact). A test quarry shouldn't live in a
                // fortress — the ring's band gets cleared to air at body height.
                for (int dy = 0; dy <= 2; dy++) {
                    var above = level.getBlockState(origin.offset(x, dy, z));
                    if (!above.isAir()) {
                        level.setBlock(origin.offset(x, dy, z), air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                }
                if (Math.abs(x) <= RexGameTests.ARENA_HALF_WIDTH && z >= -RexGameTests.ARENA_BACK && z <= RexGameTests.ARENA_FRONT) {
                    continue; // the arena template already floors this cell
                }
                // (The old test skipped by |x| ALONE: the template floor stops at z=-12, and the radius-40
                // ring reaches z=-31.5 - a hole behind the quarry that rear flankers fell through. Down there
                // the floor blocks line of sight, so the stall signature read "visible=false INVESTIGATE".)
                level.setBlock(origin.offset(x, -1, z), stone, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
        return () -> {
            for (int x = -FLANK_GROUND_HALF; x <= FLANK_GROUND_HALF; x++) {
                for (int z = back; z <= front; z++) {
                    if (Math.abs(x) <= RexGameTests.ARENA_HALF_WIDTH && z >= -RexGameTests.ARENA_BACK && z <= RexGameTests.ARENA_FRONT) {
                        continue; // template floor: not ours to remove
                    }
                    level.setBlock(origin.offset(x, -1, z), air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        };
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

    /**
     * The live-playtest report "they're getting stuck in water", for the small body. Same lake as the T-rex tests
     * (bank top sits a block above the water; vanilla only lets a swimmer climb out onto a ledge of ~0.6), started
     * against the far bank so it measures the exit, not a crossing. The raptor must leave the water and reach the
     * bank on its own, while hunting the player on shore.
     */
    private static void raptorClimbsOutOfWater(GameTestHelper helper) {
        Velociraptor[] lone = new Velociraptor[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, WATER_PLAYER_AT, false, WATER_WATCH_TICKS,
            origin -> RexWaterGameTests.buildLake(helper.getLevel(), origin, WATER_LAKE_DEPTH),
            run -> {
                run.rex.discard();
                run.cleanups.add(() -> RexWaterGameTests.removeLake(helper.getLevel(), run.origin, WATER_LAKE_DEPTH));
                lone[0] = spawnRaptor(helper, FACING_SOUTH, new Vec3(0.5, WATER_SURFACE_Y - 0.3, 33.5));
                run.cleanups.add(lone[0]::discard);
            },
            run -> {
                Velociraptor raptor = lone[0];
                double bankZ = raptor.getZ() - run.origin.getZ();
                run.status = "raptor z=" + String.format("%.1f", bankZ) + " y=" + String.format("%.1f", raptor.getY() - run.origin.getY())
                    + " inWater=" + raptor.isInWater() + " onGround=" + raptor.onGround();
                if (bankZ >= BANK_MIN_Z && !raptor.isInWater() && raptor.onGround()) {
                    run.pass();
                }
            });
    }

    /**
     * Ground truth for the "a walking player outpaces them" report: measures the raptor's actual horizontal speed
     * while it closes on a fresh target across flat arena floor, before bite standstills can dilute it. A walking
     * player does 4.32 b/s, a sprinting player 5.61; the hunt must clear the walking line outright.
     */
    private static void raptorChaseOutrunsWalkingPlayer(GameTestHelper helper) {
        final int MIN_SAMPLES = 30;
        final int MEASURE_CAP = 120;
        final double STOP_MEASURING_AT = 3.0;
        Velociraptor[] lone = new Velociraptor[1];
        Vec3[] last = new Vec3[1];
        double[] traveled = new double[1];
        int[] samples = new int[1];
        boolean[] measuring = new boolean[1];
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, CHASE_WATCH_TICKS, origin -> {}, run -> {
            run.rex.discard();
            // Far enough away that even a fast chase gives a long enough measurement corridor.
            lone[0] = spawnRaptor(helper, FACING_SOUTH, new Vec3(0.5, 0, -6.5));
            run.cleanups.add(lone[0]::discard);
            last[0] = lone[0].position();
        }, run -> {
            Velociraptor raptor = lone[0];
            double toPlayer = Math.hypot(raptor.getX() - run.player.getX(), raptor.getZ() - run.player.getZ());
            if (!measuring[0]) {
                if (raptor.getTarget() != null && toPlayer > 12.0) {
                    measuring[0] = true; // fresh approach leg: no bite standstill in the samples
                    last[0] = raptor.position();
                }
                run.status = "waiting to hunt target=" + (raptor.getTarget() != null) + " dist=" + String.format("%.1f", toPlayer);
                return;
            }
            if (toPlayer <= STOP_MEASURING_AT || samples[0] >= MEASURE_CAP) {
                run.check(samples[0] >= MIN_SAMPLES, "too few ground ticks to measure: " + samples[0] + " samples, dist=" + String.format("%.1f", toPlayer));
                double blocksPerSecond = traveled[0] * 20.0 / samples[0];
                com.dinodread.DinoDread.LOGGER.info("[gametest] raptor chase speed measured: {} b/s over {} ground ticks",
                        String.format("%.2f", blocksPerSecond), samples[0]);
                run.status = String.format("chase speed %.2f b/s over %d ticks", blocksPerSecond, samples[0]);
                run.check(blocksPerSecond >= 4.7, run.status + " - a walking player does 4.32 b/s");
                run.check(blocksPerSecond <= 5.61, run.status + " - the chase must stay under a sprinting player (5.61 b/s)");
                run.pass();
                return;
            }
            if (raptor.onGround() && !raptor.isInWater() && !raptor.isScattering()) {
                traveled[0] += Math.hypot(raptor.getX() - last[0].x, raptor.getZ() - last[0].z);
                samples[0]++;
            }
            last[0] = raptor.position();
            run.status = String.format("measuring %d/%d dist=%.1f", samples[0], MEASURE_CAP, toPlayer);
        });
    }

    // ---------------------------------------------------------------- config direction

    /** The shipped raptor defaults point the right way: smaller, terrible in water, quiet, packs of 3-4 that flee 30 s. */
    private static void defaultsAreSmallSlowAndQuiet(GameTestHelper helper) {
        if (RaptorConfig.SCALE.get() >= RexConfig.SCALE.get()) {
            helper.fail("raptors must be smaller than the T-rex");
        } else if (RaptorConfig.SWIM_SPEED_MULTIPLIER.get() >= 4.0 || RaptorConfig.SWIM_SPEED_MULTIPLIER.get() >= RexConfig.SWIM_SPEED_MULTIPLIER.get()) {
            helper.fail("raptors must stay slower swimmers than the T-rex (and a boat): they can reach the bank, not win a lake");
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
