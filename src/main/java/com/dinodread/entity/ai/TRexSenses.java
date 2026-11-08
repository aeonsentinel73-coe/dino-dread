package com.dinodread.entity.ai;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RaptorConfig;
import com.dinodread.config.RexConfig;
import com.dinodread.config.SuperRexConfig;
import com.dinodread.entity.TRex;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.VanillaGameEvent;

/**
 * What the T-rex knows about its quarry: sight (range, field of view, line of sight) and hearing
 * (player-made game events, the same events the Warden listens to). Server-side only.
 * Sneaking players are seen from less far and heard from less far; both multipliers come from {@link RexConfig}.
 * <p>
 * Hearing hooks {@link VanillaGameEvent} instead of registering a vanilla game event listener: vanilla caps how far
 * each event type is delivered (footsteps only reach 16 blocks), and the T-rex is meant to hear much further than that.
 * Note that vanilla emits no footstep events at all for a player sneaking on the ground.
 */
public class TRexSenses {
    // Sight is re-evaluated this often, so "visible" can be up to this many ticks stale.
    private static final int SIGHT_CHECK_INTERVAL_TICKS = 5;

    private final TRex rex;
    private @Nullable Vec3 lastKnownPos;
    private int ticksSinceContact;
    private boolean targetVisible;

    // Survival cap (raptors): how long the CURRENT quarry has been hunted, and the quarry the rex must leave
    // alone after hitting the cap - so a player pinned by line of sight on rough terrain (the raptors can see
    // but cannot reach) is not hunted forever: they give up within a minute and stay off for the blackout.
    private @Nullable LivingEntity survivalAgeTarget;
    private int survivalAgeTicks;
    private @Nullable LivingEntity survivalCappedQuarry;
    private long survivalCapUntilGameTime;
    /** How long a survival-capped rex ignores the quarry that beat the clock (30 s; covers the 15 s scatter). */
    public static final int SURVIVAL_CAP_BLACKOUT_TICKS = 600;

    public TRexSenses(TRex rex) {
        this.rex = rex;
    }

