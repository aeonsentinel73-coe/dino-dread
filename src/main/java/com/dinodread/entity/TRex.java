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
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.core.BlockPos;
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
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // Vanilla counts walking in units of 0.6 of a block (Entity.moveDist), and plays a footstep when it passes nextStep().
    private static final double VANILLA_MOVE_DIST_PER_BLOCK = 0.6;

    private final TRexSenses senses = new TRexSenses(this);
    private final TRexVoice voice = new TRexVoice(this);
    private HuntState huntState = HuntState.WANDER;

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
        // No animations yet.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
