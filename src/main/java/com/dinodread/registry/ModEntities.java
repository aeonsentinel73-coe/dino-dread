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

    // Base hitbox in blocks, before the TRex SCALE attribute (3x) is applied: in game it is 9 wide x 8.1 tall. The height
    // follows the Tarbosaurus model (its head tops out about 2.25 blocks up at scale 1, so about 6.8 at 3x); the width is
    // far wider than the body (about 3 blocks at 3x) on purpose: it is what the wide-body navigation was built around.
    private static final float TREX_WIDTH = 3.0F;
    private static final float TREX_HEIGHT = 2.7F;
    private static final float TREX_EYE_HEIGHT = 2.2F;
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
