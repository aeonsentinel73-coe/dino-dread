package com.dinodread.registry;

import com.dinodread.DinoDread;
import com.dinodread.config.RaptorConfig;
import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
import com.dinodread.entity.Velociraptor;

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

    // Same story: base hitbox from RaptorConfig, real size from the SCALE attribute (0.45 by default).
    public static final DeferredHolder<EntityType<?>, EntityType<Velociraptor>> VELOCIRAPTOR = ENTITY_TYPES.registerEntityType(
        "velociraptor", Velociraptor::new, MobCategory.MONSTER,
        builder -> builder.sized(RaptorConfig.HITBOX_WIDTH.get().floatValue(), RaptorConfig.HITBOX_HEIGHT.get().floatValue())
            .eyeHeight(RaptorConfig.EYE_HEIGHT.get().floatValue())
            .clientTrackingRange(TREX_TRACKING_RANGE));

    private ModEntities() {}

    // Mod-bus listener; wired up in DinoDread.
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TREX.get(), TRex.createAttributes().build());
        event.put(VELOCIRAPTOR.get(), Velociraptor.createAttributes().build());
    }
}
