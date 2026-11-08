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
 * the blocks that are not on the unbreakable list ({@link RexBlockBreaker}) and, when it stops making progress, hops, sidesteps and finally gives up ({@link RexStuckDetector}),
 * all through the shared {@link RexMover}.
 */
public class TRexHuntGoal extends Goal {
    private static final float LOOK_SPEED_DEGREES = 30.0F;
    private static final int NOT_BITING = -1;

    private final TRex rex;
    private final RexMover mover;
    private int stalkSeenTicks;
    private int chargeTicks;
    private int chargeCooldownTicks;
    private int biteWindupTicks = NOT_BITING;
    private int biteCooldownTicks;
    /** The quarry as of the last tick: lets stop() tell a kill (it died) from an escape (it got away alive). */
    private @Nullable LivingEntity lastTarget;

    public TRexHuntGoal(TRex rex) {
        this.rex = rex;
        this.mover = new RexMover(rex);
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
        mover.reset();
        stalkSeenTicks = 0;
        chargeTicks = 0;
        biteWindupTicks = NOT_BITING;
        lastTarget = null;
        rex.getVoice().roar();
    }

    @Override
    public void stop() {
        // A hunt that ended with the quarry dead (or dying) is a win, not an abandonment; neither is a rex that died
        // mid-hunt. Raptors scatter only when living quarry slipped away (unreachable path, out of the senses).
        boolean abandoned = rex.isAlive() && (lastTarget == null || lastTarget.isAlive());
        lastTarget = null;
        mover.reset();
        rex.getNavigation().stop();
        rex.setAggressive(false);
        rex.setHuntState(HuntState.WANDER);
        if (abandoned) {
            rex.onHuntAbandoned();
        }
    }

    @Override
    public void tick() {
        TRexSenses senses = rex.getSenses();
        LivingEntity target = rex.getTarget();
        lastTarget = target;
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
        // Measured from the edge of the hitbox, not its centre: a centre-based distance stopped a narrow rex far short of the noise.
        double arrival = rex.investigateReachFromEdge() + rex.getBbWidth() / 2.0;
        if (state == HuntState.INVESTIGATE && horizontalDistanceSqr <= arrival * arrival) {
            // Nothing here: stand and stare until it gives up or hears something else.
            mover.reset();
            rex.getNavigation().stop();
            return;
        }

        double speed = state == HuntState.CHARGE ? RexConfig.CHARGE_SPEED_MULTIPLIER.get() : RexConfig.STALK_SPEED_MULTIPLIER.get();
        // Flankers run a circle around the quarry instead of straight at it (identity for the T-rex and non-flankers),
        // with a speed boost so they can actually get around it; both lapse the moment the hunt does.
        destination = rex.huntDestination(destination, visible);
        speed *= rex.huntSpeedBoost();
        if (mover.driveTo(destination, speed, inBiteRange, rex.huntBreakPolicy()) == RexStuckDetector.Level.GIVE_UP) {
            rex.getSenses().forget();
            rex.getNavigation().stop();
        }
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
                chargeCooldownTicks = rex.chargeCooldownTicks();
            }
        } else if (rex.canCharge()) {
            stalkSeenTicks++;
            double activity = rex.activity();
            boolean close = rex.distanceTo(target) <= rex.chargeDistance() * activity;
            boolean watchedLongEnough = stalkSeenTicks >= rex.stalkTicksBeforeCharge();
            if (chargeCooldownTicks == 0 && (close || watchedLongEnough)) {
                chargeTicks = Math.max(1, (int) Math.round(rex.chargeMaxTicks() * activity));
                stalkSeenTicks = 0;
            }
        }

        return chargeTicks > 0 ? HuntState.CHARGE : HuntState.STALK;
    }

    private void endCharge() {
        chargeTicks = 0;
        chargeCooldownTicks = rex.chargeCooldownTicks();
    }

    /** Bite: start a windup when the target is in reach, land the bite when it ends if the target is still in reach. */
    private void tickBite(LivingEntity target) {
        if (biteWindupTicks > 0) {
            biteWindupTicks--;
        } else if (biteWindupTicks == NOT_BITING && biteCooldownTicks == 0 && rex.isInBiteRange(target)) {
            biteWindupTicks = rex.biteWindupTicks();
        }

        if (biteWindupTicks == 0) {
            biteWindupTicks = NOT_BITING;
            biteCooldownTicks = rex.biteCooldownTicks();
            if (rex.isInBiteRange(target) && rex.level() instanceof ServerLevel level) {
                rex.swingForAttack(InteractionHand.MAIN_HAND);
                rex.getVoice().bite();
                rex.doHurtTarget(level, target);
            }
        }
    }
}
