package com.dinodread.client;

import com.dinodread.DinoDread;
import com.dinodread.client.renderer.TRexRenderer;
import com.dinodread.registry.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// Client-only entry point. Never reference this package from common code: a dedicated server will crash.
@Mod(value = DinoDread.MODID, dist = Dist.CLIENT)
public class DinoDreadClient {
    public DinoDreadClient(IEventBus modEventBus) {
        modEventBus.addListener(DinoDreadClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TREX.get(), TRexRenderer::new);
    }
}
