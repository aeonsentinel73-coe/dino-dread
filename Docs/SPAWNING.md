# Dino Dread spawning: how a T-rex turns up, and how to tune it

Config file: `config/dinodread-spawning.toml` (a STARTUP config: it is read once when the game or server starts, so edit it, then restart).

## What happens

Every `attemptIntervalTicks` (default 600 = 30 s), **each eligible player rolls once** in each allowed dimension. A roll becomes
a T-rex only if every gate is open, checked cheapest first:

1. `spawnEnabled`, and the dimension is in `allowedDimensions` (overworld only by default).
2. The difficulty is not Peaceful, and the `spawn_mobs` and `spawn_monsters` game rules are on.
3. The player is alive and in survival or adventure (creative and spectator players never roll).
4. The world has run `firstSpawnDelayDays` full days of game time (0 by default).
5. `nightOnly` (on): it is dark enough at the player, `minNightFactor` 0.5 (0 = full day, 1 = deep night).
6. Under the caps: fewer than `maxRexInWorld` (1) T-rexes loaded in the dimension, and fewer than `maxRexPerPlayer` (1) within
   `capRadius` (160) of this player. Egg and command T-rexes count too: v1 is one T-rex.
7. The dice: `spawnChance` (0.05).

Then up to `placementAttempts` (24) random spots are tried, `minSpawnDistance` to `maxSpawnDistance` (56 to 96 blocks) from the
player on the surface. A spot is used only if its chunk is loaded and running (the spawner never loads or builds a chunk), its
biome is in `allowedBiomes` (`#minecraft:is_overworld`; an empty list means any biome), nobody is within `minSpawnDistance` of it
(any player, not only the roller), there is solid ground under it, no water in its body, and **the T-rex's whole hitbox fits**
(the size comes from the entity, type size times the `scale` attribute, so a resized model needs no change here).

A new T-rex is tagged `dinodread_natural_spawn`, made persistent (vanilla would otherwise soon despawn a monster that
appears 56+ blocks from every player) and, with `spawnAlerted` (on), told where the roller is. It then investigates that spot as if it
had heard a noise, which also starts its detection roar: heard first, seen second.

## Leaving

Only natural T-rexes (the tag) are ever removed; spawn eggs and `/summon` T-rexes are never touched. Checked every 5 seconds:

- `despawnWhenFar` (on): removed when no player is within `despawnDistance` (128). Keeps one that wandered off from blocking the
  next spawn. Keep it above `maxSpawnDistance` and below the server's simulation distance.
- `maxLifetimeTicks` (48000 = 40 minutes of loaded time): removed after that, unless it is hunting at that moment. 0 = no limit.
  The count restarts when its chunk reloads.

## Making one turn up quickly (testing)

In-game and instant: use the spawn egg, or `/summon dinodread:trex ~ ~ ~` (this is an egg-style rex: no tag, never despawned).
To watch the real spawner, put this in the file and restart:

    [spawning]
    nightOnly = false
    attemptIntervalTicks = 20
    spawnChance = 1.0
    [placement]
    minSpawnDistance = 40.0
    maxSpawnDistance = 60.0
    [despawn]
    maxLifetimeTicks = 0

Stand on open ground (the hitbox is `hitboxWidth` x `scale` = 4.5 blocks wide and 6.6 tall by default and needs room; a tree line fails more often, raise `placementAttempts`).

## Rarity and feel: the knobs, in the order to reach for them

| You want | Change | Notes |
|---|---|---|
| Rarer or commoner | `spawnChance` | Average wait is `attemptIntervalTicks / spawnChance` ticks of eligible time: 600 / 0.05 = 12000 ticks, 10 minutes of night, so a visit about every second night per player. Lower it first. |
| Shorter or longer visits | `maxLifetimeTicks`, `despawnDistance` | 0 / false = it stays until killed. |
| Heard from further, or a surprise | `minSpawnDistance`, `maxSpawnDistance` | Its sight is 40, hearing 52 (at night). Below the sight range it can appear in view. |
| Not only at night | `nightOnly = false`, or lower `minNightFactor` | Daytime activity is scaled by `dayAggression` in the other file. |
| Two at once (a big group) | `maxRexInWorld`, `maxRexPerPlayer` | v1 is one. |
| A grace period in a new world | `firstSpawnDelayDays` | Game-time days (20 minutes each), not the day counter. |
| Only some biomes | `allowedBiomes` | Ids and `#tags`: `["#minecraft:is_forest", "minecraft:plains"]`. |

## The other hostile mobs

`disableVanillaHostiles` (off by default) stops every other monster-category mob from spawning on its own, so the T-rex is the
only thing that hunts. It covers modded monsters too. What counts as "on its own" is `blockedSpawnReasons`: `NATURAL` (night
spawning), `CHUNK_GENERATION` (mobs made with the terrain), `PATROL` (pillager patrols), `STRUCTURE` (mobs structures place).
Spawn eggs, `/summon`, dispensers, breeding, raids, jockeys and conversions are never in the list. Spawner blocks and trial
spawners are not blocked by default (a dungeon is a place the world deliberately set a trap in): add `SPAWNER` and
`TRIAL_SPAWNER` to the list to stop those too. `vanillaHostileExceptions` lets chosen mobs through (`minecraft:zombie`,
`#minecraft:skeletons`). Anything in the `dinodread` namespace is never blocked.

Two hooks make one decision: the placement check that vanilla's natural spawner asks before it builds a mob (cheap), and the
finalize-spawn event that every spawn path fires, including phantoms, patrols, structures and chunk generation, which skip the
placement check. `MobSpawnEvent.PositionCheck` was not used: it fires after the mob is built, so it is later and no more complete
than finalize-spawn.

## What the tests cover and what they cannot

`RexSpawnGameTests` (22 tests) drive the spawner and the blocker with explicit rules, so each rule is switched on and off
inside a test, and a mutation check showed every rule's test fails when that rule is broken. They cannot say whether it
is scary: the rarity, distances and lifetime defaults are first guesses to tune with the group on the real server.
