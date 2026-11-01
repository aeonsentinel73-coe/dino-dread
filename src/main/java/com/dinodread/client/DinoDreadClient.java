package com.dinodread.client;

import com.dinodread.DinoDread;
import com.dinodread.client.renderer.TRexRenderer;
import com.dinodread.client.renderer.VelociraptorRenderer;
import com.dinodread.registry.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client-only entry point. Never reference this package from common code: a dedicated server will crash.
@Mod(value = DinoDread.MODID, dist = Dist.CLIENT)
public class DinoDreadClient {
    public DinoDreadClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(DinoDreadClient::registerRenderers);
        container.registerConfig(ModConfig.Type.CLIENT, AtmosphereConfig.SPEC);
        // Config screen: Mods > Dino Dread > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TREX.get(), TRexRenderer::new);
        event.registerEntityRenderer(ModEntities.VELOCIRAPTOR.get(), VelociraptorRenderer::new);
    }
}
