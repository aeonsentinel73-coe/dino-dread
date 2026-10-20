# Dino Dread audio: what to produce and where it goes

The code plays four sound events. Right now each one is a synthesized placeholder
(`tools/make-placeholder-sounds.sh`). To use real audio, drop files with the same names into
`src/main/resources/assets/dinodread/sounds/entity/trex/` and overwrite the placeholder.
No code change is needed. To add variants, add files and list them in
`src/main/resources/assets/dinodread/sounds.json` (the game picks one at random).

## File format (matters)

- **Ogg Vorbis (`.ogg`), mono, 44.1 kHz.** Minecraft only fades *mono* sounds with distance and
  direction. A stereo file plays flat in your head, at full volume, wherever the T-rex is, which
  defeats the point.
- Peak around -3 dB, not 0 dB. The game scales volume up for far-carrying sounds and clipping
  gets ugly.
- Short files for steps and bites; roars can be long. Leave the tail natural, no hard cut.
- Convert with ffmpeg: `ffmpeg -i in.wav -ac 1 -ar 44100 -c:a libvorbis -q:a 4 out.ogg`

## The four events

| Event | Plays when | Files (placeholder names) | Wanted |
|---|---|---|---|
| `entity.trex.roar` | It detects a player and starts hunting | `roar1.ogg`, `roar2.ogg` | The big one. 2-4 s, a deep, throaty scream with a lot of low end that rises, then drops into a rumble. Close and dry; the player should feel it in their chest. |
| `entity.trex.roar_distant` | Now and then at night while it wanders, heard from far away | `roar_distant1.ogg` | The same creature, but as if across a valley: muffled highs, long echo, 3-5 s. It should say "something huge is out there" without telling you where. |
| `entity.trex.step` | Every footstep while it walks or runs | `step1.ogg` to `step3.ogg` | A heavy low thud, 0.5-1 s, mostly below 200 Hz, with a faint ground rumble tail. Three variants so it does not sound like a machine. Camera shake is triggered by this sound. |
| `entity.trex.bite` | Its bite lands | `bite1.ogg` | A short, wet crunch, 0.3-0.6 s. |

Footsteps and roars are meant to carry far past render distance, so they are played very loud
in game terms (audible range is about 16 blocks times the volume). Keep the source files clean and
leave the loudness to the config.

## Rules for anything that gets added

- Every source (a tool, a library, a recording) goes into `CREDITS.md` with source, author and
  licence, **before** the file goes into the repo. For AI generators also note the tool, the
  date and the prompt, and check the tool's terms allow redistributing what it makes in a mod.
- Don't base a sound on a recognisable sound from a film, game or other commercial work. If it
  isn't clearly licensed, it stays out.
- No sound files without that `CREDITS.md` line. If in doubt, ask.
