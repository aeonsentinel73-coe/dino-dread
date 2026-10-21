package com.dinodread.registry;

import com.dinodread.DinoDread;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, DinoDread.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TREX_ROAR = register("entity.trex.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREX_ROAR_DISTANT = register("entity.trex.roar_distant");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREX_STEP = register("entity.trex.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREX_BITE = register("entity.trex.bite");

    private ModSounds() {}

    // Variable-range events: how far a sound carries is 16 blocks times the volume it is played at.
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
    }
}
