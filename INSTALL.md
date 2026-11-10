# Installing Dino Dread

You need three files in one folder. Takes about five minutes. Both players
(the one hosting the world and everyone joining it) need all three.

## What you need

1. **Minecraft: Java Edition**, version **26.3**, from the official launcher.
2. **NeoForge 26.3** — the mod loader.
3. **GeckoLib 5.5.7 (or newer) for NeoForge 26.3** — animation library.
4. **dinodread-1.2.0.jar** — this mod, from the GitHub release page.

> ⚠️ **Version matching is everything.** Every mod must be built for the same
> Minecraft version (26.3) and the same loader (NeoForge). A mod for 1.21 or
> Fabric will simply not load.

## Step 1 — install NeoForge

1. Go to https://neoforged.net and download the installer for **26.3**
   (it links to the latest 26.3.x build).
2. Run the `.jar` file (double-click; if it doesn't open, Java 25+ is
   installed with Minecraft — right-click → Open with → Java).
3. Choose **Install client** → OK.

Launch Minecraft once through the new **NeoForge** profile in the launcher
(profile selector next to the Play button) and quit. That creates the mods folder.

## Step 2 — drop in the two mod files

Open `%APPDATA%\.minecraft\mods` (Windows key, type `%APPDATA%`, go to
`.minecraft\mods`) and put these two files there:

- `geckolib-neoforge-26.3-5.5.7.jar` — download from
  https://modrinth.com/mod/geckolib (Versions tab, pick 26.3 + NeoForge)
- `dinodread-1.2.0.jar` — from this repo's Releases page

## Step 3 — play

Launch the NeoForge profile. If the game starts, everything is loaded:
create or open a world and the worldgen (giant trees) will apply to
**newly generated chunks** — fly out to fresh terrain to see them.

Dinosaurs spawn in the wild. To test a raptor pack immediately, open the
world to LAN with cheats (or use an op account) and run:

```
/dinodread raptorpack
```

## Multiplayer note

Every player's own installation needs all three files, AND if you run a
dedicated server instead of LAN, the same two `.jar` files go in that
server's `mods` folder too. A player without the mod cannot join the world.

## Uninstall

Delete the two `.jar` files from `mods`. Configs from first launch live in
`%APPDATA%\.minecraft\config\dinodread-*.toml` if you want to tune spawn rates.
