package com.dinodread.entity;

import com.dinodread.config.SuperRexConfig;
import com.dinodread.spawn.RexSpawner;
import com.dinodread.world.DayNight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * The super rex: a rarer T-rex (one in {@code superRexChance} of natural spawns) that is slightly bigger,
 * faster on land, slower in the water, and single-minded about players: 360-degree sight, a longer memory,
 * charges sooner and bites sooner. Its hunt is a timed event — once a survival player gets it in view,
 * {@link SuperRexConfig#DESPAWN_AFTER_SPOTTED_TICKS} later a naturally spawned super rex is gone, so a
 * player who survives the chase wins by outlasting it. Textures and model live under the {@code super_rex}
 * asset ids; the texture is the T-rex's with the red and green swapped.
 */
public class SuperRex extends TRex {
    // The "a player can see it" check is a raycast per player, so not every tick.
    private static final int SPOTTED_CHECK_INTERVAL_TICKS = 10;
    private static final int NOT_SPOTTED = -1;

    // Tick at which this rex despawns, or NOT_SPOTTED while no player has seen it yet. Not saved: after a reload the
    // clock restarts, which is the same deal maxLifetimeTicks already makes ("reloading the chunk restarts the count").
    private int despawnAtTick = NOT_SPOTTED;

    public SuperRex(EntityType<? extends SuperRex> type, Level level) {
        super(type, level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        tickSpottedDespawn(level);
    }

    /**
     * The timed hunt: the clock starts the first time a survival player is within {@code spottedRange} with clear
     * line of sight to the rex (the server's best reading of "the rex is in the player's eyesight"), and the rex
     * is discarded {@code despawnAfterSpottedTicks} later. Only natural spawns are timed; egg and command rexes are never touched.
     */
    private void tickSpottedDespawn(ServerLevel level) {
        int limit = SuperRexConfig.DESPAWN_AFTER_SPOTTED_TICKS.get();
        if (limit <= 0 || !entityTags().contains(RexSpawner.NATURAL_TAG)) {
            return;
        }
        if (despawnAtTick == NOT_SPOTTED) {
            if (tickCount % SPOTTED_CHECK_INTERVAL_TICKS == 0 && anyPlayerCanSeeMe(level)) {
                despawnAtTick = tickCount + limit;
            }
        } else if (tickCount >= despawnAtTick) {
            discard();
        }
    }

    private boolean anyPlayerCanSeeMe(ServerLevel level) {
        double range = SuperRexConfig.SPOTTED_RANGE.get();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) {
                continue;
            }
            if (player.distanceToSqr(this) <= range * range && getSensing().hasLineOfSight(player)) {
                return true;
            }
        }
        return false;
    }

    /** Test hook: ticks left on the despawn clock, or -1 while no player has seen this rex yet. */
    public int despawnCountdownTicks() {
        return despawnAtTick == NOT_SPOTTED ? NOT_SPOTTED : despawnAtTick - tickCount;
    }

    // The super rex's own senses and hunt numbers; everything else is shared with the T-rex through RexConfig.
    @Override
    public double activity() {
        return Mth.lerp(DayNight.nightFactor(level(), position()), SuperRexConfig.DAY_AGGRESSION.get(), 1.0);
    }

    @Override
    public double sightRange() {
        return SuperRexConfig.SIGHT_RANGE.get();
    }

    @Override
    public double sightFieldOfViewDegrees() {
        return SuperRexConfig.SIGHT_FOV_DEGREES.get();
    }

    @Override
    public double sneakSightMultiplier() {
        return SuperRexConfig.SNEAK_SIGHT_MULTIPLIER.get();
    }

    @Override
    public double hearingRadius() {
        return SuperRexConfig.HEARING_RADIUS.get();
    }

    @Override
    public double sneakHearingMultiplier() {
        return SuperRexConfig.SNEAK_HEARING_MULTIPLIER.get();
    }

    @Override
    public double chargeDistance() {
        return SuperRexConfig.CHARGE_DISTANCE.get();
    }

    @Override
    public int stalkTicksBeforeCharge() {
        return SuperRexConfig.STALK_TICKS_BEFORE_CHARGE.get();
    }

    @Override
    public int chargeMaxTicks() {
        return SuperRexConfig.CHARGE_MAX_TICKS.get();
    }

    @Override
    public int chargeCooldownTicks() {
        return SuperRexConfig.CHARGE_COOLDOWN_TICKS.get();
    }

    @Override
    public int giveUpTicks() {
        return SuperRexConfig.GIVE_UP_TICKS.get();
    }

    @Override
    public int biteWindupTicks() {
        return SuperRexConfig.BITE_WINDUP_TICKS.get();
    }

    @Override
    public int biteCooldownTicks() {
        return SuperRexConfig.BITE_COOLDOWN_TICKS.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, SuperRexConfig.MAX_HEALTH.get())
            .add(Attributes.MOVEMENT_SPEED, SuperRexConfig.MOVEMENT_SPEED.get())
            .add(Attributes.KNOCKBACK_RESISTANCE, SuperRexConfig.KNOCKBACK_RESISTANCE.get())
            .add(Attributes.ATTACK_DAMAGE, SuperRexConfig.ATTACK_DAMAGE.get())
            .add(Attributes.FOLLOW_RANGE, SuperRexConfig.FOLLOW_RANGE.get())
            .add(Attributes.STEP_HEIGHT, SuperRexConfig.STEP_HEIGHT.get())
            .add(NeoForgeMod.SWIM_SPEED, SuperRexConfig.SWIM_SPEED_MULTIPLIER.get())
            .add(Attributes.SCALE, SuperRexConfig.SCALE.get());
    }
}
