# Task 008 — Wander with the shared `RexMover` instead of vanilla pathfinding

**Goal:** make the T-rex's idle roaming work for its 9 x 12 body. Vanilla `RandomStrollGoal` + `PathNavigation` cannot path a body
this wide (it freezes in forests and bounded areas), so `TRexWanderGoal` becomes a small goal of its own: pick a random point,
walk straight at it with `RexMover` (no block breaking while wandering), pick another when it arrives or gets stuck.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`.
**You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the filesystem for Minecraft sources or grep
for Minecraft symbols; everything you need is listed here or already used in `TRexHuntGoal.java`.

## Files
- REWRITE `src/main/java/com/dinodread/entity/ai/TRexWanderGoal.java`
- EDIT `src/main/java/com/dinodread/config/RexConfig.java` (one new field)
- EDIT `src/main/resources/assets/dinodread/lang/en_us.json` (one new key)

Reference to copy the style from: `src/main/java/com/dinodread/entity/ai/TRexHuntGoal.java` (a `Goal` with `setFlags`, `canUse`, `canContinueToUse`,
`start`, `stop`, `tick`) and `RexMover.java` (the `driveTo(Vec3 destination, double speed, boolean standingStill, boolean allowBreaking)` and `reset()` you call).

## 1. `RexConfig`: add one field
In the `behavior` section, right after `WANDER_RANGE`: field `public static final ModConfigSpec.DoubleValue WANDER_ARRIVAL_DISTANCE;` (declare it next to the other
`public static final` fields, after `WANDER_RANGE`), assigned in the static block:
`WANDER_ARRIVAL_DISTANCE = BUILDER.comment("How close, in blocks, to its wander destination counts as having arrived.").defineInRange("wanderArrivalDistance", 6.0, 1.0, 32.0);`
(same formatting as the neighbouring entries).

## 2. Lang: add `"dinodread.configuration.wanderArrivalDistance": "Wander Arrival Distance",` right after the existing `"dinodread.configuration.wanderRange"` line (keep the JSON valid).

## 3. `TRexWanderGoal` (rewrite the whole file)
```java
public class TRexWanderGoal extends Goal {
    public TRexWanderGoal(TRex rex)
```
- It extends `net.minecraft.world.entity.ai.goal.Goal` (NOT `RandomStrollGoal` any more). Imports: `java.util.EnumSet`, `org.jspecify.annotations.Nullable`, `com.dinodread.config.RexConfig`, `com.dinodread.entity.TRex`,
  `net.minecraft.world.entity.ai.goal.Goal`, `net.minecraft.world.phys.Vec3`.
- Fields: `private final TRex rex; private final RexMover mover; private @Nullable Vec3 destination;`
- Constructor: store `rex`, `this.mover = new RexMover(rex);`, `this.setFlags(EnumSet.of(Goal.Flag.MOVE));`. **The constructor runs from the `Mob` constructor (before `TRex`'s own fields exist), so it must not call any method on `rex`** (only store it and pass it to `RexMover`, which only stores it too).
- `canUse()`: `return !rex.getSenses().hasQuarry() && rex.getRandom().nextInt(RexConfig.WANDER_INTERVAL_TICKS.get()) == 0;` (`Entity.getRandom()` returns a `RandomSource`; `nextInt(int)` is valid).
- `canContinueToUse()`: `return destination != null && !rex.getSenses().hasQuarry();`
- `start()`: `mover.reset(); destination = pickDestination();`
- `stop()`: `mover.reset(); destination = null;`
- `tick()`: if `destination == null` return. Let `double arrival = RexConfig.WANDER_ARRIVAL_DISTANCE.get();` If `rex.position().subtract(destination).horizontalDistanceSqr() <= arrival * arrival`, set `destination = null` and return.
  Otherwise: `if (mover.driveTo(destination, RexConfig.WANDER_SPEED_MULTIPLIER.get(), false, false) == RexStuckDetector.Level.GIVE_UP) { destination = null; }`
  (`RexStuckDetector` is in the same package. `allowBreaking` is false: wandering never breaks blocks.)
- `private Vec3 pickDestination()`: a random point on a horizontal ring around the rex, same height as the rex:
  `double range = RexConfig.WANDER_RANGE.get(); double angle = rex.getRandom().nextDouble() * 2.0 * Math.PI; double distance = range / 2.0 + rex.getRandom().nextDouble() * range / 2.0;`
  return `new Vec3(rex.getX() + Math.cos(angle) * distance, rex.getY(), rex.getZ() + Math.sin(angle) * distance)`.
- Class javadoc: one short paragraph saying why it is not a vanilla stroll (too wide for pathfinding; keeps roaming with nobody near, picks far destinations) and that wandering never breaks blocks.
- `TRex.registerGoals()` already does `goalSelector.addGoal(2, new TRexWanderGoal(this));` and needs no change.

## DO NOT TOUCH
`.git/`, every other file (in particular `TRex`, `TRexHuntGoal`, `RexMover`, `RexBlockBreaker`, `RexStuckDetector`, `TRexSenses`, `gametest/`, `client/`, `tasks/`, `Docs/`). Do not change any other config default.
No numeric literals other than `0`, `2.0`, `Math.PI`.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- GameTests (Claude runs them, repeatedly): `trex_wanders` and `trex_wanders_through_a_forest` pass, and every other GameTest still passes.
