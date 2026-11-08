package com.dinodread.entity.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dinodread.DinoDread;
import com.dinodread.config.RexConfig;
import com.dinodread.entity.TRex;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The T-rex's way through obstacles: when it is pushing toward something, it breaks the blocks that are not
 * on the unbreakable list in the volume just ahead of its hitbox. Server-side only, rate-limited, and gated
 * by the griefing config and the mobGriefing game rule. Blocks are broken nearest-first, at most a few per break action.
 */
public class RexBlockBreaker {
    // Keeps the volume from including the block layer exactly at the hitbox edges (the floor under its feet,
    // the air layer exactly above its head).
    private static final double BLOCK_EDGE_EPSILON = 1.0E-3;

    /** Which blocks the T-rex may break right now. */
    public enum Policy {
        /** Nothing. */
        NONE,
        /** Only foliage (the foliageBlocks config): what it breaks when it is not hunting a player, so a tree line is never a trap. */
        FOLIAGE,
        /** Anything not on the unbreakable list: what it breaks when it is hunting a player. */
        ANY
    }

    private static final BlockList UNBREAKABLE = new BlockList("unbreakableBlocks");
    private static final BlockList FOLIAGE = new BlockList("foliageBlocks");

    private final TRex rex;
    private int cooldownTicks;
    private int stompCooldownTicks;

    public RexBlockBreaker(TRex rex) {
        this.rex = rex;
    }

    /**
     * Call once per server tick while the T-rex is pushing toward something. {@code heading} is the unit-length HORIZONTAL
     * direction (y is ignored) it is trying to move in; {@code breakNow} says whether it should be breaking this tick
     * (it is touching a wall, or stuck). It breaks the blocks in the volume just ahead of its hitbox that are not
     * on the unbreakable list, nearest-first, at most a few per break action. Returns how many blocks were broken this tick.
     */
    public int tick(Vec3 heading, boolean breakNow) {
        return tick(heading, breakNow, Policy.ANY);
    }

    /** As {@link #tick(Vec3, boolean)}, but {@code policy} says which blocks it may break. */
    public int tick(Vec3 heading, boolean breakNow, Policy policy) {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return 0;
        }

        if (!breakNow || policy == Policy.NONE) {
            return 0;
        }

        if (!(rex.level() instanceof ServerLevel level)) {
            return 0;
        }

        if (!griefingAllowed(level)) {
            return 0;
        }

        double reach = RexConfig.BREAK_REACH.get();
        AABB box = rex.getBoundingBox().expandTowards(heading.x * reach, 0.0, heading.z * reach);
        int minX = Mth.floor(box.minX + BLOCK_EDGE_EPSILON);
        int minY = Mth.floor(box.minY + BLOCK_EDGE_EPSILON);
        int minZ = Mth.floor(box.minZ + BLOCK_EDGE_EPSILON);
        int maxX = Mth.floor(box.maxX - BLOCK_EDGE_EPSILON);
        int maxY = Mth.floor(box.maxY - BLOCK_EDGE_EPSILON);
        int maxZ = Mth.floor(box.maxZ - BLOCK_EDGE_EPSILON);

        AABB body = rex.getBoundingBox();
        Vec3 front = new Vec3(body.getCenter().x + heading.x * body.getXsize() / 2.0, body.minY, body.getCenter().z + heading.z * body.getZsize() / 2.0);

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !isBreakable(level, pos, state) || (policy == Policy.FOLIAGE && !FOLIAGE.matches(RexConfig.FOLIAGE_BLOCKS.get(), state))) {
                continue;
            }

            candidates.add(pos.immutable());
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(front)));

        int broken = 0;
        for (BlockPos pos : candidates) {
            if (broken >= RexConfig.BLOCKS_PER_BREAK.get()) {
                break;
            }

            if (level.destroyBlock(pos, RexConfig.BREAK_DROPS_ITEMS.get(), rex)) {
                broken++;
            }
        }

        cooldownTicks = rex.breakIntervalTicks();
        return broken;
    }

    /**
     * Treetops are not ground. If the T-rex is standing on foliage it breaks the foliage directly under its feet (same rules, limits and
     * griefing gates as {@link #tick}), so a rex that got up on a canopy, by hopping or stepping, falls through it instead of walking around up there.
     * Call it every server tick, whatever the T-rex is doing. Returns how many blocks it broke.
     */
    public int breakFoliageBeneath() {
        if (stompCooldownTicks > 0) {
            stompCooldownTicks--;
            return 0;
        }

        if (!rex.onGround() || !(rex.level() instanceof ServerLevel level) || !griefingAllowed(level)) {
            return 0;
        }

        AABB body = rex.getBoundingBox();
        int supportY = Mth.floor(body.minY - BLOCK_EDGE_EPSILON);
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(body.minX + BLOCK_EDGE_EPSILON), supportY, Mth.floor(body.minZ + BLOCK_EDGE_EPSILON),
                Mth.floor(body.maxX - BLOCK_EDGE_EPSILON), supportY, Mth.floor(body.maxZ - BLOCK_EDGE_EPSILON))) {
            if (broken >= RexConfig.BLOCKS_PER_BREAK.get()) {
                break;
            }

            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && isBreakable(level, pos, state) && FOLIAGE.matches(RexConfig.FOLIAGE_BLOCKS.get(), state)
                    && level.destroyBlock(pos, RexConfig.BREAK_DROPS_ITEMS.get(), rex)) {
                broken++;
            }
        }

        if (broken > 0) {
            stompCooldownTicks = rex.breakIntervalTicks();
        }
        return broken;
    }

    private boolean griefingAllowed(ServerLevel level) {
        return RexConfig.GRIEFING_ENABLED.get() && EventHooks.canEntityGrief(level, rex);
    }

    /** True if this block may be broken by the T-rex under the current config (used by tick and by tests). */
    public boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.canBeReplaced()) {
            return false;
        }

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > RexConfig.MAX_BREAK_HARDNESS.get()) {
            return false;
        }

        if (state.hasBlockEntity() && !RexConfig.BREAK_BLOCK_ENTITIES.get()) {
            return false;
        }

        return !UNBREAKABLE.matches(RexConfig.UNBREAKABLE_BLOCKS.get(), state);
    }

    /** A config list of block ids and #tags, parsed once and re-parsed only when the config value changes. */
    private static final class BlockList {
        private final String configName;
        private List<? extends String> cached;
        private final Set<Block> ids = new HashSet<>();
        private final List<TagKey<Block>> tags = new ArrayList<>();

        BlockList(String configName) {
            this.configName = configName;
        }

        boolean matches(List<? extends String> configured, BlockState state) {
            if (cached == null || !configured.equals(cached)) {
                ids.clear();
                tags.clear();
                for (String entry : configured) {
                    if (entry.startsWith("#")) {
                        Identifier id = Identifier.tryParse(entry.substring(1));
                        if (id == null) {
                            DinoDread.LOGGER.warn("Skipping malformed block tag entry in {} config: {}", configName, entry);
                            continue;
                        }

                        tags.add(TagKey.create(Registries.BLOCK, id));
                    } else {
                        Identifier id = Identifier.tryParse(entry);
                        if (id == null) {
                            DinoDread.LOGGER.warn("Skipping malformed block id entry in {} config: {}", configName, entry);
                            continue;
                        }

                        BuiltInRegistries.BLOCK.getOptional(id).ifPresent(ids::add);
                    }
                }

                cached = configured;
            }

            if (ids.contains(state.getBlock())) {
                return true;
            }

            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) {
                    return true;
                }
            }

            return false;
        }
    }
}
