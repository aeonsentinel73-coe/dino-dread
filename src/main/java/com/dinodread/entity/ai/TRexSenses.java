package com.dinodread.entity.ai;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
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

    public TRexSenses(TRex rex) {
        this.rex = rex;
    }

    /** Game-bus listener (wired up in DinoDread): tells every T-rex in earshot about a noise a player made. */
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getCause() instanceof Player player) || !event.getVanillaEvent().is(GameEventTags.VIBRATIONS)) {
            return;
        }

        Vec3 noisePos = event.getEventPosition();
        double radius = RexConfig.HEARING_RADIUS.get();
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
            scanForPlayers(level);
        }

        if (hasQuarry() && ticksSinceContact > RexConfig.GIVE_UP_TICKS.get() * rex.activity()) {
            forget();
        }
    }

    /** A player made a noise at {@code noisePos}: investigate it if it is within (sneak-adjusted) earshot. */
    public void hearNoise(Player player, Vec3 noisePos) {
        if (!isHuntable(player)) {
            return;
        }

        double radius = RexConfig.HEARING_RADIUS.get() * rex.activity() * (player.isSteppingCarefully() ? RexConfig.SNEAK_HEARING_MULTIPLIER.get() : 1.0);
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

    private void scanForPlayers(ServerLevel level) {
        LivingEntity current = rex.getTarget();
        Player closest = null;
        double closestDistSqr = Double.MAX_VALUE;
        boolean currentSeen = false;
        for (ServerPlayer player : level.players()) {
            if (!canSee(player, player == current)) {
                continue;
            }

            currentSeen |= player == current;
            double distSqr = rex.distanceToSqr(player);
            if (distSqr < closestDistSqr) {
                closest = player;
                closestDistSqr = distSqr;
            }
        }

        Player seen = currentSeen ? (Player) current : closest;
        targetVisible = seen != null;
        if (seen != null) {
            rex.setTarget(seen);
            lastKnownPos = seen.position();
            ticksSinceContact = 0;
        }
    }

    /** Range and line of sight always apply; the field of view only for acquiring a new target (an alerted T-rex turns its head). */
    private boolean canSee(Player player, boolean alreadyTracking) {
        if (!isHuntable(player)) {
            return false;
        }

        double range = RexConfig.SIGHT_RANGE.get() * rex.activity() * (player.isSteppingCarefully() ? RexConfig.SNEAK_SIGHT_MULTIPLIER.get() : 1.0);
        if (rex.distanceToSqr(player) > range * range) {
            return false;
        }

        if (!alreadyTracking) {
            Vec3 toPlayer = player.getEyePosition().subtract(rex.getEyePosition());
            float yawToPlayer = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * Mth.RAD_TO_DEG) - 90.0F;
            if (Mth.degreesDifferenceAbs(yawToPlayer, rex.getYHeadRot()) > RexConfig.SIGHT_FOV_DEGREES.get() / 2.0) {
                return false;
            }
        }

        return rex.getSensing().hasLineOfSight(player);
    }

    private static boolean isHuntable(LivingEntity entity) {
        return entity.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
    }
}
