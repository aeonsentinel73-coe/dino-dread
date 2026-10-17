# Task 010 — Wander start chance must compensate for the goal selector's 2-tick cadence

**Why:** `RexConfig.WANDER_INTERVAL_TICKS` is documented as "Average ticks between choosing a new wander destination" (default 120), but
`Mob` only evaluates `canUse()` of a non-running goal every second tick, so `nextInt(interval) == 0` fires on average every ~240 ticks. Vanilla's
`RandomStrollGoal` avoids this with `reducedTickDelay(interval)`. (Measured in the forest-wander GameTest: only ~3 wander starts in 3000 ticks, not ~12.)

**Goal:** make the average really be `wanderIntervalTicks`.

Working dir = THIS repo. **You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the filesystem or grep for Minecraft symbols.

## File to EDIT (only this one)
`src/main/java/com/dinodread/entity/ai/TRexWanderGoal.java`

## Change
In `canUse()`, replace `rex.getRandom().nextInt(RexConfig.WANDER_INTERVAL_TICKS.get()) == 0` by
`rex.getRandom().nextInt(reducedTickDelay(RexConfig.WANDER_INTERVAL_TICKS.get())) == 0`.
`reducedTickDelay(int)` is `protected static int` declared in `net.minecraft.world.entity.ai.goal.Goal` (the class you extend), so no import is needed.
Add the comment line above the return: `// canUse runs every second tick, so halve the interval to keep the configured average.`

## DO NOT TOUCH
`.git/`, every other file. No new config, no new literals.

## Acceptance check
`.\gradlew build` passes; `trex_wanders` and `trex_wanders_through_a_forest` still pass (Claude measures how often the forest test reaches its distance).
