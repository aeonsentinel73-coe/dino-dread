package com.dinodread.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.dinodread.DinoDread;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Worldgen tests: the giant tree feature really puts a giant trunk down, and the overridden jungle tree
 * is meaningfully taller than vanilla's was (trunk at least 8 logs before the leaves).
 */
public final class RexWorldgenGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);
    private static final List<String> NAMES = new ArrayList<>();

    static {
        test("giant_tree_feature_places_a_giant_trunk", RexWorldgenGameTests::giantTreePlacesAGiantTrunk);
        test("jungle_tree_override_is_taller_than_vanilla", RexWorldgenGameTests::jungleTreeIsTaller);
    }

    private RexWorldgenGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        NAMES.add(name);
        FUNCTIONS.register(name, () -> body);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexWorldgenGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        for (String name : NAMES) {
            RexGameTests.addTest(event, name, RexGameTests.TEST_TIMEOUT_TICKS);
        }
    }

    /**
     * Places the placed feature directly until its rarity filter lets one through, then counts the trunk:
     * base 16 + up to 14 random means the first 14 blocks above the base are solid jungle log (branch columns
     * sit beside the trunk, never in it).
     */
    private static void giantTreePlacesAGiantTrunk(GameTestHelper helper) {
        placeAndCountLogs(helper, "dinodread:giant_tree", 14, 14);
    }

    /** The mod ships a taller jungle tree: the straight trunk must reach at least 8 logs (vanilla's was 4 at minimum). */
    private static void jungleTreeIsTaller(GameTestHelper helper) {
        placeAndCountLogs(helper, "minecraft:jungle_tree", 8, 8);
    }

    private static void placeAndCountLogs(GameTestHelper helper, String featureId, int trunkDepth, int requiredLogs) {
        ServerLevel level = helper.getLevel();
        BlockPos probe = helper.absolutePos(new BlockPos(2, 0, 2));
        // The batch floor sits where it sits: find the first solid block near the probe, make its top grass,
        // and give the tree a clear 40-block air column so the free-height scan cannot clip it.
        BlockPos soil = null;
        for (int dy = 8; dy >= -8 && soil == null; dy--) {
            if (!level.getBlockState(probe.offset(0, dy, 0)).isAir()) {
                soil = probe.offset(0, dy, 0);
            }
        }
        if (soil == null) {
            helper.fail("no solid ground found near " + probe + " to plant on");
            return;
        }
        BlockPos base = soil.above();
        // The free-height scan checks a radius around the trunk (and the mega jungle's trunk is 2x2): clear a 3x3
        // air column and put grass under the whole footprint, so the terrain itself cannot reject the tree.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(soil.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y <= 40; y++) {
                    level.setBlock(base.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        PlacedFeature feature = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.parse(featureId))).value();
        ChunkGenerator generator = level.getChunkSource().getGenerator();

        // The placed feature's own rarity filter would make this test random, so the tree feature runs directly:
        // this test proves the geometry; the modifier chain is vanilla's own and exercised in real worldgen.
        boolean placed = feature.feature().value().place(level, generator, RandomSource.create(0xD1D0EA5L), base);
        if (!placed) {
            helper.fail("the " + featureId + " feature never placed. below=" + level.getBlockState(base.below())
                    + " base=" + level.getBlockState(base) + " up1=" + level.getBlockState(base.above(1))
                    + " up2=" + level.getBlockState(base.above(2)) + " floorY=" + level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ())
                    + " minY=" + level.getMinY() + " maxY=" + level.getMaxY());
            return;
        }

        int logs = 0;
        for (int y = 1; y <= trunkDepth; y++) {
            if (level.getBlockState(base.above(y)).is(Blocks.JUNGLE_LOG)) {
                logs++;
            }
        }
        if (logs >= requiredLogs) {
            helper.succeed();
        } else {
            helper.fail(featureId + " trunk was only " + logs + " of the first " + trunkDepth + " blocks solid jungle log");
        }
    }
}
