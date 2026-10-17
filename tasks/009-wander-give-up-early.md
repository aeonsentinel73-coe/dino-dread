# Task 009 — Wander abandons a blocked destination at once instead of after the full stuck ladder

**Why:** logs of the forest-wander GameTest show the wandering T-rex pushing against a tree or fence for 400+ ticks: wander used the hunt's
whole escalation ladder (break -> hop -> sidestep -> give up after `stuckGiveUpAfterTicks` = 220 ticks of no progress) before picking another destination,
so most of its time went on walls it could never get through (wander never breaks blocks), and it roamed far less than intended. For a casual wander the right
reaction to "blocked" is simply to pick another direction.

**Goal:** `TRexWanderGoal` drops its destination as soon as `RexMover` reports any stuck level, not only `GIVE_UP`.

Working dir = THIS repo (the worktree you were given). **You cannot run Gradle (bash is off); Claude builds afterwards.** Do not search the
filesystem or grep for Minecraft symbols; everything needed is in the file you edit.

## File to EDIT (only this one)
`src/main/java/com/dinodread/entity/ai/TRexWanderGoal.java`

## Change
In `tick()`, the existing check
`if (mover.driveTo(destination, RexConfig.WANDER_SPEED_MULTIPLIER.get(), false, false) == RexStuckDetector.Level.GIVE_UP) { destination = null; }`
becomes
`if (mover.driveTo(destination, RexConfig.WANDER_SPEED_MULTIPLIER.get(), false, false) != RexStuckDetector.Level.FREE) { destination = null; }`
with this comment above it: `// Blocked for stuckBreakAfterTicks without getting closer: not worth the hunt's whole hop/sidestep ladder, just pick another direction.`
(The goal then ends, because `canContinueToUse()` needs a destination, and `stop()` resets the mover.)

Update the class javadoc: replace the sentence "...picking another when it arrives or gets stuck." by "...picking another when it arrives or is blocked for a moment (`stuckBreakAfterTicks`)." — keep the rest of the javadoc.

## DO NOT TOUCH
`.git/`, every other file (in particular `RexMover`, `RexStuckDetector`, `TRexHuntGoal`, `RexConfig`, `gametest/`, `tasks/`, `Docs/`). No new numeric literals, no new config.

## Acceptance check
- `.\gradlew build` passes (Claude runs it).
- GameTests (Claude runs them repeatedly): `trex_wanders` and `trex_wanders_through_a_forest` pass, and every other GameTest still passes.

> **Outcome: tried and reverted.** Implemented exactly as written, but in the forest-wander GameTest it made wandering worse (10 of 17 samples missed the old 40-block threshold, versus 4 of 17 before): each early abandon costs a new ~240-tick wait for the next wander start, so the rex roams less. The real cause of the slow wander was task 010.