    /** Game-bus listener (wired up in DinoDread): tells every rex in earshot about a noise a player made. */
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getCause() instanceof Player player) || !event.getVanillaEvent().is(GameEventTags.VIBRATIONS)) {
            return;
        }

        Vec3 noisePos = event.getEventPosition();
        // Cover the most far-hearing rex type; each rex then filters by its own hearingRadius in hearNoise.
        double radius = Math.max(RexConfig.HEARING_RADIUS.get(),
            Math.max(SuperRexConfig.HEARING_RADIUS.get(), RaptorConfig.HEARING_RADIUS.get()));
        for (TRex rex : level.getEntitiesOfClass(TRex.class, new AABB(noisePos, noisePos).inflate(radius))) {
            rex.getSenses().hearNoise(player, noisePos);
        }
    }

    /** True while the T-rex has a target or somewhere to investigate. */
    public boolean hasQuarry() {
        return lastKnownPos != null || rex.getTarget() != null;
    }

    /** True if the current target was seen on the last sight check. */
    public boolean isTargetVisible() {
        return targetVisible && rex.getTarget() != null;
    }

    public @Nullable Vec3 getLastKnownPos() {
        return lastKnownPos;
    }

    public void forget() {
        lastKnownPos = null;
        targetVisible = false;
        ticksSinceContact = 0;
        rex.setTarget(null);
    }

    public void tick(ServerLevel level) {
        if (ticksSinceContact < Integer.MAX_VALUE) {
            ticksSinceContact++;
        }

        LivingEntity target = rex.getTarget();
        if (target != null && !isHuntable(target)) {
            forget();
        }

        if (rex.tickCount % SIGHT_CHECK_INTERVAL_TICKS == 0) {
            scanForTargets(level);
        }

        if (hasQuarry() && ticksSinceContact > rex.giveUpTicks() * rex.activity()) {
            forget();
        }

        // The survival cap counts HUNT TIME, not silence: it fires while the quarry is still plainly visible,
        // because the terrain stalemate (they see you, cannot reach you) never trips the give-up clock.
        LivingEntity held = rex.getTarget();
        if (held != survivalAgeTarget) {
            survivalAgeTarget = held;
            survivalAgeTicks = 0;
        }
        if (held != null) {
            survivalAgeTicks++;
            int persistence = rex.huntPersistenceTicks();
            if (persistence > 0 && survivalAgeTicks > persistence) {
                forget();
                survivalCappedQuarry = held;
                survivalCapUntilGameTime = level.getGameTime() + SURVIVAL_CAP_BLACKOUT_TICKS;
            }
        }
    }

    /** True while this rex must leave this quarry alone (it beat the survival clock; 0 s for rexes, uncapped). */
    public boolean survivalCapped(LivingEntity quarry) {
        return quarry != null && quarry == survivalCappedQuarry
                && rex.level().getGameTime() < survivalCapUntilGameTime;
    }

    /** A player made a noise at {@code noisePos}: investigate it if it is within (sneak-adjusted) earshot. */
    public void hearNoise(Player player, Vec3 noisePos) {
        if (!isHuntable(player)) {
            return;
        }

        double radius = rex.hearingRadius() * rex.activity() * (player.isSteppingCarefully() ? rex.sneakHearingMultiplier() : 1.0);
        if (rex.distanceToSqr(noisePos) > radius * radius) {
            return;
        }

        LivingEntity target = rex.getTarget();
        if (target == null || target == player) {
            lastKnownPos = noisePos;
            ticksSinceContact = 0;
        }
    }

    /**
     * Something told it where a player is, with no noise or sight needed: it investigates that spot as if it had heard a noise there.
     * The natural spawner uses this so a T-rex that appears beyond its own senses heads for the players instead of wandering aimlessly.
     */
    public void alertTo(Vec3 pos) {
        if (rex.getTarget() == null) {
            lastKnownPos = pos;
            ticksSinceContact = 0;
        }
    }

    /**
     * A pack-mate already hunts this quarry: join it. This is the raptors' coordination call - it starts the
     * contact clock at zero like a fresh sighting, so the shared quarry sticks even in the dark, and the
     * sight scan naturally refuses to steal it (the pack hunts as one, not six lone hunters).
     */
    public void shareQuarry(LivingEntity quarry) {
        rex.setTarget(quarry);
        lastKnownPos = quarry.position();
        targetVisible = canSee(quarry, true);
        ticksSinceContact = 0;
    }

    /** How many ticks since this rex last saw or heard its quarry (Integer.MAX_VALUE once it has given up on it). */
    public int contactAge() {
        return ticksSinceContact;
    }

    /** Pack perception: if any pack-mate perceived the shared quarry more recently than this rex did, believe it. */
    public void shareContactAge(int packAge) {
        if (packAge < ticksSinceContact) {
            ticksSinceContact = packAge;
        }
    }

    private void scanForTargets(ServerLevel level) {
        LivingEntity current = rex.getTarget();
        LivingEntity closest = null;
        double closestDistSqr = Double.MAX_VALUE;
        boolean currentSeen = false;

        for (ServerPlayer player : level.players()) {
            if (survivalCapped(player) || !canSee(player, player == current)) {
                continue;
            }
            currentSeen |= player == current;
            double distSqr = rex.distanceToSqr(player);
            if (distSqr < closestDistSqr) {
                closest = player;
                closestDistSqr = distSqr;
            }
        }

        if (rex.huntsHostileMobs()) {
            double range = rex.sightRange() * rex.activity();
            for (Monster mob : level.getEntitiesOfClass(Monster.class, rex.getBoundingBox().inflate(range), mob -> isHuntableMob(mob))) {
                if (survivalCapped(mob) || !canSee(mob, mob == current)) {
                    continue;
                }
                currentSeen |= mob == current;
                double distSqr = rex.distanceToSqr(mob);
                if (distSqr < closestDistSqr) {
                    closest = mob;
                    closestDistSqr = distSqr;
                }
            }
        }

        LivingEntity seen = currentSeen ? current : closest;
        targetVisible = seen != null;
        if (seen != null) {
            rex.setTarget(seen);
            lastKnownPos = seen.position();
            ticksSinceContact = 0;
        }
    }

    /** Range and line of sight always apply; the field of view only for acquiring a new target (an alerted T-rex turns its head). */
    private boolean canSee(LivingEntity target, boolean alreadyTracking) {
        if (!isHuntable(target)) {
            return false;
        }

        double range = rex.sightRange() * rex.activity() * (target instanceof Player player && player.isSteppingCarefully() ? rex.sneakSightMultiplier() : 1.0);
        if (rex.distanceToSqr(target) > range * range) {
            return false;
        }

        if (!alreadyTracking) {
            Vec3 toTarget = target.getEyePosition().subtract(rex.getEyePosition());
            float yawToTarget = (float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0F;
            if (Mth.degreesDifferenceAbs(yawToTarget, rex.getYHeadRot()) > rex.sightFieldOfViewDegrees() / 2.0) {
                return false;
            }
        }

        return rex.getSensing().hasLineOfSight(target);
    }

    private static boolean isHuntable(LivingEntity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity instanceof Player player) {
            return EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player);
        }
        // Animals and villagers are never "quarry": the T-rex eats them through its own prey goal. Only hostiles qualify here.
        return entity instanceof Monster && isHuntableMob(entity);
    }

    /** Hostile mobs are fair game for a raptor unless they are another rex, someone's pet, or named. */
    private static boolean isHuntableMob(LivingEntity entity) {
        if (entity instanceof TRex || entity.hasCustomName() || (entity instanceof Leashable leashable && leashable.isLeashed())) {
            return false;
        }
        return !(entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null);
    }
}
