package com.dinodread.entity.ai;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RaptorConfig;
import com.dinodread.entity.TRex;
import com.dinodread.entity.Velociraptor;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The raptor running state, shared by two flights: the one after a pack member's death (run from the nearest player,
 * natural members despawn when the clock runs out) and the scatter after losing quarry (same running, never a
 * despawn). Both run through the shared {@link RexMover}; {@link Velociraptor} owns the clocks.
 */
public class RaptorFleeGoal extends Goal {
    /** How far each leg of the flight aims. The direction is re-picked whenever the raptor arrives or gets stuck. */
    private static final double FLEE_LEG_BLOCKS = 24.0;
    private static final double PLAYER_THREAT_RADIUS = 48.0;
    private static final double MOB_THREAT_RADIUS = 24.0;
    /** Considered arrived within this many blocks of the leg's end, so a raptor does not orbit its destination. */
    private static final double ARRIVED_DIST = 3.0;

    private final Velociraptor raptor;
    private final RexMover mover;
    private @Nullable Vec3 destination;

    public RaptorFleeGoal(Velociraptor raptor) {
        this.raptor = raptor;
        this.mover = new RexMover(raptor);
        // Without the MOVE/LOOK flags the flee runs alongside the hunt goal instead of suspending it, and the hunt
        // (which ticks after the flee) overwrites the flight path: a raptor that already had quarry keeps attacking.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return raptor.isFleeing() || raptor.isScattering();
    }

    @Override
    public void start() {
        destination = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        raptor.endFleeIfDue();
        raptor.endScatterIfDue();
        if (!raptor.isFleeing() && !raptor.isScattering()) {
            return;
        }

        boolean arrived = destination != null && raptor.distanceToSqr(destination) < ARRIVED_DIST * ARRIVED_DIST;
        if (destination == null || arrived
                || mover.driveTo(destination, RaptorConfig.FLEE_SPEED_MULTIPLIER.get(), false, RexBlockBreaker.Policy.FOLIAGE)
                        == RexStuckDetector.Level.GIVE_UP) {
            destination = pickLeg();
        }
    }

    /** Straight away from the nearest threat; no threat in range means a random direction, away from wherever the pack broke apart. */
    private Vec3 pickLeg() {
        Vec3 away;
        LivingEntity threat = nearestThreat();
        if (threat != null) {
            away = raptor.position().subtract(threat.position());
        } else {
            double angle = raptor.getRandom().nextDouble() * Math.PI * 2.0;
            away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        }
        away = new Vec3(away.x, 0.0, away.z);
        if (away.lengthSqr() < 0.01) {
            double angle = raptor.getRandom().nextDouble() * Math.PI * 2.0;
            away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        }
        away = away.normalize().scale(FLEE_LEG_BLOCKS);
        return raptor.position().add(away.x, 0.0, away.z);
    }

    private @Nullable LivingEntity nearestThreat() {
        Player player = raptor.level().getNearestPlayer(raptor, PLAYER_THREAT_RADIUS);
        if (player != null && player.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) {
            return player;
        }
        LivingEntity closest = null;
        double closestDistSqr = Double.MAX_VALUE;
        for (Monster mob : raptor.level().getEntitiesOfClass(Monster.class, raptor.getBoundingBox().inflate(MOB_THREAT_RADIUS))) {
            // TRex extends Monster: neither raptors nor T-rexes count as a threat to flee from, or a pack would run from itself.
            if (!mob.isAlive() || mob == raptor || mob instanceof TRex) {
                continue;
            }
            double distSqr = raptor.distanceToSqr(mob);
            if (distSqr < closestDistSqr) {
                closest = mob;
                closestDistSqr = distSqr;
            }
        }
        return closest;
    }
}
