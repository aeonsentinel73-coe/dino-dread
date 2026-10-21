# M5 (dread audio): delegation log

Numbers come from the `usage:` line that local-vibes now prints on every `delegate` result (patched in on
2026-10-17, see CLAUDE.md). "Prompt" tokens are summed over every request of the agent loop, so the context re-sent on each
iteration counts each time; "peak context" is the largest single request.

| Piece | Who | Delegate calls | Iterations | Prompt tokens | Completion tokens | Peak context | Time |
|---|---|---|---|---|---|---|---|
| Sound registry, `sounds.json`, subtitles, `[audio]` config (tasks/014) | Qwen3.8-27B | 1 | 14 | 179,608 | 3,905 | 23,528 | 86 s |

The diff matched the spec line for line; no take-over was needed.

## What Claude did itself, and why

- **Placeholder sounds** (`tools/make-placeholder-sounds.sh`): synthesized with ffmpeg; mono 44.1 kHz Ogg Vorbis.
  Needed a measurement loop (peak level, channel count), not a spec.
- **Real sounds** (2026-10-19): Thomas generated 14 variants with Stable Audio 3 Medium; Claude read the licence, analysed each
  file (stereo, several peaking at 0 dB, trailing silence, `bite1` slow to build) and wrote `tools/convert-generated-sounds.sh`
  to turn them into mono -3 dB Ogg files that replace the placeholders. Licence summary is in `CREDITS.md`.
- **Tarbosaurus Rex model** (2026-10-19, CC BY 4.0, ziiwplay): the site requires a sign-in to download, so Thomas downloaded the
  `.bbmodel`; the GeckoLib plugin export gave `trex.geo.json` and `trex.animation.json` (the export confirmed the X-axis flips a
  hand-written converter would have had to guess), and the texture was extracted from the project file. Claude wired the
  animations: idle and walk (walk playback speed follows the ground speed so the feet match the footstep stride), and roar
  and bite driven by a synced `Action` flag, because GeckoLib's own animation-trigger packet crashes the GameTest server's
  mock players. Hitbox lowered from 12 to 8.1 blocks tall to match the model; culling box widened so the tail does not pop.
- **TRexVoice, the `nextStep()` stride override, the hunt-goal hooks:** design work. Finding that vanilla triggers a footstep
  when `moveDist` passes `nextStep()` (which is overridable) is what avoids a mixin.
- **Camera shake** (`client/RexShake`): driven by the `PlaySoundEvent` for the step and roar sounds, so it needs no custom
  packet and is always in sync with the audio.
