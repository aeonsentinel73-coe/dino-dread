package com.dinodread.gametest;

import static com.dinodread.gametest.RexGameTests.FACING_SOUTH;
import static com.dinodread.gametest.RexGameTests.MIDNIGHT_TICKS;
import static com.dinodread.gametest.RexGameTests.NOON_TICKS;
import static com.dinodread.gametest.RexGameTests.TEST_TIMEOUT_TICKS;

import java.util.function.Consumer;

import com.dinodread.DinoDread;
import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
import com.dinodread.entity.ai.HuntState;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Milestone 5 acceptance tests (dread audio), server side: the T-rex roars when it starts hunting (once per cooldown),
 * its footsteps follow the configured stride, its bite makes a sound, and it roars at the night sky only when it is
 * wandering at night. The sounds themselves are observed through the counters on {@code TRex.getVoice()}; how they
 * sound, and the camera shake, are for a person in a real client to judge.
 */
public final class RexAudioGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);

    private static final String ROARS = "trex_roars_when_it_starts_hunting";
    private static final String ROAR_COOLDOWN = "trex_does_not_roar_again_during_the_cooldown";
    private static final String STEPS = "trex_footsteps_follow_its_stride";
    private static final String BITE = "trex_bite_makes_a_sound";
    private static final String DISTANT_AT_NIGHT = "trex_roars_at_night_while_wandering";
    private static final String DISTANT_BY_DAY = "trex_does_not_roar_at_the_sun";
    private static final String DISTANT_WHILE_HUNTING = "trex_does_not_roar_at_the_moon_while_hunting";

    private static final double PLAYER_DISTANCE = 30.5;
    private static final int ROAR_MAX_TICKS = 200;
    private static final int COOLDOWN_WATCH_TICKS = 80;
    // Short of the ~22 blocks the rex has to cover before the player is in bite range, so the walk is never cut short by a bite.
    private static final double STEP_TEST_DISTANCE = 18.0;
    private static final double STEP_TOLERANCE_BEFORE = 1.0;
    private static final double STEP_TOLERANCE_AFTER = 2.0;
    private static final int STEP_MAX_TICKS = 600;
    private static final int BITE_MAX_TICKS = 900;
    // Longer than the 36-tick roar animation, shorter than the time it takes to reach the player and bite.
    private static final int ACTION_WATCH_TICKS = 50;
    private static final int DISTANT_MAX_TICKS = 120;
    private static final int DISTANT_WATCH_TICKS = 40;
    private static final int DAY_WATCH_TICKS = 60;
    private static final int ROAR_AT_ONCE = 1;

    static {
        FUNCTIONS.register(ROARS, () -> RexAudioGameTests::roarsWhenItStartsHunting);
        FUNCTIONS.register(ROAR_COOLDOWN, () -> RexAudioGameTests::doesNotRoarAgainDuringTheCooldown);
        FUNCTIONS.register(STEPS, () -> RexAudioGameTests::footstepsFollowItsStride);
        FUNCTIONS.register(BITE, () -> RexAudioGameTests::biteMakesASound);
        FUNCTIONS.register(DISTANT_AT_NIGHT, () -> RexAudioGameTests::roarsAtNightWhileWandering);
        FUNCTIONS.register(DISTANT_BY_DAY, () -> RexAudioGameTests::doesNotRoarAtTheSun);
        FUNCTIONS.register(DISTANT_WHILE_HUNTING, () -> RexAudioGameTests::doesNotRoarAtTheMoonWhileHunting);
    }

    private RexAudioGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexAudioGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        RexGameTests.addTest(event, ROARS, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, ROAR_COOLDOWN, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, STEPS, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, BITE, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, DISTANT_AT_NIGHT, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, DISTANT_BY_DAY, TEST_TIMEOUT_TICKS);
        RexGameTests.addTest(event, DISTANT_WHILE_HUNTING, TEST_TIMEOUT_TICKS);
    }

    /**
     * A rex that notices a player in view roars, exactly once, and shows it: the synced action is ROAR (which is what makes
     * clients play the roar animation) and goes back to NONE once the animation is over.
     */
    private static void roarsWhenItStartsHunting(GameTestHelper helper) {
        long[] roaredAt = {-1};
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_DISTANCE), false, ROAR_MAX_TICKS + ACTION_WATCH_TICKS, origin -> {},
            run -> {
                run.check(run.rex.getVoice().roarCount() == 0, "test setup: the rex must not have roared before it noticed anyone");
                run.check(run.rex.getAction() == TRex.Action.NONE, "test setup: the rex must not start in the middle of an action");
            },
            run -> {
                int roars = run.rex.getVoice().roarCount();
                if (roaredAt[0] < 0) {
                    run.status = "the rex never roared; hasQuarry=" + run.rex.getSenses().hasQuarry() + " roars=" + roars;
                    if (roars > 0) {
                        run.check(roars == ROAR_AT_ONCE, "expected one roar when the hunt started, got " + roars);
                        run.check(run.rex.getAction() == TRex.Action.ROAR, "the rex roared but its action is " + run.rex.getAction() + ", so clients would not animate it");
                        roaredAt[0] = run.age();
                    }
                    return;
                }

                run.status = "the roar action never ended; action=" + run.rex.getAction();
                if (run.age() - roaredAt[0] >= ACTION_WATCH_TICKS) {
                    run.check(run.rex.getAction() == TRex.Action.NONE, "the roar action never went back to NONE: " + run.rex.getAction());
                    run.pass();
                }
            });
    }

    /** Losing the player and spotting them again straight away restarts the hunt, but the roar is on cooldown. */
    private static void doesNotRoarAgainDuringTheCooldown(GameTestHelper helper) {
        long[] forgotAt = {-1};
        boolean[] huntedAgain = new boolean[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_DISTANCE), false, ROAR_MAX_TICKS + COOLDOWN_WATCH_TICKS, origin -> {},
            run -> run.check(RexConfig.ROAR_COOLDOWN_TICKS.get() > COOLDOWN_WATCH_TICKS, "test setup: the cooldown must outlast the watch"),
            run -> {
                int roars = run.rex.getVoice().roarCount();
                if (forgotAt[0] < 0) {
                    run.status = "the rex never roared the first time";
                    if (roars > 0) {
                        run.check(roars == ROAR_AT_ONCE, "expected one roar when the hunt started, got " + roars);
                        run.rex.getSenses().forget();
                        forgotAt[0] = run.age();
                    }
                    return;
                }

                huntedAgain[0] |= run.rex.getHuntState() != HuntState.WANDER;
                run.status = "hunt did not restart after the rex forgot the player; huntedAgain=" + huntedAgain[0];
                run.check(roars == ROAR_AT_ONCE, "it roared again during the cooldown: " + roars + " roars");
                if (run.age() - forgotAt[0] >= COOLDOWN_WATCH_TICKS && huntedAgain[0]) {
                    run.pass();
                }
            });
    }

    /** Footsteps come once per footstepStrideBlocks walked, not at vanilla's pace of one every ~1.7 blocks. */
    private static void footstepsFollowItsStride(GameTestHelper helper) {
        Vec3[] start = new Vec3[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_DISTANCE), false, STEP_MAX_TICKS, origin -> {},
            run -> {
                start[0] = run.rex.position();
                run.check(run.rex.getVoice().stepCount() == 0, "test setup: no footsteps before the rex has walked");
            },
            run -> {
                double walked = run.rex.position().subtract(start[0]).horizontalDistance();
                int steps = run.rex.getVoice().stepCount();
                run.status = "rex walked " + String.format("%.1f", walked) + " blocks with " + steps + " footsteps";
                if (walked >= STEP_TEST_DISTANCE) {
                    double stride = RexConfig.FOOTSTEP_STRIDE_BLOCKS.get();
                    double lowest = walked / stride - STEP_TOLERANCE_BEFORE;
                    double highest = walked / stride + STEP_TOLERANCE_AFTER;
                    run.check(steps >= lowest && steps <= highest, "walked " + String.format("%.1f", walked) + " blocks at a stride of " + stride + ": expected "
                        + String.format("%.1f", lowest) + " to " + String.format("%.1f", highest) + " footsteps, got " + steps);
                    run.pass();
                }
            });
    }

    /** A bite that lands plays the bite sound. */
    private static void biteMakesASound(GameTestHelper helper) {
        float[] startHealth = new float[1];
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_DISTANCE), false, BITE_MAX_TICKS, origin -> {},
            run -> startHealth[0] = run.player.getHealth(),
            run -> {
                run.status = "the player was never bitten";
                if (run.player.getHealth() < startHealth[0] || !run.player.isAlive()) {
                    run.check(run.rex.getVoice().biteCount() >= 1, "the bite landed but no bite sound was played");
                    run.check(run.rex.getAction() == TRex.Action.BITE, "the bite landed but the rex's action is " + run.rex.getAction() + ", so clients would not animate it");
                    run.pass();
                }
            });
    }

    /** At night, with nobody around, the countdown runs out and it roars where only far-away players will hear it. */
    private static void roarsAtNightWhileWandering(GameTestHelper helper) {
        long[] roaredAt = {-1};
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, null, false, DISTANT_MAX_TICKS, origin -> {},
            run -> {
                run.check(!run.rex.getSenses().hasQuarry() && run.rex.getHuntState() == HuntState.WANDER, "test setup: the rex must be wandering with no quarry");
                run.rex.getVoice().scheduleDistantRoar(1);
            },
            run -> {
                int roars = run.rex.getVoice().distantRoarCount();
                if (roaredAt[0] < 0) {
                    run.status = "the rex never roared at night while wandering";
                    if (roars > 0) {
                        roaredAt[0] = run.age();
                    }
                    return;
                }

                run.status = "roared at night, but then roared again too soon";
                run.check(roars == ROAR_AT_ONCE, "expected one distant roar, got " + roars);
                run.check(run.rex.getVoice().roarCount() == 0, "the distant roar must not count as a hunting roar");
                if (run.age() - roaredAt[0] >= DISTANT_WATCH_TICKS) {
                    run.pass();
                }
            });
    }

    /** The same countdown does nothing in daylight. (The night test above proves it would fire otherwise.) */
    private static void doesNotRoarAtTheSun(GameTestHelper helper) {
        RexGameTests.scenario(helper, NOON_TICKS, FACING_SOUTH, null, false, DAY_WATCH_TICKS + 1, origin -> {},
            run -> run.rex.getVoice().scheduleDistantRoar(1),
            run -> {
                run.status = "watching a daytime rex stay quiet";
                run.check(run.rex.getVoice().distantRoarCount() == 0, "it roared at the sun");
                if (run.age() >= DAY_WATCH_TICKS) {
                    run.pass();
                }
            });
    }

    /** A hunting rex has its own sounds; the countdown waits until it is wandering again. */
    private static void doesNotRoarAtTheMoonWhileHunting(GameTestHelper helper) {
        long[] armedAt = {-1};
        RexGameTests.scenario(helper, MIDNIGHT_TICKS, FACING_SOUTH, new Vec3(0.5, 0, PLAYER_DISTANCE), false, ROAR_MAX_TICKS + DISTANT_WATCH_TICKS, origin -> {},
            run -> {},
            run -> {
                if (armedAt[0] < 0) {
                    run.status = "the rex never started hunting";
                    if (run.rex.getSenses().hasQuarry()) {
                        run.rex.getVoice().scheduleDistantRoar(1);
                        armedAt[0] = run.age();
                    }
                    return;
                }

                run.status = "the hunt ended before the watch did";
                run.check(run.rex.getVoice().distantRoarCount() == 0, "it roared at the moon in the middle of a hunt");
                run.check(run.rex.getSenses().hasQuarry(), "test setup: the rex stopped hunting before the watch ended");
                if (run.age() - armedAt[0] >= DISTANT_WATCH_TICKS) {
                    run.pass();
                }
            });
    }
}
