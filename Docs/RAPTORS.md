# Velociraptor packs (raptors branch)

A second dinosaur: the velociraptor, a small fast pack hunter that shares the T-rex's brain (senses, hunt goal,
mover, voice) on its own numbers. Everything raptor-specific lives in `config/dinodread-raptors.toml`.

## What it does

- **Packs of 3-4 spawn naturally at night**, 40-72 blocks from a player, all within 6 blocks of one anchor point.
  Each pack shares a scoreboard tag (`dinodread_pack_<uuid>`); a pack that cannot fit `packMinSize` bodies fails as a whole.
- **Hunts players and hostile mobs** (zombies, skeletons, ...). Named, leashed and tamed mobs are never targeted.
- **Pack death breaks the pack**: when any member of a tagged pack dies, every survivor anywhere in the dimension
  with the same tag starts fleeing from the nearest player for `fleeTicks` (default 30 s) and despawns when the
  flee ends. Spawn-egg / `/summon` raptors flee (loosely, with nearby untagged ones) but never despawn.
- **No charge.** The hunt goal's CHARGE state is gated off for raptors (`canCharge()`); they walk their target down.
- **Terrible swimmers**: swim speed 0.5 (the T-rex is 8) and no submerged swim depth, so they float at the surface
  and any boat or swimming player outruns them. Water is safety from a pack.
- **No ground shake.** The client camera shake listens for the `entity.trex.step`/`entity.trex.roar` sound ids;
  the raptor has its own ids (`entity.velociraptor.*`), so it never shakes the camera.
- Pushes through foliage only when hunting - a raptor never breaks through a wall or a house.

## Placeholders (to be replaced)

- Model/texture: copies of the T-rex files (`velociraptor.geo.json`, `velociraptor.animation.json`, `velociraptor.png`),
  rendered at scale 0.45. Swap in a licensed raptor model with the same file names.
- Sounds: `sounds.json` maps `entity.velociraptor.*` to the T-rex oggs at pitch 1.7-1.8. Replace the entries with
  real raptor files when they exist; no code changes needed.

## Tuning (config/dinodread-raptors.toml)

| Key | Default | Notes |
|---|---|---|
| `maxHealth` | 40 | 20 hearts |
| `attackDamage` | 6 | alone a nuisance, in a pack dangerous |
| `movementSpeed` | 0.3 | equal to the T-rex's attribute; the raptor never charges, but it never stops |
| `scale` | 0.45 | placeholder model shrunk to raptor size (~1.8 blocks tall) |
| `swimSpeedMultiplier` | 0.5 | the "hates water" dial; raise it and lakes stop being safety |
| `sightRange` / `hearingRadius` | 32 | half the T-rex's |
| `huntHostileMobs` | true | off = players only |
| `packMinSize` / `packMaxSize` | 3 / 4 | natural pack size |
| `fleeTicks` | 600 | flight time after a pack death, then despawn |
| `fleeSpeedMultiplier` | 1.3 | panic speed |
| `raptorSpawnChance` | 0.1 | per roll, once a minute per player |
| `maxRaptorPerPlayer` / `maxRaptorInWorld` | 8 / 12 | caps count packs of 3-4 |
| `raptorDespawnWhenFar` | true | natural raptors only, at 128 blocks |

The T-rex's own caps and despawn sweep deliberately ignore raptors (and vice versa): the two populations are
independent.

## In-game test

- `./gradlew runClient` in this folder
- `/summon dinodread:velociraptor ~ ~ ~` (one raptor, no pack) or the Velociraptor Spawn Egg
- Natural packs: night, overworld, one roll a minute per player, ~10% each
- Kill one raptor of a natural pack: the rest run for 30 s and vanish

## Tests

`./gradlew runGameTestServer` - five raptor tests:
`raptor_pack_spawns_together_under_one_tag`, `raptor_pack_flees_and_despawns_when_one_dies`,
`raptor_hunts_a_zombie_and_never_charges`, `trex_ignores_hostile_mobs`,
`raptor_defaults_are_small_slow_and_quiet`.
