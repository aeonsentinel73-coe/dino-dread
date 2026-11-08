package com.dinodread.client.renderer;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
import com.dinodread.entity.Velociraptor;
import com.dinodread.registry.ModEntities;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.function.DoubleSupplier;

/**
 * Client-only. GeckoLib derives the model/animation/texture paths from the entity type id:
 * geckolib/models/entity/&lt;type&gt;.geo.json, geckolib/animations/entity/&lt;type&gt;.animation.json,
 * textures/entity/&lt;type&gt;.png. The T-rex renders the Tarbosaurus Rex model by ziiwplay (CC BY 4.0; see CREDITS.md),
 * the velociraptor the Peridot Scout model by logan_kant (CC0). How far each model's tail reaches behind its origin,
 * and how far the model is shifted back from the hitbox centre, differ per model and come from the constructor.
 */
public class TRexRenderer<T extends TRex> extends GeoEntityRenderer<T, LivingEntityRenderState> {
    // The Tarbosaurus model's tail tip is 72 model units (4.5 blocks) behind its origin at scale 1: far outside the hitbox, which is
    // all vanilla frustum culling looks at, so the tail would pop out of existence when only the hitbox is off screen.
    private static final double TREX_TAIL_REACH_BLOCKS_AT_SCALE_ONE = 4.5;

    private final double modelTailReachBlocksAtScaleOne;
    private final DoubleSupplier modelOffsetBlocks;

    public TRexRenderer(EntityRendererProvider.Context context, EntityType<T> type) {
        // The T-rex: shifted straight back from the hitbox centre by the config value, read live.
        this(context, type, TREX_TAIL_REACH_BLOCKS_AT_SCALE_ONE, () -> RexConfig.MODEL_OFFSET_BLOCKS.get().doubleValue());
    }

    public TRexRenderer(EntityRendererProvider.Context context, EntityType<T> type, double modelTailReachBlocksAtScaleOne, double modelOffsetBlocks) {
        this(context, type, modelTailReachBlocksAtScaleOne, () -> modelOffsetBlocks);
    }

    private TRexRenderer(EntityRendererProvider.Context context, EntityType<T> type, double modelTailReachBlocksAtScaleOne, DoubleSupplier modelOffsetBlocks) {
        super(context, type);
        this.modelTailReachBlocksAtScaleOne = modelTailReachBlocksAtScaleOne;
        this.modelOffsetBlocks = modelOffsetBlocks;
    }

    public TRexRenderer(EntityRendererProvider.Context context) {
        this(context, unchecked(ModEntities.TREX.get()));
    }

    // (the 2-arg constructor above covers the super rex too: same model, same live config offset)

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
        renderPassInfo.poseStack().translate(0.0F, 0.0F, (float) modelOffsetBlocks.getAsDouble());
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
        double reach = (modelTailReachBlocksAtScaleOne + Math.max(0.0, modelOffsetBlocks.getAsDouble())) * entity.getScale();
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(reach);
    }
}
