# Task 001 — Placeholder T-rex entity + spawn egg

**Goal:** register one placeholder T-rex entity (GeckoLib box model) and its spawn egg so the egg spawns the
rex in a dev client. No AI, no sounds, no config yet (those are later milestones).

Working dir = repo root (`D:\Documents\Claude Code\Dino Dread`). Java 25, package `com.dinodread`, mod id
`dinodread`. Mojang names are official (no obfuscation); the resource-id class is
`net.minecraft.resources.Identifier`, **not** `ResourceLocation`. GeckoLib 5 package root is
`com.geckolib` (**not** `software.bernie`). Every symbol and import below was verified against the
decompiled 26.3 / NeoForge 26.3.0.85 / GeckoLib 5.5.7 sources. **Use exactly these names and imports.
Do not substitute APIs from memory (older versions differ). Do not run Gradle; you cannot.**

Style reference: copy the formatting/comment style of `src/main/java/com/dinodread/DinoDread.java`
(4-space indent, imports grouped: third-party/mojang first, `net.minecraft`, `net.neoforged`, then
`com.dinodread`, blank line between groups, sorted alphabetically within a group).

## Files to CREATE (exact content below; keep the code as given)

### 1. `src/main/java/com/dinodread/entity/TRex.java`
```java
package com.dinodread.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// Placeholder T-rex. No AI yet (M3) and no animation controllers yet; the box model just stands there.
public class TRex extends Monster implements GeoEntity {
    // Placeholder attribute values. M3 moves these into config.
    private static final double MAX_HEALTH = 200.0;
    private static final double MOVEMENT_SPEED = 0.3;
    private static final double KNOCKBACK_RESISTANCE = 1.0;
    private static final double ATTACK_DAMAGE = 20.0;
    private static final double FOLLOW_RANGE = 64.0;
    private static final double STEP_HEIGHT = 1.5;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TRex(EntityType<? extends TRex> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
            .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
            .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
            .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
            .add(Attributes.STEP_HEIGHT, STEP_HEIGHT);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animations yet.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
```

### 2. `src/main/java/com/dinodread/registry/ModEntities.java`
```java
package com.dinodread.registry;

import com.dinodread.DinoDread;
import com.dinodread.entity.TRex;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(DinoDread.MODID);

    // Hitbox in blocks.
    private static final float TREX_WIDTH = 3.0F;
    private static final float TREX_HEIGHT = 4.0F;
    private static final float TREX_EYE_HEIGHT = 3.6F;
    private static final int TREX_TRACKING_RANGE = 10;

    public static final DeferredHolder<EntityType<?>, EntityType<TRex>> TREX = ENTITY_TYPES.registerEntityType(
        "trex", TRex::new, MobCategory.MONSTER,
        builder -> builder.sized(TREX_WIDTH, TREX_HEIGHT).eyeHeight(TREX_EYE_HEIGHT).clientTrackingRange(TREX_TRACKING_RANGE));

    private ModEntities() {}

    // Mod-bus listener; wired up in DinoDread.
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TREX.get(), TRex.createAttributes().build());
    }
}
```

### 3. `src/main/java/com/dinodread/registry/ModItems.java`
```java
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

    private ModItems() {}

    // Mod-bus listener; wired up in DinoDread.
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(TREX_SPAWN_EGG);
        }
    }
}
```

### 4. `src/main/java/com/dinodread/client/renderer/TRexRenderer.java`  (client-only package)
```java
package com.dinodread.client.renderer;

import com.dinodread.entity.TRex;
import com.dinodread.registry.ModEntities;
import com.geckolib.renderer.GeoEntityRenderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

// Client-only. GeckoLib derives the model/animation/texture paths from the entity type id:
// geckolib/models/entity/trex.geo.json, geckolib/animations/entity/trex.animation.json, textures/entity/trex.png
public class TRexRenderer extends GeoEntityRenderer<TRex, LivingEntityRenderState> {
    public TRexRenderer(EntityRendererProvider.Context context) {
        super(context, ModEntities.TREX.get());
    }
}
```

### 5. `src/main/resources/assets/dinodread/items/trex_spawn_egg.json`
```json
{
  "model": {
    "type": "minecraft:model",
    "model": "dinodread:item/trex_spawn_egg"
  }
}
```

### 6. `src/main/resources/assets/dinodread/models/item/trex_spawn_egg.json`
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "dinodread:item/trex_spawn_egg"
  }
}
```

## Files to EDIT

### 7. `src/main/java/com/dinodread/DinoDread.java`
Keep everything; change only the imports and the constructor body:
- add imports `com.dinodread.registry.ModEntities` and `com.dinodread.registry.ModItems` (in the `com.dinodread` import group, after the `net.neoforged` group, with a blank line between groups, matching the other classes above)
- constructor body becomes (the old "Registries ... are added here" comment is replaced by this code):
```java
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ModItems::addToCreativeTabs);
        LOGGER.info("Dino Dread loading");
```

### 8. `src/main/java/com/dinodread/client/DinoDreadClient.java`
- change the constructor signature to `public DinoDreadClient(IEventBus modEventBus)` (import `net.neoforged.bus.api.IEventBus`)
- constructor body (replace the placeholder comment):
```java
        modEventBus.addListener(DinoDreadClient::registerRenderers);
```
- add this method below the constructor (imports: `com.dinodread.client.renderer.TRexRenderer`, `com.dinodread.registry.ModEntities`, `net.neoforged.neoforge.client.event.EntityRenderersEvent`):
```java
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TREX.get(), TRexRenderer::new);
    }
```
- keep the existing `@Mod(value = DinoDread.MODID, dist = Dist.CLIENT)` annotation and the class comment as-is.

## Do NOT touch
`.git/` (never read, write or run anything in it), `build/`, `gradle/`, `build.gradle`, `gradle.properties`,
`settings.gradle`, `gradlew*`, `CLAUDE.md`, `CREDITS.md`, `docs/`/`Docs/`, `tasks/`, `.mcp.json`,
`src/main/templates/`, `src/main/resources/assets/dinodread/lang/en_us.json`,
`src/main/resources/assets/dinodread/geckolib/**` and `src/main/resources/assets/dinodread/textures/**`
(already exist; do not recreate or modify). Do not add other files, other entities/items/blocks, config
classes, sounds, goals, or AI. Do not add `@SubscribeEvent`/`@EventBusSubscriber` annotations; listeners
are wired by `addListener` as shown.

## Acceptance
- Exactly the 6 new files and 2 edited files above are changed; verify with `git status`/`git diff`
  if your tools allow it, otherwise list what you touched.
- No reference to `net.minecraft.client` or `com.dinodread.client` from any file outside
  `com.dinodread.client` (client/server split).
- (Claude runs `.\gradlew build` afterwards; you cannot.)
- Reply with a short list of files created/edited, plus any place where you had to deviate from the
  spec and why (ideally none).
