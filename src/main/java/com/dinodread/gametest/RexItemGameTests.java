package com.dinodread.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.dinodread.DinoDread;
import com.dinodread.registry.ModEntities;
import com.dinodread.registry.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * "Something Fancy": the food values, the lava-only cooking, and the three dinosaur loot tables.
 * The loot roll goes through the real parsed LootTable objects (fixed seeds, so the rolls are
 * deterministic); the cooking goes through the real RecipeManager (it also proves the lava bucket
 * is returned, via the crafting remainder the recipe machinery computes).
 */
public final class RexItemGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, DinoDread.MODID);
    private static final List<String> NAMES = new ArrayList<>();

    static {
        test("something_fancy_feeds_a_bite_raw_a_whole_bar_cooked", RexItemGameTests::foodValues);
        test("something_fancy_cooks_in_lava_and_returns_the_bucket", RexItemGameTests::lavaCrafting);
        test("dinosaur_corpses_drop_something_fancy", RexItemGameTests::dinoLootTables);
    }

    private RexItemGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        NAMES.add(name);
        FUNCTIONS.register(name, () -> body);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RexItemGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        for (String name : NAMES) {
            RexGameTests.addTest(event, name, RexGameTests.TEST_TIMEOUT_TICKS);
        }
    }

    /** Raw is a small bite (2 food); cooked refills the whole bar (20 food, full saturation). */
    private static void foodValues(GameTestHelper helper) {
        FoodProperties raw = new ItemStack(ModItems.SOMETHING_FANCY.get()).get(DataComponents.FOOD);
        FoodProperties cooked = new ItemStack(ModItems.SOMETHING_FANCY_COOKED.get()).get(DataComponents.FOOD);
        if (raw == null || cooked == null) {
            helper.fail("something_fancy items have no food component");
            return;
        }
        if (raw.nutrition() > 4 || cooked.nutrition() != 20 || cooked.saturation() < 16.0F) {
            helper.fail("wrong food values raw=" + raw.nutrition() + " cooked=" + cooked.nutrition()
                + "/" + cooked.saturation());
            return;
        }
        helper.succeed();
    }

    /** The grid [raw + lava bucket] must craft to the cooked item, and the empty bucket must come back. */
    private static void lavaCrafting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput grid = CraftingInput.of(2, 1, List.of(
            new ItemStack(ModItems.SOMETHING_FANCY.get()),
            new ItemStack(Items.LAVA_BUCKET)));
        var found = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level);
        if (found.isEmpty()) {
            helper.fail("raw something fancy + lava bucket matches no crafting recipe at all");
            return;
        }
        RecipeHolder<CraftingRecipe> holder = found.get();
        ItemStack result = holder.value().assemble(grid);
        if (!result.is(ModItems.SOMETHING_FANCY_COOKED.get())) {
            helper.fail("lava craft produced " + result.getItem() + " (recipe " + holder.id().identifier() + ")");
            return;
        }
        boolean bucketBack = holder.value().getRemainingItems(grid).stream().anyMatch(s -> s.is(Items.BUCKET));
        if (!bucketBack) {
            helper.fail("the lava bucket is consumed without giving the empty bucket back");
            return;
        }
        helper.succeed();
    }

    /**
     * Rolls the parsed loot tables with fixed seeds: raptor ~45% one, trex exactly one, super rex
     * one or two (both outcomes must actually occur).
     */
    private static void dinoLootTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);

        // One random stream per table, not a fresh source per roll: per-roll seeds in an arithmetic
        // progression correlate in the low bits, and a single nextInt(2) draws only those.
        int raptorDrops = 0;
        int rolls = 400;
        RandomSource raptorRandom = RandomSource.create(20261109L);
        LootTable raptor = lootTable(level, ModEntities.VELOCIRAPTOR.get());
        if (raptor == null) { helper.fail("velociraptor has no loot table"); return; }
        for (int i = 0; i < rolls; i++) {
            raptorDrops += countFancy(raptor, params, raptorRandom);
        }
        double chance = raptorDrops / (double) rolls;
        if (chance < 0.38 || chance > 0.52) {
            helper.fail("raptor drop chance measured " + chance + " over " + rolls + " rolls (want ~0.45)");
            return;
        }

        LootTable trex = lootTable(level, ModEntities.TREX.get());
        LootTable superRex = lootTable(level, ModEntities.SUPER_REX.get());
        if (trex == null || superRex == null) { helper.fail("rex/super_rex have no loot table"); return; }
        for (int i = 0; i < 60; i++) {
            int n = countFancy(trex, params, RandomSource.create(1000L + i));
            if (n != 1) {
                helper.fail("trex roll " + i + " dropped " + n + " (want exactly 1)");
                return;
            }
        }
        RandomSource superRexRandom = RandomSource.create(424242L);
        boolean sawOne = false;
        boolean sawTwo = false;
        for (int i = 0; i < 60; i++) {
            int n = countFancy(superRex, params, superRexRandom);
            if (n < 1 || n > 2) {
                helper.fail("super_rex roll " + i + " dropped " + n + " (want 1 or 2)");
                return;
            }
            sawOne |= n == 1;
            sawTwo |= n == 2;
        }
        if (!sawOne || !sawTwo) {
            helper.fail("super_rex count never varied across one 60-roll stream: sawOne=" + sawOne
                + " sawTwo=" + sawTwo);
            return;
        }
        helper.succeed();
    }

    private static LootTable lootTable(ServerLevel level, EntityType<?> type) {
        return type.getDefaultLootTable()
            .map(key -> level.getServer().reloadableRegistries().getLootTable(key))
            .orElse(null);
    }

    private static int countFancy(LootTable table, LootParams params, RandomSource random) {
        int count = 0;
        for (ItemStack stack : table.getRandomItems(params, random)) {
            if (stack.is(ModItems.SOMETHING_FANCY.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
