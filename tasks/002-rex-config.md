# Task 002 — Common-side T-rex gameplay config (attributes + AI tunables)

**Goal:** add one new config class `RexConfig` (type STARTUP, file `config/dinodread-startup.toml`) holding the
T-rex attributes and all AI tunables, register it from the mod constructor, make `TRex.createAttributes()` read
it, and add lang entries for the config screen. No AI code in this task (Claude writes the goals afterwards and
reads these exact field names).

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`, mod id `dinodread`.
Every symbol below was verified against NeoForge 26.3.0.85 / FML 12.0.12 sources. **Use exactly these names and
imports. Do not substitute APIs from memory. You cannot run Gradle (bash is off); Claude builds afterwards.**

Why STARTUP: `ModConfig.Type` here has only `LOCAL`, `CLIENT`, `SYNCED`, `STARTUP`. `EntityAttributeCreationEvent`
fires during registry initialization, BEFORE LOCAL/CLIENT configs load and long before SYNCED loads. STARTUP
configs are loaded inside `ModContainer.registerConfig` itself (during mod construction), so values are readable
when the attribute event fires. So: register with `ModConfig.Type.STARTUP` in the `DinoDread` constructor.

## Reference to copy the pattern from
`src/main/java/com/dinodread/client/AtmosphereConfig.java`: same structure (static `ModConfigSpec.Builder BUILDER`,
`public static final ModConfigSpec.DoubleValue ...` fields assigned in one `static { }` block with
`.comment(...).defineInRange(...)`, `BUILDER.push("section")` / `BUILDER.pop()`, `public static final ModConfigSpec SPEC`,
private constructor, javadoc on the class). Copy its style and indentation. Unlike that file, RexConfig lives in
package `com.dinodread.config` and must import NOTHING from `net.minecraft.client` or `com.dinodread.client`
(it is common code; a dedicated server must be able to load it).

## Files to CREATE

### 1. `src/main/java/com/dinodread/config/RexConfig.java`
Class `public final class RexConfig`. Javadoc: "Common-side T-rex tunables (attributes, senses, behavior). Registered as a
STARTUP config from {@link com.dinodread.DinoDread}; the file is {@code config/dinodread-startup.toml}. STARTUP configs
load while the mod is constructed, so these values are readable when entity attributes are created."

Fields (all `public static final`, in a single static block, one `.comment("...")` each, in this order; the `Type`
is `ModConfigSpec.DoubleValue` for `defineInRange(String, double, double, double)` and `ModConfigSpec.IntValue` for
`defineInRange(String, int, int, int)`; doubles must be written with a decimal point, e.g. `200.0`):

Section `BUILDER.push("attributes")` — put `BUILDER.gameRestart()` immediately before EACH entry in this section
(`ModConfigSpec.Builder.gameRestart()` returns the builder; it marks that the value only takes effect after a game
restart), then `.comment(...)`, then the define call:

| Java field | key | default | min | max | comment |
|---|---|---|---|---|---|
| `MAX_HEALTH` | `maxHealth` | 200.0 | 1.0 | 4096.0 | T-rex max health in half-hearts. |
| `ATTACK_DAMAGE` | `attackDamage` | 20.0 | 0.0 | 1024.0 | Damage of one bite in half-hearts (20 = a full unarmored player health bar). |
| `MOVEMENT_SPEED` | `movementSpeed` | 0.3 | 0.05 | 2.0 | Base movement speed attribute. The behavior multipliers below scale this. |
| `KNOCKBACK_RESISTANCE` | `knockbackResistance` | 1.0 | 0.0 | 1.0 | 1.0 = cannot be knocked back. |
| `FOLLOW_RANGE` | `followRange` | 64.0 | 8.0 | 128.0 | Follow range attribute in blocks (limits how far pathfinding searches). |
| `STEP_HEIGHT` | `stepHeight` | 1.5 | 0.0 | 4.0 | Step height attribute in blocks: how high it walks up without jumping. |
| `SCALE` | `scale` | 3.0 | 0.0625 | 16.0 | Scale attribute: multiplies the base hitbox and the model. |

then `BUILDER.pop();`

Section `BUILDER.push("senses")` (no restart needed):

| Java field | key | default | min | max | comment |
|---|---|---|---|---|---|
| `SIGHT_RANGE` | `sightRange` | 48.0 | 4.0 | 256.0 | How far the T-rex can see a player (needs line of sight), in blocks. |
| `SIGHT_FOV_DEGREES` | `sightFieldOfViewDegrees` | 270.0 | 30.0 | 360.0 | Width of the T-rex's field of view in degrees. 360 = sees all around. |
| `SNEAK_SIGHT_MULTIPLIER` | `sneakSightMultiplier` | 0.5 | 0.0 | 1.0 | Multiplier on sight range against a sneaking player. |
| `HEARING_RADIUS` | `hearingRadius` | 64.0 | 0.0 | 256.0 | How far the T-rex hears a player's noises (steps, mining, placing...), in blocks. 0 = deaf. |
| `SNEAK_HEARING_MULTIPLIER` | `sneakHearingMultiplier` | 0.35 | 0.0 | 1.0 | Multiplier on hearing radius for a sneaking player. |

then `BUILDER.pop();`

Section `BUILDER.push("behavior")` (no restart needed):

| Java field | key | type | default | min | max | comment |
|---|---|---|---|---|---|---|
| `WANDER_SPEED_MULTIPLIER` | `wanderSpeedMultiplier` | double | 0.5 | 0.1 | 4.0 | Speed multiplier while wandering. |
| `WANDER_INTERVAL_TICKS` | `wanderIntervalTicks` | int | 120 | 20 | 1200 | Average ticks between choosing a new wander destination. |
| `STALK_SPEED_MULTIPLIER` | `stalkSpeedMultiplier` | double | 0.6 | 0.1 | 4.0 | Speed multiplier while stalking or investigating a noise (approaching without full speed). |
| `CHARGE_SPEED_MULTIPLIER` | `chargeSpeedMultiplier` | double | 1.6 | 0.1 | 4.0 | Speed multiplier while charging. |
| `CHARGE_DISTANCE` | `chargeDistance` | double | 24.0 | 2.0 | 128.0 | Distance in blocks at which a stalking T-rex that can see its target starts charging. |
| `STALK_TICKS_BEFORE_CHARGE` | `stalkTicksBeforeCharge` | int | 100 | 0 | 2400 | Ticks of continuously seeing its target while stalking before it charges even from beyond the charge distance. |
| `CHARGE_MAX_TICKS` | `chargeMaxTicks` | int | 200 | 20 | 2400 | Longest a single charge lasts before the T-rex drops back to stalking. |
| `CHARGE_COOLDOWN_TICKS` | `chargeCooldownTicks` | int | 100 | 0 | 2400 | Ticks after a charge ends before it can charge again. |
| `GIVE_UP_TICKS` | `giveUpTicks` | int | 400 | 20 | 12000 | Ticks without seeing or hearing its target before the T-rex gives up and goes back to wandering. |
| `INVESTIGATE_REACH_DISTANCE` | `investigateReachDistance` | double | 6.0 | 1.0 | 32.0 | How close to a heard noise counts as having arrived. |
| `BITE_REACH` | `biteReach` | double | 3.0 | 0.5 | 16.0 | Bite reach in blocks, measured from the edge of the T-rex's hitbox (so it stays correct at any scale). |
| `BITE_WINDUP_TICKS` | `biteWindupTicks` | int | 10 | 0 | 100 | Ticks between starting a bite and the damage landing (telegraph). |
| `BITE_COOLDOWN_TICKS` | `biteCooldownTicks` | int | 40 | 5 | 400 | Ticks between bites. |

then `BUILDER.pop();`, then `SPEC = BUILDER.build();`, then `private RexConfig() {}`.

## Files to EDIT

### 2. `src/main/java/com/dinodread/DinoDread.java`
- Constructor becomes `public DinoDread(IEventBus modEventBus, ModContainer container)`.
- Imports to add: `net.neoforged.fml.ModContainer`, `net.neoforged.fml.config.ModConfig`, `com.dinodread.config.RexConfig`
  (keep the existing grouping/ordering style: `net.neoforged` imports together, `com.dinodread` imports together).
- Add as the FIRST statement of the constructor body:
  `container.registerConfig(ModConfig.Type.STARTUP, RexConfig.SPEC);`
  with the comment line above it: `// STARTUP configs load right here, so RexConfig values are readable when entity attributes are created.`
