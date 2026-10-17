package com.dinodread.entity.ai;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The T-rex's idle roaming, done without vanilla pathfinding: the 9 x 12 body is far too wide for
 * {@code RandomStrollGoal} + {@code PathNavigation} to path through forests or bounded areas, so it picks a
 * random point at the edge of its wander range and walks straight at it through the shared {@link RexMover},
 * picking another when it arrives or gets stuck. It keeps roaming with nobody nearby (vanilla stops idle mobs
 * that have gone a while without a player) and wandering never breaks blocks.
 */
public class TRexWanderGoal extends Goal {
    private final TRex rex;
    private final RexMover mover;
    private @Nullable Vec3 destination;

    public TRexWanderGoal(TRex rex) {
        this.rex = rex;
        this.mover = new RexMover(rex);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !rex.getSenses().hasQuarry() && rex.getRandom().nextInt(RexConfig.WANDER_INTERVAL_TICKS.get()) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return destination != null && !rex.getSenses().hasQuarry();
    }

    @Override
    public void start() {
        mover.reset();
        destination = pickDestination();
    }

    @Override
    public void stop() {
        mover.reset();
        destination = null;
    }

    @Override
    public void tick() {
        if (destination == null) {
            return;
        }

        double arrival = RexConfig.WANDER_ARRIVAL_DISTANCE.get();
        if (rex.position().subtract(destination).horizontalDistanceSqr() <= arrival * arrival) {
            destination = null;
            return;
        }

        if (mover.driveTo(destination, RexConfig.WANDER_SPEED_MULTIPLIER.get(), false, false) == RexStuckDetector.Level.GIVE_UP) {
            destination = null;
        }
    }

    private Vec3 pickDestination() {
        double range = RexConfig.WANDER_RANGE.get();
        double angle = rex.getRandom().nextDouble() * 2.0 * Math.PI;
        double distance = range / 2.0 + rex.getRandom().nextDouble() * range / 2.0;
        return new Vec3(rex.getX() + Math.cos(angle) * distance, rex.getY(), rex.getZ() + Math.sin(angle) * distance);
    }
}
