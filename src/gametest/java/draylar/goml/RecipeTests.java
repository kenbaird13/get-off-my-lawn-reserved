package draylar.goml;

import draylar.goml.registry.GOMLBlocks;
import draylar.goml.registry.GOMLItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;

public final class RecipeTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void allSurvivalRecipesLoadAndCraft(GameTestHelper h) {
        var craftable = new ArrayList<Item>();
        GOMLBlocks.ANCHORS.stream().filter(block -> block != GOMLBlocks.ADMIN_CLAIM_ANCHOR.getFirst())
                .forEach(block -> craftable.add(block.asItem()));
        GOMLBlocks.AUGMENTS.forEach(block -> craftable.add(block.asItem()));
        craftable.addAll(GOMLItems.BASE_ITEMS);
        h.assertValueEqual(craftable.size(), 23, "all survival anchors, augments, upgrade kits and goggles covered");

        var manager = h.getLevel().getRecipeManager();
        for (var item : craftable) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            var holder = manager.byKey(id);
            h.assertTrue(holder.isPresent(), "recipe loaded: " + id);
            h.assertTrue(holder.get().value() instanceof ShapedRecipe, "shaped recipe: " + id);
            var recipe = (ShapedRecipe) holder.get().value();
            var ingredients = new ArrayList<ItemStack>();
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) ingredients.add(ItemStack.EMPTY);
                else {
                    var matching = ingredient.getItems();
                    h.assertTrue(matching.length > 0, "ingredient resolves to registered items: " + id);
                    ingredients.add(matching[0].copy());
                }
            }
            var grid = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), ingredients);
            h.assertTrue(recipe.matches(grid, h.getLevel()), "survival ingredients match: " + id);
            var result = recipe.assemble(grid, h.getLevel().registryAccess());
            h.assertTrue(result.is(item) && result.getCount() == 1, "recipe produces the intended item: " + id);
        }
        h.succeed();
    }
}
