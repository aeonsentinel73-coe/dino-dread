package com.dinodread.client.renderer;

import com.dinodread.entity.Velociraptor;
import com.dinodread.registry.ModEntities;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The velociraptor renders the Peridot Scout model (CC0, see CREDITS.md). Its tail reaches 5.1 blocks behind its origin
 * at scale 1 for frustum culling, and the model sits centred on the hitbox: no back-shift like the T-rex needs.
 */
public class VelociraptorRenderer extends TRexRenderer<Velociraptor> {
    public VelociraptorRenderer(EntityRendererProvider.Context context) {
        super(context, ModEntities.VELOCIRAPTOR.get(), 5.1, 0.0);
    }
}
