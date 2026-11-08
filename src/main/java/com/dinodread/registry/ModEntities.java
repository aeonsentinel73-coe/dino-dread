package com.dinodread.registry;

import com.dinodread.DinoDread;
import com.dinodread.config.RexConfig;
import com.dinodread.entity.SuperRex;
import com.dinodread.entity.TRex;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(DinoDread.MODID);

    private static final int TREX_TRACKING_RANGE = 10;

    // The hitbox is the base size in blocks before the TRex SCALE attribute is applied (hitboxWidth, hitboxHeight and eyeHeight in the STARTUP
    // config, which is loaded before registration). The builder runs during registration, so those values are readable here.
    public static final DeferredHolder<EntityType<?>, EntityType<TRex>> TREX = ENTITY_TYPES.registerEntityType(
        "trex", TRex::new, MobCategory.MONSTER,
        builder -> builder.sized(RexConfig.HITBOX_WIDTH.get().floatValue(), RexConfig.HITBOX_HEIGHT.get().floatValue())
            .eyeHeight(RexConfig.EYE_HEIGHT.get().floatValue())
            .clientTrackingRange(TREX_TRACKING_RANGE));

    // Same base hitbox as the T-rex; the super rex's size comes from its own (larger) SCALE attribute.
    public static final DeferredHolder<EntityType<?>, EntityType<SuperRex>> SUPER_REX = ENTITY_TYPES.registerEntityType(
        "super_rex", SuperRex::new, MobCategory.MONSTER,
        builder -> builder.sized(RexConfig.HITBOX_WIDTH.get().floatValue(), RexConfig.HITBOX_HEIGHT.get().floatValue())
            .eyeHeight(RexConfig.EYE_HEIGHT.get().floatValue())
            .clientTrackingRange(TREX_TRACKING_RANGE));

    private ModEntities() {}

    // Mod-bus listener; wired up in DinoDread.
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TREX.get(), TRex.createAttributes().build());
        event.put(SUPER_REX.get(), SuperRex.createAttributes().build());
    }
}
