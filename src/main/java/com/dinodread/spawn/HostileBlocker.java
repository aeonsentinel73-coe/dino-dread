package com.dinodread.spawn;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.dinodread.DinoDread;
import com.dinodread.config.SpawnConfig;
import com.dinodread.world.IdTagList;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Stops the normal sources of hostile mobs when the disableVanillaHostiles config is on, so the T-rex is the only
 * thing that hunts. Two hooks, one decision ({@link #shouldBlock}): the placement check, which vanilla's natural
 * spawner asks before it even builds a mob (cheap), and the finalize-spawn event, which every spawn path fires,
 * including those that skip the placement check (phantoms, patrols, structures, chunk generation). Only the spawn
 * reasons in the config are blocked, so eggs, commands, summons, breeding, raids and (by default) spawners are
 * untouched. Anything in the Dino Dread namespace, and anything on the exception list, is never blocked.
 */
public final class HostileBlocker {
    public record Rules(boolean disable, List<? extends String> exceptions, List<? extends String> reasons) {
        public static Rules fromConfig() {
            return new Rules(SpawnConfig.DISABLE_VANILLA_HOSTILES.get(), SpawnConfig.VANILLA_HOSTILE_EXCEPTIONS.get(), SpawnConfig.BLOCKED_SPAWN_REASONS.get());
        }
    }

    private final Supplier<Rules> rules;
    private final IdTagList<EntityType<?>> exceptions = new IdTagList<>("vanillaHostileExceptions", Registries.ENTITY_TYPE);
    private volatile ReasonSet reasonSet;   // starts null
    private final Consumer<FinalizeSpawnEvent> finalizeListener = this::onFinalizeSpawn;
    private final Consumer<MobSpawnEvent.SpawnPlacementCheck> placementListener = this::onSpawnPlacementCheck;
    private record ReasonSet(List<? extends String> source, Set<EntitySpawnReason> reasons) {}

    public HostileBlocker(Supplier<Rules> rules) {
        this.rules = rules;
    }

    public static HostileBlocker fromConfig() {
        return new HostileBlocker(Rules::fromConfig);
    }

    public boolean shouldBlock(EntityType<?> type, EntitySpawnReason reason) {
        Rules current = rules.get();
        if (!current.disable()) {
            return false;
        }

        if (type.getCategory() != MobCategory.MONSTER) {
            return false;
        }

        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (DinoDread.MODID.equals(key.getNamespace())) {
            return false;
        }

        if (!reasonsOf(current.reasons()).contains(reason)) {
            return false;
        }

        return !exceptions.matches(current.exceptions(), BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type));
    }

    private Set<EntitySpawnReason> reasonsOf(List<? extends String> configured) {
        ReasonSet current = reasonSet;
        if (current == null || !configured.equals(current.source())) {
            EnumSet<EntitySpawnReason> reasons = EnumSet.noneOf(EntitySpawnReason.class);
            for (String name : configured) {
                try {
                    reasons.add(EntitySpawnReason.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    DinoDread.LOGGER.warn("Skipping unknown spawn reason in blockedSpawnReasons config: {}", name);
                }
            }

            current = new ReasonSet(configured, reasons);
            reasonSet = current;
        }

        return current.reasons();
    }

    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (shouldBlock(event.getEntity().getType(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }

    public void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (shouldBlock(event.getEntityType(), event.getSpawnType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    public void register(IEventBus bus) {
        bus.addListener(FinalizeSpawnEvent.class, finalizeListener);
        bus.addListener(MobSpawnEvent.SpawnPlacementCheck.class, placementListener);
    }

    public void unregister(IEventBus bus) {
        bus.unregister(finalizeListener);
        bus.unregister(placementListener);
    }
}
