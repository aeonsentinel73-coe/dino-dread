package com.dinodread.client;

import com.dinodread.DinoDread;
import com.dinodread.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Camera shake for the T-rex's stomps and roars. It is driven by the sounds themselves: the client already receives each
 * footstep and roar as a sound at a position, so the shake needs no extra packet and is always in step with what is
 * heard, falling off with distance. Each sound adds "trauma" (0..1), which decays over about a second; the camera is
 * shaken by trauma squared, so small stomps are a tremor and a roar at close range is a jolt.
 * <p>
 * Respects {@link AtmosphereConfig#SHAKE_ENABLED}, {@link AtmosphereConfig#SHAKE_STRENGTH},
 * {@link AtmosphereConfig#SHAKE_MAX_DISTANCE} and vanilla's Distortion Effects accessibility slider.
 */
@EventBusSubscriber(modid = DinoDread.MODID, value = Dist.CLIENT)
public final class RexShake {
    /** Trauma added by one sound at point-blank range, before the strength setting. */
    private static final float STEP_TRAUMA = 0.6F;
    private static final float ROAR_TRAUMA = 1.0F;
    private static final float TRAUMA_DECAY_PER_TICK = 0.035F;

    private static final float MAX_PITCH_DEGREES = 2.5F;
    private static final float MAX_YAW_DEGREES = 1.5F;
    private static final float MAX_ROLL_DEGREES = 3.0F;
    // Different speeds and phases so the three axes do not move in lockstep (units: radians per tick).
    private static final float PITCH_SPEED = 2.3F;
    private static final float YAW_SPEED = 1.9F;
    private static final float YAW_PHASE = 1.7F;
    private static final float ROLL_SPEED = 2.9F;
    private static final float ROLL_PHASE = 0.6F;

    private static float trauma;

    private RexShake() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft minecraft = Minecraft.getInstance();
        if (sound == null || minecraft.player == null || !AtmosphereConfig.SHAKE_ENABLED.getAsBoolean()) {
            return;
        }

        float weight = weightOf(sound.getIdentifier());
        if (weight == 0.0F) {
            return;
        }

        double distance = minecraft.player.position().distanceTo(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
        trauma = Math.min(1.0F, trauma + impulse(weight, distance, AtmosphereConfig.SHAKE_MAX_DISTANCE.getAsDouble(), AtmosphereConfig.SHAKE_STRENGTH.getAsDouble()));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        trauma = Math.max(0.0F, trauma - TRAUMA_DECAY_PER_TICK);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (trauma <= 0.0F || minecraft.level == null) {
            return;
        }

        float amount = trauma * trauma * minecraft.options.screenEffectScale().get().floatValue();
        float time = minecraft.level.getGameTime() + event.getPartialTick();
        event.setPitch(event.getPitch() + amount * MAX_PITCH_DEGREES * Mth.sin(time * PITCH_SPEED));
        event.setYaw(event.getYaw() + amount * MAX_YAW_DEGREES * Mth.sin(time * YAW_SPEED + YAW_PHASE));
        event.setRoll(event.getRoll() + amount * MAX_ROLL_DEGREES * Mth.sin(time * ROLL_SPEED + ROLL_PHASE));
    }

    /** Trauma a sound adds at point-blank range, or 0 for sounds that do not shake the camera. */
    private static float weightOf(Identifier sound) {
        if (sound.equals(ModSounds.TREX_STEP.getId())) {
            return STEP_TRAUMA;
        }
        return sound.equals(ModSounds.TREX_ROAR.getId()) ? ROAR_TRAUMA : 0.0F;
    }

    /** Full strength at the source, none at {@code maxDistance}, falling off smoothly (squared) in between. */
    static float impulse(float weight, double distance, double maxDistance, double strength) {
        double closeness = Mth.clamp(1.0 - distance / maxDistance, 0.0, 1.0);
        return (float) (weight * strength * closeness * closeness);
    }
}
