# M4 delegation log (experiment: the local Qwen3.8-27B writes the M4 implementation)

M4 ("big body") is marked Claude-owned in CLAUDE.md/PLAN.md. Thomas overrode that for this milestone to see how far the local model gets.
Roles: Claude = design, specs, acceptance GameTests (written FIRST), builds and test runs, symbol checks, review. Qwen (via
`mcp__local-vibes__delegate`, bash off, so it cannot build or test) = implementation. Branch `m4-big-body`, cut from main 8170fa4.
Wall time per delegate call was not measured (the calls were short, minutes at most).

## Summary table

| Piece | Spec (task file) | Qwen output | Rounds | Outcome | Claude took over |
|---|---|---|---|---|---|
| P1 config entries + lang + stepHeight default | `003-m4-config.md` (~95 lines) | ~110 lines in 2 files | 1 (13 iterations) | Exactly as specified | no |
| P2 `RexBlockBreaker` | `004-block-breaker.md` (~85 lines) | 170-line class | 2 | Round 1: 40 iterations, no file written, no summary. Round 2 (strict step-by-step procedure + "do not search"): 6 iterations, exactly as specified | no |
| P3 `RexStuckDetector` | `005-stuck-detector.md` (~65 lines) | 75-line class | 1 (5 iterations) | Exactly as specified | no |
| P4 hunt goal integration | `006-hunt-integration.md` (~70 lines) | +54/-6 lines in `TRexHuntGoal` | 1 (23 iterations) | Exactly as specified; all acceptance tests green on the first run | no |
| P5a extract `RexMover` (refactor) | `007-rex-mover.md` (~50 lines) | 100-line class, -69 lines in the goal | 1 (6 iterations) | Behaviour-preserving, all tests unchanged | no |
| P5b wander via `RexMover` | `008-wander-mover.md` (~55 lines) | 74-line goal + 1 config field + 1 lang key | 1 (8 iterations) | Exactly as specified | no |

Claude-owned (not delegated): the acceptance tests (`gametest/RexObstacleGameTests.java`, ~595 lines, 11 tests), a small refactor of `RexGameTests`
helpers to package-private (+ a `structure` hook), the one-line hitbox fix in `TRex`, and registering the tests in `DinoDread`. No piece needed the
"Claude took over" fallback.

## Per piece: what Qwen got wrong, effort

- **P1**: nothing wrong. Pure transcription of tables (field names, keys, defaults, ranges, comments) into the existing pattern. Spec was about as long as the diff.
- **P2**: the only real failure of the experiment. Round 1 ran the full 40 iterations and ended with "reached max iterations without calling finish"; no file
  existed afterwards. The transcript is not visible; the likely cause is that it kept looking for Minecraft sources/symbols to verify instead of writing
  (the same spec had worked for P1). Round 2 prepended an explicit PROCEDURE ("read these 3 files, then write the whole file in one go, do NOT search the
  filesystem, finish in <15 tool calls") and it wrote a correct file in 6 iterations. Every symbol it used (`Identifier.tryParse`, `TagKey.create(Registries.BLOCK, id)`,
  `BuiltInRegistries.BLOCK.getOptional`, `EventHooks.canEntityGrief`, `BlockPos.betweenClosed(6 ints)` + `.immutable()`, `Level.destroyBlock`) was one the spec listed;
  it did not invent or use an old-version name. Nit: the allow-list cache is static mutable state (fine on the server thread).
- **P3**: nothing wrong (plain Java, specified as pseudocode with a worked example).
- **P4**: nothing wrong. The spec gave the steering as an ordered 7-step list; Qwen followed it literally, including keeping the old `usePathfinding` branch.
- **P5a/P5b**: nothing wrong. The refactor is a good local-model job: spec much shorter than the diff and checkable by the existing tests.
- Instruction-following slips seen across the experiment: only P2 round 1 (above), and in a read-only design probe Qwen opened `TRexHuntGoal` (which I had allowed)
  that already contains the integration, so that probe was contaminated, not blind.

Effort: writing each spec at this precision cost Claude roughly as many tokens as the code it produced (P1-P4: spec lines ~ code lines). The saving from delegating is
typing plus Qwen's own re-read pass, not thinking; it is clearly positive only for refactors/transcription where the spec is shorter than the diff (P5a), and for
parallelism (Claude waits, nothing else). Review cost per piece was small because each diff matched its spec line by line.

## Where Claude's work was decisive (things the experiment would have missed without it)

1. **The premise was false: the rex hitbox was 3x4, not 9x12.** The `scale` attribute comes from the attribute defaults and never fires the "scale changed" update that
   resizes the hitbox, so the model rendered 3x but collisions, bite range and the M3 GameTests ran on the unscaled 3 x 4 box. Found by logging `getBbWidth()` while writing the
   first test (all four obstacle tests "failed" with the rex 1.5 blocks into the wall). Fixed with `refreshDimensions()` in the `TRex` constructor (4 lines incl. comment).
   Not seen in a real client: whether the same happens there (it should: same constructor path) and what the 9x12 box does to spawn-egg placement, hit detection and the camera.
