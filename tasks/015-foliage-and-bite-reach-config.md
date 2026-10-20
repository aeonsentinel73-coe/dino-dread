# Task 015: config entries `foliageBlocks` and `biteReachBelow`

**Goal:** add two entries to `RexConfig` (and their labels in the lang file). Config boilerplate only: no gameplay code changes.

Working directory: `D:\Documents\Claude Code\Dino Dread-m5` (pass it as `working_dir`).

## PROCEDURE (follow exactly)
1. Read `src/main/java/com/dinodread/config/RexConfig.java` (lines 38-60 and 170-235 are what matter) and
   `src/main/resources/assets/dinodread/lang/en_us.json`.
2. Make the four edits below, each in one edit call. Do NOT search the filesystem and do NOT grep for Minecraft symbols; nothing new is imported.
3. Finish with `finish` in under 12 tool calls. You cannot run Gradle; Claude will build.

## Edit 1: constants in `RexConfig.java`
- Directly after the line `    public static final ModConfigSpec.DoubleValue BITE_REACH;` insert:
```java
    public static final ModConfigSpec.DoubleValue BITE_REACH_BELOW;
```
- Directly after the line `    public static final ModConfigSpec.ConfigValue<List<? extends String>> UNBREAKABLE_BLOCKS;` insert:
```java
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOLIAGE_BLOCKS;
```

## Edit 2: `biteReachBelow` entry in the builder
Insert this block directly BEFORE the line `        BITE_WINDUP_TICKS = BUILDER` (the line after the `biteReach` entry):
```java
        BITE_REACH_BELOW = BUILDER
                .comment("How far below its own feet, in blocks, the T-rex can bite down at a target (on top of biteReach). A body this wide cannot get into a narrow valley or pit, but the head can reach down into it.")
                .defineInRange("biteReachBelow", 8.0, 0.0, 32.0);
```

## Edit 3: `foliageBlocks` entry in the builder
Insert this block directly AFTER the line `                        element -> element instanceof String);` (it is the last line of the `unbreakableBlocks` entry and appears only once):
```java
        FOLIAGE_BLOCKS = BUILDER
                .comment("Trees and the like. While the T-rex is not hunting a player (wandering, or going for an animal) these are the only blocks it breaks, so a tree line is never a trap and everything else is left alone then. Hunting a player uses unbreakableBlocks instead. Entries are block ids (minecraft:oak_leaves) or block tags with a leading # (#minecraft:leaves).")
                .defineListAllowEmpty("foliageBlocks",
                        List.of("#minecraft:leaves", "#minecraft:logs"),
                        () -> "minecraft:oak_leaves",
                        element -> element instanceof String);
```

## Edit 4: labels in `src/main/resources/assets/dinodread/lang/en_us.json` (keep it valid JSON, two-space indent)
- Directly after the line `  "dinodread.configuration.biteReach": "Bite Reach",` insert:
```json
  "dinodread.configuration.biteReachBelow": "Bite Reach Below",
```
- Directly after the line `  "dinodread.configuration.unbreakableBlocks": "Unbreakable Blocks",` insert:
```json
  "dinodread.configuration.foliageBlocks": "Foliage Blocks (broken when not hunting)",
```

## Do not touch
`.git/`, every other line of `RexConfig.java` and of the lang file, every other file (all Java under `entity/`, `client/`, `gametest/`, `registry/`, resources, `tools/`, `Docs/`, `CREDITS.md`, `CLAUDE.md`, build files).

## Acceptance (Claude checks, you do not run it)
`.\gradlew build` passes; `en_us.json` parses; `RexConfig` has `BITE_REACH_BELOW` and `FOLIAGE_BLOCKS` declared and defined once each.
