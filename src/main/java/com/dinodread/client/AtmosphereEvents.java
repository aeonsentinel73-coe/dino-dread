package com.dinodread.client;

import com.dinodread.DinoDread;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Fog hooks. Only the regular above-ground fog is touched; water, lava, powder snow, blindness and darkness keep vanilla. */
@EventBusSubscriber(modid = DinoDread.MODID, value = Dist.CLIENT)
public final class AtmosphereEvents {
    private static final float RGB_MAX = 255.0F;

    private AtmosphereEvents() {}

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!AtmosphereEffects.active(Minecraft.getInstance().level) || !AtmosphereConfig.FOG_ENABLED.getAsBoolean()) {
            return;
        }
        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        if (camera.entity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }

        float night = AtmosphereEffects.nightFactor(skyLightFactor(camera, event.getPartialTick()));
        int day = AtmosphereConfig.parseRgb(AtmosphereConfig.FOG_DAY_COLOR.get());
        int dark = AtmosphereConfig.parseRgb(AtmosphereConfig.FOG_NIGHT_COLOR.get());
        float blend = (float) AtmosphereConfig.FOG_COLOR_BLEND.getAsDouble();

        event.setRed(Mth.lerp(blend, event.getRed(), Mth.lerp(night, channel(day, 16), channel(dark, 16))));
        event.setGreen(Mth.lerp(blend, event.getGreen(), Mth.lerp(night, channel(day, 8), channel(dark, 8))));
        event.setBlue(Mth.lerp(blend, event.getBlue(), Mth.lerp(night, channel(day, 0), channel(dark, 0))));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!(event.getEnvironment() instanceof AtmosphericFogEnvironment)) {
            return;
        }
        if (!AtmosphereEffects.active(Minecraft.getInstance().level) || !AtmosphereConfig.FOG_ENABLED.getAsBoolean()) {
            return;
        }

        float night = AtmosphereEffects.nightFactor(skyLightFactor(event.getCamera(), event.getPartialTick()));
        float end = Mth.lerp(
                night,
                (float) AtmosphereConfig.FOG_DAY_END_DISTANCE.getAsDouble(),
                (float) AtmosphereConfig.FOG_NIGHT_END_DISTANCE.getAsDouble());
        float start = end * (float) AtmosphereConfig.FOG_START_FRACTION.getAsDouble();

        FogData fog = event.getFogData();
        fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
        fog.environmentalStart = Math.min(fog.environmentalStart, start);
        if (AtmosphereConfig.FOG_HIDES_SKY.getAsBoolean()) {
            fog.skyEnd = Math.min(fog.skyEnd, end);
            fog.cloudEnd = Math.min(fog.cloudEnd, end);
        }
    }

    private static float skyLightFactor(Camera camera, float partialTick) {
        return camera.attributeProbe().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick);
    }

    private static float channel(int rgb, int shift) {
        return ((rgb >> shift) & 0xFF) / RGB_MAX;
    }
}
