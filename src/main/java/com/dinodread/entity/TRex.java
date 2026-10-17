package com.dinodread.entity;

import com.dinodread.config.RexConfig;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// T-rex. Attributes come from RexConfig (STARTUP config). AI goals are added separately.
public class TRex extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TRex(EntityType<? extends TRex> type, Level level) {
        super(type, level);
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
