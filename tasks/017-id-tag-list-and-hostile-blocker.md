# Task 017: `IdTagList` (config list of ids and #tags) and `HostileBlocker` (the disableVanillaHostiles toggle)

**Goal:** replace the two STUB files below with real implementations. Both have a FIXED public surface (the game tests
in `RexSpawnGameTests` already call it): keep every public signature exactly as in the stubs.

Working directory: `D:\Documents\Claude Code\Dino Dread-m6` (pass it as `working_dir`).

## PROCEDURE (follow exactly)
1. Read these files: `src/main/java/com/dinodread/world/IdTagList.java` (stub), `src/main/java/com/dinodread/spawn/HostileBlocker.java`
   (stub), and lines 143-192 of `src/main/java/com/dinodread/entity/ai/RexBlockBreaker.java` (the `BlockList` class: the pattern
   `IdTagList` generalises).
2. Write BOTH files as whole files, one write each. Do NOT search the filesystem and do NOT grep for Minecraft symbols: every
   import and call below is already verified against the pinned sources.
3. Finish with `finish` in under 12 tool calls. You cannot run Gradle; Claude will build.

Do NOT touch: `.git/`, `RexBlockBreaker.java`, `DinoDread.java`, `SpawnConfig.java`, `SpawnRules.java`, any test, any other file.

## File 1: `src/main/java/com/dinodread/world/IdTagList.java` (whole file)
Same package `com.dinodread.world`, same public constructor and `matches` signature as the stub. Javadoc: "A config list of
registry ids and #tags (for example {@code minecraft:zombie} or {@code #minecraft:skeletons}), parsed once and re-parsed only when
the config value changes. Safe to call from any thread (world generation calls it): the parsed lists are immutable snapshots."

Imports: `java.util.ArrayList`, `java.util.HashSet`, `java.util.List`, `java.util.Set`, `com.dinodread.DinoDread`,
`net.minecraft.core.Holder`, `net.minecraft.core.Registry`, `net.minecraft.resources.Identifier`,
`net.minecraft.resources.ResourceKey`, `net.minecraft.tags.TagKey`.

Structure:
- fields: `private final String configName;`, `private final ResourceKey<? extends Registry<T>> registry;`,
  `private volatile Parsed<T> parsed;` (starts null).
- `private record Parsed<T>(List<? extends String> source, Set<Identifier> ids, List<TagKey<T>> tags) {}`
- constructor stores `configName` and `registry`.
- `public boolean matches(List<? extends String> configured, Holder<T> holder)`:
  1. `Parsed<T> current = parsed;`  If `current == null || !configured.equals(current.source())`, set `current = parse(configured)` and
     `parsed = current`.
  2. For each `Identifier id` in `current.ids()`: `if (holder.is(id)) return true;`
  3. For each `TagKey<T> tag` in `current.tags()`: `if (holder.is(tag)) return true;`
  4. `return false;`
- `private Parsed<T> parse(List<? extends String> configured)`: make a `HashSet<Identifier> ids` and `ArrayList<TagKey<T>> tags`. For each
  `String entry` in `configured`:
  - if `entry.startsWith("#")`: `Identifier id = Identifier.tryParse(entry.substring(1));` if `id == null` log
    `DinoDread.LOGGER.warn("Skipping malformed tag entry in {} config: {}", configName, entry);` and `continue`; else `tags.add(TagKey.create(registry, id));`
  - else: `Identifier id = Identifier.tryParse(entry);` if `id == null` log
    `DinoDread.LOGGER.warn("Skipping malformed id entry in {} config: {}", configName, entry);` and `continue`; else `ids.add(id);`
  Return `new Parsed<>(configured, ids, tags)`. (Ids that name nothing simply never match; do not warn for them.)
- Worked example: `matches(List.of("minecraft:zombie", "#minecraft:skeletons", "bad id!!"), zombieHolder)` returns true,
  `matches(same list, creeperHolder)` returns false, `matches(List.of(), anything)` returns false, and the bad entry is logged once, skipped.

## File 2: `src/main/java/com/dinodread/spawn/HostileBlocker.java` (whole file)
Keep the nested `public record Rules(boolean disable, List<? extends String> exceptions, List<? extends String> reasons)` with its
`fromConfig()` EXACTLY as in the stub. Keep these public members with these exact signatures: the constructor
`HostileBlocker(Supplier<Rules> rules)`, `static HostileBlocker fromConfig()`, `boolean shouldBlock(EntityType<?> type, EntitySpawnReason reason)`,
`void onFinalizeSpawn(FinalizeSpawnEvent event)`, `void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event)`,
`void register(IEventBus bus)`, `void unregister(IEventBus bus)`.

