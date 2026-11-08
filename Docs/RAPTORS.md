# Velociraptor packs (raptors branch)

A second dinosaur: the velociraptor, a small fast pack hunter that shares the T-rex's brain (senses, hunt goal,
mover, voice) on its own numbers. Everything raptor-specific lives in `config/dinodread-raptors.toml`.

## What it does

- **Packs of 3-4 spawn naturally at night**, 40-72 blocks from a player, all within 6 blocks of one anchor point.
  Each pack shares a scoreboard tag (`dinodread_pack_<uuid>`); a pack that cannot fit `packMinSize` bodies fails as a whole.
- **Hunts players and hostile mobs** (zombies, skeletons, ...). Named, leashed and tamed mobs are never targeted.
- **The pack hunts as one**: a raptor that has neither seen nor heard the quarry follows the pack-mate that has
  (a call inside the 72-block pack cohesion radius - the same radius that counts flank roles, NOT the 32-block
  earshot, so a flanker working the radius-40 ring never falls off the network; re-checked every half second),
  and while any member still perceives
  the quarry nobody's give-up clock runs out alone - the pack gives up together or not at all. Without this each
  raptor needed its own first contact, packs straggled, and a sprinting player watched them peel away one by
  one; half-hunting packs were also too small to ever flank.
- **Pack death breaks the pack**: when any member of a tagged pack dies, every survivor anywhere in the dimension
  with the same tag starts fleeing from the nearest player for `fleeTicks` (default 30 s) and despawns when the
  flee ends. Spawn-egg / `/summon` raptors flee (loosely, with nearby untagged ones) but never despawn.
- **The survival timer**: one hunt against one quarry never lasts more than `huntPersistenceTicks`
  (default 60 s) - even while the quarry stands in plain sight. On rough terrain "can see you" is not
  "can reach you", and the silence-based give-up clock never fires on a staring match; the cap forces the
  pack to quit the stalemate, and a capped raptor then ignores that quarry for 30 s before it may re-hunt.
  The T-rex has no cap - it is the relentless tracker.
- **No charge.** The hunt goal's CHARGE state is gated off for raptors (`canCharge()`); they walk their target down.
- **The cut-off.** A raptor that finishes its flank circle surges at flank speed onto where you are GOING, not
  where you are: hold a straight line and it meets you ahead of your own nose and bites - knocking you back
  into the pack behind it. Turn and the prediction misses; the surge is a committed 5 s, then it chases straight.
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
  A raptor that dies mid-sample sends nearby players a stop-sound for its run scrape - the sample is 4.3 s long and
  a broadcast sound cannot be recalled, so dead raptors used to leave claw noise hanging over them for a second
  or two (lava-pool playtest).
  The spawn-egg icon is the rex egg's shape in brown.

## Tuning (config/dinodread-raptors.toml)

| Key | Default | Notes |
|---|---|---|
| `maxHealth` | 24 | 4 iron-sword swings |
| `attackDamage` | 10 | raw, before armor: full iron takes 6.0 per bite, unarmored 10 |
| `movementSpeed` | 0.43 | this version's ground speed is quadratic: about 43 x (0.43 x 0.8 hunt)^2 = 5.1 blocks/s — faster than walking (4.3), under sprinting (5.6). 0.443 (5.4) tested live: the pack still kept up — every bite halves your speed and launches you back into the pack (vanilla damage knockback), so the base stays at the outrunnable 0.43 |
| `knockbackResistance` | 0.8 | punches stagger a raptor but no longer shove it out of its own bite range — bare-hand spam used to leash hunters forever (every hit applies vanilla damage knockback; armor blocks none of it) |
| `scale` | 0.6 | Peridot Scout is 2.9 blocks tall at scale 1, so this matches the 3.8-block hitbox (~1.7-2.3 tall) |
| `jumpStrength` | 0.3 | the raptor's stuck-hop height (the rex mover's 0.9, sized for its 3x body, made raptors grasshoppers in live play) |
| `swimSpeedMultiplier` | 1.0 | slow in water but can reach the bank; a boat still wins |
| `waterExitHeight` | 3.0 | highest bank it hauls itself onto from the water, like the T-rex |
| `flankRadiusBlocks` / `flankSpeedBoost` | 40 / 1.77 | a flanker moves ~2.8x a sprinting player (~16 b/s) so the wider ring still sweeps at the same ~23 degrees/s: 180 degrees in ~6 s; the waypoint rides a body-reachable lead ahead on the circle, so it runs the arc instead of spinning after it |
| `flankSweepDegrees` | 180 | end hook: how far around the quarry a flanker commits (progress counts the BODY's bearing — which way you are facing does not shorten the circle; the ring follows you). At the sweep it cuts off: a 3 s surge (`interceptTicks`) at `interceptSpeedBoost` onto where the quarry is GOING - a straight line gets met head-on and bitten. The dodge: swing your traveled line `interceptTurnDegrees` (60°) within 0.5 s of the circle ending and the cut-off is given up; after the window it's committed. A stalled circle (quarry sprinting away) is abandoned after ~5 s and never surges; a circle the TERRAIN starves (hills, cliffs, washouts — zero sweep progress for 20 s no matter how far off-ring the pathfinder threw it) is abandoned too, so a doomed circle always ends by joining the outrunnable chase instead of haunting the quarry's periphery; after the surge the ordinary chase is outrunnable. Roles are stable within a hunt: a sight blink or a stretched formation can no longer un-assign a flanker mid-circle, and a flanked quarry stays "already circled" for 15 s so overshoot-flicker can't loop circle→lunge→circle (720 = laps until lost) |
| `interceptSpeedBoost` / `interceptTicks` / `interceptTurnDegrees` | 1.25 / 60 / 60 | the cut-off surge, tuned apart from the circle: ~1.5x a sprint (~8 b/s; the straight-running quarry's own speed walks them into the meeting point), 3 s, and dodgeable by a 60° line change in the first half second (0 = never). A quarry standing still at the flank's end has no line to change and cannot dodge |
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

`./gradlew runGameTestServer` - eleven raptor tests:
`raptor_pack_spawns_together_under_one_tag`, `raptor_pack_flees_and_despawns_when_one_dies`,
`raptor_hunts_a_zombie_and_never_charges`, `raptor_pack_flanks_its_quarry`,
`raptor_pack_shares_the_hunt`, `raptor_pack_holds_the_hunt_together`,
`raptor_scatters_without_despawning`, `raptor_chase_outruns_a_walking_player`,
`raptor_climbs_out_of_water`, `trex_ignores_hostile_mobs`,
`raptor_defaults_are_small_slow_and_quiet`.
