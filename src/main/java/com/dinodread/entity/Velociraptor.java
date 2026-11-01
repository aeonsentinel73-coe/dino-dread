package com.dinodread.entity;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RaptorConfig;
import com.dinodread.entity.ai.RaptorFleeGoal;
import com.dinodread.entity.ai.RexBlockBreaker;
import com.dinodread.entity.ai.TRexHuntGoal;
import com.dinodread.entity.ai.TRexPreyGoal;
import com.dinodread.entity.ai.TRexWanderGoal;
import com.dinodread.registry.ModSounds;
import com.dinodread.spawn.RexSpawner;
import com.dinodread.world.DayNight;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * A velociraptor: the same hunting brain as the T-rex (senses, hunt goal, mover, voice) running on {@link RaptorConfig}
 * numbers, plus pack behaviour. It is smaller and quicker, cannot charge, sees hostile mobs as well as players, and hates
 * deep water (swim speed near zero). Its sounds are its own ids, which keeps it out of the T-rex camera shake.
 * <p>
 * A natural pack shares a {@code dinodread_pack_<uuid>} scoreboard tag; when any member of a tagged pack dies, every
 * survivor starts fleeing (see {@link RaptorFleeGoal} and {@link com.dinodread.spawn.RaptorSpawner}) and despawns when
 * the flee runs out. The model and sounds are still the T-rex's placeholder files.
 */
public class Velociraptor extends TRex {
    /** Prefix of the scoreboard tag that binds a natural pack together. */
    public static final String PACK_TAG_PREFIX = "dinodread_pack_";

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
            .add(NeoForgeMod.SWIM_SPEED, RaptorConfig.SWIM_SPEED_MULTIPLIER.get())
            .add(Attributes.SCALE, RaptorConfig.SCALE.get());
    }

    @Override
    public double activity() {
        return Mth.lerp(DayNight.nightFactor(level(), position()), RaptorConfig.DAY_AGGRESSION.get(), 1.0);
    }

    @Override
    public double sightRange() {
        return RaptorConfig.SIGHT_RANGE.get();
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

    @Override
    public boolean distantRoarEnabled() {
        return RaptorConfig.DISTANT_ROAR_ENABLED.get();
    }

    @Override
    public int distantRoarIntervalTicks() {
        return RaptorConfig.DISTANT_ROAR_INTERVAL_TICKS.get();
    }

    @Override
    public double distantRoarVolume() {
        return RaptorConfig.DISTANT_ROAR_VOLUME.get();
    }

    // The raptor's own sound ids (placeholder files for now). The client camera shake listens for the T-rex's
    // TREX_STEP and TREX_ROAR events by name, so these never shake the ground.
    @Override
    public DeferredHolder<SoundEvent, SoundEvent> stepSound() {
        return ModSounds.RAPTOR_STEP;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> roarSound() {
        return ModSounds.RAPTOR_ROAR;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> distantRoarSound() {
        return ModSounds.RAPTOR_ROAR_DISTANT;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> biteSound() {
        return ModSounds.RAPTOR_BITE;
    }

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
        int ticks = RaptorConfig.FLEE_TICKS.get();
        if (ticks > 0 && !level().isClientSide()) {
            fleeUntilGameTime = Math.max(fleeUntilGameTime, level().getGameTime() + ticks);
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
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        fleeUntilGameTime = input.getLongOr("FleeUntilGameTime", 0L);
    }
}
