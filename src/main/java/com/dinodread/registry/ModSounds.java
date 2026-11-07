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

    // The raptor's own ids: separate events (so the T-rex camera shake cannot catch them). Its sounds: two hunting cries,
    // a running-gait loop, and the bite (still a pitched-up cry until a real bite sound is made). No distant call.
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPTOR_ROAR = register("entity.velociraptor.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPTOR_RUN = register("entity.velociraptor.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPTOR_BITE = register("entity.velociraptor.bite");

    private ModSounds() {}

    // Variable-range events: how far a sound carries is 16 blocks times the volume it is played at.
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
    }
}
