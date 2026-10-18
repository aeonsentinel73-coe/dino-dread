# Task 013 — `TRexPreyGoal`: the T-rex occasionally goes for an animal and eats it

**Why (Thomas):** when no player is around the rex should now and then attack peaceful mobs (pigs and such), "like it's eating".

**Goal:** a new goal that, with no player detected, now and then picks a nearby animal, walks up to it, bites it dead, and stands over the kill for a while.

Working dir = THIS repo (the worktree you were given). Java 25, package `com.dinodread`.
**You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the filesystem or grep for Minecraft symbols; every symbol you need is listed
here (verified for Minecraft 26.3) or already used in `TRexHuntGoal.java` / `TRexWanderGoal.java`.

## Files
- CREATE `src/main/java/com/dinodread/entity/ai/TRexPreyGoal.java`
- EDIT `src/main/java/com/dinodread/entity/TRex.java` (`registerGoals` only)

Style references: `TRexWanderGoal.java` (a `Goal` using `RexMover`; same constructor rule) and `TRexHuntGoal.java` (bite: `rex.swingForAttack(InteractionHand.MAIN_HAND)`, `rex.doHurtTarget(level, target)`, `rex.isInBiteRange(entity)`).
Config fields already in `RexConfig` (all `.get()`): `PREY_ENABLED` (boolean), `PREY_INTERVAL_TICKS` (int), `PREY_RANGE` (double), `PREY_SPEED_MULTIPLIER` (double),
`PREY_EAT_TICKS` (int), `PREY_MAX_CHASE_TICKS` (int), and the existing `BITE_COOLDOWN_TICKS` (int).

## `TRexPreyGoal` (public class, `extends net.minecraft.world.entity.ai.goal.Goal`, constructor `TRexPreyGoal(TRex rex)`)
- **The constructor runs from the `Mob` constructor, before `TRex`'s own fields exist: only store `rex`, build `new RexMover(rex)`, and `setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK))`. Call no method on `rex` there.**
- Fields: `private final TRex rex; private final RexMover mover; private @Nullable Animal prey; private @Nullable Vec3 eatSpot; private int chaseTicks; private int biteCooldownTicks; private int eatTicksLeft;`
  plus `private static final float LOOK_SPEED_DEGREES = 30.0F;`.
- `canUse()`: `RexConfig.PREY_ENABLED.get() && !rex.getSenses().hasQuarry() && rex.getRandom().nextInt(reducedTickDelay(RexConfig.PREY_INTERVAL_TICKS.get())) == 0`, and only then
  `prey = findPrey(); return prey != null;` (assign the field in `canUse`, like vanilla goals do). Evaluate the cheap conditions first so the entity search only runs when the dice say so.
- `canContinueToUse()`: `!rex.getSenses().hasQuarry() && (eatTicksLeft > 0 || (prey != null && prey.isAlive() && chaseTicks < RexConfig.PREY_MAX_CHASE_TICKS.get()))`.
- `start()`: `mover.reset(); chaseTicks = 0; biteCooldownTicks = 0; eatTicksLeft = 0; eatSpot = null;`
- `stop()`: `mover.reset(); prey = null; eatSpot = null; eatTicksLeft = 0;`
- `tick()`, in this order:
  1. If `eatTicksLeft > 0`: `eatTicksLeft--;` if `eatSpot != null` look at it with `rex.getLookControl().setLookAt(eatSpot.x, eatSpot.y, eatSpot.z, LOOK_SPEED_DEGREES, LOOK_SPEED_DEGREES)`; `return;` (it stands still).
  2. If `prey == null` return.
  3. `chaseTicks++; if (biteCooldownTicks > 0) { biteCooldownTicks--; }` and `rex.getLookControl().setLookAt(prey, LOOK_SPEED_DEGREES, LOOK_SPEED_DEGREES)` (`LookControl.setLookAt(Entity, float, float)`).
  4. If `rex.isInBiteRange(prey)`: `mover.reset();` then, if `biteCooldownTicks == 0 && rex.level() instanceof ServerLevel level`: `rex.swingForAttack(InteractionHand.MAIN_HAND); rex.doHurtTarget(level, prey); biteCooldownTicks = RexConfig.BITE_COOLDOWN_TICKS.get();`
     and `if (!prey.isAlive()) { eatSpot = prey.position(); eatTicksLeft = RexConfig.PREY_EAT_TICKS.get(); }`. Then `return;`.
  5. Otherwise: `if (mover.driveTo(prey.position(), RexConfig.PREY_SPEED_MULTIPLIER.get(), false, false) == RexStuckDetector.Level.GIVE_UP) { prey = null; }`
     (`allowBreaking` is false: eating never breaks blocks).
- `private @Nullable Animal findPrey()`: `List<Animal> animals = rex.level().getEntitiesOfClass(Animal.class, rex.getBoundingBox().inflate(RexConfig.PREY_RANGE.get()), this::isPrey);`
  return the one with the smallest `rex.distanceToSqr(animal)` (null if the list is empty). (`Level.getEntitiesOfClass(Class<T>, AABB, Predicate<? super T>)`.)
- `private boolean isPrey(Animal animal)`: `animal.isAlive() && !animal.hasCustomName() && !animal.isLeashed()` and NOT tamed/owned, i.e. not
  `(animal instanceof OwnableEntity ownable && ownable.getOwnerReference() != null)`.
- Imports (all verified): `java.util.EnumSet`, `java.util.List`, `org.jspecify.annotations.Nullable`, `com.dinodread.config.RexConfig`, `com.dinodread.entity.TRex`,
  `net.minecraft.server.level.ServerLevel`, `net.minecraft.world.InteractionHand`, `net.minecraft.world.entity.OwnableEntity`, `net.minecraft.world.entity.ai.goal.Goal`,
  `net.minecraft.world.entity.animal.Animal`, `net.minecraft.world.phys.Vec3`. (`RexMover` and `RexStuckDetector` are in the same package.)
- Never call `rex.setTarget(...)`: a target would make `hasQuarry()` true and switch the hunt goal on.
- Class javadoc: one paragraph: occasionally, with no player detected, it picks an animal within `preyRange`, walks up (no block breaking), bites it and stands over the kill for `preyEatTicks`; tamed, leashed and name-tagged animals are left alone; a detected player always takes over (the hunt goal has higher priority and `hasQuarry()` ends this goal).

## `TRex.registerGoals()`
Change the goal list to exactly:
```java
goalSelector.addGoal(0, new FloatGoal(this));
goalSelector.addGoal(1, new TRexHuntGoal(this));
goalSelector.addGoal(2, new TRexPreyGoal(this));
goalSelector.addGoal(3, new TRexWanderGoal(this));
goalSelector.addGoal(4, new RandomLookAroundGoal(this));
```
and add `import com.dinodread.entity.ai.TRexPreyGoal;` (keep the imports in the existing alphabetical order). Touch nothing else in `TRex.java`.

## DO NOT TOUCH
`.git/`, every other file (in particular `RexMover`, `RexStuckDetector`, `TRexHuntGoal`, `TRexWanderGoal`, `TRexSenses`, `RexConfig`, `gametest/`, `tasks/`, `Docs/`). No new config, no new numeric literals except `LOOK_SPEED_DEGREES`.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- GameTests: `trex_eats_a_pig_when_no_player_is_near` (the rex kills a pig 20 blocks away) and `trex_prefers_the_player_to_a_pig` pass, and every earlier GameTest still passes.
