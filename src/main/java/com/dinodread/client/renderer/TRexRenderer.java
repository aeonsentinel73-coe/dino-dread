package com.dinodread.client.renderer;

import com.dinodread.entity.TRex;
import com.dinodread.registry.ModEntities;
import com.geckolib.renderer.GeoEntityRenderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.AABB;

// Client-only. GeckoLib derives the model/animation/texture paths from the entity type id:
// geckolib/models/entity/trex.geo.json, geckolib/animations/entity/trex.animation.json, textures/entity/trex.png
// (the Tarbosaurus Rex model by ziiwplay, CC BY 4.0; see CREDITS.md).
public class TRexRenderer extends GeoEntityRenderer<TRex, LivingEntityRenderState> {
    // The model's tail tip is 72 model units (4.5 blocks) behind its origin at scale 1: far outside the hitbox, which is
    // all vanilla frustum culling looks at, so the tail would pop out of existence when only the hitbox is off screen.
    private static final double MODEL_REACH_BLOCKS_AT_SCALE_ONE = 4.5;

    public TRexRenderer(EntityRendererProvider.Context context) {
        super(context, ModEntities.TREX.get());
    }

    @Override
    protected AABB getBoundingBoxForCulling(TRex entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(MODEL_REACH_BLOCKS_AT_SCALE_ONE * entity.getScale());
    }
}