- Keep every other statement unchanged.

### 3. `src/main/java/com/dinodread/entity/TRex.java`
- Delete the seven `private static final double ...` constants (`MAX_HEALTH`, `MOVEMENT_SPEED`, `KNOCKBACK_RESISTANCE`,
  `ATTACK_DAMAGE`, `FOLLOW_RANGE`, `STEP_HEIGHT`, `SCALE`) and their two comment lines (the "Placeholder attribute values..."
  line and the "Multiplies the base hitbox..." line).
- `createAttributes()` keeps the same shape but reads config: `.add(Attributes.MAX_HEALTH, RexConfig.MAX_HEALTH.get())`,
  `MOVEMENT_SPEED`, `KNOCKBACK_RESISTANCE`, `ATTACK_DAMAGE`, `FOLLOW_RANGE`, `STEP_HEIGHT`, `SCALE` the same way
  (`Attributes.SCALE` <- `RexConfig.SCALE.get()` etc.). `.get()` on a `DoubleValue` returns `Double`, which unboxes fine.
- Add import `com.dinodread.config.RexConfig` (in the `com.dinodread` group; there is currently no such group in this file, so put it
  first with a blank line after it, before the `com.geckolib` imports, as in `ModEntities.java`).
- Change the class comment to: `// T-rex. Attributes come from RexConfig (STARTUP config). AI goals are added separately.`
- Change nothing else (keep `cache`, constructor, `registerControllers`, `getAnimatableInstanceCache`).

