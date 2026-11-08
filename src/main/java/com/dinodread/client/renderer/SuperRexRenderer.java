package com.dinodread.client.renderer;

import com.dinodread.entity.SuperRex;
import com.dinodread.registry.ModEntities;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

// Client-only. Same model and animations as the T-rex; GeckoLib derives the asset paths from the super_rex
// entity type id, and the swap lives entirely in textures/entity/super_rex.png (red and green swapped).
public class SuperRexRenderer extends TRexRenderer<SuperRex> {
    public SuperRexRenderer(EntityRendererProvider.Context context) {
        super(context, ModEntities.SUPER_REX.get());
    }
}
