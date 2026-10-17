package com.dinodread.world;

import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Day/night blend shared by the client atmosphere (fog, lightmap) and the server-side T-rex AI, so both agree on
 * when it is dark. Built on the SKY_LIGHT_FACTOR environment attribute, which already accounts for the time of day.
 */
public final class DayNight {
    /** Vanilla overworld SKY_LIGHT_FACTOR at full night (data/minecraft/timeline/day.json); full day is 1.0. */
    private static final float VANILLA_NIGHT_SKY_FACTOR = 0.24F;

    private DayNight() {}

    /** 0 in full daylight, 1 at full night, from a SKY_LIGHT_FACTOR value. */
    public static float nightFactor(float skyLightFactor) {
        return Mth.clamp((1.0F - skyLightFactor) / (1.0F - VANILLA_NIGHT_SKY_FACTOR), 0.0F, 1.0F);
    }

    /** 0 in full daylight, 1 at full night, at a position in a level. Works on either side. */
    public static float nightFactor(Level level, Vec3 pos) {
        return nightFactor(level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, pos));
    }
}
