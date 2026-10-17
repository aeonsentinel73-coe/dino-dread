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
 * The T-rex's way through obstacles: when it is pushing toward something, it breaks the allow-listed blocks
 * in the volume just ahead of its hitbox. Server-side only, rate-limited, and gated by the griefing config
 * and the mobGriefing game rule. Blocks are broken nearest-first, at most a few per break action.
 */
public class RexBlockBreaker {
    // Keeps the volume from including the block layer exactly at the hitbox edges (the floor under its feet,
    // the air layer exactly above its head).
    private static final double BLOCK_EDGE_EPSILON = 1.0E-3;

    // The allow-list is parsed once and re-parsed only when the config value changes.
    private static List<? extends String> cachedBreakableBlocks;
    private static final Set<Block> cachedBreakableBlockIds = new HashSet<>();
    private static final List<TagKey<Block>> cachedBreakableBlockTags = new ArrayList<>();

    private final TRex rex;
    private int cooldownTicks;

    public RexBlockBreaker(TRex rex) {
        this.rex = rex;
    }

    /**
     * Call once per server tick while the T-rex is pushing toward something. {@code heading} is the unit-length HORIZONTAL
     * direction (y is ignored) it is trying to move in; {@code breakNow} says whether it should be breaking this tick
     * (it is touching a wall, or stuck). Returns how many blocks were broken this tick.
     */
    public int tick(Vec3 heading, boolean breakNow) {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return 0;
        }

        if (!breakNow) {
            return 0;
        }

        if (!(rex.level() instanceof ServerLevel level)) {
            return 0;
        }

        if (!RexConfig.GRIEFING_ENABLED.get() || !EventHooks.canEntityGrief(level, rex)) {
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
            if (state.isAir() || !isBreakable(level, pos, state)) {
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

        cooldownTicks = RexConfig.BREAK_INTERVAL_TICKS.get();
        return broken;
    }

    /** True if this block may be broken by the T-rex under the current config (used by tick and by tests). */
    public boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > RexConfig.MAX_BREAK_HARDNESS.get()) {
            return false;
        }

        if (state.hasBlockEntity() && !RexConfig.BREAK_BLOCK_ENTITIES.get()) {
            return false;
        }

        return matchesAllowList(state);
    }

    private static boolean matchesAllowList(BlockState state) {
        List<? extends String> configured = RexConfig.BREAKABLE_BLOCKS.get();
        if (cachedBreakableBlocks == null || !configured.equals(cachedBreakableBlocks)) {
            cachedBreakableBlockIds.clear();
            cachedBreakableBlockTags.clear();
            for (String entry : configured) {
                if (entry.startsWith("#")) {
                    Identifier id = Identifier.tryParse(entry.substring(1));
                    if (id == null) {
                        DinoDread.LOGGER.warn("Skipping malformed block tag entry in breakableBlocks config: {}", entry);
                        continue;
                    }

                    cachedBreakableBlockTags.add(TagKey.create(Registries.BLOCK, id));
                } else {
                    Identifier id = Identifier.tryParse(entry);
                    if (id == null) {
                        DinoDread.LOGGER.warn("Skipping malformed block id entry in breakableBlocks config: {}", entry);
                        continue;
                    }

                    BuiltInRegistries.BLOCK.getOptional(id).ifPresent(cachedBreakableBlockIds::add);
                }
            }

            cachedBreakableBlocks = configured;
        }

        for (Block block : cachedBreakableBlockIds) {
            if (state.is(block)) {
                return true;
            }
        }

        for (TagKey<Block> tag : cachedBreakableBlockTags) {
            if (state.is(tag)) {
                return true;
            }
        }

        return false;
    }
}
