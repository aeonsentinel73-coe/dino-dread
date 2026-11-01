package com.dinodread.client.renderer;

import com.dinodread.entity.Velociraptor;
import com.dinodread.registry.ModEntities;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The velociraptor shares the T-rex's placeholder model files; GeckoLib picks them by the velociraptor entity type id. */
public class VelociraptorRenderer extends TRexRenderer<Velociraptor> {
    public VelociraptorRenderer(EntityRendererProvider.Context context) {
        super(context, ModEntities.VELOCIRAPTOR.get());
    }
}
