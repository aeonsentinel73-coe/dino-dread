# Task 016: spawning config (`dinodread-spawning.toml`), the `SpawnRules` snapshot and lang labels

**Goal:** add a NEW STARTUP config class `SpawnConfig` for natural T-rex spawning and the vanilla-hostile toggle, a
`SpawnRules` record that snapshots it (so code and tests can pass rules around without reading the config), and the lang
labels. Pure transcription of the tables below into the existing pattern; no behaviour code.

Working directory: `D:\Documents\Claude Code\Dino Dread-m6` (pass it as `working_dir`).

## PROCEDURE (follow exactly)
1. Read `src/main/java/com/dinodread/config/RexConfig.java` (the pattern to copy: static `BUILDER`, `push`/`pop`, static
   `SPEC`, private constructor) and `src/main/resources/assets/dinodread/lang/en_us.json` (last 10 lines only matter).
2. Make the three changes below, each in a single write or edit. Do NOT search the filesystem and do NOT grep for
   Minecraft symbols: nothing here needs a symbol you have not seen in `RexConfig.java`.
3. Finish with `finish` in under 10 tool calls. You cannot run Gradle; Claude will build.

Do NOT touch: `.git/`, `RexConfig.java`, `DinoDread.java`, any other file.

## Change 1: create `src/main/java/com/dinodread/config/SpawnConfig.java` (whole file)
- `package com.dinodread.config;`, imports `java.util.List` and `net.neoforged.neoforge.common.ModConfigSpec`.
- `public final class SpawnConfig`, javadoc: "Natural T-rex spawning and the vanilla-hostile toggle. Registered as a STARTUP
  config from DinoDread; the file is {@code config/dinodread-spawning.toml}. Read through SpawnRules, which snapshots these values."
- Same structure as `RexConfig`: `private static final ModConfigSpec.Builder BUILDER`, one `public static final` field per
  entry (declared first, grouped by section with a blank line between sections), then `public static final ModConfigSpec SPEC;`,
  then a `static { ... SPEC = BUILDER.build(); }` block, then `private SpawnConfig() {}`.
- The constant name is the entry name in UPPER_SNAKE_CASE (`spawnEnabled` -> `SPAWN_ENABLED`). Types: `BooleanValue` for
  `define`, `DoubleValue` for `defineInRange` with double bounds, `IntValue` for `defineInRange` with int bounds,
  `ConfigValue<List<? extends String>>` for lists.
- Do NOT call `.gameRestart()` on anything (these are read live from the snapshot, unlike attributes).
- Lists use exactly this form (copy the `UNBREAKABLE_BLOCKS` entry in `RexConfig`): 
  `.defineListAllowEmpty("name", List.of(...defaults...), () -> "<newElement>", element -> element instanceof String)`.
- Each section is `BUILDER.push("<section>"); ... BUILDER.pop();`. Comments are given as text; use `.comment("...")`
  with exactly that text (a two-string comment where two strings are given).

### Section `spawning`
| Constant | Entry name | Kind | Default | Range | New-element | Comment |
|---|---|---|---|---|---|---|
| SPAWN_ENABLED | spawnEnabled | boolean | true | | | Master switch for natural T-rex spawns. Spawn eggs and /summon always work. |
| NIGHT_ONLY | nightOnly | boolean | true | | | Natural spawns only happen while it is dark at the player. |
| MIN_NIGHT_FACTOR | minNightFactor | double | 0.5 | 0.0 to 1.0 | | How dark it must be at the player for nightOnly: 0 = full daylight counts, 1 = only the middle of the night. 0.5 starts a little after dusk. |
| FIRST_SPAWN_DELAY_DAYS | firstSpawnDelayDays | int | 0 | 0 to 10000 | | No natural spawn until the world has run this many full days (24000 ticks of game time each, 20 minutes). 0 = from the start. |
| ATTEMPT_INTERVAL_TICKS | attemptIntervalTicks | int | 600 | 20 to 72000 | | Every this many ticks each eligible player rolls once for a spawn (600 ticks = 30 seconds). |
| SPAWN_CHANCE | spawnChance | double | 0.05 | 0.0 to 1.0 | | Chance that one roll spawns a T-rex, if everything else allows it. This is the main rarity knob: with the defaults a player at night waits about 10 minutes on average. 0 = never, 1 = every roll. |
| ALLOWED_DIMENSIONS | allowedDimensions | list | `"minecraft:overworld"` | | `"minecraft:overworld"` | Dimensions where natural spawns happen, as dimension ids. |
| ALLOWED_BIOMES | allowedBiomes | list | `"#minecraft:is_overworld"` | | `"#minecraft:is_overworld"` | Biomes where natural spawns happen, as biome ids (minecraft:plains) or biome tags with a leading # (#minecraft:is_forest). An empty list = any biome. |

