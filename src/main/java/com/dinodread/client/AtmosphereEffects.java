package com.dinodread.client;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Shared helpers for the atmosphere hooks ({@link AtmosphereEvents} and the lightmap mixin). Client only. */
public final class AtmosphereEffects {
    /**
     * Vanilla overworld SKY_LIGHT_FACTOR at full night (data/minecraft/timeline/day.json); full day is 1.0.
     * Used only to turn a sky-light factor into a 0 (day) .. 1 (night) blend.
     */
    private static final float VANILLA_NIGHT_SKY_FACTOR = 0.24F;

    private AtmosphereEffects() {}

    /** 0 in full daylight, 1 at full night, from a SKY_LIGHT_FACTOR value. */
    public static float nightFactor(float skyLightFactor) {
        return Mth.clamp((1.0F - skyLightFactor) / (1.0F - VANILLA_NIGHT_SKY_FACTOR), 0.0F, 1.0F);
    }

    /** True when the master switch is on and the level is one we apply effects in. */
    public static boolean active(@Nullable ClientLevel level) {
        if (level == null || !AtmosphereConfig.SPEC.isLoaded() || !AtmosphereConfig.MASTER_ENABLED.getAsBoolean()) {
            return false;
        }
        return !AtmosphereConfig.OVERWORLD_ONLY.getAsBoolean() || level.dimension().equals(Level.OVERWORLD);
    }

    /** Scales the lightmap's sky light and ambient floor down. Called at the end of the lightmap extract. */
    public static void darkenLightmap(LightmapRenderState state) {
        if (!state.needsUpdate || !active(Minecraft.getInstance().level) || !AtmosphereConfig.DARKNESS_ENABLED.getAsBoolean()) {
            return;
        }
        float night = nightFactor(state.skyFactor);
        float skyScale = Mth.lerp(
                night,
                (float) AtmosphereConfig.DAY_SKY_LIGHT_SCALE.getAsDouble(),
                (float) AtmosphereConfig.NIGHT_SKY_LIGHT_SCALE.getAsDouble());
        state.skyFactor *= skyScale;
        state.ambientColor = new Vector3f(state.ambientColor).mul((float) AtmosphereConfig.AMBIENT_LIGHT_SCALE.getAsDouble());
    }
}
