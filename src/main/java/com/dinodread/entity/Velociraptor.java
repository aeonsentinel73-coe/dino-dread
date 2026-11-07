package com.dinodread.entity;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RaptorConfig;
import com.dinodread.entity.ai.HuntState;
import com.dinodread.entity.ai.RaptorFleeGoal;
import com.dinodread.entity.ai.RexBlockBreaker;
import com.dinodread.entity.ai.TRexHuntGoal;
import com.dinodread.entity.ai.TRexPreyGoal;
import com.dinodread.entity.ai.TRexWanderGoal;
import com.dinodread.registry.ModSounds;
import com.dinodread.spawn.RexSpawner;
import com.dinodread.world.DayNight;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
 * the flee runs out. The model is the Peridot Scout (CC0, see CREDITS.md); its sounds were made for this mod.
 */
public class Velociraptor extends TRex {
    /** Prefix of the scoreboard tag that binds a natural pack together. */
    public static final String PACK_TAG_PREFIX = "dinodread_pack_";

    // Animation names inside geckolib/animations/entity/velociraptor.animation.json (the Peridot Scout's own names).
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.peridot_scout.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.peridot_scout.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.peridot_scout.run");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.peridot_scout.swim");
    private static final RawAnimation CRY = RawAnimation.begin().thenPlay("animation.peridot_scout.roar");
    private static final RawAnimation ATTACK_LEFT = RawAnimation.begin().thenPlay("animation.peridot_scout.attack_left");
    private static final RawAnimation ATTACK_RIGHT = RawAnimation.begin().thenPlay("animation.peridot_scout.attack_right");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.peridot_scout.death");
    private static final String ATTACK_RIGHT_TRIGGER = "attack_right";
    // The Peridot animations' own lengths, in ticks: call 3.0 s, attacks 1.0 s.
    private static final int CRY_ACTION_TICKS = 60;
    private static final int ATTACK_ACTION_TICKS = 20;
    // The walk animation is 4.8 s long with two strides per loop (each foot lands twice), so 4 footfalls per cycle.
    private static final double PERIDOT_WALK_CYCLE_SECONDS = 4.8;
    private static final int PERIDOT_FOOTFALLS_PER_CYCLE = 4;
    // The running-gait sound is a ~4.3 s file restarted this often while sprinting, so the gallop runs on without gaps.
    private static final int RUN_SOUND_PERIOD_TICKS = 85;

    private int nextRunSoundTick;
    private boolean lastBiteWasLeft;

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

    // Packs are quiet until they find you: no ambient night calls. (The T-rex's distant-roar config does not apply here.)
    @Override
    public boolean distantRoarEnabled() {
        return false;
    }

    // The raptor's own sound ids. The client camera shake listens for the T-rex's TREX_STEP and TREX_ROAR events by name,
    // so these never shake the ground. The bite is still a pitched-up cry stand-in until a real bite sound is made.
    @Override
    public DeferredHolder<SoundEvent, SoundEvent> stepSound() {
        return ModSounds.RAPTOR_RUN;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> roarSound() {
        return ModSounds.RAPTOR_ROAR;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> biteSound() {
        return ModSounds.RAPTOR_BITE;
    }

    // ---- Animations: the Peridot Scout's own clips (see the RawAnimation constants). ----

    @Override
    protected RawAnimation idleAnimation() {
        return IDLE;
    }

    @Override
    protected RawAnimation walkAnimation() {
        return WALK;
    }

    @Override
    protected RawAnimation roarAnimation() {
        return CRY;
    }

    @Override
    protected RawAnimation biteAnimation() {
        return ATTACK_LEFT;
    }

    @Override
    protected RawAnimation swimAnimation() {
        return SWIM;
    }

    @Override
    protected RawAnimation runAnimation() {
        return RUN;
    }

    @Override
    protected RawAnimation deathAnimation() {
        return DEATH;
    }

    @Override
    protected double walkCycleSeconds() {
        return PERIDOT_WALK_CYCLE_SECONDS;
    }

    @Override
    protected int walkFootfallsPerCycle() {
        return PERIDOT_FOOTFALLS_PER_CYCLE;
    }

    @Override
    protected int actionDurationTicks(Action action) {
        return switch (action) {
            case ROAR -> CRY_ACTION_TICKS;
            case BITE, BITE_ALT -> ATTACK_ACTION_TICKS;
            case NONE -> 0;
        };
    }

    // A raptor never charges, so "sprinting" means it has spotted quarry (or is fleeing the pack's loss).
    @Override
    public boolean sprintingVisual() {
        HuntState state = getHuntState();
        return state == HuntState.STALK || state == HuntState.CHARGE || isFleeing();
    }

    // Two attack animations, alternated so a pack biting together does not look like one clamped loop.
    @Override
    public Action nextBiteAction() {
        lastBiteWasLeft = !lastBiteWasLeft;
        return lastBiteWasLeft ? Action.BITE : Action.BITE_ALT;
    }

    @Override
    protected AnimationController<TRex> registerActionAnims(AnimationController<TRex> controller) {
        return super.registerActionAnims(controller).triggerableAnim(ATTACK_RIGHT_TRIGGER, ATTACK_RIGHT);
    }

    @Override
    protected void playActionAnimation(Action action) {
        if (action == Action.BITE_ALT) {
            triggerAnim(ACTION_CONTROLLER, ATTACK_RIGHT_TRIGGER);
        } else {
            super.playActionAnimation(action);
        }
    }

    // Footfalls are silent; the raptor's movement noise is the running-gait loop, played while sprinting (below).
    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (sprintingVisual() && !isInWater()) {
            if (tickCount >= nextRunSoundTick) {
                nextRunSoundTick = tickCount + RUN_SOUND_PERIOD_TICKS;
                getVoice().step();
            }
        } else {
            nextRunSoundTick = 0;
        }
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
