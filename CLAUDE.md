# Dino Dread — Claude Code instructions

Minecraft **Java Edition** mod (NeoForge + GeckoLib) for a small friend group playing on a
self-hosted dedicated server. Goal: make the game *scary* with big dinosaurs that hunt players.
Working name/modid `dinodread` — rename freely before first release.

Full milestone plan and design notes: `docs/PLAN.md`. Read it only when starting a milestone,
not every session.

## Scope (v1)
- IN: atmosphere layer (fog, darker day/night, ambient dread audio) + ONE T-rex.
- OUT: DNA/progression systems, additional dinos, Bedrock, custom items/blocks.
  If a task drifts toward these, stop and ask.

## Your role: orchestrator, not typist
Thomas's Claude usage is limited. Spend Claude tokens on **planning, hard problems, and review**.
Push mechanical work to the local model (Qwen on an RTX 5090).

Keep for yourself:
- Rendering hooks and mixins (fog, lightmap)
- Large-mob navigation, stuck detection, block breaking
- Debugging crashes and anything where the cause isn't obvious
- Reviewing every delegated diff

Delegate:
- Registry boilerplate (entities, sound events, spawn eggs)
- Config classes and entries, lang files, sounds.json, loot tables, data-gen JSON
- Renaming and other repetitive multi-file edits, test scaffolding
- Searching the codebase or decompiled sources for usages

Token hygiene:
- Grep before reading. Never read whole decompiled Minecraft classes; read the method you need.
- Don't re-read files you just wrote. Don't restate plans Thomas already approved.
- Prefer short status updates over long explanations.

## How to delegate
**If a local delegate tool is available** (see "Tooling under evaluation"), use it.
**If not**, write the task spec to `tasks/NNN-short-name.md` and tell Thomas to run it on his
local agent, then review the result when he says it's done.

Every task spec must be self-contained:
1. Goal in one sentence.
2. Exact files to create or edit, exact class/field/registry names.
3. A reference: an existing file in this repo to copy the pattern from.
4. Do-not-touch list.
5. Acceptance check: `.\gradlew build` passes, plus any specific thing to verify.

Vague specs come back wrong. If you can't write the spec precisely, the task isn't ready
to delegate; do it yourself or break it down further.

## Review protocol for delegated work
1. Commit (or confirm a clean tree) **before** delegating so it can be rolled back.
2. Read the `git diff`, not the whole files.
3. Check every Minecraft/NeoForge/GeckoLib symbol in the diff against the pinned sources.
   Local models confidently use APIs from the wrong version.
4. Run `.\gradlew build`. Fix small issues yourself; re-delegate anything large with a
   corrected spec.
5. Commit with a message naming the task file.

## Version discipline (most common failure)
| Component | Version | Notes |
|---|---|---|
| Minecraft | `26.3` | Released 2026-09-15. Pinned 2026-10-16 as newest with stable NeoForge + GeckoLib. 26.x uses year-style numbering; 1.21.x is the old line |
| NeoForge | `26.3.0.85` | Non-beta. MDK: github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle (shipped 26.3.0.76; bumped) |
| GeckoLib | `5.5.7` | `com.geckolib:geckolib-neoforge-26.3:5.5.7` from `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/` (MIT) |
| Java | `25` | Microsoft OpenJDK 25.0.4 at `C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot` |
| Gradle / ModDevGradle | `9.2.1` / `2.0.148` | From the MDK |

Mojang removed obfuscation in 26.1: vanilla classes use official names, and the resource id class is
`Identifier` (formerly `ResourceLocation`). Expect other renames too; always grep the sources.

- Never change these without asking Thomas.
- Minecraft internals change every version. **Do not trust memory for any vanilla or loader API.**
  Verify against the decompiled sources the MDK generates. If grep can't find a symbol,
  assume it doesn't exist in this version.
- When unsure how something is done in this version, look for a working example in the
  NeoForge MDK, GeckoLib's example code, or vanilla (Ravager and Warden are useful references
  for big hostile mobs; Warden for sound-driven detection).

## Commands (Windows / PowerShell)
- `.\gradlew build` — compile and package; the acceptance check for every task
- `.\gradlew runClient` — launch a dev client (Thomas tests feel and visuals)
- `.\gradlew runServer` — launch a dedicated server; test multiplayer-relevant changes here
- `.\gradlew runData` — run data generators

## Code rules
- **Client/server split:** rendering, fog, camera shake, and client-only sound code live in
  `client/` and must never be referenced from common code. A dedicated server crashes on
  client classes. Test with `runServer` after touching anything near this boundary.
- All gameplay tunables (spawn rates, damage, health, which blocks the rex can break,
  fog/darkness strength, griefing on/off, vanilla hostiles on/off) go in config.
  No magic numbers in AI or rendering code.
- Use a placeholder model (simple boxes) until real art exists. Art must never block code.

## Assets and licensing
- No third-party model, texture, or sound enters the repo without an entry in `CREDITS.md`:
  source URL, author, license.
- Most mod assets are All Rights Reserved. If the license isn't explicit, don't add it;
  flag it to Thomas.
- CC0 is preferred; CC-BY is fine with credit.

