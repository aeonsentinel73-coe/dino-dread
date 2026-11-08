package com.dinodread.entity;

import org.jspecify.annotations.Nullable;

import com.dinodread.DinoDread;
import com.dinodread.config.RaptorConfig;
import com.dinodread.entity.ai.HuntState;
import com.dinodread.entity.ai.RaptorFleeGoal;
import com.dinodread.entity.ai.RexBlockBreaker;
import com.dinodread.entity.ai.TRexHuntGoal;
import com.dinodread.entity.ai.TRexPreyGoal;
import com.dinodread.entity.ai.TRexWanderGoal;
import com.dinodread.registry.ModSounds;
import com.dinodread.spawn.RexSpawner;
import com.dinodread.world.DayNight;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * A velociraptor: the same hunting brain as the T-rex (senses, hunt goal, mover, voice) running on {@link RaptorConfig}
 * numbers, plus pack behaviour. It is smaller and quicker, cannot charge, sees hostile mobs as well as players, and hates
 * deep water (swim speed near zero). Its sounds are its own ids, which keeps it out of the T-rex camera shake.
 * <p>
 * A natural pack shares a {@code dinodread_pack_<uuid>} scoreboard tag; when any member of a tagged pack dies, every
 * survivor starts fleeing (see {@link RaptorFleeGoal} and {@link com.dinodread.spawn.RaptorSpawner}) and despawns when
 * the flee runs out. The model is the Peridot Scout (CC0, see CREDITS.md); its sounds were made for this mod.
 */
public class Velociraptor extends TRex {
    /** Prefix of the scoreboard tag that binds a natural pack together. */
    public static final String PACK_TAG_PREFIX = "dinodread_pack_";

