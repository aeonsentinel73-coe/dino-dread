# Task 005 — `RexStuckDetector`: notices the T-rex is not getting anywhere and escalates

**Goal:** add one new plain-Java class `com.dinodread.entity.ai.RexStuckDetector` (no Minecraft types at all) that is
fed the T-rex's distance to where it is going once per tick and reports how stuck it is, escalating over time:
`FREE -> BREAK -> HOP -> SIDESTEP -> GIVE_UP`. Task 006 wires it into the hunt goal; a GameTest
(`trex_stuck_detector_escalates`) already exercises it directly.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`.
**You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the filesystem for Minecraft sources:
this class needs no Minecraft API, only `com.dinodread.config.RexConfig`.

## File to CREATE (only this one)
`src/main/java/com/dinodread/entity/ai/RexStuckDetector.java`

Style reference: `src/main/java/com/dinodread/entity/ai/TRexSenses.java` (javadoc on the class, small named constants,
tunables read from `RexConfig`). The config fields you read (all already exist in `RexConfig`, `.get()` returns `Integer`/`Double`):
`STUCK_CHECK_INTERVAL_TICKS` (int), `STUCK_MIN_PROGRESS` (double), `STUCK_BREAK_AFTER_TICKS`, `STUCK_HOP_AFTER_TICKS`,
`STUCK_SIDESTEP_AFTER_TICKS`, `STUCK_GIVE_UP_AFTER_TICKS`, `STUCK_SIDESTEP_SWITCH_TICKS` (all int).

## Public surface (exact)
```java
public final class RexStuckDetector {
    public enum Level { FREE, BREAK, HOP, SIDESTEP, GIVE_UP }

    public RexStuckDetector()

    /** Call once per tick while the T-rex is trying to get to a destination. Returns the current level. */
    public Level tick(double distanceToDestination)

    /** Something other than moving closer counts as progress (e.g. blocks were just broken): back to FREE. */
    public void markProgress()

    /** Not trying to move any more (arrived, biting, new target...): back to FREE and forget the baseline. */
    public void reset()

    public Level getLevel()

    /** +1.0 or -1.0: which way a sidestep currently goes. It flips every STUCK_SIDESTEP_SWITCH_TICKS of being stuck. */
    public double getSidestepSign()
}
```

## Behavior (fields: `int stuckTicks`, `int ticksSinceCheck`, `double distanceAtCheck`, `boolean hasBaseline`)
`tick(distance)`:
1. If `!hasBaseline`: `distanceAtCheck = distance; ticksSinceCheck = 0; hasBaseline = true;`.
2. `ticksSinceCheck++`.
3. If `ticksSinceCheck >= STUCK_CHECK_INTERVAL_TICKS.get()`: it is a check tick.
   - progressed = `distanceAtCheck - distance >= STUCK_MIN_PROGRESS.get()` (it got at least that much closer since the last check).
   - If progressed: `stuckTicks = 0;` else: `stuckTicks += ticksSinceCheck;`
   - Then `distanceAtCheck = distance; ticksSinceCheck = 0;`.
4. Return `getLevel()`.

`getLevel()` compares `stuckTicks` against the thresholds from the LARGEST down (so a mis-ordered config still returns something sensible):
`>= STUCK_GIVE_UP_AFTER_TICKS` -> GIVE_UP; else `>= STUCK_SIDESTEP_AFTER_TICKS` -> SIDESTEP; else `>= STUCK_HOP_AFTER_TICKS` -> HOP;
else `>= STUCK_BREAK_AFTER_TICKS` -> BREAK; else FREE.

`markProgress()`: `stuckTicks = 0; hasBaseline = false;`.
`reset()`: same as `markProgress()` (also `ticksSinceCheck = 0`).

`getSidestepSign()`: `int phase = Math.max(0, stuckTicks - STUCK_SIDESTEP_AFTER_TICKS.get()) / STUCK_SIDESTEP_SWITCH_TICKS.get();`
return `phase % 2 == 0 ? 1.0 : -1.0`.

Worked example with defaults (check every 10 ticks, min progress 0.5, break 20 / hop 60 / sidestep 100 / give up 220, switch 40):
feeding the same distance 50.0 every tick returns FREE for ticks 1-19, BREAK for 20-59, HOP for 60-99, SIDESTEP from tick 100 (sign +1 until
tick 139, -1 from tick 140 to 179, +1 from tick 180), GIVE_UP from tick 220. Feeding a distance that drops by at least 0.5 every 10 ticks returns FREE.

## DO NOT TOUCH
`.git/`, every existing file, `build.gradle`, `gradle.properties`, the `client/` package, everything under `tasks/` and `Docs/`.
No imports from `net.minecraft` or `net.neoforged` (only `com.dinodread.config.RexConfig`). No numeric literals other than
`0`, `1`, `2`, `0.0`, `1.0`, `-1.0`.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- GameTest `dinodread:trex_stuck_detector_escalates` (Claude runs it) follows the worked example above using the config defaults.
