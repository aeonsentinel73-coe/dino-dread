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
    private static final int FLANK_GROUND_HALF = 24;
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
        test("raptor_pack_shares_the_hunt", RexRaptorGameTests::raptorPackSharesTheHunt);
        test("raptor_pack_holds_the_hunt_together", RexRaptorGameTests::raptorPackHoldsTheHuntTogether);
        test("raptor_scatters_without_despawning", RexRaptorGameTests::raptorScattersWithoutDespawning);
        test("raptor_chase_outruns_a_walking_player", RexRaptorGameTests::raptorChaseOutrunsWalkingPlayer);
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
        java.util.Set<java.util.UUID> everFlanked = new java.util.HashSet<>();
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
            if (everFlanked.size() < expectedFlankers(pack.size())) {
                // Target acquisition is variable (observed up to ~700 ticks), so only call it broken late.
                if (run.age() > 1600) {
                    run.fail("a pack of " + pack.size() + " hunting together should keep " + expectedFlankers(pack.size())
                            + " raptor(s) on the flanks, saw " + everFlanked.size() + " (" + run.status + ")");
                }
                return;
            }
            // End hook: everyone who ran a circle must come off it and rejoin the head-on chase, not run laps.
            // Sticky flag on purpose: after finishing, the flankers bite, the fixture can die, and targets clear -
            // the proof is the completed circle, not a currently-held target.
            boolean allDone = true;
            for (Velociraptor raptor : pack) {
                if (everFlanked.contains(raptor.getUUID()) && !raptor.hasCompletedFlank()) {
                    allDone = false;
                }
            }
            if (allDone) {
                run.pass();
            } else if (run.age() > FLANK_WATCH_TICKS - 400) {
                StringBuilder diag = new StringBuilder();
                for (Velociraptor raptor : pack) {
                    if (everFlanked.contains(raptor.getUUID())) {
                        diag.append(" [").append(raptor.flankDebug()).append(']');
                    }
                }
                run.fail("flanker(s) never ended their circle (end hook broken, running laps): " + run.status + diag);
            }
        });
    }

    /** One flanker in a pack of three, two in a pack of four (mirrors the entity's slot assignment). */
    private static int expectedFlankers(int packSize) {
        return packSize >= 4 ? 2 : packSize == 3 ? 1 : 0;
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
        int sightWere = RaptorConfig.SIGHT_RANGE.get().intValue();
        double flankRadiusWere = RaptorConfig.FLANK_RADIUS_BLOCKS.get();
        scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, FLANK_PLAYER_AT, false, 700, origin -> {
            // The blind pair need floor out to the arena corners (widenGround covers x +-24).
            undoGround[0] = widenGround(helper.getLevel(), origin);
        }, run -> {
            run.cleanups.add(undoGround[0]::run);
            RaptorConfig.SIGHT_RANGE.set(16.0);
            // Flanker slots follow UUID order, so the caller might be the one that circles: keep the ring inside
            // the reduced sight, or a circling caller would go blind and the test would prove the wrong thing.
            RaptorConfig.FLANK_RADIUS_BLOCKS.set(8.0);
            run.cleanups.add(() -> RaptorConfig.FLANK_RADIUS_BLOCKS.set(flankRadiusWere));
            run.cleanups.add(() -> RaptorConfig.SIGHT_RANGE.set((double) sightWere));
            run.rex.discard();
            String packTag = Velociraptor.PACK_TAG_PREFIX + "gametest";
            // The caller is 6 blocks from the player inside the 16-block sight; the pair are 20-29: blind, and
            // the motionless silent player gives them nothing to hear either.
            trio[0] = spawnRaptor(helper, 0.0F, new Vec3(0.5, 0, 2.5));
            trio[1] = spawnRaptor(helper, 180.0F, new Vec3(-20.5, 0, -12.5));
            trio[2] = spawnRaptor(helper, 180.0F, new Vec3(-14.5, 0, -12.5));
            for (Velociraptor raptor : trio) {
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
     * Extends the arena floor out to {@code FLANK_GROUND_HALF} blocks wide so a flank circle fits, forces those
     * chunks ticking, and returns the undo (back to air), same contract as the water tests' lake.
     */
    private static Runnable widenGround(ServerLevel level, BlockPos origin) {
        net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        int back = -RexGameTests.ARENA_BACK - 2;
        int front = (int) FLANK_PLAYER_AT.z + 24;
        for (int chunkX = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX() - FLANK_GROUND_HALF);
                chunkX <= net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX() + FLANK_GROUND_HALF); chunkX++) {
            for (int chunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ() + back);
                    chunkZ <= net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ() + front); chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true);
            }
        }
        for (int x = -FLANK_GROUND_HALF; x <= FLANK_GROUND_HALF; x++) {
            for (int z = back; z <= front; z++) {
                if (Math.abs(x) <= RexGameTests.ARENA_HALF_WIDTH) {
                    continue; // the arena already has this floor
                }
                level.setBlock(origin.offset(x, -1, z), stone, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
        return () -> {
            for (int x = -FLANK_GROUND_HALF; x <= FLANK_GROUND_HALF; x++) {
                for (int z = back; z <= front; z++) {
                    if (Math.abs(x) <= RexGameTests.ARENA_HALF_WIDTH) {
                        continue;
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
