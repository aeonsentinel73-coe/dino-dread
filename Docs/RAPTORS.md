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

## Assets

- Model/texture: the Peridot Scout by logan_kant (Blockbench Workshop, CC0; see CREDITS.md). Files:
  `velociraptor.geo.json`, `velociraptor.animation.json`, `velociraptor.png`; source project `art/peridot-scout.bbmodel`.
  Played animations: idle, walk, run (while hunting or fleeing), swim, roar (as the hunting-call pose),
  attack_left/attack_right (alternated per bite), tail_whip (an occasional idle flick, ~every 15 s of standing
  around mid-wander), and death. Unused: scan, hello, and the dead gray texture.
- Sounds (`sounds/entity/raptor/`, sources in `art/sounds/`): the two hunting cries play when a raptor starts hunting
  (`entity.velociraptor.roar`), and the running-gait loop plays while it sprints (`entity.velociraptor.run`, restarted
  ~4.25 s apart, silent in water and at a walk). No distant call. Still a placeholder: the bite (`cry2` at pitch 1.5).
  The spawn-egg icon is the rex egg's shape in brown.

## Tuning (config/dinodread-raptors.toml)

| Key | Default | Notes |
|---|---|---|
| `maxHealth` | 24 | 4 iron-sword swings |
| `attackDamage` | 10 | raw, before armor: full iron takes 6.0 per bite, unarmored 10 |
| `movementSpeed` | 0.45 | this version's ground speed is quadratic: about 43 x (0.45 x 0.8 hunt)^2 = 5.4 blocks/s — faster than walking (4.3), a hair under sprinting (5.6) |
| `scale` | 0.6 | Peridot Scout is 2.9 blocks tall at scale 1, so this matches the 3.8-block hitbox (~1.7-2.3 tall) |
| `jumpStrength` | 0.3 | the raptor's stuck-hop height (the rex mover's 0.9, sized for its 3x body, made raptors grasshoppers in live play) |
| `swimSpeedMultiplier` | 1.0 | slow in water but can reach the bank; a boat still wins |
| `waterExitHeight` | 3.0 | highest bank it hauls itself onto from the water, like the T-rex |
| `flankRadiusBlocks` / `flankSpeedBoost` | 6.0 / 1.25 | a flanker moves ~1.6x a sprinting player (speed is quadratic in the boost) |
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
- `/dinodread raptorpack` (op level 2): forces a tagged pack 8-16 blocks away, any time of day, creative included.
  Flanking is keyed to the shared pack tag, which spawn eggs never assign - egg raptors are lone hunters and never
  flank, so this is the only way to see the behavior from a spawn-egg-only test.
- Natural packs: night, overworld, one roll a minute per player, ~10% each
- Kill one raptor of a natural pack: the rest run for 30 s and vanish

## Tests

`./gradlew runGameTestServer` - nine raptor tests:
`raptor_pack_spawns_together_under_one_tag`, `raptor_pack_flees_and_despawns_when_one_dies`,
`raptor_hunts_a_zombie_and_never_charges`, `raptor_pack_flanks_its_quarry`,
`raptor_scatters_without_despawning`, `raptor_chase_outruns_a_walking_player`,
`raptor_climbs_out_of_water`, `trex_ignores_hostile_mobs`,
`raptor_defaults_are_small_slow_and_quiet`.
