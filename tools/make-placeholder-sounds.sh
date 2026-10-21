#!/usr/bin/env bash
# Regenerates the PLACEHOLDER T-rex sounds with ffmpeg's built-in synthesizers (nothing sampled, nothing downloaded).
# They exist so the sound code can be tested before real audio does. Replace them by dropping real files with
# the same names into the output folder (see Docs/AUDIO.md). Needs ffmpeg with libvorbis on PATH.
# Run from anywhere in Git Bash:  bash tools/make-placeholder-sounds.sh
set -euo pipefail

# The real, generated sounds now live under the same names (see tools/convert-generated-sounds.sh and CREDITS.md).
if [ "${1:-}" != "--force" ]; then
    echo "Refusing to run: this would overwrite the real T-rex sounds with synthesized placeholders. Use --force if you mean it." >&2
    exit 1
fi

OUT="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/assets/dinodread/sounds/entity/trex"
mkdir -p "$OUT"

# synth <file> <seconds> <aevalsrc expression> [extra audio filters]
# Output is mono 44.1 kHz Ogg Vorbis: Minecraft only attenuates mono sounds by distance.
synth() {
    local file="$1" dur="$2" expr="$3" filters="${4:-anull}"
    ffmpeg -v error -y -f lavfi -i "aevalsrc='${expr}':s=44100:d=${dur}" \
        -af "${filters},alimiter=limit=0.7:level=0" -ac 1 -c:a libvorbis -q:a 4 "${OUT}/${file}.ogg"
}

# Footsteps: a falling sine thump plus a short noise burst, low-passed. Three slightly different pitches.
for n in 1 2 3; do
    base=$((34 + n * 4))
    synth "step${n}" 0.7 "0.9*sin(2*PI*(${base}+30*exp(-14*t))*t)*exp(-9*t)+0.35*(random(0)*2-1)*exp(-28*t)" "lowpass=f=260,volume=1.6"
done

# Bite: a crunch of band-passed noise over a short low thump.
synth "bite1" 0.45 "0.7*(random(0)*2-1)*exp(-16*t)+0.6*sin(2*PI*90*t)*exp(-12*t)" "bandpass=f=900:width_type=h:w=1800,volume=2.0"

# Roars: detuned low buzz with vibrato and tremolo under a noise bed, swelling and fading; two variants.
roar() { # <file> <seconds> <fundamental Hz> [extra filters]
    local f0="$3"
    synth "$1" "$2" "(0.5*sin(2*PI*${f0}*t+3*sin(2*PI*6*t))+0.3*sin(2*PI*${f0}*2*t+2*sin(2*PI*7*t))+0.2*sin(2*PI*${f0}*3*t)+0.25*(random(0)*2-1))*pow(sin(PI*t/$2),0.6)*(0.7+0.3*sin(2*PI*23*t))" "lowpass=f=1800,asoftclip,volume=1.5,${4:-anull}"
}
roar "roar1" 2.4 70
roar "roar2" 2.8 62

# Distant roar: the same idea, muffled, with a long echo tail as if heard across a valley.
roar "roar_distant1" 3.2 58 "lowpass=f=700,aecho=0.8:0.8:300|700:0.5|0.3"

echo "Wrote placeholder sounds to ${OUT}"
