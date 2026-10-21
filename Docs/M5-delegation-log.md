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

## Not verified

Nothing has been heard or felt in a real client: how the placeholder sounds sound, whether the shake strengths feel right,
whether footsteps are audible enough at range. The knobs are in `Docs/AUDIO.md`.
