package com.dinodread.entity.ai;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

/**
 * Occasionally, with no player detected, the T-rex picks an animal within {@code preyRange}, walks up to it
 * (never breaking blocks), bites it and stands over the kill for {@code preyEatTicks}; tamed, leashed and
 * name-tagged animals are left alone. A detected player always takes over: the hunt goal has higher priority
 * and {@code hasQuarry()} ends this goal.
 */
public class TRexPreyGoal extends Goal {
    private static final float LOOK_SPEED_DEGREES = 30.0F;

    private final TRex rex;
    private final RexMover mover;
    private @Nullable Animal prey;
    private @Nullable Vec3 eatSpot;
    private int chaseTicks;
    private int biteCooldownTicks;
    private int eatTicksLeft;

    public TRexPreyGoal(TRex rex) {
        this.rex = rex;
        this.mover = new RexMover(rex);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // canUse runs every second tick, so halve the interval to keep the configured average.
        if (!RexConfig.PREY_ENABLED.get() || rex.getSenses().hasQuarry() || rex.getRandom().nextInt(reducedTickDelay(RexConfig.PREY_INTERVAL_TICKS.get())) != 0) {
            return false;
        }

        prey = findPrey();
        return prey != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !rex.getSenses().hasQuarry() && (eatTicksLeft > 0 || (prey != null && prey.isAlive() && chaseTicks < RexConfig.PREY_MAX_CHASE_TICKS.get()));
    }

    @Override
    public void start() {
        mover.reset();
        chaseTicks = 0;
        biteCooldownTicks = 0;
        eatTicksLeft = 0;
        eatSpot = null;
    }

    @Override
    public void stop() {
        mover.reset();
        prey = null;
        eatSpot = null;
        eatTicksLeft = 0;
    }

    @Override
    public void tick() {
        if (eatTicksLeft > 0) {
            eatTicksLeft--;
            if (eatSpot != null) {
                rex.getLookControl().setLookAt(eatSpot.x, eatSpot.y, eatSpot.z, LOOK_SPEED_DEGREES, LOOK_SPEED_DEGREES);
            }
            return;
        }

        if (prey == null) {
            return;
        }

        chaseTicks++;
        if (biteCooldownTicks > 0) {
            biteCooldownTicks--;
        }
        rex.getLookControl().setLookAt(prey, LOOK_SPEED_DEGREES, LOOK_SPEED_DEGREES);

        if (rex.isInBiteRange(prey)) {
            mover.reset();
            if (biteCooldownTicks == 0 && rex.level() instanceof ServerLevel level) {
                rex.swingForAttack(InteractionHand.MAIN_HAND);
                rex.doHurtTarget(level, prey);
                biteCooldownTicks = RexConfig.BITE_COOLDOWN_TICKS.get();
            }
            if (!prey.isAlive()) {
                eatSpot = prey.position();
                eatTicksLeft = RexConfig.PREY_EAT_TICKS.get();
            }
            return;
        }

        if (mover.driveTo(prey.position(), RexConfig.PREY_SPEED_MULTIPLIER.get(), false, false) == RexStuckDetector.Level.GIVE_UP) {
            prey = null;
        }
    }

    private @Nullable Animal findPrey() {
        List<Animal> animals = rex.level().getEntitiesOfClass(Animal.class, rex.getBoundingBox().inflate(RexConfig.PREY_RANGE.get()), this::isPrey);
        @Nullable Animal closest = null;
        double closestDistanceSqr = Double.MAX_VALUE;
        for (Animal animal : animals) {
            double distanceSqr = rex.distanceToSqr(animal);
            if (distanceSqr < closestDistanceSqr) {
                closest = animal;
                closestDistanceSqr = distanceSqr;
            }
        }
        return closest;
    }

    private boolean isPrey(Animal animal) {
        if (!animal.isAlive() || animal.hasCustomName() || animal.isLeashed()) {
            return false;
        }
        return !(animal instanceof OwnableEntity ownable && ownable.getOwnerReference() != null);
    }
}
