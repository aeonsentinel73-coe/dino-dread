# M6 (spawning and balance): delegation log

Branch `m6-spawning`, cut from `m5-dread-audio` at 1332c33. Roles as in M4/M5: Claude = design, acceptance GameTests written FIRST, symbol
verification, specs, review, mutation checks; Qwen3.8-27B (via `mcp__local-vibes__delegate`, bash off) = the mechanical code.
Numbers are the `usage:` line of each delegate result ("prompt" is summed over every request of the loop; "peak" is the largest single request).

| Piece | Spec | Delegate calls | Iterations | Prompt tokens | Completion tokens | Peak context | Time | Outcome |
|---|---|---|---|---|---|---|---|---|
| `SpawnConfig` (20 entries, 5 sections), `SpawnRules` record (17 components, 17 withers, `fromConfig`), 27 lang labels | `016-spawn-config-and-rules.md` | 1 | 8 | 109,527 | 5,878 | 23,912 | 130 s | Exactly as specified |
| `IdTagList` (generic id/#tag list, thread-safe snapshot) and `HostileBlocker` (the toggle) | `017-id-tag-list-and-hostile-blocker.md` | 1 | 4 | 16,980 | 2,125 | 7,433 | 45 s | Exactly as specified |
| `RexSpawner` (gates, placement search, despawn sweep, level tick) | `018-rex-spawner.md` | 1 | 4 | 20,530 | 2,514 | 9,056 | 52 s | Exactly as specified; passed 52/52 once the test fixture was fixed (see below) |
| `spawnAlerted` option: 18th record component, 17 wither edits, config entry, lang label, 6-line call in `RexSpawner` | `019-spawn-alerted.md` | 1 | 4 | 26,626 | 3,217 | 13,314 | 69 s | Exactly as specified |
| **Total** | | **4** | **20** | **173,663** | **13,734** | | **296 s** | 0 rounds repeated, 0 take-overs, no wrong-version or invented API |

Everything Qwen wrote came from symbols the spec listed; none of its output needed a fix. The specs were again about as long as the code
(018: 165 lines of spec, 181 of code; 017: 101 and 197; 016: 146 and 250), but this time the verification was where the work went: `EntityTypes.ZOMBIE` (not `EntityType.ZOMBIE`),
`Identifier`, `ResourceKey.identifier()`, `Heightmap` in `levelgen`, `LevelTickEvent.Post`, `ServerLevel.isSpawningMonsters()`,
`FinalizeSpawnEvent` as a top-level class but `MobSpawnEvent.SpawnPlacementCheck` nested, `EntityType.spawn` returning null for a
cancelled spawn, `registerConfig(type, spec, fileName)`.

## What Claude did itself, and why

- **The tests (`RexSpawnGameTests`, 22 tests, ~680 lines) and the stubs.** Written first against stubbed public surface (`IdTagList`,
  `RexSpawner`, `HostileBlocker`). Red run: 12 of the 17 tests then written failed, 5 passed (the config defaults test; three hostile-blocker negatives that a do-nothing stub
  satisfies, which got their teeth from the mutation checks below; and the placement test, which passed by luck, see below).
- **`TRexSenses.alertTo` (+10 lines, additive).** Found while designing the defaults: a rex that spawns 56 to 96 blocks away is beyond its
  own sight (40) and hearing (52), so it wanders at random and would almost never meet the player before it despawned. The alert makes it
  investigate the roller's position as if it had heard a noise, which also starts the hunt goal and so the detection roar at spawn. Not
  delegated: ten lines, and it needed the judgement that it belongs in `TRexSenses` rather than in the spawner.
- **Wiring in `DinoDread`**, and making `RexGameTests.spawnPlayer` package-private (one word) for the two-player test.
- **The mutation harness** (outside the repo), see below.

## Test-fixture findings (the things the suite would have gotten wrong)

- **The GameTest server turns `spawn_mobs` off** (`GameTestServer.TEST_GAME_RULES`). The spawner honours the game rule (so should admins'
  `/gamerule spawn_mobs false`), so all 8 positive spawn tests failed at first with "no T-rex spawned" while the negative ones passed. Fixed by
  turning the rule on for the one synchronous tick of each test body (vanilla's spawner never gets a turn inside it), plus a test that
  the rules do stop a spawn.
- **Forced chunks include terrain outside the stone arena.** A negative placement test ("a second player stands on the only open ground")
  was satisfied by a rex spawning in the natural terrain at the edge of the arena's chunks. Only tests that rely on a *gate* (night, caps,
  biome, dimension, chance...) are placement-independent; the two-player test uses a ring (40 to 60) that nothing in those chunks can satisfy.
- **A vanilla placement check is random** (zombies roll for light), so a test of "the placement check fails" passed on the do-nothing stub by
  luck. Replaced by posting `MobSpawnEvent.SpawnPlacementCheck` by hand with a known default answer.
- **The first two-player test had never passed** (I added it and ran only the mutation harness; every group showed the same failure, which
  is how it was noticed). Run the clean suite after adding a test, before mutating.

## Mutation checks (can each test fail?)

The harness breaks one behaviour per test in `RexSpawner`/`HostileBlocker`, runs the whole suite and lists the failures (three rounds, 8 suite runs, 2 to 6 behaviours per run). Broken and caught: night gate, world cap, per-player cap, dimension gate, biome gate, first-spawn
delay, spawn_monsters rule, chance roll, minimum distance, nearest-other-player check, hitbox collision check, interval gate, persistence, natural
tag (on spawn and in the despawn filter), despawnWhenFar, lifetime limit and its 0 = unlimited case, hunting guard, the alert (missing, and always on),
hostile reason filter, namespace exemption, monster category, exceptions, placement listener, finalize listener.
Two weaknesses found and fixed: the `despawnWhenFar` case could not fail (the player was inside `despawnDistance` either way: now 20 vs 39 blocks),
and one mutation (lifetime 0) was masked by an earlier assertion in the same test, so it was rerun alone. Side effect worth knowing: dropping
the natural-tag filter made the live despawn sweep remove the *egg* rexes of unrelated old tests, which then failed too.

Production wiring is covered separately: `spawner_level_tick_is_on_the_game_bus` (a stray natural rex is removed by the game's own tick, no hand-built
event), and a manual run with `disableVanillaHostiles = true` in `run/config/dinodread-spawning.toml`: with no test blocker registered a natural zombie
spawn was blocked (exactly the five tests that assume the shipped default failed, as they should; the file was restored).

## Results

- `.\gradlew build` green. Full GameTest suite, `.\gradlew runGameTestServer --rerun-tasks`: **56/56 on 3 consecutive runs** after the last change
  (34 existing + 22 new; 40, 33 and 40 s). Earlier states: 52/52 once, then 55/55 three times in a row.
- Not run: `.\gradlew runServer` (no `run/eula.txt`; accepting the EULA is Thomas's decision). `runGameTestServer` loads the mod server-side and the log shows no
  client-class errors or config warnings (apart from the deliberate malformed-entry warnings the tests provoke).

## Design decisions for Thomas to veto

1. **Custom spawner, not biome-modifier JSON**: every tunable must be in config, and a biome modifier cannot read config.
2. **Natural T-rexes are tagged `dinodread_natural_spawn`, persistent, and removed by our own rules** (no player within 128 blocks; 40 minutes of loaded time
   unless hunting) so a stray never blocks the next spawn. Egg and `/summon` T-rexes are never touched (and still count against the caps).
3. **`spawnAlerted` on by default** (needs the 10-line `TRexSenses.alertTo`). It points the rex at the roller once, at spawn, so it roars and goes; the
   give-up timer (240 ticks at night) means from 96 blocks it may arrive only at the edge of its own senses. If it fails to find players often,
   lower `maxSpawnDistance` or raise `giveUpTicks`.
4. **The toggle blocks every monster-category mob (modded too)** except the `dinodread` namespace and the exception list, for `NATURAL`,
   `CHUNK_GENERATION`, `PATROL` and `STRUCTURE`. Spawner blocks, trial spawners, raids, eggs and commands are left alone by default (`blockedSpawnReasons`
   can add `SPAWNER`). Two hooks (the placement check, cheap; finalize-spawn, complete), one decision; `PositionCheck` was rejected because it fires after the mob exists.
5. **Honours the `spawn_mobs` / `spawn_monsters` game rules and Peaceful**; ignores creative and spectator players.
6. **Defaults are guesses**: 5% per 30-second roll while dark, so about one visit per two nights per player; one rex per dimension; ring 56 to 96; stays 40 minutes.
7. **STARTUP config type** as briefed (restart to change values). COMMON would allow editing without a restart, if tuning with the group makes that worth it.

## Not verified

Nothing has been seen or heard in a real client or on a real server with several people: how often it turns up, whether 56 to 96 blocks feels right,
whether the alerted walk-in works in forests and hills (the GameTests use flat ground), and how the 9-wide body fares in `placementAttempts` in trees.
`Docs/SPAWNING.md` lists the knobs and a quick recipe to force a spawn.
