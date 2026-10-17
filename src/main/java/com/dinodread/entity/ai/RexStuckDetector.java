package com.dinodread.entity.ai;

import com.dinodread.config.RexConfig;

/**
 * Notices when the T-rex is not getting anywhere and escalates: it checks progress every
 * {@code STUCK_CHECK_INTERVAL_TICKS} ticks and, while it makes no progress, counts up stuck ticks and moves
 * through {@link Level}s: free, break, hop, sidestep, give up. Task 006 feeds it from the hunt goal.
 * <p>
 * Plain Java, no Minecraft types: it is fed the distance to the destination once per tick and reports the
 * current level, so it can be exercised directly by a game test.
 */
public final class RexStuckDetector {
    /** How stuck the T-rex is, escalating over time. */
    public enum Level {
        FREE, BREAK, HOP, SIDESTEP, GIVE_UP
    }

    private int stuckTicks;
    private int ticksSinceCheck;
    private double distanceAtCheck;
    private boolean hasBaseline;

    /** Call once per tick while the T-rex is trying to get to a destination. Returns the current level. */
    public Level tick(double distanceToDestination) {
        if (!hasBaseline) {
            distanceAtCheck = distanceToDestination;
            ticksSinceCheck = 0;
            hasBaseline = true;
        }

        ticksSinceCheck++;
        if (ticksSinceCheck >= RexConfig.STUCK_CHECK_INTERVAL_TICKS.get()) {
            boolean progressed = distanceAtCheck - distanceToDestination >= RexConfig.STUCK_MIN_PROGRESS.get();
            if (progressed) {
                stuckTicks = 0;
            } else {
                stuckTicks += ticksSinceCheck;
            }

            distanceAtCheck = distanceToDestination;
            ticksSinceCheck = 0;
        }

        return getLevel();
    }

    /** Something other than moving closer counts as progress (e.g. blocks were just broken): back to FREE. */
    public void markProgress() {
        stuckTicks = 0;
        hasBaseline = false;
    }

    /** Not trying to move any more (arrived, biting, new target...): back to FREE and forget the baseline. */
    public void reset() {
        stuckTicks = 0;
        ticksSinceCheck = 0;
        hasBaseline = false;
    }

    public Level getLevel() {
        // Compared from the largest threshold down, so a mis-ordered config still returns something sensible.
        if (stuckTicks >= RexConfig.STUCK_GIVE_UP_AFTER_TICKS.get()) {
            return Level.GIVE_UP;
        } else if (stuckTicks >= RexConfig.STUCK_SIDESTEP_AFTER_TICKS.get()) {
            return Level.SIDESTEP;
        } else if (stuckTicks >= RexConfig.STUCK_HOP_AFTER_TICKS.get()) {
            return Level.HOP;
        } else if (stuckTicks >= RexConfig.STUCK_BREAK_AFTER_TICKS.get()) {
            return Level.BREAK;
        }

        return Level.FREE;
    }

    /** +1.0 or -1.0: which way a sidestep currently goes. It flips every {@code STUCK_SIDESTEP_SWITCH_TICKS} of being stuck. */
    public double getSidestepSign() {
        int phase = Math.max(0, stuckTicks - RexConfig.STUCK_SIDESTEP_AFTER_TICKS.get()) / RexConfig.STUCK_SIDESTEP_SWITCH_TICKS.get();
        return phase % 2 == 0 ? 1.0 : -1.0;
    }
}