2. **The design points that make the stuck layer work** (all in the specs, none found by Qwen): progress = distance to the destination closed (so a sidestep is not progress),
   breaking a block counts as progress (a thick wall is not "stuck"), the detector is reset when the rex is in bite range or has arrived, `stepHeight` default 3.0 so ordinary
   ledges are stepped and the hop is only the fallback.
3. **Test-fixture diagnosis**: the forest-wander test first failed intermittently because the rex wandered out of the flat arena into natural terrain; after fencing the arena it failed
   100% because vanilla `PathNavigation` could not path the 9-wide body at all (nav done, no path). That became the acceptance test for P5.
4. **Blind design probe** (Qwen asked for a stuck-detection/recovery design without the spec; contaminated, see above): its design OR-ed three stuck signals (false-positive prone),
   proposed forcing the breaker every tick (violates the rate limit), used a 1.2 hop velocity (~9 blocks), and suggested no suspension in water. Good ideas worth keeping:
   suspend the detector in water/lava, and a short per-destination cooldown after giving up so the rex does not instantly re-target the same noise (not implemented).

## Acceptance results
- 17 GameTests (5 earlier + the framework default + 11 new: wooden house, wooden house at an angle, trees, forest, 3- and 4-block ledges (the 4-block one needs the hop), stone house, mobGriefing off,
  block breaker rules, stuck detector escalation, forest wander). Against the unchanged M3 rex the 4 behaviour tests failed for the right reasons (blocked at wall/trees/ledge, frozen at stone).
- Final: `.\gradlew runGameTestServer --rerun-tasks` run 6 times in a row after the last change: 6/6 runs, 17/17 tests each (earlier code states: 5/5 with 13 tests, then 3/5 on the first unfenced fixture of the forest-wander test and 0/8 on the fenced one before P5, see above). `.\gradlew build` green; `.\gradlew runServer` reaches `Done` with no client-class errors.

## Follow-up: the `trex_wanders_through_a_forest` flake (found by an independent 5x run, ~1 in 6 failing)
- **Diagnosis (Claude):** 16 temporary copies of the test in one run plus per-100-tick state logs (position, idle ticks, running goals, number of wander starts, `hasQuarry`).
  It was **not a stall**: `hasQuarry` was never true, the goal kept restarting, nothing froze. The rex simply roamed slowly: about 3 wander starts in 3000 ticks instead of ~12,
  with each wander typically ending by pushing against a tree or the fence for the whole stuck ladder. The 40-block threshold sat right at the edge of that slow distribution
  (reaching it took 1750-2800 ticks of a 3000 budget, ~4 of 17 samples missed).
- **A real bug, mine (spec 008):** `wanderIntervalTicks` is documented as "average ticks between wander destinations" (120), but `Mob` only evaluates a non-running goal's `canUse()`
  every second tick, so `nextInt(120) == 0` fired on average every ~240 ticks. Vanilla `RandomStrollGoal` compensates with `reducedTickDelay(interval)`; my spec for task 008 did not.
  Fixed by task `010-wander-interval.md` (Qwen, 1 round, 4 iterations, exactly as specified): `nextInt(reducedTickDelay(interval))`.
- **A wrong hypothesis, tried and reverted:** task `009-wander-give-up-early.md` made wander drop a blocked destination at the first stuck level instead of after the whole ladder.
  Qwen implemented it exactly (1 round, 4 iterations) but the measured result was worse (10 of 17 samples missed the old threshold): quick abandons just add more ~240-tick waits.
  Reverted; the spec file is kept and marked as such.
- **Test made bounded instead of lowering a number:** the test now passes when the rex has been in 4 different 6x6-block areas (it left its start and made several separate trips),
  with a 12000-tick budget. Measured over 29 samples after the fix (17 + 12 suite runs): 4 areas reached after 232-2837 ticks (median ~450, one slow outlier at 2837), so the budget is over 4x the worst (it was 6000 in the first 12-run batch, raised after that outlier and the 3 extra runs below); a rex that cannot move
  (the earlier vanilla-pathfinding freeze) stays in one area and fails.
- Unseen-in-client risks of the real 9x12 hitbox (spawn-egg placement, bite reach, eye height, step height, pushing, break performance) are listed under "Risks to check by eye" below.
- **Result:** the full suite, 12 consecutive (budget 6000) plus 3 more (budget 12000), i.e. 15 consecutive `.\gradlew runGameTestServer --rerun-tasks` runs after the last code change: 12/12 runs green, 17/17 tests each.

