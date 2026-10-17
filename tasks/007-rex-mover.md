# Task 007 — Extract the T-rex's steering into a shared `RexMover` (pure refactor)

**Goal:** move the "drive toward a destination, clearing the way" logic out of `TRexHuntGoal.driveTo` into a new class
`RexMover` so the wander goal can reuse it in task 008. **Behavior must not change**: all existing GameTests that pass today must still pass.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`.
**You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the filesystem for Minecraft sources or
grep for Minecraft symbols: this is a pure move of code that already compiles; every symbol you need is already in `TRexHuntGoal.java`.

## Files
- CREATE `src/main/java/com/dinodread/entity/ai/RexMover.java`
- EDIT `src/main/java/com/dinodread/entity/ai/TRexHuntGoal.java`

## `RexMover` (exact surface)
```java
public final class RexMover {
    public RexMover(TRex rex)   // creates its own RexBlockBreaker(rex) and RexStuckDetector

    /**
     * Steers the T-rex toward {@code destination} this tick. {@code standingStill} = it is where it wants to be (e.g. in bite
     * range): the stuck detector is reset instead of fed. {@code allowBreaking} = it may break allow-listed blocks it pushes against.
     * Returns the stuck level this tick; {@link RexStuckDetector.Level#GIVE_UP} means it did NOT steer, the mover has reset itself,
     * and the caller must abandon this destination.
     */
    public RexStuckDetector.Level driveTo(Vec3 destination, double speed, boolean standingStill, boolean allowBreaking)

    /** Forget all stuck state (call when a goal starts, stops, or arrives). */
    public void reset()
}
```
The body of `driveTo` is the CURRENT body of `TRexHuntGoal.driveTo(Vec3, double, boolean)` moved over unchanged, with these differences only:
- `inBiteRange` is renamed `standingStill`.
- On `GIVE_UP` it does `stuck.reset(); return RexStuckDetector.Level.GIVE_UP;` and does NOT call `rex.getSenses().forget()` or `rex.getNavigation().stop()` (the hunt goal does those).
- The `breaker.tick(...)` call (and the `markProgress()` after it) only happens when `allowBreaking` is true.
- At the end it returns `level`.
- `MIN_HEADING_DISTANCE`, `PATH_RECALC_INTERVAL_TICKS` and the field `pathRecalcTicks` move into `RexMover` (including the whole `usePathfinding` branch with `moveTo` / `isDone()` fallback); `reset()` also sets `pathRecalcTicks = 0`.
- Fields: `private final TRex rex; private final RexBlockBreaker breaker; private final RexStuckDetector stuck = new RexStuckDetector(); private int pathRecalcTicks;`
Copy the exact imports it needs from `TRexHuntGoal.java`. Add a one-paragraph class javadoc saying it is the shared steering for hunt and wander.

## `TRexHuntGoal` changes
- Remove the fields `breaker`, `stuck` (and the `breaker` construction in the constructor), the constants `PATH_RECALC_INTERVAL_TICKS` and `MIN_HEADING_DISTANCE`, the field `pathRecalcTicks`, and the whole `driveTo` method.
- Add `private final RexMover mover;` constructed in the constructor: `this.mover = new RexMover(rex);`
- Everywhere it called `stuck.reset()` (in `start()`, `stop()` and the INVESTIGATE-arrival early return) call `mover.reset()` instead; `start()` no longer sets `pathRecalcTicks`.
- The last line of `tick()` becomes:
  `if (mover.driveTo(destination, speed, inBiteRange, true) == RexStuckDetector.Level.GIVE_UP) { rex.getSenses().forget(); rex.getNavigation().stop(); }`
- Remove imports that are no longer used. Update the class javadoc reference only if it now points at something removed (keep the `{@link RexBlockBreaker}` / `{@link RexStuckDetector}` links; add `{@link RexMover}` where it says it clears its way).

## DO NOT TOUCH
`.git/`, every other file (in particular `RexBlockBreaker`, `RexStuckDetector`, `RexConfig`, `TRexSenses`, `TRexWanderGoal`, `TRex`, `gametest/`, `client/`, `tasks/`, `Docs/`).
Do not change any logic, number, or ordering of operations in the moved code.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- All GameTests that passed before still pass (house, trees, forest, ledges, stone house, mobGriefing off, hunt, ...). The only GameTest allowed to fail is `trex_wanders_through_a_forest`.
