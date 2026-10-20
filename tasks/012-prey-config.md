# Task 012 — Config and lang for the T-rex eating animals ("prey")

**Goal:** add six tunables for the new prey behaviour (task 013 writes the goal) to `RexConfig`, plus their lang strings. No AI code here.

Working dir = THIS repo (the worktree you were given). **You cannot run Gradle (bash is off); Claude builds afterwards.**
Do not search the filesystem or grep for Minecraft symbols.

## Files to EDIT (only these two)
- `src/main/java/com/dinodread/config/RexConfig.java`
- `src/main/resources/assets/dinodread/lang/en_us.json`

Reference to copy the pattern from: the existing entries in `RexConfig.java` (declare next to the others; assign in the `static { }` block with
`BUILDER.comment(...).defineInRange(...)` / `.define(...)`, `BUILDER.push("section")` / `BUILDER.pop()`), and the `"dinodread.configuration.<key>"` lang lines.

## RexConfig
Declare (next to the other `public static final` fields, before `SPEC`) and assign in a NEW section `BUILDER.push("prey")` ... `BUILDER.pop();` placed
right after the closing `BUILDER.pop();` of the `"stuck"` section and before `BUILDER.push("daytime")`:

| Java field | key | type / define call | default | min | max | comment |
|---|---|---|---|---|---|---|
| `PREY_ENABLED` | `preyEnabled` | `ModConfigSpec.BooleanValue`, `.define("preyEnabled", true)` | true | | | "Whether the T-rex occasionally goes for animals (pigs, cows, sheep, chickens, ...) and eats them while no player is detected." |
| `PREY_INTERVAL_TICKS` | `preyIntervalTicks` | `IntValue`, `defineInRange(String, int, int, int)` | 900 | 20 | 72000 | "Average ticks between the T-rex noticing a nearby animal to eat while no player is detected." |
| `PREY_RANGE` | `preyRange` | `DoubleValue`, `defineInRange(String, double, double, double)` | 32.0 | 4.0 | 128.0 | "How far, in blocks, from the T-rex it notices animals to eat. Tamed, leashed and name-tagged animals are never eaten." |
| `PREY_SPEED_MULTIPLIER` | `preySpeedMultiplier` | `DoubleValue` | 1.0 | 0.1 | 4.0 | "Speed multiplier while going for an animal." |
| `PREY_EAT_TICKS` | `preyEatTicks` | `IntValue` | 100 | 0 | 2400 | "Ticks it stands over a kill 'eating' before it wanders on." |
| `PREY_MAX_CHASE_TICKS` | `preyMaxChaseTicks` | `IntValue` | 400 | 20 | 2400 | "Longest it chases one animal before losing interest." |

## Lang (insert before the `"dinodread.configuration.daytime"` line, keep the JSON valid)
```
"dinodread.configuration.prey": "Eating Animals",
"dinodread.configuration.preyEnabled": "Eat Animals",
"dinodread.configuration.preyIntervalTicks": "Prey Interval (ticks)",
"dinodread.configuration.preyRange": "Prey Range",
"dinodread.configuration.preySpeedMultiplier": "Prey Speed Multiplier",
"dinodread.configuration.preyEatTicks": "Eating Time (ticks)",
"dinodread.configuration.preyMaxChaseTicks": "Longest Chase (ticks)",
```

## DO NOT TOUCH
`.git/`, every other file and every existing entry/default. Doubles need a decimal point. No new imports.

## Acceptance check
`.\gradlew build` passes (Claude runs it); all six fields exist with the exact names; `en_us.json` is valid JSON.
