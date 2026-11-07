package com.dinodread.entity;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.ai.HuntState;
import com.dinodread.entity.ai.RexBlockBreaker;
import com.dinodread.entity.ai.TRexHuntGoal;
import com.dinodread.entity.ai.TRexPreyGoal;
import com.dinodread.entity.ai.TRexSenses;
import com.dinodread.entity.ai.TRexVoice;
import com.dinodread.entity.ai.TRexWanderGoal;
import com.dinodread.registry.ModSounds;
import com.dinodread.world.DayNight;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;

// T-rex. Attributes come from RexConfig (STARTUP config); the hunting brain lives in entity.ai.
public class TRex extends Monster implements GeoEntity {
    /**
     * A one-off thing the T-rex is doing that has its own animation. The server sets it with {@link #startAction}; it travels
     * to clients as ordinary synced entity data (GeckoLib's own animation-trigger packet cannot be sent to the mock players in
     * GameTests), and each client plays the matching animation when it changes.
     */
    public enum Action { NONE, ROAR, BITE, BITE_ALT, TAIL_WHIP }

    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(TRex.class, EntityDataSerializers.BYTE);
    private static final Action[] ACTIONS = Action.values();
    // How long each action stays set: the length of its animation in trex.animation.json (1.8 s and 0.7083 s) in ticks.
    private static final int ROAR_ACTION_TICKS = 36;
    private static final int BITE_ACTION_TICKS = 15;

