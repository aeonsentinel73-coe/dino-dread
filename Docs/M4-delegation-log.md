# M4 delegation log (experiment: local Qwen3.8-27B does the M4 implementation)

Claude owns: design, specs, the acceptance GameTests (written first), builds/test runs, symbol checks, review.
Qwen (via `mcp__local-vibes__delegate`, bash off) writes the implementation. Effort figures are rough.

## Claude-owned groundwork (not delegated)
- **Acceptance GameTests** (`gametest/RexObstacleGameTests.java`, ~330 lines; small refactor of `RexGameTests` helpers to package-private
  plus a `structure` hook). Written before any implementation; checked against the unchanged M3 rex: 4 of the 5 new tests fail
  for the right reason (blocked at wall / trees / ledge / frozen at stone), the 5th (mobGriefing off) is the control for the wood-house test.
- **Premise check found a real bug:** the task said the rex hitbox is 9x12. It was 3x4. The `scale` attribute comes from attribute defaults, which
  never fire the "scale changed" update that resizes the hitbox (model 3x, hitbox unscaled). Claude fixed it with one `refreshDimensions()` call in the
  `TRex` constructor (4 lines incl. comment). Everything M1-M3 measured (bite range, collisions, "9 wide pathfinding froze") was really about a 3x4 box.

