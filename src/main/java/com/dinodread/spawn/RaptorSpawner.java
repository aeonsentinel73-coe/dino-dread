package com.dinodread.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dinodread.config.RaptorConfig;
import com.dinodread.entity.Velociraptor;
import com.dinodread.registry.ModEntities;
import com.dinodread.world.DayNight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Natural velociraptor packs. Every {@code attemptIntervalTicks} each eligible player rolls once; on a hit, a pack of
 * {@code packMinSize} to {@code packMaxSize} raptors is placed on the surface nearby, within {@code packSpreadBlocks} of one
 * anchor point, and every member gets a shared {@code dinodread_pack_<uuid>} tag. A natural raptor carries
 * {@link RexSpawner#NATURAL_TAG} and is removed when no player is near (it has no lifetime cap; packs simply leave when
 * far away or when the pack breaks — see {@link #onPackDeath}). Egg and command raptors are never touched. Server-side only.
 */
public final class RaptorSpawner {
    /** Survivors of a tagged pack anywhere in this radius around the death start fleeing. */
    private static final double PACK_SEARCH_RADIUS = 96.0;
    /** A raptor with no pack tag (spawn egg, /summon): nearby raptors also with no tag flee, but nobody despawns. */
    private static final double UNTAGGED_PACK_RADIUS = 16.0;
    private static final int DESPAWN_CHECK_INTERVAL_TICKS = 100;

    private RaptorSpawner() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tickLevel(level, RaptorRules.fromConfig(), level.getRandom());
        }
    }

    public static int tickLevel(ServerLevel level, RaptorRules rules, RandomSource random) {
        long time = level.getGameTime();
        if (time % DESPAWN_CHECK_INTERVAL_TICKS == 0) {
            despawnStrayRaptors(level, rules);
        }
        if (!rules.spawnEnabled() || time % Math.max(1, rules.attemptIntervalTicks()) != 0) {
            return 0;
        }
        int spawned = 0;
        for (ServerPlayer player : level.players()) {
            spawned += trySpawnPack(level, player, rules, random);
        }
        return spawned;
    }

    /** Rolls once for one player; returns the number of raptors placed (0 when the roll or the placement fails). */
    public static int trySpawnPack(ServerLevel level, ServerPlayer player, RaptorRules rules, RandomSource random) {
        if (!rules.allowedDimensions().contains(level.dimension().identifier().toString())) {
            return 0;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.isSpawningMonsters()) {
            return 0;
        }
        if (!player.isAlive() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) {
            return 0;
        }
        if (rules.nightOnly() && DayNight.nightFactor(level, player.position()) < rules.minNightFactor()) {
            return 0;
        }
        int packSize = packSize(rules, random);
        if (countRaptor(level, null, 0.0) + packSize > rules.maxRaptorInWorld()
                || countRaptor(level, player.position(), rules.capRadius()) + packSize > rules.maxRaptorPerPlayer()) {
            return 0;
        }
        if (random.nextDouble() >= rules.spawnChance()) {
            return 0;
        }

        Velociraptor probe = ModEntities.VELOCIRAPTOR.get().create(level, EntitySpawnReason.NATURAL);
        if (probe == null) {
            return 0;
        }
        double min = rules.minSpawnDistance();
        double max = Math.max(rules.maxSpawnDistance(), min);
        for (int attempt = 0; attempt < rules.placementAttempts(); attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            double distance = Mth.lerp(random.nextDouble(), min, max);
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            // Only chunks that are loaded and running: this must never load or generate a chunk. Check BEFORE touching any block.
            if (!level.isPositionEntityTicking(new BlockPos(blockX, player.getBlockY(), blockZ))) {
                continue;
            }
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            // Nobody (not just this player) may be inside the minimum distance of the anchor.
            if (level.getNearestPlayer(x, surfaceY, z, min, EntitySelector.NO_CREATIVE_OR_SPECTATOR) != null) {
                continue;
            }
            if (!RexSpawner.canStandAt(level, probe, x, surfaceY, z)) {
                continue;
            }
            return placePack(level, x, surfaceY, z, packSize, rules, random);
        }
        return 0;
    }

    /**
     * Debug/force path for {@code /dinodread raptorpack}: places one tagged pack on solid ground 8 to 16 blocks from the
     * player, bypassing the natural gates (night, caps, chance roll, spawn distance, creative) but not world sanity:
     * the chunks must tick and each body must have a standable spot, or the pack fails as a whole. Flanking needs a
     * shared pack tag, which spawn eggs never assign, so this is the only way to raise a pack that flanks.
     */
    public static int forceSpawnPack(ServerLevel level, ServerPlayer player, RaptorRules rules, RandomSource random) {
        Velociraptor probe = ModEntities.VELOCIRAPTOR.get().create(level, EntitySpawnReason.NATURAL);
        if (probe == null) {
            return 0;
        }
        int packSize = packSize(rules, random);
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            double distance = 8.0 + random.nextDouble() * 8.0;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            if (!level.isPositionEntityTicking(new BlockPos(blockX, player.getBlockY(), blockZ))) {
                continue;
            }
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            if (!RexSpawner.canStandAt(level, probe, x, surfaceY, z)) {
                continue;
            }
            int placed = placePack(level, x, surfaceY, z, packSize, rules, random);
            if (placed > 0) {
                return placed;
            }
        }
        return 0;
    }

    /**
     * Spreads packSize bodies around the anchor and places them. A pack that cannot fit packMinSize bodies fails as a whole:
     * half a pack appearing in the world is not what the config promises.
     */
    private static int placePack(ServerLevel level, double anchorX, double anchorY, double anchorZ,
            int packSize, RaptorRules rules, RandomSource random) {
        String packTag = Velociraptor.PACK_TAG_PREFIX + UUID.randomUUID();
        List<Velociraptor> placed = new ArrayList<>();
        for (int i = 0; i < packSize; i++) {
            double spread = Math.max(1, rules.packSpreadBlocks());
            Velociraptor raptor = ModEntities.VELOCIRAPTOR.get().create(level, EntitySpawnReason.NATURAL);
            if (raptor == null) {
                break;
            }
            boolean seated = false;
            for (int offsetTry = 0; offsetTry < 8 && !seated; offsetTry++) {
                double angle = random.nextDouble() * (Math.PI * 2.0);
                double distance = 1.0 + random.nextDouble() * (spread - 1.0);
                double x = anchorX + Math.cos(angle) * distance;
                double z = anchorZ + Math.sin(angle) * distance;
                // The anchor's chunk being live does not cover a member spread across a chunk border: check each body's own spot.
                if (!level.isPositionEntityTicking(new BlockPos(Mth.floor(x), Mth.floor(anchorY), Mth.floor(z)))) {
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                seated = RexSpawner.canStandAt(level, raptor, x, surfaceY, z);
            }
            if (!seated) {
                continue;
            }
            float yaw = random.nextFloat() * 360.0F;
            raptor.setYRot(yaw);
            raptor.setYHeadRot(yaw);
            raptor.setYBodyRot(yaw);
            EventHooks.finalizeMobSpawn(raptor, level, level.getCurrentDifficultyAt(raptor.blockPosition()), EntitySpawnReason.NATURAL, null);
            if (raptor.isSpawnCancelled()) {
                continue;
            }
            RexSpawner.markNatural(raptor);
            raptor.addTag(packTag);
            if (level.addFreshEntity(raptor)) {
                placed.add(raptor);
            }
        }

        if (placed.size() < Math.min(rules.packMinSize(), packSize)) {
            placed.forEach(Velociraptor::discard);
            return 0;
        }
        return placed.size();
    }

    private static int packSize(RaptorRules rules, RandomSource random) {
        int min = Math.max(1, rules.packMinSize());
        int max = Math.max(min, rules.packMaxSize());
        return min + random.nextInt(max - min + 1);
    }

    /**
     * NeoForge-bus listener: when a raptor dies, the rest of its pack runs. Tagged packs are found by their shared tag
     * (spawn-egg raptors each carry their own tag once spawned naturally; a plain egg raptor has none, in which case any
     * untagged raptor standing nearby also flees — but only natural raptors despawn when their flee ends).
     */
    public static void onPackDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Velociraptor dead) || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        @Nullable String tag = dead.packTag();
        double radius = tag != null ? PACK_SEARCH_RADIUS : UNTAGGED_PACK_RADIUS;
        for (Velociraptor kin : level.getEntitiesOfClass(Velociraptor.class, dead.getBoundingBox().inflate(radius))) {
            if (kin == dead || !kin.isAlive() || !tagMatches(kin.packTag(), tag)) {
                continue;
            }
            kin.startFleeing();
        }
    }

    private static boolean tagMatches(@Nullable String kinTag, @Nullable String deadTag) {
        return deadTag == null ? kinTag == null : deadTag.equals(kinTag);
    }

    public static int despawnStrayRaptors(ServerLevel level, RaptorRules rules) {
        int removed = 0;
        for (Velociraptor raptor : level.getEntities(EntityTypeTest.forClass(Velociraptor.class),
                candidate -> candidate.entityTags().contains(RexSpawner.NATURAL_TAG))) {
            // A pack whose flight clock ran out always despawns, even if this raptor itself never ticked again
            // (it can wander into a chunk that is not entity-ticking, where a self-ticking countdown would stall).
            boolean flightOver = raptor.fleeExpired();
            if (raptor.scatterPendingEnd()) {
                // A scatter run that ran out while the raptor was unloaded: end it quietly, never a despawn.
                raptor.clearScatter();
            }
            boolean tooFar = rules.despawnWhenFar()
                    && level.getNearestPlayer(raptor.getX(), raptor.getY(), raptor.getZ(), rules.despawnDistance(), false) == null;
            if (flightOver || tooFar) {
                raptor.discard();
                removed++;
            }
        }
        return removed;
    }

    public static int countRaptor(ServerLevel level, @Nullable Vec3 center, double radius) {
        int count = 0;
        for (Velociraptor raptor : level.getEntities(EntityTypeTest.forClass(Velociraptor.class), LivingEntity::isAlive)) {
            if (center == null || raptor.position().distanceToSqr(center) <= radius * radius) {
                count++;
            }
        }
        return count;
    }
}
