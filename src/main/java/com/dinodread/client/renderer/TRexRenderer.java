package com.dinodread.client.renderer;

import com.dinodread.entity.TRex;
import com.dinodread.registry.ModEntities;
import com.geckolib.renderer.GeoEntityRenderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

// Client-only. GeckoLib derives the model/animation/texture paths from the entity type id:
// geckolib/models/entity/trex.geo.json, geckolib/animations/entity/trex.animation.json, textures/entity/trex.png
public class TRexRenderer extends GeoEntityRenderer<TRex, LivingEntityRenderState> {
    public TRexRenderer(EntityRendererProvider.Context context) {
        super(context, ModEntities.TREX.get());
    }
}
