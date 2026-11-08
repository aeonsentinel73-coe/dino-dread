# Dino Dread

A Minecraft Java Edition mod (NeoForge + GeckoLib) for a small friend-group server: big,
scary dinosaurs that hunt players. A T-rex that stalks, charges, breaks through your walls,
and can be beaten; a rare faster "super rex" variant that will hunt you until it dies or you
survive its window; velociraptor packs of 3–4 that flank their quarry and scatter when they
lose it; and a worldgen layer of very large trees so the forest feels big enough to hide
something in.

## Roster
| Mob | Behaviour |
|---|---|
| Tyrannosaurus Rex | Stalk → charge → bite hunt, block breaking, sound- and scent-driven tracking |
| Super Rex | 1-in-20 rex variant: faster, larger, recoloured; timed hunt, gives up after 90 s of losing you |
| Velociraptor pack | Spawns in packs of 3–4; the pack hunts as one (one sighting rallies every member in earshot); flankers circle the quarry and cut off the runner's line; pack routs and despawns when one dies; scatters briefly when it loses sight of prey |

Worldgen: mega-jungle-trunk "giant trees" in every forest/jungle/taiga/savanna biome (plus
swamps, cherry grove, meadow) and taller vanilla jungle trees. See `Docs/GIANT-TREES.md`.

## Building
Requires Java 25; Minecraft 26.3 / NeoForge 26.3.0.85 / GeckoLib 5.5.7 (see `gradle.properties`).

```
gradlew build        # compile and package
gradlew runClient    # dev client
gradlew runGameTestServer   # headless gameplay test suite
```

All gameplay tunables are config files under `run/config/` after first launch
(`dinodread-startup.toml`, `dinodread-spawning.toml`, `dinodread-superrex.toml`,
`dinodread-raptors.toml`).

For testing: raptor flanking is pack behavior keyed to the shared pack tag the spawner
assigns — raptors placed one at a time with spawn eggs are lone hunters and never flank.
Use `/dinodread raptorpack` (op level 2) to force a tagged pack onto the ground 8–16
blocks from you, day or night, creative included.

## Credits & provenance
Third-party models, textures and sounds, with authors and licenses: [`CREDITS.md`](CREDITS.md).
Design and milestone notes live in `Docs/`.

**AI provenance:** this project is authored and directed by Aeon (design decisions,
acceptance criteria, review and testing), with the code written largely by AI coding
agents (Claude and a local Qwen model) under that direction. The game-test suite in
`src/main/java/com/dinodread/gametest/` is the review record.

## License
All rights reserved (see `gradle.properties`), except third-party assets listed in
`CREDITS.md`, which remain under their own licenses.
