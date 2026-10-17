package com.dinodread.entity.ai;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The T-rex's whole hunt as one small state machine (see {@link HuntState}): investigate a noise or last known
 * position, stalk a target it can see, charge once it is close (or has watched it long enough), and bite
 * whenever it is in reach. Senses live in {@link TRexSenses}; every number comes from {@link RexConfig}.
 * Movement steers straight at the destination; the T-rex is far too wide for vanilla pathfinding, so it clears its way by breaking
 * allow-listed blocks ({@link RexBlockBreaker}) and, when it stops making progress, hops, sidesteps and finally gives up ({@link RexStuckDetector}).
 */
public class TRexHuntGoal extends Goal {
    // How often the path is rebuilt. Wide-body path searches are not cheap, so not every tick.
    private static final int PATH_RECALC_INTERVAL_TICKS = 10;
    private static final float LOOK_SPEED_DEGREES = 30.0F;
    private static final int NOT_BITING = -1;
    // Below this the destination is "here" and there is no heading to push in.
    private static final double MIN_HEADING_DISTANCE = 1.0E-3;

    private final TRex rex;
    private final RexBlockBreaker breaker;
    private final RexStuckDetector stuck = new RexStuckDetector();
    private int pathRecalcTicks;
    private int stalkSeenTicks;
    private int chargeTicks;
    private int chargeCooldownTicks;
    private int biteWindupTicks = NOT_BITING;
    private int biteCooldownTicks;

    public TRexHuntGoal(TRex rex) {
        this.rex = rex;
        this.breaker = new RexBlockBreaker(rex);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return rex.getSenses().hasQuarry();
    }

    @Override
    public boolean canContinueToUse() {
        return rex.getSenses().hasQuarry();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        stuck.reset();
        pathRecalcTicks = 0;
        stalkSeenTicks = 0;
        chargeTicks = 0;
        biteWindupTicks = NOT_BITING;
    }

    @Override
    public void stop() {
        stuck.reset();
        rex.getNavigation().stop();
        rex.setAggressive(false);
        rex.setHuntState(HuntState.WANDER);
    }

    @Override
    public void tick() {
        TRexSenses senses = rex.getSenses();
        LivingEntity target = rex.getTarget();
        boolean visible = target != null && senses.isTargetVisible();
        Vec3 destination = visible ? target.position() : senses.getLastKnownPos();
        if (destination == null && target != null) {
            destination = target.position(); // target set by something else: head for it
        }
        if (destination == null) {
            return;
        }

        if (chargeCooldownTicks > 0) {
            chargeCooldownTicks--;
        }
        if (biteCooldownTicks > 0) {
            biteCooldownTicks--;
        }

        HuntState state = updateState(target, visible);
        rex.setHuntState(state);
        rex.setAggressive(state == HuntState.CHARGE);
        rex.getLookControl().setLookAt(destination.x, destination.y + (visible ? target.getEyeHeight() : 0.0), destination.z, LOOK_SPEED_DEGREES, LOOK_SPEED_DEGREES);

        if (visible) {
            tickBite(target);
        }

        boolean inBiteRange = visible && rex.isInBiteRange(target);
        double horizontalDistanceSqr = rex.position().subtract(destination).horizontalDistanceSqr();
        double arrival = RexConfig.INVESTIGATE_REACH_DISTANCE.get();
        if (state == HuntState.INVESTIGATE && horizontalDistanceSqr <= arrival * arrival) {
            // Nothing here: stand and stare until it gives up or hears something else.
            stuck.reset();
            rex.getNavigation().stop();
            return;
        }

        double speed = state == HuntState.CHARGE ? RexConfig.CHARGE_SPEED_MULTIPLIER.get() : RexConfig.STALK_SPEED_MULTIPLIER.get();
        driveTo(destination, speed, inBiteRange);
    }

