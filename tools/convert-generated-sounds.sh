#!/usr/bin/env bash
# Turns the AI-generated T-rex sounds (stereo FLAC) into the files the game plays: mono 44.1 kHz Ogg Vorbis,
# leading/trailing silence trimmed, tiny fades so nothing clicks. Overwrites the files of the same name in the sounds folder.
# Needs ffmpeg with libvorbis on PATH. Run from Git Bash:
#     bash tools/convert-generated-sounds.sh "D:/Downloads"
# Source names it looks for: "Detection Roar N", "Distant Roar N", "Footstep N" (or the misspelling "Foostep N"), "Bite N",
# with N = 1 and up, as .flac. Add a variant by generating it, naming it with the next number, adding a line below and listing the
# new file in src/main/resources/assets/dinodread/sounds.json.
#
# Two level modes:
#   peak  - set the loudest sample to PEAK_DB (everything but footsteps).
#   punch - for footsteps. The game never plays a sound above gain 1.0 (a higher volume only reaches further), so how loud
#           a step is at the player's ear is decided here. Every variant is brought to the same AVERAGE level (one of the
#           generated steps was 10 dB quieter than the rest), then tanh-saturated, which makes the thud read much louder
#           without clipping, and finally the peak is set to PUNCH_PEAK_DB.
set -euo pipefail

SRC="${1:?usage: bash tools/convert-generated-sounds.sh <folder with the .flac files>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/dinodread/sounds/entity/trex"
OUT_R="$ROOT/src/main/resources/assets/dinodread/sounds/entity/raptor"
# Native Windows ffmpeg cannot open git-bash's /tmp; scratch under %LOCALAPPDATA%\Temp (an MSYS path under /c/.. that converts cleanly).
TMP="$(cygpath -u "${LOCALAPPDATA:-$TEMP}/Temp")/dino-sounds.$$"
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT

PEAK_DB=-3            # headroom: the game plays these loud, and Vorbis can overshoot a little
PUNCH_MEAN_DB=-13     # average level every footstep is brought to before saturation
PUNCH_PEAK_DB=-1.5
LEAD_THRESHOLD_DB=-50 # audio quieter than this at the start/end counts as silence
TAIL_THRESHOLD_DB=-60
TAIL_KEEP_SECONDS=0.05
FADE_IN_SECONDS=0.002
FADE_OUT_SECONDS=0.04

mkdir -p "$OUT" "$OUT_R"

volume_stat() { # <file> <max|mean>
    ffmpeg -v info -i "$(cygpath -w "$1")" -af volumedetect -f null - 2>&1 | sed -n "s/.*$2_volume: \(-\?[0-9.]*\) dB.*/\1/p"
}

# convert <output name> <peak|punch> <source name 1> [<source name 2> ...]   (first source that exists wins)
# A source is found as $SRC/<name>.flac, $SRC/<name>.opus, <repo root>/<name>.opus, or art/sounds/<name>.opus
# (the raptor takes arrive as .opus files; their keepsakes live in art/sounds/). TARGET_OUT picks the output folder; it defaults to the T-rex's.
TARGET_OUT="$OUT"
convert() {
    local name="$1" mode="$2"; shift 2
    local in="" candidate
    for candidate in "$@"; do
        [ -f "$SRC/$candidate.flac" ] && { in="$SRC/$candidate.flac"; break; }
        [ -f "$SRC/$candidate.opus" ] && { in="$SRC/$candidate.opus"; break; }
        [ -f "$ROOT/$candidate.opus" ] && { in="$ROOT/$candidate.opus"; break; }
        [ -f "$ROOT/art/sounds/$candidate.opus" ] && { in="$ROOT/art/sounds/$candidate.opus"; break; }
    done
    [ -n "$in" ] || { echo "missing source for $name (looked for: $*) - skipped, keeping any existing $name.ogg" >&2; return 0; }

    # Pass 1: mono mix, trim leading and trailing silence. (Native ffmpeg gets native paths: this shell does not convert them.)
    ffmpeg -v error -y -i "$(cygpath -w "$in")" -af "pan=mono|c0=0.5*c0+0.5*c1,silenceremove=start_periods=1:start_threshold=${LEAD_THRESHOLD_DB}dB:start_silence=0.01,areverse,silenceremove=start_periods=1:start_threshold=${TAIL_THRESHOLD_DB}dB:start_silence=${TAIL_KEEP_SECONDS},areverse" "$(cygpath -w "$TMP/trimmed.wav")"
    local source="$TMP/trimmed.wav" target_peak="$PEAK_DB"
    if [ "$mode" = "punch" ]; then
        local mean punch_gain
        mean="$(volume_stat "$TMP/trimmed.wav" mean)"
        punch_gain="$(awk -v t="$PUNCH_MEAN_DB" -v m="$mean" 'BEGIN { printf "%.2f", t - m }')"
        ffmpeg -v error -y -i "$(cygpath -w "$TMP/trimmed.wav")" -af "volume=${punch_gain}dB,asoftclip=type=tanh" "$(cygpath -w "$TMP/punched.wav")"
        source="$TMP/punched.wav"
        target_peak="$PUNCH_PEAK_DB"
    fi

    local peak duration gain fade_start
    peak="$(volume_stat "$source" max)"
    duration="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$(cygpath -w "$source")")"
    gain="$(awk -v t="$target_peak" -v p="$peak" 'BEGIN { printf "%.2f", t - p }')"
    fade_start="$(awk -v d="$duration" -v f="$FADE_OUT_SECONDS" 'BEGIN { printf "%.4f", (d > f ? d - f : 0) }')"

    # Final pass: gain to the target peak, short fades, encode.
    ffmpeg -v error -y -i "$(cygpath -w "$source")" -af "volume=${gain}dB,afade=t=in:d=${FADE_IN_SECONDS},afade=t=out:st=${fade_start}:d=${FADE_OUT_SECONDS}" -ac 1 -ar 44100 -c:a libvorbis -q:a 5 "$(cygpath -w "$TARGET_OUT/$name.ogg")"
    printf "%-18s <- %-22s %-5s %5.2fs, mean %6s dB, peak %6s dB\n" "$name.ogg" "$(basename "$in")" "$mode" "$duration" "$(volume_stat "$TARGET_OUT/$name.ogg" mean)" "$(volume_stat "$TARGET_OUT/$name.ogg" max)"
}

convert roar1 peak "Detection Roar 1"
convert roar2 peak "Detection Roar 2"
convert roar3 peak "Detection Roar 3"
convert roar_distant1 peak "Distant Roar 1"
convert roar_distant2 peak "Distant Roar 2"
convert roar_distant3 peak "Distant Roar 3"
convert step1 punch "Footstep 1" "Foostep 1"
convert step2 punch "Footstep 2" "Foostep 2"
convert step3 punch "Footstep 3" "Foostep 3"
convert step4 punch "Footstep 4" "Foostep 4"
convert bite1 peak "Bite 1"
convert bite2 peak "Bite 2"
convert bite3 peak "Bite 3"
convert bite4 peak "Bite 4"

# Raptor: a running-gait loop and two hunting cries (made for this mod). peak mode; the run loop reuses the
# footstep volume in game. Sources: "Raptor Running", "Raptor Hunting Cry", "Raptor Hunting Cry 2" (.opus in the repo root).
TARGET_OUT="$OUT_R"
convert run peak "Raptor Running"
convert cry1 peak "Raptor Hunting Cry"
convert cry2 peak "Raptor Hunting Cry 2"

echo "Wrote $(ls "$OUT"/*.ogg "$OUT_R"/*.ogg | wc -l) files to $OUT and $OUT_R"
