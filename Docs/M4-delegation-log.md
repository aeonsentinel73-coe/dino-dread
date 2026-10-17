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
