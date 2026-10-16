package com.dinodread.registry;

import com.dinodread.DinoDread;
import com.dinodread.entity.TRex;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(DinoDread.MODID);

    // Hitbox in blocks.
    private static final float TREX_WIDTH = 3.0F;
    private static final float TREX_HEIGHT = 4.0F;
    private static final float TREX_EYE_HEIGHT = 3.6F;
    private static final int TREX_TRACKING_RANGE = 10;

    public static final DeferredHolder<EntityType<?>, EntityType<TRex>> TREX = ENTITY_TYPES.registerEntityType(
        "trex", TRex::new, MobCategory.MONSTER,
        builder -> builder.sized(TREX_WIDTH, TREX_HEIGHT).eyeHeight(TREX_EYE_HEIGHT).clientTrackingRange(TREX_TRACKING_RANGE));

    private ModEntities() {}

    // Mod-bus listener; wired up in DinoDread.
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TREX.get(), TRex.createAttributes().build());
    }
}
