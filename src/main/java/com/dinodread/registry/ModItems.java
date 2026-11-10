package com.dinodread.registry;

import com.dinodread.DinoDread;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DinoDread.MODID);

    public static final DeferredItem<SpawnEggItem> TREX_SPAWN_EGG = ITEMS.registerItem(
        "trex_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.TREX.get()));

    public static final DeferredItem<SpawnEggItem> SUPER_REX_SPAWN_EGG = ITEMS.registerItem(
        "super_rex_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.SUPER_REX.get()));
    public static final DeferredItem<SpawnEggItem> VELOCIRAPTOR_SPAWN_EGG = ITEMS.registerItem(
        "velociraptor_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.VELOCIRAPTOR.get()));

    /**
     * Dinosaur meat, "Something Fancy" at the friend's request. Raw it is a bite (2 food); it cooks only by
     * crafting with a lava bucket (vanilla returns the empty bucket automatically via the bucket's crafting
     * remainder) and then refills a full hunger bar. No furnace/campfire recipe exists on purpose: the lava
     * crafting is the only way.
     */
    public static final DeferredItem<Item> SOMETHING_FANCY = ITEMS.registerItem(
        "something_fancy", Item::new,
        props -> props.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build()));

    public static final DeferredItem<Item> SOMETHING_FANCY_COOKED = ITEMS.registerItem(
        "something_fancy_cooked", Item::new,
        props -> props.food(new FoodProperties.Builder().nutrition(20).saturationModifier(1.0F).build()));

    private ModItems() {}

    // Mod-bus listener; wired up in DinoDread.
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(TREX_SPAWN_EGG);
            event.accept(SUPER_REX_SPAWN_EGG);
            event.accept(VELOCIRAPTOR_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(SOMETHING_FANCY);
            event.accept(SOMETHING_FANCY_COOKED);
        }
    }
}
