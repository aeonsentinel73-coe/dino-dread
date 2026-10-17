# Task 006 — Wire block breaking and stuck recovery into `TRexHuntGoal`

**Goal:** make the hunting T-rex clear its own way: while it drives toward its destination it breaks allow-listed blocks it
is pushing against (`RexBlockBreaker`), and when it stops making progress it escalates through hopping, sidestepping and finally
giving up (`RexStuckDetector`). Only `TRexHuntGoal.java` changes.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`.
**You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the filesystem for Minecraft sources; every
symbol you need is listed here (all verified for Minecraft 26.3 / NeoForge 26.3.0.85) or already used in the file you edit.

## File to EDIT (only this one)
`src/main/java/com/dinodread/entity/ai/TRexHuntGoal.java`

Already written (read them first, do not change them): `RexBlockBreaker` (`tick(Vec3 heading, boolean breakNow)` returns the number
of blocks broken; heading = unit horizontal direction it pushes in), `RexStuckDetector` (`tick(double distance)` returns its `Level`;
`markProgress()`, `reset()`, `getSidestepSign()`), config fields `RexConfig.STUCK_HOP_VELOCITY` and `RexConfig.STUCK_HOP_FORWARD_VELOCITY`
(both `DoubleValue`), `RexConfig.STUCK_SIDESTEP_DISTANCE` (`DoubleValue`), `TRexSenses.forget()`.

## Changes

### 1. Fields and constructor
- `private final RexBlockBreaker breaker;` and `private final RexStuckDetector stuck = new RexStuckDetector();`
- Constructor: `this.breaker = new RexBlockBreaker(rex);` (after `this.rex = rex;`).
- `private static final double MIN_HEADING_DISTANCE = 1.0E-3;` (below this the destination is "here" and there is no heading).

### 2. `start()` and `stop()`: add `stuck.reset();` to both.

### 3. In `tick()`
- Compute `boolean inBiteRange = visible && rex.isInBiteRange(target);` once, just before the "arrival" check
  (`target` is non-null whenever `visible` is true; keep `@Nullable` handling as in the file).
- In the existing early return for "INVESTIGATE and arrived" (the `if (state == HuntState.INVESTIGATE && horizontalDistanceSqr <= ...)` block)
  add `stuck.reset();` before `return;`.
- Change the final call to `driveTo(destination, speed, inBiteRange);`.

### 4. Replace `driveTo(Vec3 destination, double speed)` by `driveTo(Vec3 destination, double speed, boolean inBiteRange)` with this behavior, in order:
1. `Vec3 toDestination = destination.subtract(rex.position()); double distance = toDestination.horizontalDistance();`
   `boolean hasHeading = distance > MIN_HEADING_DISTANCE;`
   `Vec3 heading = hasHeading ? new Vec3(toDestination.x / distance, 0.0, toDestination.z / distance) : Vec3.ZERO;`
2. Stuck level: if `inBiteRange || !hasHeading` call `stuck.reset()` and use `RexStuckDetector.Level.FREE`; otherwise
   `level = stuck.tick(distance)`.
3. If `level == RexStuckDetector.Level.GIVE_UP`: `stuck.reset(); rex.getSenses().forget(); rex.getNavigation().stop(); return;`
   (it abandons the hunt; the wander goal takes over).
4. Hop: if `level == RexStuckDetector.Level.HOP && rex.onGround()` then
   `rex.setDeltaMovement(heading.x * RexConfig.STUCK_HOP_FORWARD_VELOCITY.get(), RexConfig.STUCK_HOP_VELOCITY.get(), heading.z * RexConfig.STUCK_HOP_FORWARD_VELOCITY.get());`
   (`Entity.setDeltaMovement(double, double, double)`, `Entity.onGround()`).
5. Sidestep: `Vec3 wanted = destination; Vec3 pushHeading = heading;` and if `level == RexStuckDetector.Level.SIDESTEP`:
   `double sign = stuck.getSidestepSign(); pushHeading = new Vec3(-heading.z * sign, 0.0, heading.x * sign);`
   `wanted = new Vec3(rex.getX() + pushHeading.x * RexConfig.STUCK_SIDESTEP_DISTANCE.get(), rex.getY(), rex.getZ() + pushHeading.z * RexConfig.STUCK_SIDESTEP_DISTANCE.get());`
6. Breaking: `int broken = breaker.tick(pushHeading, rex.horizontalCollision || level != RexStuckDetector.Level.FREE);`
   (`Entity.horizontalCollision` is a public boolean field.) `if (broken > 0) { stuck.markProgress(); }`
7. Steering: exactly the existing code (the `usePathfinding` false branch with `setWantedPosition`, and the pathfinding branch with its
   `pathRecalcTicks`, `moveTo` and `isDone()` fallback), but using `wanted.x/.y/.z` instead of `destination.x/.y/.z` everywhere.

### 5. Class javadoc
Replace the last sentence ("Movement uses vanilla navigation with a straight-line fallback; wide-body pathing is milestone 4.") with:
"Movement steers straight at the destination; the T-rex is far too wide for vanilla pathfinding, so it clears its way by breaking
allow-listed blocks ({@link RexBlockBreaker}) and, when it stops making progress, hops, sidesteps and finally gives up ({@link RexStuckDetector})."

## DO NOT TOUCH
`.git/`, every file other than `TRexHuntGoal.java` (in particular `RexBlockBreaker`, `RexStuckDetector`, `RexConfig`, `TRexSenses`,
`TRexWanderGoal`, `TRex`, anything under `gametest/`, `client/`, `tasks/`, `Docs/`). Do not change state-machine logic (`updateState`, `endCharge`,
`tickBite`). No new numeric literals except `MIN_HEADING_DISTANCE`, `0.0`.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- GameTests (Claude runs them): `trex_breaks_into_wooden_house`, `trex_pushes_through_trees`, `trex_leaves_stone_house_alone`,
  `trex_respects_mob_griefing_off`, `trex_climbs_ledge`, plus the earlier ones (`trex_hunts_player` etc.) all pass.
