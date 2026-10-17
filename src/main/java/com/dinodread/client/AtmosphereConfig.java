package com.dinodread.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side atmosphere tunables (fog and darkness). Registered as a CLIENT config from
 * {@link DinoDreadClient}; the file is {@code config/dinodread-client.toml}.
 */
public final class AtmosphereConfig {
    /** Used when a color string in the config fails to parse (it is validated, so this is a safety net). */
    private static final int FALLBACK_RGB = 0x808080;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MASTER_ENABLED;
    public static final ModConfigSpec.BooleanValue OVERWORLD_ONLY;

    public static final ModConfigSpec.BooleanValue FOG_ENABLED;
    public static final ModConfigSpec.DoubleValue FOG_DAY_END_DISTANCE;
    public static final ModConfigSpec.DoubleValue FOG_NIGHT_END_DISTANCE;
    public static final ModConfigSpec.DoubleValue FOG_START_FRACTION;
    public static final ModConfigSpec.DoubleValue FOG_COLOR_BLEND;
    public static final ModConfigSpec.ConfigValue<String> FOG_DAY_COLOR;
    public static final ModConfigSpec.ConfigValue<String> FOG_NIGHT_COLOR;
    public static final ModConfigSpec.BooleanValue FOG_HIDES_SKY;

    public static final ModConfigSpec.BooleanValue DARKNESS_ENABLED;
    public static final ModConfigSpec.DoubleValue DAY_SKY_LIGHT_SCALE;
    public static final ModConfigSpec.DoubleValue NIGHT_SKY_LIGHT_SCALE;
    public static final ModConfigSpec.DoubleValue AMBIENT_LIGHT_SCALE;

    public static final ModConfigSpec SPEC;

    static {
        MASTER_ENABLED = BUILDER
                .comment("Master switch for every Dino Dread atmosphere effect (fog and darkness).")
                .define("masterEnabled", true);
        OVERWORLD_ONLY = BUILDER
                .comment("Only apply the effects in the Overworld. The Nether and End keep their own look.")
                .define("overworldOnly", true);

        BUILDER.push("fog");
        FOG_ENABLED = BUILDER
                .comment("Thicker, tinted fog. Only ever pulls fog closer than vanilla, never pushes it further.")
                .define("fogEnabled", true);
        FOG_DAY_END_DISTANCE = BUILDER
                .comment("Distance in blocks where fog is total during the day.")
                .defineInRange("fogDayEndDistance", 120.0, 8.0, 1024.0);
        FOG_NIGHT_END_DISTANCE = BUILDER
                .comment("Distance in blocks where fog is total at night.")
                .defineInRange("fogNightEndDistance", 48.0, 8.0, 1024.0);
        FOG_START_FRACTION = BUILDER
                .comment("Where fog begins, as a fraction of the end distance. 0 = fog starts at the camera.")
                .defineInRange("fogStartFraction", 0.2, 0.0, 0.95);
        FOG_COLOR_BLEND = BUILDER
                .comment("How strongly the fog color is pulled toward the colors below. 0 = vanilla, 1 = fully ours.")
                .defineInRange("fogColorBlend", 0.75, 0.0, 1.0);
        FOG_DAY_COLOR = BUILDER
                .comment("Fog color during the day, as #RRGGBB.")
                .define("fogDayColor", "#7A858C", AtmosphereConfig::isHexColor);
        FOG_NIGHT_COLOR = BUILDER
                .comment("Fog color at night, as #RRGGBB.")
                .define("fogNightColor", "#04060A", AtmosphereConfig::isHexColor);
        FOG_HIDES_SKY = BUILDER
                .comment("Also pull sky and cloud fog in to the same distance, so the sky blends into the fog.")
                .define("fogHidesSky", true);
        BUILDER.pop();

        BUILDER.push("darkness");
        DARKNESS_ENABLED = BUILDER
                .comment("Darken the world's light levels (sky light and the ambient floor).")
                .define("darknessEnabled", true);
        DAY_SKY_LIGHT_SCALE = BUILDER
                .comment("Multiplier on sky light during the day. 1 = vanilla, lower = gloomier.")
                .defineInRange("daySkyLightScale", 0.8, 0.0, 1.0);
        NIGHT_SKY_LIGHT_SCALE = BUILDER
                .comment("Multiplier on sky light at night. 1 = vanilla, 0 = no moonlight at all.")
                .defineInRange("nightSkyLightScale", 0.4, 0.0, 1.0);
        AMBIENT_LIGHT_SCALE = BUILDER
                .comment("Multiplier on the ambient light floor (the faint glow that keeps dark places from going pitch black).")
                .defineInRange("ambientLightScale", 0.6, 0.0, 1.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private AtmosphereConfig() {}

    /** True for "#RRGGBB" or "RRGGBB". */
    public static boolean isHexColor(Object value) {
        return value instanceof String s && s.matches("#?[0-9A-Fa-f]{6}");
    }

    /** Parses a validated hex color into a packed 0xRRGGBB int. */
    public static int parseRgb(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (NumberFormatException e) {
            return FALLBACK_RGB;
        }
    }
}
