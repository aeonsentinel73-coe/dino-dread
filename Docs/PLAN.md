# Dino Dread — Plan

## Design intent
The audience is a casual player who wants **big scary dinosaurs breaking in to eat you**,
layered on normal Minecraft survival. Fear comes mostly from the *approach*:
- hearing it before seeing it
- the ground shaking
- not knowing which direction it's coming from

The fight itself matters less. When time is short, protect the dread audio and atmosphere
over model polish or AI sophistication.

## Phase 0 — Validate (before code, one evening)
Build a throwaway modpack from existing dinosaur and horror/ambience mods plus a heavy-fog shader.
Play it with the group. Write down what felt right, what was missing, and what was annoying.
That list refines this plan.

## Milestones
Legend: **[C]** keep in Claude Code. **[L]** delegate to the local model.

### M1 — Setup
- [C] Choose versions and fill in the table in CLAUDE.md
- [L] NeoForge MDK project, GeckoLib dependency, git repo, CREDITS.md, `tasks/` folder
- [L] Register a placeholder T-rex entity (box model) and a spawn egg
- **Done when:** the rex spawns from an egg in `runClient`, and `runServer` boots cleanly.

### M2 — Atmosphere
- [C] Fog hook (distance and color, stronger at night) and lightmap darkening for day and night
- [L] Config entries for every strength value, with an on/off toggle
- **Done when:** visibly darker and foggier, adjustable from config, no crash on `runServer`.

### M3 — Rex brain
- [L] Attributes: health, damage, speed, knockback resistance (all from config)
- [C] AI goals: wander, then long-range detection (sound/sight; Warden is a reference), then
  stalk, then charge, then bite
- **Done when:** on flat open ground it finds you and kills you.

### M4 — Big body (hardest milestone; budget the most time)
- [C] Navigation that works for a ~4-block-wide mob (vanilla pathing assumes ~1 block)
- [C] Stuck detection and recovery
- [C] Block breaking: configurable allow-list (wood, glass, leaves by default; stone off),
  plus a griefing master toggle (Ravager is a reference)
- **Done when:** it reliably gets into a basic wooden house, and doesn't get stuck on
  trees and terrain most of the time.

### M5 — Dread audio
- [L] Sound event registration, sounds.json, subtitles
- [C] Footsteps audible far beyond render distance, plus slight camera shake scaled by distance
- [C] Roar when it detects a player; occasional distant roars at night
- Sourcing: CC0 libraries or AI sound-effect generators; log everything in CREDITS.md
- **Done when:** someone playing blind could tell it's coming and roughly how close.

### M6 — Spawning and balance
- [L] Spawn rules: rarity, night-only or biome options, cap per player (all config)
- [L] Config toggle to disable vanilla hostile mobs
- [C] Multiplayer test on the real dedicated server
- **Done when:** a session feels scary rather than tedious. Tune with the group.

### M7 — Art swap
- Find a Blockbench/GeckoLib-format model with an explicit permissive license, or get the
  author's permission, or commission one.
- Realistic meshes (e.g. Sketchfab) won't convert; it must be a cube-based Minecraft model.
- Animations needed: walk, run, idle, roar, bite. Likely hand-made in Blockbench if the model
  doesn't include them.
- [C] Wire animations to AI states.
- **Done when:** no placeholder boxes remain, and every asset is credited.

## Later (explicitly not v1)
- More dinosaur types (a raptor pack is the natural second)
- DNA/progression systems