    // Animation names inside geckolib/animations/entity/velociraptor.animation.json (the Peridot Scout's own names).
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.peridot_scout.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.peridot_scout.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.peridot_scout.run");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.peridot_scout.swim");
    private static final RawAnimation CRY = RawAnimation.begin().thenPlay("animation.peridot_scout.roar");
    private static final RawAnimation ATTACK_LEFT = RawAnimation.begin().thenPlay("animation.peridot_scout.attack_left");
    private static final RawAnimation ATTACK_RIGHT = RawAnimation.begin().thenPlay("animation.peridot_scout.attack_right");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.peridot_scout.death");
    private static final RawAnimation TAIL_WHIP = RawAnimation.begin().thenPlay("animation.peridot_scout.tail_whip");
    private static final String ATTACK_RIGHT_TRIGGER = "attack_right";
    private static final String TAIL_WHIP_TRIGGER = "tail_whip";
    // The Peridot animations' own lengths, in ticks: call 3.0 s, attacks 1.0 s, tail whip 1.6 s.
    private static final int CRY_ACTION_TICKS = 60;
    private static final int ATTACK_ACTION_TICKS = 20;
    private static final int TAIL_WHIP_ACTION_TICKS = 32;
    // Idle variety: while a raptor stands around wandering, a tail whip fires about every 15 seconds on average.
    private static final double IDLE_TAIL_WHIP_CHANCE_PER_TICK = 1.0 / 300.0;
    // The walk animation is 4.8 s long with two strides per loop (each foot lands twice), so 4 footfalls per cycle.
    private static final double PERIDOT_WALK_CYCLE_SECONDS = 4.8;
    private static final int PERIDOT_FOOTFALLS_PER_CYCLE = 4;
    // The running-gait sound is a ~4.3 s file restarted this often while sprinting, so the gallop runs on without gaps.
    private static final int RUN_SOUND_PERIOD_TICKS = 85;

    private int nextRunSoundTick;
    private boolean lastBiteWasLeft;

    public Velociraptor(EntityType<? extends Velociraptor> type, Level level) {
        super(type, level);
    }

    // The flee outranks the hunt: a raptor whose pack broke runs, it does not fight back.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RaptorFleeGoal(this));
        goalSelector.addGoal(2, new TRexHuntGoal(this));
        goalSelector.addGoal(3, new TRexPreyGoal(this));
        goalSelector.addGoal(4, new TRexWanderGoal(this));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, RaptorConfig.MAX_HEALTH.get())
            .add(Attributes.MOVEMENT_SPEED, RaptorConfig.MOVEMENT_SPEED.get())
            .add(Attributes.KNOCKBACK_RESISTANCE, RaptorConfig.KNOCKBACK_RESISTANCE.get())
            .add(Attributes.ATTACK_DAMAGE, RaptorConfig.ATTACK_DAMAGE.get())
            .add(Attributes.FOLLOW_RANGE, RaptorConfig.FOLLOW_RANGE.get())
            .add(Attributes.STEP_HEIGHT, RaptorConfig.STEP_HEIGHT.get())
            // A small body should not clear a full player-jump: vanilla's strength is tuned for a 1.8-block frame.
            .add(Attributes.JUMP_STRENGTH, RaptorConfig.JUMP_STRENGTH.get())
            .add(NeoForgeMod.SWIM_SPEED, RaptorConfig.SWIM_SPEED_MULTIPLIER.get())
            .add(Attributes.SCALE, RaptorConfig.SCALE.get());
    }

    // The rex stuck hop (0.9, roughly 4 blocks) was sized for the 3x T-rex; a raptor clears ledges with a real raptor's
    // leap instead, so it can still hop a one-block bank or fallen log without launching itself over the canopy.
    @Override
    public double stuckHopVelocity() {
        return RaptorConfig.JUMP_STRENGTH.get();
    }

    @Override
    public double stuckHopForwardVelocity() {
        // Roughly its chase speed, so a hop carries it onward instead of stalling it under the prey.
        return 0.45;
    }

    @Override
    public double activity() {
        return Mth.lerp(DayNight.nightFactor(level(), position()), RaptorConfig.DAY_AGGRESSION.get(), 1.0);
    }

    @Override
    public double sightRange() {
        return sightRangeOverride != null ? sightRangeOverride : RaptorConfig.SIGHT_RANGE.get();
    }

    @Override
    public double sightFieldOfViewDegrees() {
        return RaptorConfig.SIGHT_FOV_DEGREES.get();
    }

    @Override
    public double sneakSightMultiplier() {
        return RaptorConfig.SNEAK_SIGHT_MULTIPLIER.get();
    }

    @Override
    public double hearingRadius() {
        return RaptorConfig.HEARING_RADIUS.get();
    }

    @Override
    public double sneakHearingMultiplier() {
        return RaptorConfig.SNEAK_HEARING_MULTIPLIER.get();
    }

    @Override
    public int giveUpTicks() {
        return RaptorConfig.GIVE_UP_TICKS.get();
    }

    @Override
    public double biteReach() {
        return RaptorConfig.BITE_REACH.get();
    }

    @Override
    public double biteReachBelow() {
        return RaptorConfig.BITE_REACH_BELOW.get();
    }

    @Override
    public int biteWindupTicks() {
        return RaptorConfig.BITE_WINDUP_TICKS.get();
    }

    @Override
    public int biteCooldownTicks() {
        return RaptorConfig.BITE_COOLDOWN_TICKS.get();
    }

    // No charge: a raptor walks its target down, it does not sprint at it.
    @Override
    public boolean canCharge() {
        return false;
    }

    @Override
    public boolean huntsHostileMobs() {
        return RaptorConfig.HUNT_HOSTILE_MOBS.get();
    }

    // A raptor is not a bulldozer: it pushes through leaves and bushes, never through walls or houses.
    @Override
    public RexBlockBreaker.Policy huntBreakPolicy() {
        return RexBlockBreaker.Policy.FOLIAGE;
    }

    @Override
    public double swimDepthBlocks() {
        return RaptorConfig.SWIM_DEPTH.get();
    }

    @Override
    public double waterExitHeight() {
        return RaptorConfig.WATER_EXIT_HEIGHT.get();
    }

    @Override
    public double footstepStrideBlocks() {
        return RaptorConfig.FOOTSTEP_STRIDE_BLOCKS.get();
    }

    @Override
    public double footstepVolume() {
        return RaptorConfig.FOOTSTEP_VOLUME.get();
    }

    @Override
    public double biteVolume() {
        return RaptorConfig.BITE_VOLUME.get();
    }

    @Override
    public double roarVolume() {
        return RaptorConfig.ROAR_VOLUME.get();
    }

    @Override
    public int roarCooldownTicks() {
        return RaptorConfig.ROAR_COOLDOWN_TICKS.get();
    }

    // Packs are quiet until they find you: no ambient night calls. (The T-rex's distant-roar config does not apply here.)
    @Override
    public boolean distantRoarEnabled() {
        return false;
    }

    // The raptor's own sound ids. The client camera shake listens for the T-rex's TREX_STEP and TREX_ROAR events by name,
    // so these never shake the ground. The bite is still a pitched-up cry stand-in until a real bite sound is made.
    @Override
    public DeferredHolder<SoundEvent, SoundEvent> stepSound() {
        return ModSounds.RAPTOR_RUN;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> roarSound() {
        return ModSounds.RAPTOR_ROAR;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> biteSound() {
        return ModSounds.RAPTOR_BITE;
    }

    // ---- Animations: the Peridot Scout's own clips (see the RawAnimation constants). ----

    @Override
    protected RawAnimation idleAnimation() {
        return IDLE;
    }

    @Override
    protected RawAnimation walkAnimation() {
        return WALK;
    }

    @Override
    protected RawAnimation roarAnimation() {
        return CRY;
    }

    @Override
    protected RawAnimation biteAnimation() {
        return ATTACK_LEFT;
    }

    @Override
    protected RawAnimation swimAnimation() {
        return SWIM;
    }

    @Override
    protected RawAnimation runAnimation() {
        return RUN;
    }

    @Override
    protected RawAnimation deathAnimation() {
        return DEATH;
    }

    @Override
    protected double walkCycleSeconds() {
        return PERIDOT_WALK_CYCLE_SECONDS;
    }

    @Override
    protected int walkFootfallsPerCycle() {
        return PERIDOT_FOOTFALLS_PER_CYCLE;
    }

    @Override
    protected int actionDurationTicks(Action action) {
        return switch (action) {
            case ROAR -> CRY_ACTION_TICKS;
            case BITE, BITE_ALT -> ATTACK_ACTION_TICKS;
            case TAIL_WHIP -> TAIL_WHIP_ACTION_TICKS;
            case NONE -> 0;
        };
    }

    // A raptor never charges, so "sprinting" means it has spotted quarry (or is fleeing the pack's loss, or scattering
    // after losing a hunt).
    @Override
    public boolean sprintingVisual() {
        HuntState state = getHuntState();
        return state == HuntState.STALK || state == HuntState.CHARGE || isFleeing() || isScattering();
    }

    // ---- Flanking: one raptor of a pack of three (two of four) runs a circle around the quarry. ----

    /** Which flanker this raptor is for the current quarry: -2 unevaluated, -1 straight chaser, 0 = left circle, 1 = right. */
    private int flankSlot = -2;
    private @Nullable UUID flankEvaluatedFor;
    /** Quarry whose circle this raptor has completed: a finished flanker joins the straight chase and stays there. */
    private @Nullable UUID flankCompletedFor;
    /** Until this tick, a finished/abandoned circle against the same quarry does not reopen (anti-flicker loop). */
    private long flankReopenTick;
    /**
     * Bearing the current circle started from; NaN when no circle is in progress. Sweep progress is measured as
     * this raptor's bearing around the quarry from that start, so it survives sight flicker (a blink does NOT
     * restart the circle - it resumes) and completion fires on what the player has seen, not waypoint bookkeeping.
     */
    private double flankStartBearing = Double.NaN;
    /** The body's bearing last tick; its clamped advance is the REAL circle progress (a waypoint's is not). */
    private double flankLastBodyBearing = Double.NaN;
    /** Real body sweep this circle, radians. */
    private double flankSweep;
    /** Ticks without meaningful sweep: the quarry is outrunning the circle, so give the circle up (escape valve). */
    private int flankStallTicks;
    /** Set when a flank sweep completes: the cut-off surge runs to where the quarry is GOING (see huntDestination). */
    private int interceptUntilTick;
    /** Surge bookkeeping for the turn dodge: arm tick, quarry position then, and its unit travel direction then. */
    private int interceptStartTick = -1;
    private double interceptQuarryStartX;
    private double interceptQuarryStartZ;
    private double interceptDirX;
    private double interceptDirZ;
    /** Set by huntDestination() each tick the goal asks for a destination (before huntSpeedBoost() reads it). */
    private boolean flankingNow;
    private boolean interceptingNow;

    @Override
    public Vec3 huntDestination(Vec3 chase, boolean visible) {
        LivingEntity target = getTarget();
        if (!visible || target == null || isInWater() || isFleeing() || isScattering()) {
            flankingNow = false;
            interceptingNow = false;
            // flankSlot deliberately SURVIVES the blink: resetting it here made one lost frame of sight
            // re-run role assignment, and a pack stretched by a retreating quarry counted fewer packmates
            // inside the role radius - the flankers silently lost the circle mid-run (live playtest: circle,
            // break out, no ABANDONED log, flankers losing ground while the straight chase caught up).
            // Roles now change only with the quarry: a new target UUID re-evaluates below.
            return chase; // note: sweep progress deliberately survives a blink
        }
        // The post-flank cut-off: run to where the quarry is going, not where it is (see the flank completion).
        if (tickCount < interceptUntilTick) {
            // The dodge (live playtest: the surge felt too cruel): the first half-second belongs to the quarry.
            // Compare the direction it has ACTUALLY traveled since the arm against the line it was on then -
            // accumulated displacement, not instantaneous velocity, so sprint footstep jitter cannot fake a turn.
            // Swing interceptTurnDegrees off the old line inside the window and the cut-off is given up: the
            // raptor drops to ordinary chase speed and a straight runner simply outruns the whole thing.
            if (interceptStartTick >= 0 && tickCount <= interceptStartTick + INTERCEPT_TURN_WINDOW_TICKS) {
                double limit = RaptorConfig.INTERCEPT_TURN_DEGREES.get();
                double movedX = target.getX() - interceptQuarryStartX;
                double movedZ = target.getZ() - interceptQuarryStartZ;
                double moved = Math.hypot(movedX, movedZ);
                if (limit > 0.0 && moved > 0.4) { // ~2 b/s: below that there is no traveled line to compare
                    double dot = (movedX * interceptDirX + movedZ * interceptDirZ) / moved;
                    double turn = Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
                    if (turn >= limit) {
                        DinoDread.LOGGER.info("[flankdiag] INTERCEPT CANCELLED (turn={}deg after={}t) raptor={}",
                                String.format(java.util.Locale.ROOT, "%.0f", turn), tickCount - interceptStartTick,
                                getName().getString());
                        interceptingNow = false;
                        interceptUntilTick = 0;
                        interceptStartTick = -1;
                        return chase;
                    }
                }
            }
            flankingNow = false;
            interceptingNow = true;
            double ddx = getX() - target.getX();
            double ddz = getZ() - target.getZ();
            double gap = Math.hypot(ddx, ddz);
            double surgeBlocksPerTick = Math.max(0.02, 2.1 * getSpeed() * getSpeed() * RaptorConfig.INTERCEPT_SPEED_BOOST.get());
            double leadTicks = Mth.clamp(gap / surgeBlocksPerTick, 4.0, 40.0); // flight time of the lunge
            return new Vec3(target.getX() + target.getDeltaMovement().x * leadTicks,
                    target.getY(),
                    target.getZ() + target.getDeltaMovement().z * leadTicks);
        }
        interceptingNow = false;
        if (flankSlot == -2 || !target.getUUID().equals(flankEvaluatedFor)) {
            boolean sameQuarry = target.getUUID().equals(flankEvaluatedFor);
            flankEvaluatedFor = target.getUUID();
            flankSlot = evaluateFlankSlot();
            if (!sameQuarry) {
                flankStartBearing = Double.NaN; // new quarry: a fresh circle (a blink keeps the running one)
            }
        }
        if (target.getUUID().equals(flankCompletedFor) && tickCount < flankReopenTick) {
            flankSlot = -1; // this raptor has already run its circle for this quarry (reopen delay below)
        }
        if (flankSlot < 0) {
            flankingNow = false;
            return chase;
        }
        // Where this raptor sits on the circle around the quarry, in world XZ.
        double dx = getX() - target.getX();
        double dz = getZ() - target.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0E-3) {
            flankingNow = true; // on top of it: the bite logic owns this tick
            return chase;
        }
        double side = flankSlot == 0 ? 1.0 : -1.0;
        double radius = Math.max(2.5, flankRadiusOverride != null ? flankRadiusOverride : RaptorConfig.FLANK_RADIUS_BLOCKS.get());
        // Ground blocks/tick this raptor can actually produce right now (measured quadratic speed law:
        // b/s ~= 43 x speed^2). Every demand on the circle - lead distance, progress credit - is capped by it,
        // or the waypoint outruns the body: live playtest saw spin-in-place, falling behind, laps never ended.
        double blocksPerTick = 2.1 * getSpeed() * getSpeed();
        double tickArc = blocksPerTick / radius; // radians of circle the body can run in one tick
        double bearing = Math.atan2(dz, dx);
        double sweepLimit = Math.toRadians(RaptorConfig.FLANK_SWEEP_DEGREES.get());
        if (Double.isNaN(flankStartBearing)) {
            flankStartBearing = bearing;
            flankLastBodyBearing = bearing;
            flankSweep = 0.0;
            flankStallTicks = 0;
        }
        // Progress is the BODY's travel around the quarry, never the waypoint's: a waypoint advancing on its own
        // books circles the body never ran, and the flank "finished" before it got near the front (playtest).
        // Forward-only, jitter clamped, teleports credited nothing beyond real running.
        double delta = Math.atan2(Math.sin(bearing - flankLastBodyBearing), Math.cos(bearing - flankLastBodyBearing)) * side;
        flankLastBodyBearing = bearing;
        if (delta > 0.0) {
            flankSweep += Math.min(delta, tickArc * 2.0);
            flankStallTicks = delta >= tickArc * 0.4 ? 0 : flankStallTicks + 1;
        } else {
            flankStallTicks++;
        }
        // Running onto (or back onto) the circle is not a stall - the bearing sits still while the body heads for
        // the ring. Only a raptor that IS on the circle and still cannot sweep is being outrun.
        if (Math.abs(distance - radius) >= radius * 0.5) {
            flankStallTicks = 0;
        }
        if (flankSweep >= sweepLimit) {
            DinoDread.LOGGER.info("[flankdiag] flank COMPLETED sweep={}deg dist={} raptor={}",
                    String.format(java.util.Locale.ROOT, "%.0f", Math.toDegrees(flankSweep)),
                    String.format(java.util.Locale.ROOT, "%.1f", distance),
                    getName().getString());
            flankCompletedFor = target.getUUID();
            flankReopenTick = tickCount + FLANK_REOPEN_TICKS;
            flankStartBearing = Double.NaN;
            flankingNow = false;
            flankSlot = -1;
            // The cut-off surge (live playtest design): a flanker that WORKED its way around commits to closing
            // the last stretch onto where the quarry is GOING, not where it is. Run straight and it meets you
            // ahead of your own nose and bites - knocking you back into the pack behind it. Dodge it by SWINGING
            // your line of travel ~60 degrees within half a second of the circle ending (see huntDestination):
            // the surge is only committed once you have held the same line for that long. An abandoned
            // (stalled) flank never surges.
            beginIntercept(target);
            return chase;
        }
        // Escape valve (live playtest): a quarry sprinting straight away barely changes bearing, so a flanker
        // running at ~1.4x sprint would hold its boost forever and no player could ever outrun the circle.
        // After a few seconds of no sweep, abandon it: the pack chases straight at chase speed, which IS outrunnable.
        if (flankStallTicks > FLANK_STALL_TICKS) {
            DinoDread.LOGGER.info("[flankdiag] flank ABANDONED (stall) sweep={}deg stallTicks={} dist={} speed={} raptor={}",
                    String.format(java.util.Locale.ROOT, "%.0f", Math.toDegrees(flankSweep)), flankStallTicks,
                    String.format(java.util.Locale.ROOT, "%.1f", distance),
                    String.format(java.util.Locale.ROOT, "%.2f", getSpeed()),
                    getName().getString());
            flankCompletedFor = target.getUUID();
            flankReopenTick = tickCount + FLANK_REOPEN_TICKS;
            flankStartBearing = Double.NaN;
            flankingNow = false;
            flankSlot = -1;
            return chase;
        }
        // The waypoint is pinned to the BODY: a body-sized lead around the circle (never further), with a floor
        // so it does not sit on the raptor's own nose and stop it dead. The raptor always runs a little around
        // its own bearing - that is the circle - and can always reach the point it is running to.
        double desiredLead = Math.toRadians(RaptorConfig.FLANK_ARC_DEGREES_PER_SECOND.get());
        double lookahead = Mth.clamp(desiredLead, Math.toRadians(15.0), Math.max(tickArc * 30.0, Math.toRadians(15.0)));
        double cos = Mth.cos((float) (side * lookahead));
        double sin = Mth.sin((float) (side * lookahead));
        double ux = dx / distance;
        double uz = dz / distance;
        double px = (ux * cos - uz * sin) * radius;
        double pz = (ux * sin + uz * cos) * radius;
        flankingNow = true;
        Vec3 dest = new Vec3(target.getX() + px, target.getY(), target.getZ() + pz);
        flankLastDestTick = tickCount;
        return dest;
    }

    private int flankLastDestTick;

    // Game-test overrides (null = use config). Game tests share one JVM and run arenas CONCURRENTLY: mutating
    // the static config knobs bled into every other running test - a hold-the-hunt test silently shrank the
    // sight of live flankers to 16 and their ring to 8, and the flank tests "reproduced" a nonexistent entity
    // bug (stalled sweep mid-circle). Entity-scoped debug values make each test an island again.
    private @Nullable Double flankRadiusOverride;
    private @Nullable Double sightRangeOverride;
    private @Nullable Integer fleeTicksOverride;

    /** Test hook: pin one raptor's flank ring radius / sight range / pack-death flee length. Null = leave. */
    public void debugOverride(@Nullable Double flankRadiusBlocks, @Nullable Double sightRange, @Nullable Integer fleeTicks) {
        if (flankRadiusBlocks != null) {
            flankRadiusOverride = flankRadiusBlocks;
        }
        if (sightRange != null) {
            sightRangeOverride = sightRange;
        }
        if (fleeTicks != null) {
            fleeTicksOverride = fleeTicks;
        }
    }

    /** Ticks of negligible bearing progress before a flanker quits its circle and chases straight (5 seconds). */
    private static final int FLANK_STALL_TICKS = 100;
    /** A flanked quarry stays "already circled" this long before the same raptor may try it again (15 s). */
    private static final int FLANK_REOPEN_TICKS = 300;
    /** Half a second: how long the surge stays dodgeable by a 60-degree swing of the quarry's line (see config). */
    private static final int INTERCEPT_TURN_WINDOW_TICKS = 10;

    /** Arm the post-flank cut-off surge and snapshot the quarry's line of travel for the turn dodge. */
    private void beginIntercept(LivingEntity quarry) {
        interceptUntilTick = tickCount + RaptorConfig.INTERCEPT_TICKS.get();
        double vx = quarry.getDeltaMovement().x;
        double vz = quarry.getDeltaMovement().z;
        double v = Math.hypot(vx, vz);
        if (v > 0.01) { // ~0.2 b/t: an actual line of travel; a standing quarry has no line to change, no dodge
            interceptStartTick = tickCount;
            interceptQuarryStartX = quarry.getX();
            interceptQuarryStartZ = quarry.getZ();
            interceptDirX = vx / v;
            interceptDirZ = vz / v;
        } else {
            interceptStartTick = -1;
        }
    }

    @Override
    public double huntSpeedBoost() {
        return flankingNow ? RaptorConfig.FLANK_SPEED_BOOST.get()
                : interceptingNow ? RaptorConfig.INTERCEPT_SPEED_BOOST.get()
                : 1.0;
    }

    /** True while this raptor is running its circle: read by tests and debugging. */
    public boolean flankingThisTick() {
        return flankingNow;
    }

    /** True while this raptor is surging to cut off the quarry's line of travel after a finished flank. */
    public boolean interceptingThisTick() {
        return interceptingNow;
    }

    /** Game-test hook: start the cut-off surge directly (flank completion is stochastic; this is not). */
    public void debugIntercept(int ticks) {
        LivingEntity quarry = getTarget();
        if (quarry == null) {
            interceptUntilTick = tickCount + ticks;
            interceptStartTick = -1;
            return;
        }
        beginIntercept(quarry);
        interceptUntilTick = tickCount + ticks; // the test owns the length, not the config
    }

    /** True once this raptor has completed its circle against the current quarry and rejoined the straight chase. */
    public boolean flankFinished() {
        LivingEntity target = getTarget();
        return target != null && target.getUUID().equals(flankCompletedFor);
    }

    /** Temporary gametest diagnostics. */
    public String flankDebug() {
        LivingEntity t = getTarget();
        double d = t == null ? -1 : Math.hypot(getX() - t.getX(), getZ() - t.getZ());
        return String.format(java.util.Locale.ROOT, "slot=%d dist=%.1f circling=%b done=%b sweep=%.0f stall=%d state=%s destAge=%d visible=%b contact=%d",
                flankSlot, d, flankingNow, flankCompletedFor != null, Math.toDegrees(flankSweep), flankStallTicks,
                getHuntState(), tickCount - flankLastDestTick, getSenses().isTargetVisible(), getSenses().contactAge());
    }

    /** Current circle progress in degrees, for tests and diagnostics. */
    public double flankSweepDegrees() {
        return Math.toDegrees(flankSweep);
    }

    /** Sticky version for tests: a circle has been completed at some point (survives the quarry dying). */
    public boolean hasCompletedFlank() {
        return flankCompletedFor != null;
    }

    /**
     * Who flanks, decided fresh per quarry: pack members within earshot sorted by id - the first (and in a pack of
     * four, the second) flanks, the rest chase straight. Stateless, so it survives deaths and stragglers.
     */
    private int evaluateFlankSlot() {
        String tag = packTag();
        if (tag == null) {
            return -1; // a spawn-egg raptor on its own does not have flanks to run
        }
        List<Velociraptor> pack = new ArrayList<>(level().getEntitiesOfClass(Velociraptor.class,
                getBoundingBox().inflate(FLANK_PACK_RADIUS),
                other -> other != this && other.isAlive() && tag.equals(other.packTag())));
        pack.add(this);
        int flankers = pack.size() >= 4 ? 2 : pack.size() == 3 ? 1 : 0;
        if (flankers == 0) {
            return -1;
        }
        pack.sort(Comparator.comparing(Velociraptor::getUUID));
        return pack.indexOf(this) < flankers ? pack.indexOf(this) : -1;
    }

    private static final double FLANK_PACK_RADIUS = 72.0; // must cover a formation stretched by a retreat:
    // roles counted from one raptor's box, so a tighter radius dropped far packmates from the count and
    // silently un-flanked them mid-hunt (live playtest, backing away from a four-pack)

    // ---- Scatter: losing the quarry sends a raptor running for a while, without the pack-death despawn. ----

    // Same absolute-game-time clock as the pack-death flee (a not-ticking entity still ends on schedule via the sweep).
    private long scatterUntilGameTime;

    @Override
    public void onHuntAbandoned() {
        // A completed (or abandoned) circle REOPENS after FLANK_REOPEN_TICKS, it does not clear on hunt churn.
        // At flank speed a raptor overshoots, loses the player behind it for a second, the hunt flickers off and
        // right back on - clearing the record there let it re-flank the same quarry in a loop: circle, lunge,
        // circle, lunge, with the boost effectively permanent (live playtest). The delay absorbs the flicker; a
        // real second encounter is flankable again fifteen seconds later.
        interceptUntilTick = 0;
        // A pack-death flight already owns the running state; mid-swim goal churn (the float goal) is not losing quarry.
        if (isFleeing() || isInWater() || !(level() instanceof ServerLevel level)) {
            return;
        }
        // The rout belongs to the pack, not to one raptor's bad seconds: while any pack-mate within earshot still
        // holds quarry, a member that momentarily lost the trail just drops back to wander - the shared-perception
        // pass (shareThePackHunt) puts it straight back on the hunt. Scattering alone is what the live playtest
        // saw: a sprinting player watched the pack peel away one raptor at a time.
        if (packHuntsAny(level)) {
            return;
        }
        int ticks = RaptorConfig.SCATTER_FLEE_TICKS.get();
        if (ticks > 0) {
            scatterUntilGameTime = Math.max(scatterUntilGameTime, level().getGameTime() + ticks);
        }
    }

    public boolean isScattering() {
        return scatterUntilGameTime > 0 && level().getGameTime() <= scatterUntilGameTime;
    }

    private boolean scatterExpired() {
        return scatterUntilGameTime > 0 && level().getGameTime() > scatterUntilGameTime;
    }

    /** Ends a due scatter; called alongside endFleeIfDue(). Scattering never despawns anything. */
    public void endScatterIfDue() {
        if (!level().isClientSide() && scatterExpired()) {
            scatterUntilGameTime = 0;
        }
    }

    /** True when a scatter clock ran out on an entity that stopped ticking; the sweep clears it, despawning nothing. */
    public boolean scatterPendingEnd() {
        return scatterExpired();
    }

    public void clearScatter() {
        scatterUntilGameTime = 0;
    }

    // Two attack animations, alternated so a pack biting together does not look like one clamped loop.
    @Override
    public Action nextBiteAction() {
        lastBiteWasLeft = !lastBiteWasLeft;
        return lastBiteWasLeft ? Action.BITE : Action.BITE_ALT;
    }

    @Override
    protected AnimationController<TRex> registerActionAnims(AnimationController<TRex> controller) {
        return super.registerActionAnims(controller)
            .triggerableAnim(ATTACK_RIGHT_TRIGGER, ATTACK_RIGHT)
            .triggerableAnim(TAIL_WHIP_TRIGGER, TAIL_WHIP);
    }

    @Override
    protected void playActionAnimation(Action action) {
        switch (action) {
            case BITE_ALT -> triggerAnim(ACTION_CONTROLLER, ATTACK_RIGHT_TRIGGER);
            case TAIL_WHIP -> triggerAnim(ACTION_CONTROLLER, TAIL_WHIP_TRIGGER);
            default -> super.playActionAnimation(action);
        }
    }

    // Footfalls are silent; the raptor's movement noise is the running-gait loop, played while sprinting (below).
    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        shareThePackHunt(level);
        if (sprintingVisual() && !isInWater()) {
            if (tickCount >= nextRunSoundTick) {
                nextRunSoundTick = tickCount + RUN_SOUND_PERIOD_TICKS;
                getVoice().step();
            }
        } else {
            nextRunSoundTick = 0;
        }
        // Idle variety: a raptor standing around mid-wander flicks its tail now and then. The synced action (and its
        // duration) keeps repeats out while the whip plays, and it never interrupts a hunt, a flight or another action.
        if (getAction() == Action.NONE
                && getHuntState() == HuntState.WANDER
                && !isFleeing()
                && getDeltaMovement().horizontalDistanceSqr() < 1.0E-4
                && random.nextDouble() < IDLE_TAIL_WHIP_CHANCE_PER_TICK) {
            startAction(Action.TAIL_WHIP);
        }
    }

    /**
     * The pack hunts as one: a raptor that has neither seen nor heard the quarry follows the pack-mate that has,
     * and while any pack-mate still perceives the shared quarry, nobody's give-up clock runs out alone. Live
     * playtest round one: each raptor needed its own first contact. Round two: the pack peeled away one by one
     * behind a sprinting player - the give-up clocks were individual, so stragglers lost contact (silence plus
     * distance) and scattered on their own schedule while the boosted flanker kept the trail. The pack now gives
     * up together, or not at all.
     */
    private void shareThePackHunt(ServerLevel level) {
        if (tickCount % PACK_SHARE_INTERVAL_TICKS != 0 || isFleeing()) {
            return;
        }
        String tag = packTag();
        if (tag == null) {
            return; // a spawn-egg raptor is its own pack of one: nobody to hear
        }
        // Within earshot (the raptors coordinate by calls, so the range is their hearing, scaled by activity).
        double radius = Math.max(RaptorConfig.HEARING_RADIUS.get(), 4.0) * Math.max(0.25, activity());
        LivingEntity myQuarry = getTarget();
        Velociraptor freshest = null;
        int freshestAge = Integer.MAX_VALUE;
        int myQuarryPackAge = -1; // the freshest contact ANY pack-mate has on MY quarry
        for (Velociraptor other : level.getEntitiesOfClass(Velociraptor.class,
                getBoundingBox().inflate(radius),
                other -> other != this && other.isAlive() && tag.equals(other.packTag())
                        && other.getTarget() != null && other.getTarget().isAlive())) {
            int age = other.getSenses().contactAge();
            if (myQuarry != null && other.getTarget() == myQuarry
                    && (myQuarryPackAge < 0 || age < myQuarryPackAge)) {
                myQuarryPackAge = age;
            }
            if (age < freshestAge) {
                freshestAge = age;
                freshest = other;
            }
        }
        if (myQuarry != null) {
            if (myQuarryPackAge >= 0) {
                getSenses().shareContactAge(myQuarryPackAge);
            }
            return;
        }
        if (freshest != null && !isScattering()) {
            getSenses().shareQuarry(freshest.getTarget());
        }
    }

    /** True while any tagged pack-mate within earshot still holds live quarry (used to hold the rout together). */
    private boolean packHuntsAny(ServerLevel level) {
        String tag = packTag();
        if (tag == null) {
            return false;
        }
        double radius = Math.max(RaptorConfig.HEARING_RADIUS.get(), 4.0) * Math.max(0.25, activity());
        return !level.getEntitiesOfClass(Velociraptor.class,
                getBoundingBox().inflate(radius),
                other -> other != this && other.isAlive() && tag.equals(other.packTag())
                        && other.getTarget() != null && other.getTarget().isAlive()).isEmpty();
    }

    private static final int PACK_SHARE_INTERVAL_TICKS = 10;

    /** The pack tag this raptor carries, or null for a lone spawn-egg raptor (each of which is its own pack of one). */
    public @Nullable String packTag() {
        for (String tag : entityTags()) {
            if (tag.startsWith(PACK_TAG_PREFIX)) {
                return tag;
            }
        }
        return null;
    }

    // When the flight ends, in absolute level game time (not a countdown): an entity that wanders into a chunk
    // that is not entity-ticking stops ticking entirely, and a tick countdown would never expire. The spawner's
    // sweep despawns those by the clock instead.
    private long fleeUntilGameTime;

    /** Start (or extend) the post-pack-death flight. Safe to call on every survivor at once. */
    public void startFleeing() {
        int ticks = fleeTicksOverride != null ? fleeTicksOverride : RaptorConfig.FLEE_TICKS.get();
        if (ticks > 0 && !level().isClientSide()) {
            fleeUntilGameTime = Math.max(fleeUntilGameTime, level().getGameTime() + ticks);
            // A pack death outranks "we lost the prey": these survivors run the death flight, then despawn.
            scatterUntilGameTime = 0;
        }
    }

    public boolean isFleeing() {
        return fleeUntilGameTime > 0 && level().getGameTime() <= fleeUntilGameTime;
    }

    /** True once the flight's clock has run out, whether or not this entity has been ticking. */
    public boolean fleeExpired() {
        return fleeUntilGameTime > 0 && level().getGameTime() > fleeUntilGameTime;
    }

    /**
     * Ends a due flight, called by {@link RaptorFleeGoal} every tick it runs: a naturally spawned raptor despawns
     * with the flight; a spawn-egg or /summon raptor just stops running.
     */
    public void endFleeIfDue() {
        if (!level().isClientSide() && fleeExpired()) {
            fleeUntilGameTime = 0;
            if (entityTags().contains(RexSpawner.NATURAL_TAG)) {
                discard();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (fleeUntilGameTime > 0) {
            output.putLong("FleeUntilGameTime", fleeUntilGameTime);
        }
        if (scatterUntilGameTime > 0) {
            output.putLong("ScatterUntilGameTime", scatterUntilGameTime);
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        fleeUntilGameTime = input.getLongOr("FleeUntilGameTime", 0L);
        scatterUntilGameTime = input.getLongOr("ScatterUntilGameTime", 0L);
    }
}
