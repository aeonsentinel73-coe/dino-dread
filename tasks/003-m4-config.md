# Task 003 — M4 config: griefing, block allow-list, breaking rate, stuck-recovery tunables

**Goal:** add the M4 tunables to the existing `RexConfig` (plus matching lang entries) so later tasks can read them.
No AI code in this task.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`, mod id `dinodread`.
Every symbol below was verified against NeoForge 26.3.0.85. **Use exactly these names and imports. Do not substitute
APIs from memory. You cannot run Gradle (bash is off); Claude builds afterwards.**

## Reference to copy the pattern from
`src/main/java/com/dinodread/config/RexConfig.java` itself: same `public static final ModConfigSpec.XxxValue FIELD;`
declarations at the top, assigned inside the one `static { }` block with `BUILDER.push("section")` / `.comment(...)` /
`.defineInRange(...)` / `BUILDER.pop()`, then `SPEC = BUILDER.build();`. Copy its indentation and comment style.
Lang entries: copy the pattern of the existing `"dinodread.configuration.usePathfinding": "Use Pathfinding"` lines in
`src/main/resources/assets/dinodread/lang/en_us.json`.

## Files to EDIT (only these two)

### 1. `src/main/java/com/dinodread/config/RexConfig.java`

**a) Change the default of `STEP_HEIGHT`** (key `stepHeight`) from `1.5` to `3.0`, and update its comment to
`"Step height attribute in blocks: how high it walks up without jumping. The T-rex is 12 blocks tall, so 3 is a normal stride for it."`
(min 0.0 and max 4.0 stay).

**b) Add imports** `java.util.List` at the top (above the neoforge import).

**c) Add these fields** (declare them after the existing `public static final` declarations, before `SPEC`; the
`SPEC` field stays last). Assign them in the `static { }` block, in a NEW section pair inserted **after the closing
`BUILDER.pop();` of the "behavior" section and before `BUILDER.push("daytime")`**.

Section `BUILDER.push("obstacles")`:

| Java field | key | type / define call | default | min | max | comment |
|---|---|---|---|---|---|---|
| `GRIEFING_ENABLED` | `griefingEnabled` | `ModConfigSpec.BooleanValue`, `.define("griefingEnabled", true)` | true | | | "Master switch for block breaking. Off = the T-rex never breaks any block. The mobGriefing game rule is honored too (rule off = no breaking)." |
| `BREAKABLE_BLOCKS` | `breakableBlocks` | `ModConfigSpec.ConfigValue<List<? extends String>>`, `.defineListAllowEmpty("breakableBlocks", List.of(<defaults below>), () -> "minecraft:oak_planks", element -> element instanceof String)` | see below | | | "Blocks the T-rex may break while pushing through obstacles. Entries are block ids (minecraft:glass) or block tags with a leading # (#minecraft:logs). Anything not matched is never broken. Stone, dirt and ores are deliberately absent." |
| `BREAK_BLOCK_ENTITIES` | `breakBlockEntities` | `BooleanValue`, `.define(...)` | false | | | "Allow breaking blocks that hold data (chests, furnaces, signs...) when they match the list above. Off = those blocks are never broken." |
| `MAX_BREAK_HARDNESS` | `maxBreakHardness` | `DoubleValue`, `defineInRange(String, double, double, double)` | 5.0 | 0.0 | 3600000.0 | "Blocks harder than this (vanilla hardness: planks 2, logs 2, glass 0.3, obsidian 50) are never broken, even if listed." |
| `BREAK_INTERVAL_TICKS` | `breakIntervalTicks` | `IntValue`, `defineInRange(String, int, int, int)` | 4 | 1 | 200 | "Ticks between break actions while the T-rex is pushing against obstacles." |
| `BLOCKS_PER_BREAK` | `blocksPerBreak` | `IntValue` | 6 | 1 | 256 | "Most blocks removed in one break action." |
| `BREAK_REACH` | `breakReach` | `DoubleValue` | 1.5 | 0.5 | 16.0 | "How far in front of its hitbox, in blocks, the T-rex breaks blocks." |
| `BREAK_DROPS_ITEMS` | `breakDropsItems` | `BooleanValue` | true | | | "Broken blocks drop their items." |

Default value of `BREAKABLE_BLOCKS` (exactly these ten strings, in this order):
`"#minecraft:logs"`, `"#minecraft:planks"`, `"#minecraft:leaves"`, `"#minecraft:wooden_doors"`,
`"#minecraft:wooden_trapdoors"`, `"#minecraft:wooden_stairs"`, `"#minecraft:wooden_slabs"`,
`"#minecraft:wooden_fences"`, `"#c:glass_blocks"`, `"#c:glass_panes"`.

`BUILDER.pop();` after the last entry of the section.

Section `BUILDER.push("stuck")` (all `defineInRange`):

| Java field | key | type | default | min | max | comment |
|---|---|---|---|---|---|---|
| `STUCK_CHECK_INTERVAL_TICKS` | `stuckCheckIntervalTicks` | `IntValue` | 10 | 2 | 100 | "How often, in ticks, the T-rex checks whether it is getting closer to where it is going." |
| `STUCK_MIN_PROGRESS` | `stuckMinProgress` | `DoubleValue` | 0.5 | 0.05 | 10.0 | "Blocks it must have closed in on its destination since the last check to count as making progress." |
| `STUCK_BREAK_AFTER_TICKS` | `stuckBreakAfterTicks` | `IntValue` | 20 | 1 | 1200 | "Ticks without progress before it starts breaking blocks even when it is not touching a wall." |
| `STUCK_HOP_AFTER_TICKS` | `stuckHopAfterTicks` | `IntValue` | 60 | 1 | 1200 | "Ticks without progress before it starts hopping (to get over ledges). Should be above the break value." |
| `STUCK_SIDESTEP_AFTER_TICKS` | `stuckSidestepAfterTicks` | `IntValue` | 100 | 1 | 1200 | "Ticks without progress before it starts walking sideways to go round the obstacle. Should be above the hop value." |
| `STUCK_GIVE_UP_AFTER_TICKS` | `stuckGiveUpAfterTicks` | `IntValue` | 220 | 1 | 6000 | "Ticks without progress before it gives up on its current target. Should be above the sidestep value." |
| `STUCK_SIDESTEP_SWITCH_TICKS` | `stuckSidestepSwitchTicks` | `IntValue` | 40 | 5 | 400 | "While sidestepping it switches between left and right every this many ticks." |
| `STUCK_SIDESTEP_DISTANCE` | `stuckSidestepDistance` | `DoubleValue` | 12.0 | 2.0 | 64.0 | "How far to the side, in blocks, a sidestep aims." |
| `STUCK_HOP_VELOCITY` | `stuckHopVelocity` | `DoubleValue` | 0.9 | 0.1 | 3.0 | "Upward speed of a hop in blocks per tick (0.42 is a player jump; 0.9 clears roughly 4 blocks)." |
| `STUCK_HOP_FORWARD_VELOCITY` | `stuckHopForwardVelocity` | `DoubleValue` | 0.3 | 0.0 | 2.0 | "Forward speed of a hop in blocks per tick." |

`BUILDER.pop();` after the last entry.

Doubles must be written with a decimal point (`5.0`, `3600000.0`).

### 2. `src/main/resources/assets/dinodread/lang/en_us.json`
Add these entries (JSON, comma-separated, inserted after the existing `"dinodread.configuration.behavior"` group's
last entry and before `"dinodread.configuration.daytime"`; keep the file valid JSON — mind the commas):

```
"dinodread.configuration.obstacles": "Obstacles and Block Breaking",
"dinodread.configuration.griefingEnabled": "Block Breaking Enabled",
"dinodread.configuration.breakableBlocks": "Breakable Blocks",
"dinodread.configuration.breakBlockEntities": "Break Chests And Other Block Entities",
"dinodread.configuration.maxBreakHardness": "Maximum Block Hardness",
"dinodread.configuration.breakIntervalTicks": "Break Interval (ticks)",
"dinodread.configuration.blocksPerBreak": "Blocks Per Break",
"dinodread.configuration.breakReach": "Break Reach",
"dinodread.configuration.breakDropsItems": "Broken Blocks Drop Items",
"dinodread.configuration.stuck": "Getting Unstuck",
"dinodread.configuration.stuckCheckIntervalTicks": "Stuck Check Interval (ticks)",
"dinodread.configuration.stuckMinProgress": "Minimum Progress",
"dinodread.configuration.stuckBreakAfterTicks": "Break After (ticks)",
"dinodread.configuration.stuckHopAfterTicks": "Hop After (ticks)",
"dinodread.configuration.stuckSidestepAfterTicks": "Sidestep After (ticks)",
"dinodread.configuration.stuckGiveUpAfterTicks": "Give Up After (ticks)",
"dinodread.configuration.stuckSidestepSwitchTicks": "Sidestep Switch Interval (ticks)",
"dinodread.configuration.stuckSidestepDistance": "Sidestep Distance",
"dinodread.configuration.stuckHopVelocity": "Hop Upward Speed",
"dinodread.configuration.stuckHopForwardVelocity": "Hop Forward Speed",
```
(Existing entries use the same `dinodread.configuration.<key>` pattern; the `.title`/section lines stay untouched.)

## DO NOT TOUCH
`.git/`, anything under `src/main/java` other than `RexConfig.java`, `build.gradle`, `gradle.properties`, the
`client/` package, every other file under `tasks/` and `docs/`/`Docs/`. Do not rename or reorder existing fields or
change any other existing default. Do not add imports other than `java.util.List`.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- All 18 new fields exist with the exact names above; `STEP_HEIGHT` default is `3.0`; `BREAKABLE_BLOCKS` default has
  the ten strings; `en_us.json` is valid JSON with the new keys.