### Section `placement`
| Constant | Entry name | Kind | Default | Range | Comment |
|---|---|---|---|---|---|
| MIN_SPAWN_DISTANCE | minSpawnDistance | double | 56.0 | 8.0 to 256.0 | Closest, in blocks, to a player that a natural spawn may appear. Keep it above the T-rex's sight range so it is never conjured in view; it should be heard before it is seen. |
| MAX_SPAWN_DISTANCE | maxSpawnDistance | double | 96.0 | 16.0 to 256.0 | Furthest, in blocks, from the player that a natural spawn may appear. Only chunks that are loaded and running can be used, so very large values just fail more often. |
| PLACEMENT_ATTEMPTS | placementAttempts | int | 24 | 1 to 256 | Random spots tried per roll. The T-rex needs room for its whole body, so forests and hills fail more often; raise this if spawns seem too rare there. |

### Section `limits`
| Constant | Entry name | Kind | Default | Range | Comment |
|---|---|---|---|---|---|
| MAX_REX_PER_PLAYER | maxRexPerPlayer | int | 1 | 0 to 16 | No natural spawn if this many T-rexes (of any origin) are already within capRadius of the player. |
| CAP_RADIUS | capRadius | double | 160.0 | 16.0 to 512.0 | Radius in blocks around a player for maxRexPerPlayer. |
| MAX_REX_IN_WORLD | maxRexInWorld | int | 1 | 0 to 64 | No natural spawn if this many T-rexes (of any origin) are already loaded in the dimension. Dino Dread v1 is ONE T-rex. |

### Section `despawn`
| Constant | Entry name | Kind | Default | Range | Comment |
|---|---|---|---|---|---|
| DESPAWN_WHEN_FAR | despawnWhenFar | boolean | true | | A naturally spawned T-rex is removed when no player is within despawnDistance, so one that wandered off never blocks the next spawn. Off = it stays until killed. Spawn-egg T-rexes are never touched. |
| DESPAWN_DISTANCE | despawnDistance | double | 128.0 | 32.0 to 512.0 | Distance in blocks used by despawnWhenFar. Keep it above maxSpawnDistance and below the server's simulation distance (a T-rex in a chunk that is not running cannot be checked). |
| MAX_LIFETIME_TICKS | maxLifetimeTicks | int | 48000 | 0 to 24000000 | A naturally spawned T-rex leaves after this many loaded ticks (48000 = 40 minutes), unless it is hunting right then. 0 = no limit. Reloading the chunk restarts the count. |

### Section `vanillaHostiles`
| Constant | Entry name | Kind | Default | Range | New-element | Comment |
|---|---|---|---|---|---|---|
| DISABLE_VANILLA_HOSTILES | disableVanillaHostiles | boolean | false | | | Stop other hostile mobs (everything in the monster category: zombies, skeletons, creepers, phantoms, mod monsters...) from spawning on their own, so the T-rex is the only thing that hunts. Spawn eggs, /summon and the T-rex itself are never affected. |
| VANILLA_HOSTILE_EXCEPTIONS | vanillaHostileExceptions | list | empty list `List.of()` | | `"minecraft:zombie"` | Hostile mobs that still spawn when disableVanillaHostiles is on, as entity ids (minecraft:zombie) or entity tags with a leading # (#minecraft:skeletons). |
| BLOCKED_SPAWN_REASONS | blockedSpawnReasons | list | `"NATURAL"`, `"CHUNK_GENERATION"`, `"PATROL"`, `"STRUCTURE"` | | `"NATURAL"` | Which kinds of spawn disableVanillaHostiles blocks, by vanilla spawn reason name: NATURAL (the normal night spawning), CHUNK_GENERATION (mobs made when terrain is generated), PATROL (pillager patrols), STRUCTURE (mobs placed by structures). Add SPAWNER to also stop dungeon and trial spawners. Egg, command, breeding, raid and summon spawns are not in the list and never blocked. |

## Change 2: create `src/main/java/com/dinodread/spawn/SpawnRules.java` (whole file)
`package com.dinodread.spawn;` Imports `java.util.List` and `com.dinodread.config.SpawnConfig`.

