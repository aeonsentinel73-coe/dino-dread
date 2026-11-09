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
        test("giant_tree_attached_to_tree_biomes", RexWorldgenGameTests::giantTreeAttachedToTreeBiomes);
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

    /**
     * End-to-end attachment check, which the direct-placement tests cannot cover (placing a feature
     * programmatically bypasses both the biome modifiers and the placement chain): (1) the biome modifier
     * must really list the giants in the vegetal decoration of a forest and a jungle, and (2) no CountPlacement
     * in the chain may sample 0 - in 26.3 "minecraft:count" takes an IntProvider, and a fractional JSON count
     * (0.35) silently truncates to zero attempts: valid JSON, no error, a feature that never runs
     * (confirmed: a whole walked world with zero jungle_log across 14 forest + 10 plains chunks).
     */
    private static void giantTreeAttachedToTreeBiomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var access = level.registryAccess();
        var placedReg = access.lookupOrThrow(Registries.PLACED_FEATURE);
        var biomeReg = access.lookupOrThrow(Registries.BIOME);
        Identifier baseId = Identifier.parse("dinodread:giant_tree");
        Identifier jungleId = Identifier.parse("dinodread:giant_tree_jungle");

        PlacedFeature baseFeature = placedReg
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, baseId)).value();
        // 26.3 hands every decorated feature the RAW chunk origin (y = build bottom, -64 overworld). Vanilla tree
        // chains never carry a count/heightmap themselves - the biome points at a wrapper placed feature whose
        // chain lifts the origin to the surface. A chain without a heightmap modifier runs thousands of times,
        // inspects bedrock, and silently places nothing (confirmed via a logging Feature: 100% of rejections at
        // y=-64 while a walked world showed zero trunks).
        boolean hasHeightmap = false;
        double countMean = 0.0;
        int countSamples = 0;
        for (var modifier : baseFeature.placement()) {
            if (modifier instanceof net.minecraft.world.level.levelgen.placement.HeightmapPlacement) {
                hasHeightmap = true;
            }
            if (modifier instanceof net.minecraft.world.level.levelgen.placement.CountPlacement counter) {
                for (int seed = 0; seed < 200; seed++) {
                    countMean += counter.count(RandomSource.create((long) seed + 1L), BlockPos.ZERO);
                    countSamples++;
                }
            }
        }
        if (!hasHeightmap) {
            helper.fail("giant_tree placement has no heightmap modifier - decoration would run it at the chunk "
                    + "origin below bedrock and it would silently place nothing");
            return;
        }
        if (countSamples == 0 || countMean / countSamples <= 0.0) {
            helper.fail("giant_tree CountPlacement averages " + countMean + " over " + countSamples
                    + " seeds - it would never run");
            return;
        }

        // 26.3: Biome.getGenerationSettings().features() is a list of HolderSets across steps; presence of the
        // id anywhere in it proves the biome modifier attached the placed feature to that biome.
        java.util.function.Predicate<Identifier> attachedTo = biomeId -> biomeReg
                .getOrThrow(ResourceKey.create(Registries.BIOME, biomeId)).value()
                .getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().map(k -> k.identifier().equals(baseId)).orElse(false));
        boolean onForest = attachedTo.test(Identifier.parse("minecraft:forest"));
        boolean onJungle = biomeReg.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:jungle")))
                .value().getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().map(k -> k.identifier().equals(jungleId)
                        || k.identifier().equals(baseId)).orElse(false));
        if (!onForest || !onJungle) {
            helper.fail("biome modifiers did not attach the giants: forest_has_base=" + onForest
                    + " jungle_has_dense=" + onJungle);
            return;
        }
        helper.succeed();
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
