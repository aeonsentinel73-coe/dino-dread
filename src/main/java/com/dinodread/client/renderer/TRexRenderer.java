package com.dinodread.client.renderer;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
import com.dinodread.registry.ModEntities;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

// Client-only. GeckoLib derives the model/animation/texture paths from the entity type id:
// geckolib/models/entity/trex.geo.json, geckolib/animations/entity/trex.animation.json, textures/entity/trex.png
// (the Tarbosaurus Rex model by ziiwplay, CC BY 4.0; see CREDITS.md).
public class TRexRenderer<T extends TRex> extends GeoEntityRenderer<T, LivingEntityRenderState> {
    // The model's tail tip is 72 model units (4.5 blocks) behind its origin at scale 1: far outside the hitbox, which is
    // all vanilla frustum culling looks at, so the tail would pop out of existence when only the hitbox is off screen.
    private static final double MODEL_TAIL_REACH_BLOCKS_AT_SCALE_ONE = 4.5;

    public TRexRenderer(EntityRendererProvider.Context context) {
        this(context, unchecked(ModEntities.TREX.get()));
    }

    // GeckoLib derives the model/animation/texture paths from the entity type id passed here, so the super rex
    // renderer gets the geckolib/models/entity/super_rex.geo.json family (the same model, swapped-colors texture).
    protected TRexRenderer(EntityRendererProvider.Context context, EntityType<T> type) {
        super(context, type);
    }

    // The single-argument constructor is only ever used for the trex type (T=TRex); the cast documents that.
    @SuppressWarnings("unchecked")
    private static <T extends TRex> EntityType<T> unchecked(EntityType<?> type) {
        return (EntityType<T>) type;
    }

    // The model is shifted straight back from the hitbox centre (modelOffsetBlocks), so the hitbox sits over the chest and legs and less of it is
    // wasted on the tail. This runs after the entity's scale and rotation are on the pose, so +Z here is backwards along the model, in scaled blocks.
    // Raw on purpose: spelling out RenderPassInfo<LivingEntityRenderState> does not compile (GeckoLib adds GeoRenderState to the render state at runtime),
    // and the raw signature is the erasure of the inherited one, which Java accepts as an override.
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void adjustRenderPose(RenderPassInfo renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        renderPassInfo.poseStack().translate(0.0F, 0.0F, RexConfig.MODEL_OFFSET_BLOCKS.get().floatValue());
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
        double reach = (MODEL_TAIL_REACH_BLOCKS_AT_SCALE_ONE + Math.max(0.0, RexConfig.MODEL_OFFSET_BLOCKS.get())) * entity.getScale();
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(reach);
    }
}
