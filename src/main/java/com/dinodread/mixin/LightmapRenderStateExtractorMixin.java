package com.dinodread.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinodread.client.AtmosphereEffects;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

/** Client-only mixin (listed under "client" in dinodread.mixins.json): darkens the lightmap after vanilla fills it in. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void dinodread$darken(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        AtmosphereEffects.darkenLightmap(renderState);
    }
}