### 4. `src/main/resources/assets/dinodread/lang/en_us.json`
Valid JSON must remain valid (no trailing comma on the last entry). Make these changes:
- change the value of `"dinodread.configuration.title"` to `"Dino Dread"`;
- append these entries after the existing last entry (add the comma to the previous last line), in this order:
```
  "dinodread.configuration.section.dinodread.startup.toml": "Dino Dread T-rex",
  "dinodread.configuration.section.dinodread.startup.toml.title": "Dino Dread T-rex",
  "dinodread.configuration.attributes": "Attributes (restart needed)",
  "dinodread.configuration.senses": "Senses",
  "dinodread.configuration.behavior": "Behavior",
  "dinodread.configuration.maxHealth": "Max Health",
  "dinodread.configuration.attackDamage": "Bite Damage",
  "dinodread.configuration.movementSpeed": "Movement Speed",
  "dinodread.configuration.knockbackResistance": "Knockback Resistance",
  "dinodread.configuration.followRange": "Follow Range",
  "dinodread.configuration.stepHeight": "Step Height",
  "dinodread.configuration.scale": "Scale",
  "dinodread.configuration.sightRange": "Sight Range",
  "dinodread.configuration.sightFieldOfViewDegrees": "Field Of View (degrees)",
  "dinodread.configuration.sneakSightMultiplier": "Sneak Sight Multiplier",
  "dinodread.configuration.hearingRadius": "Hearing Radius",
  "dinodread.configuration.sneakHearingMultiplier": "Sneak Hearing Multiplier",
  "dinodread.configuration.wanderSpeedMultiplier": "Wander Speed",
  "dinodread.configuration.wanderIntervalTicks": "Wander Interval (ticks)",
  "dinodread.configuration.stalkSpeedMultiplier": "Stalk Speed",
  "dinodread.configuration.chargeSpeedMultiplier": "Charge Speed",
  "dinodread.configuration.chargeDistance": "Charge Distance",
  "dinodread.configuration.stalkTicksBeforeCharge": "Stalk Time Before Charge (ticks)",
  "dinodread.configuration.chargeMaxTicks": "Charge Duration (ticks)",
  "dinodread.configuration.chargeCooldownTicks": "Charge Cooldown (ticks)",
  "dinodread.configuration.giveUpTicks": "Give Up After (ticks)",
  "dinodread.configuration.investigateReachDistance": "Noise Arrival Distance",
  "dinodread.configuration.biteReach": "Bite Reach",
  "dinodread.configuration.biteWindupTicks": "Bite Windup (ticks)",
  "dinodread.configuration.biteCooldownTicks": "Bite Cooldown (ticks)"
```
(keep the existing blank-line grouping style if you like, e.g. a blank line before the new block).

## Do NOT touch
`.git/` (never read, write or run anything in it), `build/`, `gradle/`, `build.gradle`, `gradle.properties`,
`settings.gradle`, `gradlew*`, `CLAUDE.md`, `CREDITS.md`, `Docs/`, `tasks/`, `.mcp.json`, `src/main/templates/`,
`src/main/java/com/dinodread/client/**` (AtmosphereConfig, AtmosphereEvents, AtmosphereEffects, DinoDreadClient, renderer),
`src/main/java/com/dinodread/mixin/**`, `src/main/java/com/dinodread/registry/**`,
`src/main/resources/assets/dinodread/geckolib/**`, `.../textures/**`, `.../items/**`, `.../models/**`.
Do not add goals, AI, sounds, tests, or any other file.

## Acceptance
- Exactly 1 new file (`RexConfig.java`) and 3 edited files (`DinoDread.java`, `TRex.java`, `en_us.json`).
- No reference to `net.minecraft.client` or `com.dinodread.client` anywhere in `RexConfig.java`.
- The field names in `RexConfig` match the table exactly (Claude's goal code reads them).
- `en_us.json` is valid JSON.
- (Claude runs `.\gradlew build` afterwards; you cannot.)
- Reply with a short list of files created/edited plus any deviation from this spec and why (ideally none).
