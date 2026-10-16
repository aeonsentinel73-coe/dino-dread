package com.dinodread.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// Placeholder T-rex. No AI yet (M3) and no animation controllers yet; the box model just stands there.
public class TRex extends Monster implements GeoEntity {
    // Placeholder attribute values. M3 moves these into config.
    private static final double MAX_HEALTH = 200.0;
    private static final double MOVEMENT_SPEED = 0.3;
    private static final double KNOCKBACK_RESISTANCE = 1.0;
    private static final double ATTACK_DAMAGE = 20.0;
    private static final double FOLLOW_RANGE = 64.0;
    private static final double STEP_HEIGHT = 1.5;
    // Multiplies the base hitbox (ModEntities) and the rendered model together. Vanilla attribute range is 0.0625..16.
    private static final double SCALE = 3.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TRex(EntityType<? extends TRex> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
            .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
            .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
            .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
            .add(Attributes.STEP_HEIGHT, STEP_HEIGHT)
            .add(Attributes.SCALE, SCALE);
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
