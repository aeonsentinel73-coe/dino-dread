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
- **TRexVoice, the `nextStep()` stride override, the hunt-goal hooks:** design work. Finding that vanilla triggers a footstep
  when `moveDist` passes `nextStep()` (which is overridable) is what avoids a mixin.
- **Camera shake** (`client/RexShake`): driven by the `PlaySoundEvent` for the step and roar sounds, so it needs no custom
  packet and is always in sync with the audio.
- **Seven GameTests** (`RexAudioGameTests`), then a mutation check: with the behaviors disabled the matching tests failed,
  five in the first round (including 11 footsteps in 18 blocks at vanilla's stride versus about 3 with ours) and three in the
  second (daylight, hunting and cooldown suppression), so each test can actually fail.

## Not verified

Nothing has been heard or felt in a real client: how the placeholder sounds sound, whether the shake strengths feel right,
whether footsteps are audible enough at range. The knobs are in `Docs/AUDIO.md`.
