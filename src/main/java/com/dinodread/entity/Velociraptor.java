package com.dinodread.entity;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RaptorConfig;
import com.dinodread.entity.ai.HuntState;
import com.dinodread.entity.ai.RaptorFleeGoal;
import com.dinodread.entity.ai.RexBlockBreaker;
import com.dinodread.entity.ai.TRexHuntGoal;
import com.dinodread.entity.ai.TRexPreyGoal;
import com.dinodread.entity.ai.TRexWanderGoal;
import com.dinodread.registry.ModSounds;
import com.dinodread.spawn.RexSpawner;
import com.dinodread.world.DayNight;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * A velociraptor: the same hunting brain as the T-rex (senses, hunt goal, mover, voice) running on {@link RaptorConfig}
 * numbers, plus pack behaviour. It is smaller and quicker, cannot charge, sees hostile mobs as well as players, and hates
 * deep water (swim speed near zero). Its sounds are its own ids, which keeps it out of the T-rex camera shake.
 * <p>
 * A natural pack shares a {@code dinodread_pack_<uuid>} scoreboard tag; when any member of a tagged pack dies, every
 * survivor starts fleeing (see {@link RaptorFleeGoal} and {@link com.dinodread.spawn.RaptorSpawner}) and despawns when
 * the flee runs out. The model is the Peridot Scout (CC0, see CREDITS.md); its sounds were made for this mod.
 */
public class Velociraptor extends TRex {
    /** Prefix of the scoreboard tag that binds a natural pack together. */
    public static final String PACK_TAG_PREFIX = "dinodread_pack_";