- **Seven GameTests** (`RexAudioGameTests`), then a mutation check: with the behaviors disabled the matching tests failed,
  five in the first round (including 11 footsteps in 18 blocks at vanilla's stride versus about 3 with ours) and three in the
  second (daylight, hunting and cooldown suppression), so each test can actually fail.

## Playtest 1 follow-up (2026-10-20)

Feedback from the first real-client session: shake too faint (5x), footsteps too quiet, "his pathing really struggles with hills", "a hard
time escaping trees", and "he started to break the tree in front of him, but when it was just a few floating leaves left he got stuck".

| Piece | Who | Delegate calls | Iterations | Prompt tokens | Completion tokens | Peak context | Time |
|---|---|---|---|---|---|---|---|
| `foliageBlocks` and `biteReachBelow` config entries and labels (tasks/015) | Qwen3.8-27B | 1 | 7 | 51,167 | 1,722 | 11,585 | 35 s |

Claude wrote `RexTerrainGameTests` first (hill ramps, rolling hills, a leaf-and-log cage, a plank cage) and ran it against the old
code: the plain ramps already passed (the M4 "sand hill" had only ever been a straight ledge), and two tests failed. What they found:

- **Valleys, not slopes.** A 9-wide body cannot get down into a valley narrower than itself, so it straddled the dip with the player 6 blocks below,
  out of bite reach (3 blocks). Fix: `biteReachBelow` (8) extends the bite reach downward.
- **Trees were a trap once the hunt ended.** Wandering and chasing animals could not break anything, so a rex that lost its player in a
  forest (leaves block sight, sneaking makes no noise, 240 ticks and it forgets) was boxed in by the last leaves and logs. Fix: a
  break policy (`RexBlockBreaker.Policy`): hunting breaks anything not unbreakable, wandering and prey break only `foliageBlocks`
  (leaves and logs), so a player's planks and glass stay safe (the plank-cage test guards that).
- **A phase-lock hid the collisions.** `horizontalCollision` flips every other tick while pushing at a wall, and the wander goal only ticks
  every second tick, so it read "no collision" every time and broke blocks only when the stuck detector escalated (6 blocks per 40 ticks
  against 48 for a hunt). Fix: `TRex.collidedRecently()` samples every tick, and the wander goal ticks every tick. Cage escape went from
  about 2000 ticks to about 500.

## Playtest 2 follow-up (2026-10-20)

Feedback: the rex climbs over trees and walks on top of them; it needs to break blocks faster and may leave rubble (no drops); it must be skinnier
and a little shorter, with less of the hitbox wasted on the tail.

- **Climbing:** the stuck-recovery hop (0.9 upward speed, about 4.5 blocks under vanilla gravity) plus the 3 block step height lands it on a canopy.
  With foliage breaking fixed the rex no longer needed to hop in the dense-forest test (it stayed at ground level), but nothing made it come
  down once up there. Test first (a rex on a leaf platform inside an unbreakable stone ring: stayed up forever), then
  `RexBlockBreaker.breakFoliageBeneath()`: foliage under its feet is broken, so treetops are not ground (back on the ground in ~100 ticks).
- **Faster, no drops:** `breakIntervalTicks` 4 to 2, `blocksPerBreak` 6 to 24, `breakDropsItems` off. About 6.7x faster; server tick time stayed at 3-4 ms
  while it cleared 243 blocks.
- **Hitbox:** now config (`hitboxWidth`, `hitboxHeight`, `eyeHeight`, `modelOffsetBlocks`): 4.5 wide x 6.6 tall (was 9 x 8.1), and the model is shifted
  1.5 blocks back in the renderer so the hitbox sits over the chest and legs, not the hips and tail.
- **What the narrower body broke** (found by the suite): the investigate-arrival distance was measured from the rex's centre (6 blocks), fine
  for a 9 wide body but it stopped a narrow one 3.75 blocks short of a player hidden in a roofed house, where it stared forever without
  breaking anything. It is now `investigateReachFromEdge` (1.5, from the hitbox edge; the old key is retired so stale configs cannot apply the
  wrong meaning). Two M4 tests that placed structures from a fixed 9 wide body now derive them from the real hitbox.

## Playtest 3 follow-up: water (2026-10-20)

Report: "he seemed to get stuck in the water"; idea: a rex that can swim fast but stays slower than a boat, so hiding in water fails but a boat still escapes.
Reproduced first in `RexWaterGameTests` (a 27 block lake with the bank a block above the water, as on a real shore, the player on the far side):

- **Slow:** a land mob crawls in water (vanilla swim acceleration 0.02, drag 0.8). This T-rex measured 0.5 to 0.9 blocks/s, so a lake took over a minute.
- **Stuck:** vanilla only lets a swimmer pull itself out onto a ledge about 0.6 blocks up, so a bank a block above the water was a wall: the rex sat against the far
  bank for the rest of the test (3000 ticks), bobbing a quarter block short of the top. My first assist lived inside the water-only physics and stopped
  the moment the feet left the water, a quarter block too early; the trace (collision, vertical speed, in-water flag per tick) showed that. It now keeps
  pushing for 10 ticks after leaving the water (`waterExitHeight`, 3 blocks; 0 turns it off).
- **Speed fix and a trap in it:** `water_movement_efficiency` cannot do it (it is halved while swimming), so the config is NeoForge's swim speed attribute
  (`swimSpeedMultiplier`, 8). But NeoForge also multiplies the float nudge by that attribute, and at 8 the rex was thrown out of the water and leapt across the lake like a
  dolphin (only 45 of about 200 crossing ticks counted as in water; the position log showed it 1.6 blocks above the surface). `TRex.jumpInFluid` keeps the vanilla 0.04.
- **Result:** in the water it charges at about 6.3 blocks/s and stalks at about 3.1 (swim acceleration scales with the hunt speed). A swimming player manages roughly
  2 to 4, and a boat 8 (0.04 / (1 - 0.9); friction is applied before the acceleration is added). Test bands: charging 5.0 to 6.8, average at least 3.5.
- **Mutation checks:** swim multiplier 1 fails both lakes (0.80 blocks/s); the assist off (`waterExitHeight = 0`) fails both (the deep one never gets across).
- A theory-first estimate of the speed was off by about a factor of two; measuring was the only thing that worked. The lake tests also left their dug-out basin
  behind and broke an unrelated spawner test that reuses the same ground, so they now restore the arena floor in a cleanup.

## Playtest 3 follow-up: submerging (2026-10-20)

Report, after the water fix was confirmed in the client: "he's doing a Jesus", i.e. a swimming rex rode on top of the water. Vanilla's float logic taps jump while more than 0.4 blocks
of the body is under water (`Entity.getFluidJumpThreshold`), so the rex floated with its feet 0.4 under the surface. The test came first (deep lake, average body-under-water over the
middle of the lake): 0.31 blocks on the old code, so it failed.

- **Swim depth:** `swimDepth` (3 blocks by default; 0 turns it off) is the new threshold: `TRex.getFluidJumpThreshold()` returns it, so the float goal only pushes up from deeper than that.
  In water shallower than the depth it does not float at all and wades along the bottom (the 2-block shallow lake measures 1.9 blocks under water and a charge speed of 6.5, the same as on land).
- **Banks:** against a bank it needs the vanilla float level, because the climb-out assist only works when the body raised by `waterExitHeight` clears the bank, and at 3 blocks down it does not.
  So the depth drops to 0 for 10 ticks after any horizontal collision. Mutation: without that clause the deep lake still gets across but slowly (charging 4.59 blocks/s, under the 5.0 floor).
- **Sinking:** water sinks a mob by only about half a block a second, so after every time the rex rose to climb out it would have ridden the surface again for seconds. A 0.02 per tick
  downward push applies while it is more than 0.3 blocks above its depth. Tested separately (placed at rest on the surface: 2.5 blocks down after 23 ticks; with the push off only 1.5 blocks after 50
  ticks, so that test fails). The lake test alone did not catch this: the drop off the bank into the lake carries the rex down with it.
- **Result** (four full runs, 61 of 61 each): in the deep lake it swims with 2.86 to 2.91 of its 6.6 blocks under water and the eyes never go under. The charge speed measured
  over the whole crossing dropped from about 6.2 to 5.35 to 5.66 blocks/s, still inside the 5.0 to 6.8 band: the figure includes the moment at the far bank where it rises to climb out. The shallow
  lake (wading) is 6.52 every time. A boat is 8.

## Not verified

Nothing has been heard or felt in a real client: how the placeholder sounds sound, whether the shake strengths feel right,
whether footsteps are audible enough at range. The knobs are in `Docs/AUDIO.md`.
