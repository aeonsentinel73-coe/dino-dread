# Task 014: sound events, sounds.json, subtitles and audio config

**Goal:** register four T-rex sound events, describe them in `sounds.json` with subtitles, and add the `[audio]`
config entries that later pieces will read. Pure registry/config boilerplate: no gameplay code changes.

Working directory: `D:\Documents\Claude Code\Dino Dread-m5` (pass it as `working_dir`).

## PROCEDURE (follow exactly)
1. Read these files: `src/main/java/com/dinodread/registry/ModItems.java` (the pattern to copy),
   `src/main/java/com/dinodread/DinoDread.java`, `src/main/java/com/dinodread/config/RexConfig.java`
   (only lines 60-80 and 250-285 matter), `src/main/resources/assets/dinodread/lang/en_us.json`.
2. Make the six changes below, each one in a single write or edit. Do NOT search the filesystem and do NOT grep for
   Minecraft symbols: every import below is already verified against the pinned sources.
3. Finish with `finish` in under 15 tool calls. You cannot run Gradle; Claude will build.

## Change 1: create `src/main/java/com/dinodread/registry/ModSounds.java` (whole file)
```java
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
```

## Change 2: edit `DinoDread.java`
- Add `import com.dinodread.registry.ModSounds;` directly after the line `import com.dinodread.registry.ModItems;`.
- Add `        ModSounds.SOUND_EVENTS.register(modEventBus);` directly after the line `        ModItems.ITEMS.register(modEventBus);`.

## Change 3: create `src/main/resources/assets/dinodread/sounds.json` (whole file, exactly this content)
```json
{
  "entity.trex.roar": {
    "subtitle": "subtitles.dinodread.entity.trex.roar",
    "sounds": ["dinodread:entity/trex/roar1", "dinodread:entity/trex/roar2"]
  },
  "entity.trex.roar_distant": {
    "subtitle": "subtitles.dinodread.entity.trex.roar_distant",
    "sounds": ["dinodread:entity/trex/roar_distant1"]
  },
  "entity.trex.step": {
    "subtitle": "subtitles.dinodread.entity.trex.step",
    "sounds": ["dinodread:entity/trex/step1", "dinodread:entity/trex/step2", "dinodread:entity/trex/step3"]
  },
  "entity.trex.bite": {
    "subtitle": "subtitles.dinodread.entity.trex.bite",
    "sounds": ["dinodread:entity/trex/bite1"]
  }
}
```
The `.ogg` files already exist; do not touch them.

## Change 4: edit `RexConfig.java`, constants
Insert these lines directly after the line `    public static final ModConfigSpec.IntValue PREY_MAX_CHASE_TICKS;` (keep one blank line before and after the block):
```java
    public static final ModConfigSpec.DoubleValue FOOTSTEP_VOLUME;
    public static final ModConfigSpec.DoubleValue FOOTSTEP_STRIDE_BLOCKS;
    public static final ModConfigSpec.DoubleValue ROAR_VOLUME;
    public static final ModConfigSpec.IntValue ROAR_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue DISTANT_ROAR_ENABLED;
    public static final ModConfigSpec.IntValue DISTANT_ROAR_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue DISTANT_ROAR_VOLUME;
```

## Change 5: edit `RexConfig.java`, entries
Insert this block directly BEFORE the line `        BUILDER.push("daytime");` (copy the style of the `prey` block just above it; end the block with a blank line):
```java
        BUILDER.push("audio");
        FOOTSTEP_VOLUME = BUILDER
                .comment("Volume of its footsteps. A sound carries about 16 blocks per 1.0 of volume, so 6.0 is heard out to roughly 96 blocks, well past render distance.")
                .defineInRange("footstepVolume", 6.0, 0.1, 16.0);
        FOOTSTEP_STRIDE_BLOCKS = BUILDER
                .comment("Blocks it walks between two footsteps. Vanilla steps every ~1.7 blocks, far too fast for a body this big.")
                .defineInRange("footstepStrideBlocks", 7.0, 1.0, 20.0);
        ROAR_VOLUME = BUILDER
                .comment("Volume of the roar when it starts hunting a player (audible out to about 16 blocks per 1.0).")
                .defineInRange("roarVolume", 5.0, 0.1, 16.0);
        ROAR_COOLDOWN_TICKS = BUILDER
                .comment("Minimum ticks between two detection roars, so a player who keeps ducking in and out of sight does not trigger a roar every few seconds.")
                .defineInRange("roarCooldownTicks", 600, 0, 72000);
        DISTANT_ROAR_ENABLED = BUILDER
                .comment("Whether it occasionally roars at night while it is not hunting, to be heard from far away.")
                .define("distantRoarEnabled", true);
        DISTANT_ROAR_INTERVAL_TICKS = BUILDER
                .comment("Average ticks between distant roars at night while it is not hunting (6000 ticks = 5 minutes).")
                .defineInRange("distantRoarIntervalTicks", 6000, 200, 720000);
        DISTANT_ROAR_VOLUME = BUILDER
                .comment("Volume of the distant roar (audible out to about 16 blocks per 1.0, so 12.0 is about 192 blocks).")
                .defineInRange("distantRoarVolume", 12.0, 0.1, 16.0);
        BUILDER.pop();

```

## Change 6: edit `src/main/resources/assets/dinodread/lang/en_us.json` (keep valid JSON, two-space indent, trailing commas only where a line follows)
- Directly after the line `  "item.dinodread.trex_spawn_egg": "Tyrannosaurus Rex Spawn Egg",` and its following blank line, insert:
```json
  "subtitles.dinodread.entity.trex.roar": "Tyrannosaurus Rex roars",
  "subtitles.dinodread.entity.trex.roar_distant": "Distant roar",
  "subtitles.dinodread.entity.trex.step": "Heavy footsteps",
  "subtitles.dinodread.entity.trex.bite": "Tyrannosaurus Rex bites",

```
- Directly after the line `  "dinodread.configuration.preyMaxChaseTicks": "Longest Chase (ticks)",` insert:
```json
  "dinodread.configuration.audio": "Sound",
  "dinodread.configuration.footstepVolume": "Footstep Volume",
  "dinodread.configuration.footstepStrideBlocks": "Footstep Stride (blocks)",
  "dinodread.configuration.roarVolume": "Roar Volume",
  "dinodread.configuration.roarCooldownTicks": "Roar Cooldown (ticks)",
  "dinodread.configuration.distantRoarEnabled": "Distant Roars",
  "dinodread.configuration.distantRoarIntervalTicks": "Distant Roar Interval (ticks)",
  "dinodread.configuration.distantRoarVolume": "Distant Roar Volume",
```

## Do not touch
`.git/`, every `.ogg` file, `tools/`, `Docs/`, `CREDITS.md`, `CLAUDE.md`, `build.gradle`, `gradle.properties`, any other Java file
(`TRex.java`, the goals, the client package, the GameTests), any other lang line.

## Acceptance (Claude checks, you do not run it)
`.\gradlew build` passes in the m5 worktree; `sounds.json` and `en_us.json` parse as JSON; every sound path in `sounds.json` has a matching `.ogg`;
the four events appear in the registry; `RexConfig` has the seven new entries inside an `audio` section.