    // Animation names inside geckolib/animations/entity/velociraptor.animation.json (the Peridot Scout's own names).
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.peridot_scout.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.peridot_scout.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.peridot_scout.run");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.peridot_scout.swim");
    private static final RawAnimation CRY = RawAnimation.begin().thenPlay("animation.peridot_scout.roar");
    private static final RawAnimation ATTACK_LEFT = RawAnimation.begin().thenPlay("animation.peridot_scout.attack_left");
    private static final RawAnimation ATTACK_RIGHT = RawAnimation.begin().thenPlay("animation.peridot_scout.attack_right");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.peridot_scout.death");
    private static final RawAnimation TAIL_WHIP = RawAnimation.begin().thenPlay("animation.peridot_scout.tail_whip");
    private static final String ATTACK_RIGHT_TRIGGER = "attack_right";
    private static final String TAIL_WHIP_TRIGGER = "tail_whip";
    // The Peridot animations' own lengths, in ticks: call 3.0 s, attacks 1.0 s, tail whip 1.6 s.
    private static final int CRY_ACTION_TICKS = 60;
    private static final int ATTACK_ACTION_TICKS = 20;
    private static final int TAIL_WHIP_ACTION_TICKS = 32;
    // Idle variety: while a raptor stands around wandering, a tail whip fires about every 15 seconds on average.
    private static final double IDLE_TAIL_WHIP_CHANCE_PER_TICK = 1.0 / 300.0;
    // The walk animation is 4.8 s long with two strides per loop (each foot lands twice), so 4 footfalls per cycle.
    private static final double PERIDOT_WALK_CYCLE_SECONDS = 4.8;
    private static final int PERIDOT_FOOTFALLS_PER_CYCLE = 4;
    // The running-gait sound is a ~4.3 s file restarted this often while sprinting, so the gallop runs on without gaps.
    private static final int RUN_SOUND_PERIOD_TICKS = 85;

    private int nextRunSoundTick;
    private boolean lastBiteWasLeft;

    public Velociraptor(EntityType<? extends Velociraptor> type, Level level) {
        super(type, level);
    }

    // The flee outranks the hunt: a raptor whose pack broke runs, it does not fight back.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RaptorFleeGoal(this));
        goalSelector.addGoal(2, new TRexHuntGoal(this));
        goalSelector.addGoal(3, new TRexPreyGoal(this));
        goalSelector.addGoal(4, new TRexWanderGoal(this));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, RaptorConfig.MAX_HEALTH.get())
            .add(Attributes.MOVEMENT_SPEED, RaptorConfig.MOVEMENT_SPEED.get())
            .add(Attributes.KNOCKBACK_RESISTANCE, RaptorConfig.KNOCKBACK_RESISTANCE.get())
            .add(Attributes.ATTACK_DAMAGE, RaptorConfig.ATTACK_DAMAGE.get())
            .add(Attributes.FOLLOW_RANGE, RaptorConfig.FOLLOW_RANGE.get())
            .add(Attributes.STEP_HEIGHT, RaptorConfig.STEP_HEIGHT.get())
            // A small body should not clear a full player-jump: vanilla's strength is tuned for a 1.8-block frame.
            .add(Attributes.JUMP_STRENGTH, RaptorConfig.JUMP_STRENGTH.get())
            .add(NeoForgeMod.SWIM_SPEED, RaptorConfig.SWIM_SPEED_MULTIPLIER.get())
            .add(Attributes.SCALE, RaptorConfig.SCALE.get());
    }

    // The rex stuck hop (0.9, roughly 4 blocks) was sized for the 3x T-rex; a raptor clears ledges with a real raptor's
    // leap instead, so it can still hop a one-block bank or fallen log without launching itself over the canopy.
    @Override
    public double stuckHopVelocity() {
        return RaptorConfig.JUMP_STRENGTH.get();
    }

    @Override
    public double stuckHopForwardVelocity() {
        // Roughly its chase speed, so a hop carries it onward instead of stalling it under the prey.
        return 0.45;
    }

    @Override
    public double activity() {
        return Mth.lerp(DayNight.nightFactor(level(), position()), RaptorConfig.DAY_AGGRESSION.get(), 1.0);
    }

    @Override
    public double sightRange() {
        return RaptorConfig.SIGHT_RANGE.get();
    }

    @Override
    public double sightFieldOfViewDegrees() {
        return RaptorConfig.SIGHT_FOV_DEGREES.get();
    }

    @Override
    public double sneakSightMultiplier() {
        return RaptorConfig.SNEAK_SIGHT_MULTIPLIER.get();
    }

    @Override
    public double hearingRadius() {
        return RaptorConfig.HEARING_RADIUS.get();
    }

    @Override
    public double sneakHearingMultiplier() {
        return RaptorConfig.SNEAK_HEARING_MULTIPLIER.get();
    }

    @Override
    public int giveUpTicks() {
        return RaptorConfig.GIVE_UP_TICKS.get();
    }

    @Override
    public double biteReach() {
        return RaptorConfig.BITE_REACH.get();
    }

    @Override
    public double biteReachBelow() {
        return RaptorConfig.BITE_REACH_BELOW.get();
    }

    @Override
    public int biteWindupTicks() {
        return RaptorConfig.BITE_WINDUP_TICKS.get();
    }

    @Override
    public int biteCooldownTicks() {
        return RaptorConfig.BITE_COOLDOWN_TICKS.get();
    }

    // No charge: a raptor walks its target down, it does not sprint at it.
    @Override
    public boolean canCharge() {
        return false;
    }

    @Override
    public boolean huntsHostileMobs() {
        return RaptorConfig.HUNT_HOSTILE_MOBS.get();
    }

    // A raptor is not a bulldozer: it pushes through leaves and bushes, never through walls or houses.
    @Override
    public RexBlockBreaker.Policy huntBreakPolicy() {
        return RexBlockBreaker.Policy.FOLIAGE;
    }

    @Override
    public double swimDepthBlocks() {
        return RaptorConfig.SWIM_DEPTH.get();
    }

    @Override
    public double waterExitHeight() {
        return RaptorConfig.WATER_EXIT_HEIGHT.get();
    }

    @Override
    public double footstepStrideBlocks() {
        return RaptorConfig.FOOTSTEP_STRIDE_BLOCKS.get();
    }

    @Override
    public double footstepVolume() {
        return RaptorConfig.FOOTSTEP_VOLUME.get();
    }

    @Override
    public double biteVolume() {
        return RaptorConfig.BITE_VOLUME.get();
    }

    @Override
    public double roarVolume() {
        return RaptorConfig.ROAR_VOLUME.get();
    }

    @Override
    public int roarCooldownTicks() {
        return RaptorConfig.ROAR_COOLDOWN_TICKS.get();
    }

    // Packs are quiet until they find you: no ambient night calls. (The T-rex's distant-roar config does not apply here.)
    @Override
    public boolean distantRoarEnabled() {
        return false;
    }

    // The raptor's own sound ids. The client camera shake listens for the T-rex's TREX_STEP and TREX_ROAR events by name,
    // so these never shake the ground. The bite is still a pitched-up cry stand-in until a real bite sound is made.
    @Override
    public DeferredHolder<SoundEvent, SoundEvent> stepSound() {
        return ModSounds.RAPTOR_RUN;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> roarSound() {
        return ModSounds.RAPTOR_ROAR;
    }

    @Override
    public DeferredHolder<SoundEvent, SoundEvent> biteSound() {
        return ModSounds.RAPTOR_BITE;
    }

    // ---- Animations: the Peridot Scout's own clips (see the RawAnimation constants). ----

    @Override
    protected RawAnimation idleAnimation() {
        return IDLE;
    }

    @Override
    protected RawAnimation walkAnimation() {
        return WALK;
    }

    @Override
    protected RawAnimation roarAnimation() {
        return CRY;
    }

    @Override
    protected RawAnimation biteAnimation() {
        return ATTACK_LEFT;
    }

    @Override
    protected RawAnimation swimAnimation() {
        return SWIM;
    }

    @Override
    protected RawAnimation runAnimation() {
        return RUN;
    }

    @Override
    protected RawAnimation deathAnimation() {
        return DEATH;
    }

    @Override
    protected double walkCycleSeconds() {
        return PERIDOT_WALK_CYCLE_SECONDS;
    }

    @Override
    protected int walkFootfallsPerCycle() {
        return PERIDOT_FOOTFALLS_PER_CYCLE;
    }

    @Override
    protected int actionDurationTicks(Action action) {
        return switch (action) {
            case ROAR -> CRY_ACTION_TICKS;
            case BITE, BITE_ALT -> ATTACK_ACTION_TICKS;
            case TAIL_WHIP -> TAIL_WHIP_ACTION_TICKS;
            case NONE -> 0;
        };
    }

    // A raptor never charges, so "sprinting" means it has spotted quarry (or is fleeing the pack's loss, or scattering
    // after losing a hunt).
    @Override
    public boolean sprintingVisual() {
        HuntState state = getHuntState();
        return state == HuntState.STALK || state == HuntState.CHARGE || isFleeing() || isScattering();
    }

    // ---- Flanking: one raptor of a pack of three (two of four) runs a circle around the quarry. ----

    /** Which flanker this raptor is for the current quarry: -2 unevaluated, -1 straight chaser, 0 = left circle, 1 = right. */
    private int flankSlot = -2;
    private @Nullable UUID flankEvaluatedFor;
    /** Set by huntDestination() each tick the goal asks for a destination (before huntSpeedBoost() reads it). */
    private boolean flankingNow;

    @Override
    public Vec3 huntDestination(Vec3 chase, boolean visible) {
        LivingEntity target = getTarget();
        if (!visible || target == null || isInWater() || isFleeing() || isScattering()) {
            flankingNow = false;
            flankSlot = -2;
            return chase;
        }
        if (flankSlot == -2 || !target.getUUID().equals(flankEvaluatedFor)) {
            flankEvaluatedFor = target.getUUID();
            flankSlot = evaluateFlankSlot();
        }
        if (flankSlot < 0) {
            flankingNow = false;
            return chase;
        }
        // Where this raptor sits on the circle around the quarry, in world XZ.
        double dx = getX() - target.getX();
        double dz = getZ() - target.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0E-3) {
            flankingNow = true; // on top of it: the bite logic owns this tick
            return chase;
        }
        // Step the bearing a little further around the chosen side, and pull it to the flank radius: the point rides
        // around the quarry as that point moves, which is the circling.
        double side = flankSlot == 0 ? 1.0 : -1.0;
        double step = Math.toRadians(RaptorConfig.FLANK_ARC_DEGREES_PER_SECOND.get()) / 20.0;
        double cos = Mth.cos((float) (side * step));
        double sin = Mth.sin((float) (side * step));
        double ux = dx / distance;
        double uz = dz / distance;
        double radius = Math.max(2.5, RaptorConfig.FLANK_RADIUS_BLOCKS.get());
        double px = (ux * cos - uz * sin) * radius;
        double pz = (ux * sin + uz * cos) * radius;
        flankingNow = true;
        return new Vec3(target.getX() + px, target.getY(), target.getZ() + pz);
    }

    @Override
    public double huntSpeedBoost() {
        return flankingNow ? RaptorConfig.FLANK_SPEED_BOOST.get() : 1.0;
    }

    /** True while this raptor is running its circle: read by tests and debugging. */
    public boolean flankingThisTick() {
        return flankingNow;
    }

    /**
     * Who flanks, decided fresh per quarry: pack members within earshot sorted by id - the first (and in a pack of
     * four, the second) flanks, the rest chase straight. Stateless, so it survives deaths and stragglers.
     */
    private int evaluateFlankSlot() {
        String tag = packTag();
        if (tag == null) {
            return -1; // a spawn-egg raptor on its own does not have flanks to run
        }
        List<Velociraptor> pack = new ArrayList<>(level().getEntitiesOfClass(Velociraptor.class,
                getBoundingBox().inflate(FLANK_PACK_RADIUS),
                other -> other != this && other.isAlive() && tag.equals(other.packTag())));
        pack.add(this);
        int flankers = pack.size() >= 4 ? 2 : pack.size() == 3 ? 1 : 0;
        if (flankers == 0) {
            return -1;
        }
        pack.sort(Comparator.comparing(Velociraptor::getUUID));
        return pack.indexOf(this) < flankers ? pack.indexOf(this) : -1;
    }

    private static final double FLANK_PACK_RADIUS = 48.0;

    // ---- Scatter: losing the quarry sends a raptor running for a while, without the pack-death despawn. ----

    // Same absolute-game-time clock as the pack-death flee (a not-ticking entity still ends on schedule via the sweep).
    private long scatterUntilGameTime;

    @Override
    public void onHuntAbandoned() {
        // A pack-death flight already owns the running state; mid-swim goal churn (the float goal) is not losing quarry.
        if (isFleeing() || isInWater()) {
            return;
        }
        int ticks = RaptorConfig.SCATTER_FLEE_TICKS.get();
        if (ticks > 0) {
            scatterUntilGameTime = Math.max(scatterUntilGameTime, level().getGameTime() + ticks);
        }
    }

    public boolean isScattering() {
        return scatterUntilGameTime > 0 && level().getGameTime() <= scatterUntilGameTime;
    }

    private boolean scatterExpired() {
        return scatterUntilGameTime > 0 && level().getGameTime() > scatterUntilGameTime;
    }

    /** Ends a due scatter; called alongside endFleeIfDue(). Scattering never despawns anything. */
    public void endScatterIfDue() {
        if (!level().isClientSide() && scatterExpired()) {
            scatterUntilGameTime = 0;
        }
    }

    /** True when a scatter clock ran out on an entity that stopped ticking; the sweep clears it, despawning nothing. */
    public boolean scatterPendingEnd() {
        return scatterExpired();
    }

    public void clearScatter() {
        scatterUntilGameTime = 0;
    }

    // Two attack animations, alternated so a pack biting together does not look like one clamped loop.
    @Override
    public Action nextBiteAction() {
        lastBiteWasLeft = !lastBiteWasLeft;
        return lastBiteWasLeft ? Action.BITE : Action.BITE_ALT;
    }

    @Override
    protected AnimationController<TRex> registerActionAnims(AnimationController<TRex> controller) {
        return super.registerActionAnims(controller)
            .triggerableAnim(ATTACK_RIGHT_TRIGGER, ATTACK_RIGHT)
            .triggerableAnim(TAIL_WHIP_TRIGGER, TAIL_WHIP);
    }

    @Override
    protected void playActionAnimation(Action action) {
        switch (action) {
            case BITE_ALT -> triggerAnim(ACTION_CONTROLLER, ATTACK_RIGHT_TRIGGER);
            case TAIL_WHIP -> triggerAnim(ACTION_CONTROLLER, TAIL_WHIP_TRIGGER);
            default -> super.playActionAnimation(action);
        }
    }

    // Footfalls are silent; the raptor's movement noise is the running-gait loop, played while sprinting (below).
    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (sprintingVisual() && !isInWater()) {
            if (tickCount >= nextRunSoundTick) {
                nextRunSoundTick = tickCount + RUN_SOUND_PERIOD_TICKS;
                getVoice().step();
            }
        } else {
            nextRunSoundTick = 0;
        }
        // Idle variety: a raptor standing around mid-wander flicks its tail now and then. The synced action (and its
        // duration) keeps repeats out while the whip plays, and it never interrupts a hunt, a flight or another action.
        if (getAction() == Action.NONE
                && getHuntState() == HuntState.WANDER
                && !isFleeing()
                && getDeltaMovement().horizontalDistanceSqr() < 1.0E-4
                && random.nextDouble() < IDLE_TAIL_WHIP_CHANCE_PER_TICK) {
            startAction(Action.TAIL_WHIP);
        }
    }

    /** The pack tag this raptor carries, or null for a lone spawn-egg raptor (each of which is its own pack of one). */
    public @Nullable String packTag() {
        for (String tag : entityTags()) {
            if (tag.startsWith(PACK_TAG_PREFIX)) {
                return tag;
            }
        }
        return null;
    }

    // When the flight ends, in absolute level game time (not a countdown): an entity that wanders into a chunk
    // that is not entity-ticking stops ticking entirely, and a tick countdown would never expire. The spawner's
    // sweep despawns those by the clock instead.
    private long fleeUntilGameTime;

    /** Start (or extend) the post-pack-death flight. Safe to call on every survivor at once. */
    public void startFleeing() {
        int ticks = RaptorConfig.FLEE_TICKS.get();
        if (ticks > 0 && !level().isClientSide()) {
            fleeUntilGameTime = Math.max(fleeUntilGameTime, level().getGameTime() + ticks);
            // A pack death outranks "we lost the prey": these survivors run the death flight, then despawn.
            scatterUntilGameTime = 0;
        }
    }

    public boolean isFleeing() {
        return fleeUntilGameTime > 0 && level().getGameTime() <= fleeUntilGameTime;
    }

    /** True once the flight's clock has run out, whether or not this entity has been ticking. */
    public boolean fleeExpired() {
        return fleeUntilGameTime > 0 && level().getGameTime() > fleeUntilGameTime;
    }

    /**
     * Ends a due flight, called by {@link RaptorFleeGoal} every tick it runs: a naturally spawned raptor despawns
     * with the flight; a spawn-egg or /summon raptor just stops running.
     */
    public void endFleeIfDue() {
        if (!level().isClientSide() && fleeExpired()) {
            fleeUntilGameTime = 0;
            if (entityTags().contains(RexSpawner.NATURAL_TAG)) {
                discard();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (fleeUntilGameTime > 0) {
            output.putLong("FleeUntilGameTime", fleeUntilGameTime);
        }
        if (scatterUntilGameTime > 0) {
            output.putLong("ScatterUntilGameTime", scatterUntilGameTime);
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        fleeUntilGameTime = input.getLongOr("FleeUntilGameTime", 0L);
        scatterUntilGameTime = input.getLongOr("ScatterUntilGameTime", 0L);
    }
}
