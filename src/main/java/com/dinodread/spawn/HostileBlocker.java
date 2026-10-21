package com.dinodread.spawn;

import java.util.List;
import java.util.function.Supplier;

import com.dinodread.config.SpawnConfig;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** STUB (replaced by tasks/017): blocks natural spawns of other hostile mobs when the config says so. */
public final class HostileBlocker {
    public record Rules(boolean disable, List<? extends String> exceptions, List<? extends String> reasons) {
        public static Rules fromConfig() {
            return new Rules(SpawnConfig.DISABLE_VANILLA_HOSTILES.get(), SpawnConfig.VANILLA_HOSTILE_EXCEPTIONS.get(), SpawnConfig.BLOCKED_SPAWN_REASONS.get());
        }
    }

    public HostileBlocker(Supplier<Rules> rules) {}

    public static HostileBlocker fromConfig() {
        return new HostileBlocker(Rules::fromConfig);
    }

    public boolean shouldBlock(EntityType<?> type, EntitySpawnReason reason) {
        return false;
    }

    public void onFinalizeSpawn(FinalizeSpawnEvent event) {}

    public void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {}

    public void register(IEventBus bus) {}

    public void unregister(IEventBus bus) {}
}
