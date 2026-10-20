package com.dinodread.entity;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.ai.HuntState;
import com.dinodread.entity.ai.TRexHuntGoal;
import com.dinodread.entity.ai.TRexPreyGoal;
import com.dinodread.entity.ai.TRexSenses;
import com.dinodread.entity.ai.TRexVoice;
import com.dinodread.entity.ai.TRexWanderGoal;
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

// T-rex. Attributes come from RexConfig (STARTUP config); the hunting brain lives in entity.ai.
public class TRex extends Monster implements GeoEntity {
    /**
     * A one-off thing the T-rex is doing that has its own animation. The server sets it with {@link #startAction}; it travels
     * to clients as ordinary synced entity data (GeckoLib's own animation-trigger packet cannot be sent to the mock players in
     * GameTests), and each client plays the matching animation when it changes.
     */
    public enum Action { NONE, ROAR, BITE }

    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(TRex.class, EntityDataSerializers.BYTE);
    private static final Action[] ACTIONS = Action.values();
    // How long each action stays set: the length of its animation in trex.animation.json (1.8 s and 0.7083 s) in ticks.
    private static final int ROAR_ACTION_TICKS = 36;
    private static final int BITE_ACTION_TICKS = 15;

    private static final String ACTION_CONTROLLER = "action";
    private static final String ROAR_TRIGGER = "roar";
    private static final String BITE_TRIGGER = "bite";
    private static final String MOVEMENT_CONTROLLER = "movement";
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

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final TRexSenses senses = new TRexSenses(this);
    private final TRexVoice voice = new TRexVoice(this);
    private HuntState huntState = HuntState.WANDER;
    private int actionTicksLeft;

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
        if (actionTicksLeft > 0 && --actionTicksLeft == 0) {
            entityData.set(DATA_ACTION, (byte) Action.NONE.ordinal());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, (byte) Action.NONE.ordinal());
    }

    /** Server side: the T-rex starts a roar or a bite, which clients show with its animation. */
    public void startAction(Action action) {
        entityData.set(DATA_ACTION, (byte) action.ordinal());
        actionTicksLeft = action == Action.ROAR ? ROAR_ACTION_TICKS : BITE_ACTION_TICKS;
    }

    public Action getAction() {
        return ACTIONS[entityData.get(DATA_ACTION)];
    }

    // Client side: the synced action changed, so play its animation (on the client GeckoLib's triggerAnim is purely local).
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (level().isClientSide() && DATA_ACTION.equals(accessor)) {
            switch (getAction()) {
                case ROAR -> triggerAnim(ACTION_CONTROLLER, ROAR_TRIGGER);
                case BITE -> triggerAnim(ACTION_CONTROLLER, BITE_TRIGGER);
                case NONE -> { }
            }
        }
    }

    // A footstep every footstepStrideBlocks blocks walked instead of vanilla's ~1.7, which is far too quick for this body.
    @Override
    protected float nextStep() {
        return moveDist + (float) (RexConfig.FOOTSTEP_STRIDE_BLOCKS.get() * VANILLA_MOVE_DIST_PER_BLOCK);
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

    /** True if the target is within bite reach, measured from the edge of the (scaled) hitbox. */
    public boolean isInBiteRange(LivingEntity target) {
        return getAttackBoundingBox(RexConfig.BITE_REACH.get()).intersects(target.getHitbox());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, RexConfig.MAX_HEALTH.get())
            .add(Attributes.MOVEMENT_SPEED, RexConfig.MOVEMENT_SPEED.get())
            .add(Attributes.KNOCKBACK_RESISTANCE, RexConfig.KNOCKBACK_RESISTANCE.get())
            .add(Attributes.ATTACK_DAMAGE, RexConfig.ATTACK_DAMAGE.get())
            .add(Attributes.FOLLOW_RANGE, RexConfig.FOLLOW_RANGE.get())
            .add(Attributes.STEP_HEIGHT, RexConfig.STEP_HEIGHT.get())
            .add(Attributes.SCALE, RexConfig.SCALE.get());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TRex>(MOVEMENT_CONTROLLER, MOVEMENT_TRANSITION_TICKS, TRex::movementAnimation));
        // Registered second, so a roar or bite overrides the bones it animates; otherwise this controller plays nothing.
        controllers.add(new AnimationController<TRex>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS, state -> PlayState.STOP)
            .triggerableAnim(ROAR_TRIGGER, ROAR)
            .triggerableAnim(BITE_TRIGGER, BITE));
    }

    private static PlayState movementAnimation(AnimationTest<TRex> state) {
        if (!state.isMoving()) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(IDLE);
        }

        TRex rex = state.animatable();
        double blocksPerSecond = Math.hypot(rex.getX() - rex.xo, rex.getZ() - rex.zo) * TICKS_PER_SECOND;
        double animationBlocksPerSecond = RexConfig.FOOTSTEP_STRIDE_BLOCKS.get() * FOOTFALLS_PER_WALK_CYCLE / WALK_CYCLE_SECONDS;
        state.setControllerSpeed((float) Mth.clamp(blocksPerSecond / animationBlocksPerSecond, MIN_WALK_SPEED, MAX_WALK_SPEED));
        return state.setAndContinue(WALK);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
