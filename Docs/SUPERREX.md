# Super Rex — tuning guide

The super rex is a variant T-rex, added on the `superrex` branch. It is a separate entity type
(`dinodread:super_rex`) that reuses the T-rex's model, animations, sounds and hunting brain, with its
own numbers.

## What makes it different

| | T-rex (default) | Super rex (default) |
|---|---|---|
| Land speed (`movementSpeed`) | 0.3 | 0.33 |
| Water speed (`swimSpeedMultiplier`) | 8.0 (beats a swimming player, loses to a boat) | 5.0 (loses to both) |
| Size (`scale`) | 3.0 | 3.3 (~10% bigger) |
| Health / bite | 200 / 18 | 264 / 30 |
| Sight / hearing | 40 / 52 blocks, 270° FOV | 64 / 64 blocks, 360° FOV |
| Sneak multipliers (sight / hearing) | 0.5 / 0.35 | 0.7 / 0.5 |
| Give up after (no contact) | 240 ticks | 1200 ticks |
| Charge from / after watching | 24 blocks / 100 ticks | 40 blocks / 20 ticks |
| Bite windup / cooldown | 10 / 40 ticks | 8 / 30 ticks |
| Daytime aggression (`dayAggression`) | 0.6 | 1.0 (no daylight let-up) |
| Texture | `textures/entity/trex.png` | red and green swapped (`super_rex.png`) |

Everything above lives in `config/dinodread-superrex.toml` (STARTUP — restart to apply attribute changes).
Shared systems (block breaking, stuck handling, prey, audio, footsteps, swim depth, model offset) stay
in `dinodread-startup.toml` and apply to both rexes.

## Spawning

`dinodread-spawning.toml`: `superRexChance = 0.05` — one in 20 natural rex spawns is a super rex
instead of a T-rex. The roll happens after all the normal spawn rules pass, so the super rex obeys
nightOnly, the biome list, the distance ring and the caps like any other rex (and counts against them:
a super rex blocks the next spawn just like a T-rex).

## The timed hunt (despawn)

The super rex hunt is an event with a clock:

- The clock starts the first time a survival player is within `spottedRange` (default 64 blocks) of a
  naturally spawned super rex **with clear line of sight** — the server's reading of "the rex is in the
  player's eyesight".
- `despawnAfterSpottedTicks` (default 2400 = 2 minutes) later the rex is removed, whether or not it
  ever reaches the player. Survive the two minutes and it is over.
- The time it takes the rex to walk into view is *not* counted — the clock only starts once it is seen,
  which is the "time to get into eyesight plus 2 minutes" the design asks for.
- Egg and `/summon` super rexes are never touched by this rule. `despawnAfterSpottedTicks = 0` turns
  it off. After a chunk reload the clock restarts (same deal as `maxLifetimeTicks`).
- The normal despawn rules (`despawnWhenFar`, `maxLifetimeTicks`) still apply on top.

## Testing

- `.\gradlew runGameTestServer` runs the four `superrex_*` game tests: the substitution chance, the
  caps counting, the attribute direction (faster/bigger/slower-in-water defaults), and the timed
  despawn (seen natural rex goes, unseen-by-the-rule egg rex stays).
- In-game: `/summon dinodread:super_rex ~ ~ ~` or the Super Rex spawn egg (spawn-egg tab).

## Files that define it

- `entity/SuperRex.java` — the variant: attribute table, sense/hunt overrides, the despawn clock.
- `config/SuperRexConfig.java` — its tunables (`dinodread-superrex.toml`).
- `TRex.java` gained per-entity getters (`sightRange()`, `chargeDistance()`, …) so the shared brain
  reads per-variant numbers; the T-rex defaults are the old RexConfig reads.
- `spawn/RexSpawner.pickType` — the one-in-20 substitution.
- Client: `SuperRexRenderer` reuses `TRexRenderer`; assets under the `super_rex` ids.
