package com.dinodread.entity.ai;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RexConfig;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Config-driven stroll. Unlike the vanilla water-avoiding stroll it keeps wandering when no player is nearby
 * (vanilla stops idle mobs that have gone a while without a player within 32 blocks), and it picks destinations
 * further out because the T-rex is far wider than a normal mob.
 */
public class TRexWanderGoal extends RandomStrollGoal {
    // Same vertical reach vanilla strolling uses.
    private static final int VERTICAL_RANGE = 7;

    public TRexWanderGoal(PathfinderMob mob) {
        super(mob, RexConfig.WANDER_SPEED_MULTIPLIER.get(), RexConfig.WANDER_INTERVAL_TICKS.get(), false);
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        Vec3 pos = LandRandomPos.getPos(mob, RexConfig.WANDER_RANGE.get(), VERTICAL_RANGE);
        return pos != null ? pos : super.getPosition();
    }
}
