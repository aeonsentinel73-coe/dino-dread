# Task 019: `spawnAlerted` option (a new natural T-rex heads for the player who rolled the spawn)

**Why:** a T-rex that spawns 56 to 96 blocks away is beyond its own sight (40) and hearing (52), so without help it wanders
randomly and mostly never finds anyone. With `spawnAlerted` the spawner tells it where the player is (it investigates that
spot like a heard noise, and roars as the hunt starts), so it is heard before it is seen.
Claude has ALREADY added `public void alertTo(Vec3 pos)` to `TRexSenses` (do not touch that file).

**Goal:** add the config entry, the 18th `SpawnRules` component (appended LAST, so the existing order does not move), its lang
label, and the call in `RexSpawner`.

Working directory: `D:\Documents\Claude Code\Dino Dread-m6` (pass it as `working_dir`).

## PROCEDURE (follow exactly)
1. Read: `src/main/java/com/dinodread/config/SpawnConfig.java`, `src/main/java/com/dinodread/spawn/SpawnRules.java`,
   `src/main/java/com/dinodread/spawn/RexSpawner.java` (only lines 95-125 matter), and the last 30 lines of
   `src/main/resources/assets/dinodread/lang/en_us.json`.
2. Make the four changes below. SpawnRules.java is a whole-file rewrite (one write); the others are single edits.
   Do NOT search the filesystem and do NOT grep for Minecraft symbols: nothing here needs a symbol you have not seen.
3. Finish with `finish` in under 12 tool calls. You cannot run Gradle; Claude will build.

Do NOT touch: `.git/`, `TRexSenses.java`, any test, any other file.

## Change 1: `SpawnConfig.java`
- Add the field line `    public static final ModConfigSpec.BooleanValue SPAWN_ALERTED;` directly after the `PLACEMENT_ATTEMPTS` field line.
- In the `placement` section, directly after the `PLACEMENT_ATTEMPTS = BUILDER ... defineInRange("placementAttempts", 24, 1, 256);` statement
  and before that section's `BUILDER.pop();`, add:
```java
        SPAWN_ALERTED = BUILDER
                .comment("A newly spawned T-rex is told where the player who rolled the spawn is, so it heads that way (and roars as it sets off) instead of wandering aimlessly.",
                        "Off = it spawns calm and may never find anyone, since it spawns beyond its own sight and hearing.")
                .define("spawnAlerted", true);
```

## Change 2: `SpawnRules.java` (whole file)
Same file as now, with exactly these differences:
1. A new LAST record component: after `int maxLifetimeTicks` add `, boolean spawnAlerted` (so the record has 18 components; `spawnAlerted` is last).
2. `fromConfig()`: add `SpawnConfig.SPAWN_ALERTED.get()` as the new last argument (after `SpawnConfig.MAX_LIFETIME_TICKS.get()`).
3. Every existing wither: add `spawnAlerted` as the new last argument of its `new SpawnRules(...)` call (after `maxLifetimeTicks`, or after `value` in `withMaxLifetimeTicks`).
4. A new 18th wither `public SpawnRules withSpawnAlerted(boolean value)` placed last, whose call passes `value` as the last argument and every other component unchanged.

## Change 3: `RexSpawner.java`
In `trySpawn`, replace the line
`            return place(level, rex, random); // canStandAt already moved the rex to this spot`
with
```java
            // canStandAt already moved the rex to this spot
            TRex placed = place(level, rex, random);
            if (placed != null && rules.spawnAlerted()) {
                placed.getSenses().alertTo(player.position());
            }
            return placed;
```

## Change 4: `en_us.json`
Add this line directly after the line `  "dinodread.configuration.placementAttempts": "Placement Attempts",`:
```
  "dinodread.configuration.spawnAlerted": "Alert New T-rex",
```

## Acceptance (Claude checks)
`.\gradlew build` passes (Claude updates the tests' `new SpawnRules(...)` call itself); `SpawnRules` has 18 components and 18 withers.