    private HuntState updateState(@Nullable LivingEntity target, boolean visible) {
        if (!visible || target == null) {
            if (chargeTicks > 0) {
                endCharge();
            }
            stalkSeenTicks = 0;
            return HuntState.INVESTIGATE;
        }

        if (chargeTicks > 0) {
            if (--chargeTicks == 0) {
                chargeCooldownTicks = RexConfig.CHARGE_COOLDOWN_TICKS.get();
            }
        } else {
            stalkSeenTicks++;
            double activity = rex.activity();
            boolean close = rex.distanceTo(target) <= RexConfig.CHARGE_DISTANCE.get() * activity;
            boolean watchedLongEnough = stalkSeenTicks >= RexConfig.STALK_TICKS_BEFORE_CHARGE.get();
            if (chargeCooldownTicks == 0 && (close || watchedLongEnough)) {
                chargeTicks = Math.max(1, (int) Math.round(RexConfig.CHARGE_MAX_TICKS.get() * activity));
                stalkSeenTicks = 0;
            }
        }

        return chargeTicks > 0 ? HuntState.CHARGE : HuntState.STALK;
    }

    private void endCharge() {
        chargeTicks = 0;
        chargeCooldownTicks = RexConfig.CHARGE_COOLDOWN_TICKS.get();
    }

    /** Bite: start a windup when the target is in reach, land the bite when it ends if the target is still in reach. */
    private void tickBite(LivingEntity target) {
        if (biteWindupTicks > 0) {
            biteWindupTicks--;
        } else if (biteWindupTicks == NOT_BITING && biteCooldownTicks == 0 && rex.isInBiteRange(target)) {
            biteWindupTicks = RexConfig.BITE_WINDUP_TICKS.get();
        }

        if (biteWindupTicks == 0) {
            biteWindupTicks = NOT_BITING;
            biteCooldownTicks = RexConfig.BITE_COOLDOWN_TICKS.get();
            if (rex.isInBiteRange(target) && rex.level() instanceof ServerLevel level) {
                rex.swingForAttack(InteractionHand.MAIN_HAND);
                rex.doHurtTarget(level, target);
            }
        }
    }

    private void driveTo(Vec3 destination, double speed, boolean inBiteRange) {
        Vec3 toDestination = destination.subtract(rex.position());
        double distance = toDestination.horizontalDistance();
        boolean hasHeading = distance > MIN_HEADING_DISTANCE;
        Vec3 heading = hasHeading ? new Vec3(toDestination.x / distance, 0.0, toDestination.z / distance) : Vec3.ZERO;

        RexStuckDetector.Level level;
        if (inBiteRange || !hasHeading) {
            stuck.reset();
            level = RexStuckDetector.Level.FREE;
        } else {
            level = stuck.tick(distance);
        }

        if (level == RexStuckDetector.Level.GIVE_UP) {
            // It abandons the hunt; the wander goal takes over.
            stuck.reset();
            rex.getSenses().forget();
            rex.getNavigation().stop();
            return;
        }

        if (level == RexStuckDetector.Level.HOP && rex.onGround()) {
            rex.setDeltaMovement(heading.x * RexConfig.STUCK_HOP_FORWARD_VELOCITY.get(), RexConfig.STUCK_HOP_VELOCITY.get(), heading.z * RexConfig.STUCK_HOP_FORWARD_VELOCITY.get());
        }

        Vec3 wanted = destination;
        Vec3 pushHeading = heading;
        if (level == RexStuckDetector.Level.SIDESTEP) {
            double sign = stuck.getSidestepSign();
            pushHeading = new Vec3(-heading.z * sign, 0.0, heading.x * sign);
            wanted = new Vec3(rex.getX() + pushHeading.x * RexConfig.STUCK_SIDESTEP_DISTANCE.get(), rex.getY(), rex.getZ() + pushHeading.z * RexConfig.STUCK_SIDESTEP_DISTANCE.get());
        }

        int broken = breaker.tick(pushHeading, rex.horizontalCollision || level != RexStuckDetector.Level.FREE);
        if (broken > 0) {
            stuck.markProgress();
        }

        if (!RexConfig.USE_PATHFINDING.get()) {
            rex.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, speed);
            return;
        }

        if (--pathRecalcTicks <= 0) {
            pathRecalcTicks = PATH_RECALC_INTERVAL_TICKS;
            rex.getNavigation().moveTo(wanted.x, wanted.y, wanted.z, speed);
        }

        // No path (or the path ran out short of the target): push straight at it.
        if (rex.getNavigation().isDone()) {
            rex.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, speed);
        }
    }
}