Javadoc: "A snapshot of the spawning config, so the spawner can be driven by explicit rules (the game tests do) instead of
reading the config directly. {@link #fromConfig()} is what the game uses."

```java
public record SpawnRules(
    boolean spawnEnabled,
    boolean nightOnly,
    double minNightFactor,
    int firstSpawnDelayDays,
    int attemptIntervalTicks,
    double spawnChance,
    List<? extends String> allowedDimensions,
    List<? extends String> allowedBiomes,
    double minSpawnDistance,
    double maxSpawnDistance,
    int placementAttempts,
    int maxRexPerPlayer,
    double capRadius,
    int maxRexInWorld,
    boolean despawnWhenFar,
    double despawnDistance,
    int maxLifetimeTicks) {
```
The record components are exactly those 17, in that order. Inside the record body:
1. `public static SpawnRules fromConfig()` returns `new SpawnRules(SpawnConfig.SPAWN_ENABLED.get(), SpawnConfig.NIGHT_ONLY.get(), ...)`
   passing the matching `SpawnConfig.<CONSTANT>.get()` for every component, in record order (`SpawnConfig` constant = entry name
   in UPPER_SNAKE_CASE, same as the record component).
2. One "wither" per component: `public SpawnRules with<Component>(<type> value)` returning a new `SpawnRules` that is a copy
   of `this` with just that component replaced. Name = `with` + the component name with its first letter upper-cased
   (`withNightOnly`, `withSpawnChance`, `withAllowedBiomes`, ...). The list-typed ones take `List<? extends String> value`.
   17 withers, one line of javadoc is not needed. Each body is a single `return new SpawnRules(...)` listing all 17 components
   in record order, with `value` in place of the replaced one.

## Change 3: edit `src/main/resources/assets/dinodread/lang/en_us.json`
The last line of the file's object is currently `  "dinodread.configuration.dayAggression": "Daytime Aggression"` (no trailing comma, then a line `}`).
Add a trailing comma to that line, then append this block as lines directly after it (two-space indent, one blank line
before the block), keeping the final `}` on its own last line and leaving every existing line otherwise unchanged:

```
  "dinodread.configuration.section.dinodread.spawning.toml": "Dino Dread Spawning",
  "dinodread.configuration.section.dinodread.spawning.toml.title": "Dino Dread Spawning",
  "dinodread.configuration.spawning": "Natural Spawning",
  "dinodread.configuration.spawnEnabled": "Natural Spawning Enabled",
  "dinodread.configuration.nightOnly": "Night Only",
  "dinodread.configuration.minNightFactor": "Minimum Darkness",
  "dinodread.configuration.firstSpawnDelayDays": "First Spawn Delay (days)",
  "dinodread.configuration.attemptIntervalTicks": "Spawn Roll Interval (ticks)",
  "dinodread.configuration.spawnChance": "Spawn Chance per Roll",
  "dinodread.configuration.allowedDimensions": "Allowed Dimensions",
  "dinodread.configuration.allowedBiomes": "Allowed Biomes",
  "dinodread.configuration.placement": "Placement",
  "dinodread.configuration.minSpawnDistance": "Minimum Spawn Distance",
  "dinodread.configuration.maxSpawnDistance": "Maximum Spawn Distance",
  "dinodread.configuration.placementAttempts": "Placement Attempts",
  "dinodread.configuration.limits": "Limits",
  "dinodread.configuration.maxRexPerPlayer": "Most Near One Player",
  "dinodread.configuration.capRadius": "Near-Player Radius",
  "dinodread.configuration.maxRexInWorld": "Most in a Dimension",
  "dinodread.configuration.despawn": "Despawning",
  "dinodread.configuration.despawnWhenFar": "Despawn When Far From Players",
  "dinodread.configuration.despawnDistance": "Despawn Distance",
  "dinodread.configuration.maxLifetimeTicks": "Longest Stay (ticks)",
  "dinodread.configuration.vanillaHostiles": "Other Hostile Mobs",
  "dinodread.configuration.disableVanillaHostiles": "Disable Other Hostile Mobs",
  "dinodread.configuration.vanillaHostileExceptions": "Hostile Mob Exceptions",
  "dinodread.configuration.blockedSpawnReasons": "Blocked Spawn Reasons"
```

## Acceptance (Claude checks)
`.\gradlew build` passes; `SpawnConfig` has 20 entries in 5 sections; `SpawnRules` has 17 components, 17 withers and `fromConfig()`.