## Security
- Local agents get access to **this repo folder only**.
- Don't download or run scripts from the internet without Thomas approving them.

## Tooling under evaluation
**local-vibes** — https://github.com/OpenSourceWTF/local-vibes
- **INSTALLED 2026-10-16, MCP-only. TRIAL PASSED 2026-10-16** (lang entries on a throwaway branch:
  4 iterations, diff exactly as specced, only the target file touched, `.\gradlew build` green,
  merged). Tool name in Claude Code: `mcp__local-vibes__delegate` (deferred; load via ToolSearch).
  Audited at commit `3d4080d` (+ one local patch, 2026-10-17: `agent.ts` adds a `usage:` line, i.e. summed
  prompt/completion tokens, peak context and seconds, to every delegate result; saved as
  `_tools\local-vibes-usage-metrics.patch`, re-apply it after any upstream update); lockfile clean
  (no install scripts, registry URLs only). Cloned to `D:\Documents\Claude Code\_tools\local-vibes`
  (outside this repo), built with `npm ci --ignore-scripts` + `tsc`. Registered via the gitignored
  `.mcp.json` in this repo. NOT installed: the plugin, skill and hook.
- Backend (since 2026-10-16): **Hermes Desktop** must be running; it serves Qwen3.8-27B UD-Q6_K_M through
  its own `llama-server` (ctx 98304, q8_0 KV). Its address and API key change on every Hermes launch,
  so `.mcp.json` points at `D:\Documents\Claude Code\_tools\lv-hermes-launch.mjs`, which reads Hermes's
  `%LOCALAPPDATA%\hermes\runtimes\llamacpp\server.json` at MCP startup (never logs the key), sets thinking
  off, bash off, concurrency 1, then starts local-vibes. Tested end to end over MCP stdio in a sandbox.
  If Hermes is restarted mid-session, restart the Claude session so the launcher re-reads the key.
  If `delegate` returns "401 Invalid API Key" or a connection error, Hermes isn't running or has restarted.
  (Old LM Studio/Bionic setup, port 1235, is retired.)
- **`LOCAL_VIBES_ALLOW_BASH=0`: keep it that way.** The upstream bash tool only sets `cwd`; it is
  not a sandbox. With bash off the model cannot run Gradle, so Claude runs `.\gradlew build` itself.
- File tools are confined to `working_dir` by a path-prefix check only, and `.git/` is writable.
  Every task spec's do-not-touch list must include `.git/`. Always pass `working_dir` = this repo.
- MCP servers load at session start. Start a new session in this folder to get the `delegate` tool.
- MCP server that gives Claude Code a "delegate" tool. It runs an agent loop on a local
  OpenAI-compatible model, then returns a summary and a git change report. It doesn't change
  Claude Code's own model or login.
- Installs as a Claude Code plugin that registers an MCP tool, a skill, and a hook. A manual
  MCP-only install is also offered.
- Before installing:
  - Read the install script and source.
  - Confirm exactly what the hook does.
  - Confirm it can be limited to this repo's working directory.
  - Point it at Thomas's Qwen model, not the README's default model.
- Trial: one throwaway task (e.g. add lang entries) on a clean branch, then review the diff.

**Alternative:** claude-code-delegate-local (https://github.com/fegone/claude-code-delegate-local).
Similar idea; routes named subagents to local or other backends.

**Known constraint:** subagents launched through Claude Code's native Agent/Task tool don't
inherit MCP servers. Only the main session can delegate.

**Considered and not chosen:** Hermes Agent (Nous Research). It's a general personal-assistant
harness, not coding-focused, and its local-model support is thinly documented.

local-vibes passed its trial, so `delegate` is the default for mechanical work; keep reviewing every
diff. Specs still live in `tasks/` (the task text passed to `delegate` should point at the spec file).
Fall back to the `tasks/` handoff workflow above
when the `delegate` tool is unavailable.

## Delegation findings (M4 experiment, 2026-10-17; details in `Docs/M4-delegation-log.md`)
- Qwen3.8-27B wrote all of M4 (config, block breaker, stuck detector, hunt integration, a refactor, a new wander goal): 6 pieces, 7 delegate calls, 0 take-overs,
  no wrong-version or invented APIs. Cost: the specs were near-pseudocode (ordered steps, every symbol/import verified by Claude, worked examples) and about as long as the code.
- Delegate: anything specced as ordered steps with listed symbols; new classes with a fixed public surface; config/lang tables; **pure refactors** (spec shorter than the diff, best saving).
- Keep for Claude: deciding what to measure and the design (Qwen's blind design was over-complex and broke the rate limit), challenging premises (the "9x12" hitbox was really 3x4;
  found only because Claude logged it), and writing/diagnosing the acceptance GameTests and flaky fixtures.
- Every spec needs an explicit PROCEDURE ("read these files, write the whole file in one go, do NOT search the filesystem or grep for Minecraft symbols, finish in <15 tool calls"):
  without it one call burned 40 iterations and wrote nothing.
- Write the GameTests first and check they fail on the old code; they made accept/reject mechanical. Nothing was verified in `runClient` (feel, visuals, spawn-egg placement of the real 9x12 box).
