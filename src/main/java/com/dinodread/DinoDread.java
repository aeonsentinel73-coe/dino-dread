package com.dinodread;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import com.dinodread.config.RexConfig;
import com.dinodread.registry.ModEntities;
import com.dinodread.registry.ModItems;

// The value here must match modId in META-INF/neoforge.mods.toml (generated from mod_id in gradle.properties)
@Mod(DinoDread.MODID)
public class DinoDread {
    public static final String MODID = "dinodread";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DinoDread(IEventBus modEventBus, ModContainer container) {
        // STARTUP configs load right here, so RexConfig values are readable when entity attributes are created.
        container.registerConfig(ModConfig.Type.STARTUP, RexConfig.SPEC);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ModItems::addToCreativeTabs);
        LOGGER.info("Dino Dread loading");
    }
}
