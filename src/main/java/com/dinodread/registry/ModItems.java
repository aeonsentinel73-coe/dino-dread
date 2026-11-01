package com.dinodread.registry;

import com.dinodread.DinoDread;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DinoDread.MODID);

    public static final DeferredItem<SpawnEggItem> TREX_SPAWN_EGG = ITEMS.registerItem(
        "trex_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.TREX.get()));

    public static final DeferredItem<SpawnEggItem> VELOCIRAPTOR_SPAWN_EGG = ITEMS.registerItem(
        "velociraptor_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.VELOCIRAPTOR.get()));

    private ModItems() {}

    // Mod-bus listener; wired up in DinoDread.
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(TREX_SPAWN_EGG);
            event.accept(VELOCIRAPTOR_SPAWN_EGG);
        }
    }
}
