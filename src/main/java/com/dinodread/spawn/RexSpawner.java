package com.dinodread.spawn;

import org.jspecify.annotations.Nullable;

import com.dinodread.entity.TRex;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** STUB (replaced by tasks/018): the config-driven natural spawner for the T-rex. */
public final class RexSpawner {
    public static final String NATURAL_TAG = "dinodread_natural_spawn";

    private RexSpawner() {}

    public static void onLevelTick(LevelTickEvent.Post event) {}

    public static int tickLevel(ServerLevel level, SpawnRules rules, RandomSource random) {
        return 0;
    }

    public static @Nullable TRex trySpawn(ServerLevel level, ServerPlayer player, SpawnRules rules, RandomSource random) {
        return null;
    }

    public static boolean canStandAt(ServerLevel level, TRex rex, double x, double y, double z) {
        return false;
    }

    public static int despawnStrayRexes(ServerLevel level, SpawnRules rules) {
        return 0;
    }

    public static void markNatural(TRex rex) {}
}