    protected static final String ACTION_CONTROLLER = "action";
    private static final String ROAR_TRIGGER = "roar";
    private static final String BITE_TRIGGER = "bite";
    protected static final String MOVEMENT_CONTROLLER = "movement";
    // Animation names inside geckolib/animations/entity/trex.animation.json (the Tarbosaurus model's own names).
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tarbosaurus.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tarbosaurus.walk");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.tarbosaurus.roar");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.tarbosaurus.bite");
    private static final int MOVEMENT_TRANSITION_TICKS = 6;
    private static final int ACTION_TRANSITION_TICKS = 2;
    // The walk animation is 1.6 s long and puts down two feet per cycle. Playing it at the speed that makes the feet land
    // once per footstepStrideBlocks keeps the legs matched to the ground speed and to the footstep sounds.
    private static final double WALK_CYCLE_SECONDS = 1.6;
    private static final int FOOTFALLS_PER_WALK_CYCLE = 2;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MIN_WALK_SPEED = 0.3;
    private static final double MAX_WALK_SPEED = 3.0;
    // Vanilla counts walking in units of 0.6 of a block (Entity.moveDist), and plays a footstep when it passes nextStep().
    private static final double VANILLA_MOVE_DIST_PER_BLOCK = 0.6;
    // Upward speed while hauling itself out of the water; the same 0.3 vanilla uses for its own (much shorter) pull out.
    private static final double WATER_EXIT_CLIMB_SPEED = 0.3;
    private static final int WATER_EXIT_GRACE_TICKS = 10;
    // Vanilla's upward nudge per tick while a mob floats (LivingEntity.jumpInLiquid), before NeoForge scales it by the swim speed.
    private static final double FLUID_JUMP_NUDGE = 0.04;
    // Swim depth: it counts as "at a bank", and floats up to climb out, for this long after it last ran into something.
    private static final int SWIM_BANK_TICKS = 10;
    // Water sinks a mob by only 0.005 a tick (about half a block a second at most), so on its own it would take seconds to get from the surface down to its swim depth.
    private static final double SINK_NUDGE = 0.02;
    // It is pushed down only while this far above its depth, and floats up only below it, so it settles at the depth instead of bobbing around it.
    private static final double SINK_SETTLE_BAND = 0.3;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final TRexSenses senses = new TRexSenses(this);
    private final TRexVoice voice = new TRexVoice(this);
    // Breaks the foliage it ends up standing on (treetops are not ground); the movement code has its own breaker for what is in its way.
    private final RexBlockBreaker stomper = new RexBlockBreaker(this);
    private HuntState huntState = HuntState.WANDER;
    private int actionTicksLeft;
    private int lastHorizontalCollisionTick = Integer.MIN_VALUE / 2;
    private int lastInWaterTick = Integer.MIN_VALUE / 2;

    public TRex(EntityType<? extends TRex> type, Level level) {
        super(type, level);
        // The scale attribute comes from the attribute defaults, which never fire the "scale changed" update that
        // resizes the hitbox: without this the model is 3x but the hitbox stays at the unscaled 3 x 4 blocks.
        refreshDimensions();
    }

    // Runs from the Mob constructor, before the fields above are initialized: goals must only touch them lazily.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TRexHuntGoal(this));
        goalSelector.addGoal(2, new TRexPreyGoal(this));
        goalSelector.addGoal(3, new TRexWanderGoal(this));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        senses.tick(level);
        voice.tick(level);
        stomper.breakFoliageBeneath();
        if (horizontalCollision) {
            lastHorizontalCollisionTick = tickCount;
        }
        if (actionTicksLeft > 0 && --actionTicksLeft == 0) {
            entityData.set(DATA_ACTION, (byte) Action.NONE.ordinal());
        }
    }

    /**
     * True if it ran into something horizontally during the last {@code ticks} ticks. The {@code horizontalCollision} flag itself flips
     * on alternate ticks while the T-rex pushes against a wall, so a goal that only ticks every second tick (the default) can read
     * "no collision" every single time; this is sampled on every tick, whatever the goals do.
     */
    public boolean collidedRecently(int ticks) {
        return tickCount - lastHorizontalCollisionTick <= ticks;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, (byte) Action.NONE.ordinal());
    }

    /** Server side: the T-rex starts a roar or a bite, which clients show with its animation. */
    public void startAction(Action action) {
        entityData.set(DATA_ACTION, (byte) action.ordinal());
        actionTicksLeft = actionDurationTicks(action);
    }

    /** How long a synced action stays set: the length of that action's animation in this model's animation file. */
    protected int actionDurationTicks(Action action) {
        return switch (action) {
            case ROAR -> ROAR_ACTION_TICKS;
            case BITE, BITE_ALT -> BITE_ACTION_TICKS;
            case NONE, TAIL_WHIP -> 0; // the Tarbosaurus has no tail-whip animation; the raptor overrides this
        };
    }

    /** The bite action to sync when a bite lands; BITE_ALT lets a model with two attack animations alternate them. */
    public Action nextBiteAction() {
        return Action.BITE;
    }

    public Action getAction() {
        return ACTIONS[entityData.get(DATA_ACTION)];
    }

    // Client side: the synced action changed, so play its animation (on the client GeckoLib's triggerAnim is purely local).
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (level().isClientSide() && DATA_ACTION.equals(accessor)) {
            playActionAnimation(getAction());
        }
    }

    /** Client side: play the animation for a freshly synced one-off action. Trigger names must match registerControllers. */
    protected void playActionAnimation(Action action) {
        switch (action) {
            case ROAR -> triggerAnim(ACTION_CONTROLLER, ROAR_TRIGGER);
            case BITE, BITE_ALT -> triggerAnim(ACTION_CONTROLLER, BITE_TRIGGER);
            case NONE, TAIL_WHIP -> { } // the Tarbosaurus has no tail-whip animation; the raptor overrides this
        }
    }

    // A footstep every footstepStrideBlocks blocks walked instead of vanilla's ~1.7, which is far too quick for this body.
    @Override
    protected float nextStep() {
        return moveDist + (float) (footstepStrideBlocks() * VANILLA_MOVE_DIST_PER_BLOCK);
    }

    // The footstep is its own sound, at a volume that carries; the block's step sound would be a faint tick under it.
    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        voice.step();
    }

    public TRexSenses getSenses() {
        return senses;
    }

    public TRexVoice getVoice() {
        return voice;
    }

    public HuntState getHuntState() {
        return huntState;
    }

    public void setHuntState(HuntState huntState) {
        this.huntState = huntState;
    }

    /**
     * How alert the T-rex is right now: 1.0 at night, {@link RexConfig#DAY_AGGRESSION} in full daylight, blended at dusk
     * and dawn. Scales sight range, hearing radius, charge distance and duration, and how long it keeps hunting.
     */
    public double activity() {
        return Mth.lerp(DayNight.nightFactor(level(), position()), RexConfig.DAY_AGGRESSION.get(), 1.0);
    }

    /**
     * Tunables read per entity instead of straight from {@link RexConfig}, so the velociraptor
     * ({@link Velociraptor}) can run the same brain on its own numbers. Every default is the T-rex config value.
     */
    public double sightRange() {
        return RexConfig.SIGHT_RANGE.get();
    }

    public double sightFieldOfViewDegrees() {
        return RexConfig.SIGHT_FOV_DEGREES.get();
    }

    public double sneakSightMultiplier() {
        return RexConfig.SNEAK_SIGHT_MULTIPLIER.get();
    }

    public double hearingRadius() {
        return RexConfig.HEARING_RADIUS.get();
    }

    public double sneakHearingMultiplier() {
        return RexConfig.SNEAK_HEARING_MULTIPLIER.get();
    }

    public int giveUpTicks() {
        return RexConfig.GIVE_UP_TICKS.get();
    }

    public double biteReach() {
        return RexConfig.BITE_REACH.get();
    }

    public double biteReachBelow() {
        return RexConfig.BITE_REACH_BELOW.get();
    }

    public int biteWindupTicks() {
        return RexConfig.BITE_WINDUP_TICKS.get();
    }

    public int biteCooldownTicks() {
        return RexConfig.BITE_COOLDOWN_TICKS.get();
    }

    /** Whether the hunt goal may enter the CHARGE state at all (raptors have no charge). */
    public boolean canCharge() {
        return true;
    }

    /** Whether the senses also look for hostile mobs (zombies, skeletons, ...) next to players. */
    public boolean huntsHostileMobs() {
        return false;
    }

    /** Which blocks the hunt mover may break on the way to a target (raptors only push through foliage). */
    public RexBlockBreaker.Policy huntBreakPolicy() {
        return RexBlockBreaker.Policy.ANY;
    }

    /** Blocks of body to keep under the surface while swimming; 0 = vanilla floating. */
    public double swimDepthBlocks() {
        return RexConfig.SWIM_DEPTH.get();
    }

    public double waterExitHeight() {
        return RexConfig.WATER_EXIT_HEIGHT.get();
    }

    public double footstepStrideBlocks() {
        return RexConfig.FOOTSTEP_STRIDE_BLOCKS.get();
    }

    public double footstepVolume() {
        return RexConfig.FOOTSTEP_VOLUME.get();
    }

    public double biteVolume() {
        return RexConfig.BITE_VOLUME.get();
    }

    public double roarVolume() {
        return RexConfig.ROAR_VOLUME.get();
    }

    public int roarCooldownTicks() {
        return RexConfig.ROAR_COOLDOWN_TICKS.get();
    }

    public boolean distantRoarEnabled() {
        return RexConfig.DISTANT_ROAR_ENABLED.get();
    }

    public int distantRoarIntervalTicks() {
        return RexConfig.DISTANT_ROAR_INTERVAL_TICKS.get();
    }

    public double distantRoarVolume() {
        return RexConfig.DISTANT_ROAR_VOLUME.get();
    }

    /**
     * The sound events this rex type plays. The raptor has its own ids (the same placeholder files for now):
     * the client camera shake keys off {@link ModSounds#TREX_STEP} and {@link ModSounds#TREX_ROAR},
     * so a raptor never shakes the camera.
     */
    public DeferredHolder<SoundEvent, SoundEvent> stepSound() {
        return ModSounds.TREX_STEP;
    }

    public DeferredHolder<SoundEvent, SoundEvent> roarSound() {
        return ModSounds.TREX_ROAR;
    }

    public DeferredHolder<SoundEvent, SoundEvent> distantRoarSound() {
        return ModSounds.TREX_ROAR_DISTANT;
    }

    public DeferredHolder<SoundEvent, SoundEvent> biteSound() {
        return ModSounds.TREX_BITE;
    }

    /**
     * True if the target is within bite reach, measured from the edge of the (scaled) hitbox, and further down by biteReachBelow: a body
     * this wide cannot get into a narrow valley or pit, but its head can reach down into one.
     */
    public boolean isInBiteRange(LivingEntity target) {
        return getAttackBoundingBox(biteReach()).expandTowards(0.0, -biteReachBelow(), 0.0).intersects(target.getHitbox());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, RexConfig.MAX_HEALTH.get())
            .add(Attributes.MOVEMENT_SPEED, RexConfig.MOVEMENT_SPEED.get())
            .add(Attributes.KNOCKBACK_RESISTANCE, RexConfig.KNOCKBACK_RESISTANCE.get())
            .add(Attributes.ATTACK_DAMAGE, RexConfig.ATTACK_DAMAGE.get())
            .add(Attributes.FOLLOW_RANGE, RexConfig.FOLLOW_RANGE.get())
            .add(Attributes.STEP_HEIGHT, RexConfig.STEP_HEIGHT.get())
            .add(NeoForgeMod.SWIM_SPEED, RexConfig.SWIM_SPEED_MULTIPLIER.get())
            .add(Attributes.SCALE, RexConfig.SCALE.get());
    }

    // Floating: a mob in water keeps tapping jump to stay at the surface, an upward nudge of 0.04 a tick. NeoForge multiplies that nudge by the swim speed
    // attribute, so with the swim speed that makes it fast enough, the rex was thrown clear of the water and bounced across the lake like a dolphin.
    // The attribute is for how fast it swims, not for how it bobs: keep the vanilla nudge.
    @Override
    public void jumpInFluid(FluidType type) {
        setDeltaMovement(getDeltaMovement().add(0.0, FLUID_JUMP_NUDGE, 0.0));
    }

    /** Blocks of body to keep under the surface right now: the configured swim depth, or 0 (vanilla's float level) while it is against a bank, so it can rise and climb out. */
    private double swimDepth() {
        return collidedRecently(SWIM_BANK_TICKS) ? 0.0 : swimDepthBlocks();
    }

    // The float goal taps jump only while more of the body than this is under water, which is what makes a mob ride on the surface (vanilla: 0.4 blocks, so a
    // T-rex walked on the water). Ask for the swim depth instead: it floats up from deeper, and in water shallower than that it wades along the bottom.
    @Override
    public double getFluidJumpThreshold() {
        double depth = swimDepth();
        return depth > 0.0 ? depth : super.getFluidJumpThreshold();
    }

    // Vanilla lets a swimmer that bumps a ledge pull itself out of the water only if the ledge is within about 0.6 blocks; that made any bank a
    // block above the water a wall for a T-rex. Same idea with waterExitHeight: if the body, raised that far, would clear what it bumped, climb.
    // It keeps pushing for a few ticks after the rex has risen out of the water, which it does before it clears the bank: the water physics
    // that vanilla's own pull-out lives in stop applying the moment the feet are out.
    @Override
    public void travel(Vec3 input) {
        super.travel(input);
        if (isInWater()) {
            lastInWaterTick = tickCount;
        }
        double depth = swimDepth();
        if (depth > 0.0 && isInWater() && !onGround() && getFluidHeight(FluidTags.WATER) < depth - SINK_SETTLE_BAND) {
            setDeltaMovement(getDeltaMovement().add(0.0, -SINK_NUDGE, 0.0));
        }
        double exitHeight = waterExitHeight();
        if (exitHeight > 0.0 && horizontalCollision && !onGround() && tickCount - lastInWaterTick <= WATER_EXIT_GRACE_TICKS) {
            Vec3 movement = getDeltaMovement();
            if (isFree(movement.x, exitHeight, movement.z)) {
                setDeltaMovement(movement.x, WATER_EXIT_CLIMB_SPEED, movement.z);
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TRex>(MOVEMENT_CONTROLLER, MOVEMENT_TRANSITION_TICKS, this::movementAnimation));
        // Registered second, so a roar or bite overrides the bones it animates; otherwise this controller plays nothing.
        controllers.add(registerActionAnims(new AnimationController<TRex>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS, state -> PlayState.STOP)));
    }

    /** The one-off action animations on the action controller; trigger names must match playActionAnimation. */
    protected AnimationController<TRex> registerActionAnims(AnimationController<TRex> controller) {
        return controller
            .triggerableAnim(ROAR_TRIGGER, roarAnimation())
            .triggerableAnim(BITE_TRIGGER, biteAnimation());
    }

    // The animations this rex type plays, by name inside its own animation file (GeckoLib finds the file by entity type id).
    // The defaults are the Tarbosaurus model's; the velociraptor overrides them with its Peridot Scout animations.
    protected RawAnimation idleAnimation() {
        return IDLE;
    }

    protected RawAnimation walkAnimation() {
        return WALK;
    }

    protected RawAnimation roarAnimation() {
        return ROAR;
    }

    protected RawAnimation biteAnimation() {
        return BITE;
    }

    /** Played instead of walk while moving through water, if this model has a swim animation. */
    protected @Nullable RawAnimation swimAnimation() {
        return null;
    }

    /** Played instead of walk while sprinting (a hunt or a flight), if this model has a run animation. */
    protected @Nullable RawAnimation runAnimation() {
        return null;
    }

    /** Played while this rex is dying, if this model has a death animation. */
    protected @Nullable RawAnimation deathAnimation() {
        return null;
    }

    /** Seconds and footfalls per loop of walkAnimation(): playback speed is matched to ground speed with these. */
    protected double walkCycleSeconds() {
        return WALK_CYCLE_SECONDS;
    }

    protected int walkFootfallsPerCycle() {
        return FOOTFALLS_PER_WALK_CYCLE;
    }

    /** True while this rex visibly sprints: drives the run animation and the raptor's running-gait sound. */
    public boolean sprintingVisual() {
        return getHuntState() == HuntState.CHARGE;
    }

    private PlayState movementAnimation(AnimationTest<TRex> state) {
        TRex rex = state.animatable();
        RawAnimation death = deathAnimation();
        if (death != null && rex.isDeadOrDying()) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(death);
        }
        if (!state.isMoving()) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(idleAnimation());
        }
        RawAnimation swim = swimAnimation();
        if (swim != null && rex.isInWater()) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(swim);
        }
        RawAnimation run = runAnimation();
        RawAnimation animation = run != null && rex.sprintingVisual() ? run : walkAnimation();
        double blocksPerSecond = Math.hypot(rex.getX() - rex.xo, rex.getZ() - rex.zo) * TICKS_PER_SECOND;
        double animationBlocksPerSecond = rex.footstepStrideBlocks() * walkFootfallsPerCycle() / walkCycleSeconds();
        state.setControllerSpeed((float) Mth.clamp(blocksPerSecond / animationBlocksPerSecond, MIN_WALK_SPEED, MAX_WALK_SPEED));
        return state.setAndContinue(animation);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
