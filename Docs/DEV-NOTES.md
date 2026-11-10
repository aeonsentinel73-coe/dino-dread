# Dev notes — lessons learned (Minecraft 26.x / NeoForge)

Project-specific gotchas and proven recipes discovered while building this mod.
The giant-tree investigation is documented in `GIANT-TREES.md`; this file is where
cross-cutting engine quirks and dev recipes live.

## API renames vs old tutorials (26.x)
- `ResourceLocation` is now **`Identifier`** (`Identifier.parse("mod:id")`).
- `Level.isClientSide` is a private field — use `level().isClientSide()`.
- `SoundSource.MOBS` split into `HOSTILE` / `NEUTRAL`; monsters return `HOSTILE`.
- `ServerLevel.stopSound` is gone — send `ClientboundStopSoundPacket` per nearby
  `ServerPlayer` via `player.connection`.
- `Entity.setInvulnerable` removed — for gametest fixtures, heal per tick instead.
- `PlacedFeature` is a record: `.placement()`, not `.getPlacement()`.
- `FeatureType` class no longer exists — custom features register as
  `MapCodec<? extends Feature>` in `Registries.FEATURE_TYPE`
  (`DeferredRegister<MapCodec<? extends Feature>>`).
- Brigadier: resource-id arguments use `IdentifierArgument.id()`;
  `StringArgumentType.word()` rejects `:` and will fail on any namespaced id.

## Movement physics
- Ground speed is **quadratic** in `MOVEMENT_SPEED`: `blocks/s ≈ 43 × (attribute ×
  speedMultiplier)²`. `Mob.setSpeed` sets the walk input, not a velocity — dialing
  "run speed" means solving through the square.

## Worldgen JSON rules
- **A placement chain must contain a surface modifier (`minecraft:heightmap` or the
  `surface_*` filter family).** Chunk decoration passes each feature the raw chunk
  origin (y = build bottom, −64 overworld). Without one, the feature runs thousands
  of times against bedrock and places ZERO with no error. Vanilla hides this by
  having biomes reference wrapper placed features (`trees_jungle` etc.) whose
  preamble is `count → in_square → surface_water_depth_filter → heightmap → biome`.
- `minecraft:count` takes an **IntProvider**: a bare fraction (`0.35`) truncates to
  0 attempts — valid JSON, no error, feature never runs. Sub-chunk density =
  `weighted_list` of integers, e.g. `{0:65, 1:35}` ≈ one attempt per three chunks
  (vanilla's own trick for 50.1).
- `minecraft:uniform` IntProvider is integer-only (`min_inclusive`/`max_inclusive`).
- Naming an unregistered placer/feature type (e.g. `mega_jungle_foliage_placer`) is
  a hard registry-load crash: "Failed to parse … from pack", server refuses to load
  datapacks entirely.
- NeoForge biome modifier JSON key is `step` (singular), e.g. `vegetal_decoration`;
  `steps` fails to parse.
- Saved dimension data lives under `saves/<world>/dimensions/minecraft/overworld/`.

## Game-test pitfalls
- Direct `ConfiguredFeature.place()` in a gametest **bypasses biome modifiers and the
  placement chain** — density bugs and missing-surface-modifier bugs are invisible to
  it. Chain-level guards must assert chain *contents* (modifier classes present, count
  IntProvider sampled > 0 over many seeds), and end-to-end proof needs the headless
  lab below.
- `TreeFeature` refuses silently unless the trunk column and its ±1 neighbours are
  free up to `treeHeight - limit` — clear a 3×3 air shaft before asserting placement.

## Headless worldgen-lab recipe (real chunk generation, no player)
1. `run/eula.txt` → `eula=true`; `run/server.properties` (gitignored):
   `level-name=Lab`, `level-seed=<n>`, `online-mode=false`, `spawn-protection=0`,
   `sync-chunk-writes=false`, `enable-rcon=true`, `rcon.port=25599`,
   `rcon.password=*** `pause-when-empty-seconds=-1` — without the last one a
   playerless server **pauses** and probes silently stop ticking.
2. `./gradlew runServer` (background). Drive it with RCON; packet framing:
   auth = (id, type 3, body), read response body as `head[8:-2]` of the reply.
3. `forceload add -120 -120 119 119` generates 256 chunks; wait; `save-all flush`;
   `stop`; scan the region files (below).
4. RCON gotcha: `say` output goes to game chat, not the RCON response — to read
   command results, write through the log and grep `run/logs/latest.log`.

## Region-file (.mca) scan recipe
- Offset table = first 4096 bytes = 1024 big-endian uint32 words. `word == 0` → empty.
- **`start = (word >> 8) * 4096`** — high 24 bits are the sector index, low 8 bits the
  sector count. (Using the raw word as a byte offset is wrong and silently finds nothing.)
- At `start`: uint32 length, one type byte (2 = zlib), then `zlib.decompress`.
- The decompressed chunk NBT stores ids as plain strings, so substring-testing for
  `jungle_log`, a biome id, etc. is a reliable presence/absence check.

## Loot tables (26.3)
- Bind to entities **by id**: `data/<ns>/loot_table/entities/<entity>.json`; `EntityType.Builder`
  derives the key from the registry name automatically (only `noLootTable()` opts out).
- JSON keys are **singular**: `condition` and `modifier` (not `conditions`/`functions`). There is no
  `set_probability` function — chance is `{"condition": {"type": "minecraft:random_chance",
  "chance": 0.45}}` on a pool or entry.
- Count providers are the new **ContextIntProvider** system: `uniform` takes plain `min`/`max`.
  Do NOT paste worldgen provider syntax (`min_inclusive`/`max_inclusive`) into loot JSON —
  the two codecs look alike and are not the same system.
- Not reachable via `registryAccess()` in a gametest; use
  `level.getServer().reloadableRegistries().getLootTable(key)`.

## Randomness in game tests
- `LootTable.getRandomItems(params, random)` honors the passed source, but do not seed each roll
  with `RandomSource.create(i*k + c)`: arithmetic-progression seeds correlate in the low bits, and a
  `nextInt(2)`-sized draw reads only those (observed: 60/60 identical outcomes). Pass ONE stream
  advanced across all rolls; variance is then deterministic but sane.

## Crafting recipes (26.3)
- `crafting_shapeless`/`crafting_shaped` codecs no longer accept `category` (parse failure = whole
  datapack refuses to load). Ingredients accept plain `"ns:id"` / `"#tag"` strings.
- Bucket items return their empty bucket from any crafting grid (vanilla `craftRemainder` on
  BucketItem) — lava-bucket cooking needs no code.