Javadoc on the class: "Stops the normal sources of hostile mobs when the disableVanillaHostiles config is on, so the T-rex is the only
thing that hunts. Two hooks, one decision ({@link #shouldBlock}): the placement check, which vanilla's natural spawner asks before it even
builds a mob (cheap), and the finalize-spawn event, which every spawn path fires, including those that skip the placement check
(phantoms, patrols, structures, chunk generation). Only the spawn reasons in the config are blocked, so eggs, commands, summons,
breeding, raids and (by default) spawners are untouched. Anything in the Dino Dread namespace, and anything on the exception list, is never blocked."

Imports (all verified): `java.util.EnumSet`, `java.util.List`, `java.util.Locale`, `java.util.Set`, `java.util.function.Consumer`,
`java.util.function.Supplier`, `com.dinodread.DinoDread`, `com.dinodread.config.SpawnConfig`, `com.dinodread.world.IdTagList`,
`net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.core.registries.Registries`, `net.minecraft.resources.Identifier`,
`net.minecraft.world.entity.EntitySpawnReason`, `net.minecraft.world.entity.EntityType`, `net.minecraft.world.entity.MobCategory`,
`net.neoforged.bus.api.IEventBus`, `net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent`,
`net.neoforged.neoforge.event.entity.living.MobSpawnEvent`.

Fields:
```java
private final Supplier<Rules> rules;
private final IdTagList<EntityType<?>> exceptions = new IdTagList<>("vanillaHostileExceptions", Registries.ENTITY_TYPE);
private volatile ReasonSet reasonSet;   // starts null
private final Consumer<FinalizeSpawnEvent> finalizeListener = this::onFinalizeSpawn;
private final Consumer<MobSpawnEvent.SpawnPlacementCheck> placementListener = this::onSpawnPlacementCheck;
private record ReasonSet(List<? extends String> source, Set<EntitySpawnReason> reasons) {}
```
The constructor stores `rules`.

`shouldBlock(EntityType<?> type, EntitySpawnReason reason)` (the whole decision, in this order, return false at the first failing test):
1. `Rules current = rules.get();` `if (!current.disable()) return false;`
2. `if (type.getCategory() != MobCategory.MONSTER) return false;`
3. `Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);` `if (DinoDread.MODID.equals(key.getNamespace())) return false;`
4. `if (!reasonsOf(current.reasons()).contains(reason)) return false;`
5. `return !exceptions.matches(current.exceptions(), BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type));`

`private Set<EntitySpawnReason> reasonsOf(List<? extends String> configured)`: `ReasonSet current = reasonSet;` if `current == null || !configured.equals(current.source())`
build `EnumSet<EntitySpawnReason> reasons = EnumSet.noneOf(EntitySpawnReason.class);` and for each `String name`: try
`reasons.add(EntitySpawnReason.valueOf(name.trim().toUpperCase(Locale.ROOT)));` catch `IllegalArgumentException` and log
`DinoDread.LOGGER.warn("Skipping unknown spawn reason in blockedSpawnReasons config: {}", name);`. Then `current = new ReasonSet(configured, reasons); reasonSet = current;`. Return `current.reasons()`.

`onFinalizeSpawn(FinalizeSpawnEvent event)`: `if (shouldBlock(event.getEntity().getType(), event.getSpawnType())) { event.setSpawnCancelled(true); event.setCanceled(true); }`
(`setSpawnCancelled(true)` is what stops the spawn; cancelling the event as well skips the pointless finalize.)

`onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event)`: `if (shouldBlock(event.getEntityType(), event.getSpawnType())) { event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL); }`

`register(IEventBus bus)`: `bus.addListener(FinalizeSpawnEvent.class, finalizeListener);` then `bus.addListener(MobSpawnEvent.SpawnPlacementCheck.class, placementListener);`
`unregister(IEventBus bus)`: `bus.unregister(finalizeListener);` then `bus.unregister(placementListener);`
(The listeners are fields so `unregister` gets the same object that `register` added; `register`/`unregister` can be called more than once.)

## Acceptance (Claude checks)
`.\gradlew build` passes; the hostile_blocker_* GameTests pass.