## Risks to check by eye (real 9x12 hitbox; none of this was seen in a real client)
Now that the hitbox really is 9 wide x 12 tall (it was 3 x 4 for all of M1-M3, so nobody has played it this way): **spawn eggs** place a 9x12 box with no obstruction check, so an egg used in a
house, forest or cave spawns the rex inside blocks (suffocation damage, stuck), spawn it in the open; **bite reach** (`biteReach` = 3) is measured from the hitbox edge, so it now bites
players up to ~7.5 blocks from its centre and as high as 15 blocks up (a player on a tree or roof is in reach), while **charge/sight/hearing distances** are still measured from its centre;
**eye height** is now 10.8 blocks (it was 3.6), so it sees over fences and low buildings but its line of sight to players inside houses is blocked by roofs, and M3's sight tuning was done on the small box;
**step height 3.0** makes it climb 3-block ledges and walls in one smooth snap, which may look odd for a 12-tall mob (and it will step over low garden walls); **pushing/collisions** with
players and mobs now use the big box (players may be shoved while standing "inside" it); players and arrows hit a much bigger target; and a hunt that flattens a forest or house (90-750 blocks, each with
item drops and block updates, leaf decay) may cost TPS on a real server (`breakDropsItems`, `blocksPerBreak`, `breakIntervalTicks` are the knobs). F3+B should now show the hitbox matching the model.

## Follow-up 2 (Thomas): unbreakable list instead of allow-list, and the rex eats animals
Request: the rex should get through most player structures; natural terrain ("smooth stone, sand, etc.") plus obsidian, End materials, iron blocks and concrete must stay unbreakable;
and with no player around it should now and then attack peaceful mobs "like it's eating". Same method: Claude wrote the tests first (3 failed on the old code for the right reasons), then specs, then Qwen.

| Piece | Task file | Qwen output | Rounds | Outcome | Claude took over |
|---|---|---|---|---|---|
| Unbreakable list (config + lang + `RexBlockBreaker`) | `011-unbreakable-list.md` | 3 files, +40/-34 | 1 (9 iterations, 95 s) | Exactly as specified; it also noticed 3 stale "allow-list" javadoc mentions in files it was told not to touch and reported them instead (I fixed those 3 lines) | no |
| Prey config + lang | `012-prey-config.md` | 2 files, +35 | 1 (8 iterations, 42 s) | Exactly as specified | no |
| `TRexPreyGoal` + goal registration | `013-prey-goal.md` | 90-line goal + 4 lines in `TRex` | 1 (11 iterations, 67 s) | As specified, one harmless deviation: the "prey died" check sits outside the bite-cooldown block (also covers prey killed by something else while in reach) | no |

Design decisions (mine, for Thomas to veto): (1) breaking is now "everything except `unbreakableBlocks`" (default 41 entries: natural terrain tags/ids, smooth stone, obsidian, concrete, iron blocks, End blocks), still capped by `maxBreakHardness` 5.0 (so fluids, ender chests, netherite and the like stay safe) and by `breakBlockEntities` (chests, furnaces, beds... stay);
(2) replaceable blocks (grass, flowers, snow layers, fire, water) are never break candidates; (3) iron *bars/doors/trapdoors*, glass, terracotta, deepslate bricks, cut/smooth sandstone, bricks, cobblestone and all stone bricks are breakable - only "iron blocks" were asked for;
(4) prey = any `Animal` (pigs, cows, sheep, chickens, horses, untamed wolves...) except tamed (owned), leashed or name-tagged ones, within `preyRange` 32, about once per 900 ticks while no player is detected (`hasQuarry()` false); a detected player always preempts (hunt goal priority 1, prey 2, wander 3), the rex walks up without breaking blocks, bites (20 damage, kills pigs/cows/sheep in one bite), stands over the kill for `preyEatTicks` 100 and its loot drops normally.

Test-fixture lessons while writing the matrix test: sand/gravel placed in mid-air fall (so sand has its own hill test), and plants placed without support pop off when a neighbour breaks (replaceable blocks are now asserted through `isBreakable` directly).
New tests: breaker material matrix (15 breakable, 15 unbreakable materials), stone brick house is broken into, concrete+iron house is left alone (rex keeps moving), sand hill is climbed not dug, rex eats a pig 20 blocks away with no player (410 ticks in the first run), rex prefers the player to a pig in between.
Prey test flake (2 of the first 10 full-suite runs failed with "rex never ate the pig"): a fixture problem, not the AI. In the unfenced arena the rex wandered out of the force-loaded chunks to x=23 and stood frozen there (a rex in a chunk that is not entity-ticking does not run its AI, the same effect as in the M3 notes), so the pig was never reached. Fixed by fencing the arena for that test; after that 13 of 13 samples ate the pig, after 94-4711 ticks (median ~850), budget raised to 20000 ticks.
- Result of follow-up 2: 12 consecutive `.\gradlew runGameTestServer --rerun-tasks` runs after the last change, 12/12 green, 21/21 tests each; `.\gradlew build` green; `.\gradlew runServer` reaches `Done` with no client-class errors and no config warnings.
