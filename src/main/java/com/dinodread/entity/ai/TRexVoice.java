package com.dinodread.entity.ai;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;
import com.dinodread.registry.ModSounds;
import com.dinodread.world.DayNight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Everything the T-rex makes noise with: the roar when it starts hunting, the footsteps, the bite, and the occasional
 * roar at night that is meant to be heard from far away. Every volume, interval and cooldown comes from {@link RexConfig}.
 * The counters are how GameTests observe that a sound was played.
 * <p>
 * How far a sound carries is 16 blocks times its volume (the sound events are variable-range), so these play at volumes
 * well above vanilla's and are audible far beyond render distance.
 */
public class TRexVoice {
    /** Random pitch spread around 1.0, so repeated steps and roars do not sound like one looped file. */
    private static final float PITCH_VARIATION = 0.1F;
    /** {@link DayNight} night factor (0 = full day, 1 = full night) from which it may roar at night. */
    private static final float DISTANT_ROAR_MIN_NIGHT_FACTOR = 0.5F;
    /** Stands in for "last roared long ago", so the first roar is never held back by the cooldown. */
    private static final int NEVER_ROARED = Integer.MIN_VALUE / 2;

    private final TRex rex;
    private int lastRoarTick = NEVER_ROARED;
    private int distantRoarCountdown;
    private int roarCount;
    private int distantRoarCount;
    private int stepCount;
    private int biteCount;

    public TRexVoice(TRex rex) {
        this.rex = rex;
        this.distantRoarCountdown = nextDistantRoarDelay();
    }

    /** The roar of a T-rex that has just noticed a player. Returns false (and stays quiet) while still on cooldown. */
    public boolean roar() {
        if (rex.tickCount - lastRoarTick < RexConfig.ROAR_COOLDOWN_TICKS.get()) {
            return false;
        }

        lastRoarTick = rex.tickCount;
        roarCount++;
        play(ModSounds.TREX_ROAR, RexConfig.ROAR_VOLUME.get());
        return true;
    }

    public void step() {
        stepCount++;
        play(ModSounds.TREX_STEP, RexConfig.FOOTSTEP_VOLUME.get());
    }

    public void bite() {
        biteCount++;
        play(ModSounds.TREX_BITE, RexConfig.BITE_VOLUME.get());
    }

    /**
     * Runs every server tick. The countdown to the next distant roar only runs on a night-time T-rex that is wandering, not
     * hunting; in daylight or during a hunt it simply waits, so the roar is never wasted on a moment that has its own sounds.
     */
    public void tick(ServerLevel level) {
        if (!RexConfig.DISTANT_ROAR_ENABLED.get()
            || rex.getHuntState() != HuntState.WANDER
            || rex.getSenses().hasQuarry()
            || DayNight.nightFactor(level, rex.position()) < DISTANT_ROAR_MIN_NIGHT_FACTOR) {
            return;
        }

        if (--distantRoarCountdown > 0) {
            return;
        }

        distantRoarCountdown = nextDistantRoarDelay();
        distantRoarCount++;
        play(ModSounds.TREX_ROAR_DISTANT, RexConfig.DISTANT_ROAR_VOLUME.get());
    }

    /** Test hook: make the next distant roar come after this many qualifying ticks. */
    public void scheduleDistantRoar(int ticks) {
        distantRoarCountdown = ticks;
    }

    public int roarCount() {
        return roarCount;
    }

    public int distantRoarCount() {
        return distantRoarCount;
    }

    public int stepCount() {
        return stepCount;
    }

    public int biteCount() {
        return biteCount;
    }

    /** Between half and one and a half times the configured average, so the roars are not on a metronome. */
    private int nextDistantRoarDelay() {
        int average = RexConfig.DISTANT_ROAR_INTERVAL_TICKS.get();
        return average / 2 + rex.getRandom().nextInt(average);
    }

    private void play(DeferredHolder<SoundEvent, SoundEvent> sound, double volume) {
        float pitch = 1.0F + (rex.getRandom().nextFloat() - rex.getRandom().nextFloat()) * PITCH_VARIATION;
        rex.playSound(sound.get(), (float) volume, pitch);
    }
}
