package com.dinodread.entity.ai;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;

import net.minecraft.world.phys.Vec3;

/**
 * The T-rex's shared steering: drive toward a destination, clearing the way. Used by both the hunt goal and
 * the wander goal. The T-rex is far too wide for vanilla pathfinding, so it steers straight at the
 * destination, breaks the blocks it pushes against that are not on the unbreakable list ({@link RexBlockBreaker}) and, when it stops making
 * progress, hops, sidesteps and finally gives up ({@link RexStuckDetector}).
 */
public final class RexMover {
    // How often the path is rebuilt. Wide-body path searches are not cheap, so not every tick.
    private static final int PATH_RECALC_INTERVAL_TICKS = 10;
    // "Touching a wall" means it bumped into something within this many ticks: the collision flag flips every other tick, and goals tick every other tick.
    private static final int RECENT_COLLISION_TICKS = 4;
    // Below this the destination is "here" and there is no heading to push in.
    private static final double MIN_HEADING_DISTANCE = 1.0E-3;

    private final TRex rex;
    private final RexBlockBreaker breaker;
    private final RexStuckDetector stuck = new RexStuckDetector();
    private int pathRecalcTicks;

    public RexMover(TRex rex) {
        this.rex = rex;
        this.breaker = new RexBlockBreaker(rex);
    }

    /**
     * Steers the T-rex toward {@code destination} this tick. {@code standingStill} = it is where it wants to be (e.g. in bite
     * range): the stuck detector is reset instead of fed. {@code breaking} says which of the blocks it pushes against it may break:
     * anything not on the unbreakable list when hunting a player, only foliage when wandering or after an animal (so trees are never a trap).
     * Returns the stuck level this tick; {@link RexStuckDetector.Level#GIVE_UP} means it did NOT steer, the mover has reset itself,
     * and the caller must abandon this destination.
     */
    public RexStuckDetector.Level driveTo(Vec3 destination, double speed, boolean standingStill, RexBlockBreaker.Policy breaking) {
        Vec3 toDestination = destination.subtract(rex.position());
        double distance = toDestination.horizontalDistance();
        boolean hasHeading = distance > MIN_HEADING_DISTANCE;
        Vec3 heading = hasHeading ? new Vec3(toDestination.x / distance, 0.0, toDestination.z / distance) : Vec3.ZERO;

        RexStuckDetector.Level level;
        if (standingStill || !hasHeading) {
            stuck.reset();
            level = RexStuckDetector.Level.FREE;
        } else {
            level = stuck.tick(distance);
        }

        if (level == RexStuckDetector.Level.GIVE_UP) {
            // It abandons the destination; the caller takes over.
            stuck.reset();
            return RexStuckDetector.Level.GIVE_UP;
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

        if (breaking != RexBlockBreaker.Policy.NONE) {
            int broken = breaker.tick(pushHeading, rex.collidedRecently(RECENT_COLLISION_TICKS) || level != RexStuckDetector.Level.FREE, breaking);
            if (broken > 0) {
                stuck.markProgress();
            }
        }

        if (!RexConfig.USE_PATHFINDING.get()) {
            rex.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, speed);
            return level;
        }

        if (--pathRecalcTicks <= 0) {
            pathRecalcTicks = PATH_RECALC_INTERVAL_TICKS;
            rex.getNavigation().moveTo(wanted.x, wanted.y, wanted.z, speed);
        }

        // No path (or the path ran out short of the target): push straight at it.
        if (rex.getNavigation().isDone()) {
            rex.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, speed);
        }

        return level;
    }

    /** Forget all stuck state (call when a goal starts, stops, or arrives). */
    public void reset() {
        stuck.reset();
        pathRecalcTicks = 0;
    }
}
