package com.villaging.villager;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Finds a crafting recipe for an item that the villager's inventory can satisfy right now, and crafts it once. */
final class CraftPlanner {
	/** The outcome of one attempt. {@code result} is null when nothing could be crafted. */
	record Outcome(ItemStack result, boolean needsTable) {
	}

	private CraftPlanner() {
	}

	/**
	 * Crafts {@code wanted} once: consumes the ingredients from {@code inventory} and returns the product (not yet added).
	 * Recipes that need a 3x3 grid are skipped, and reported through {@code needsTable}, unless {@code tableAvailable}.
	 */
	static Outcome craftOnce(ServerLevel level, VillagerInventory inventory, Item wanted, boolean tableAvailable) {
		boolean needsTable = false;
		for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
			if (!(holder.value() instanceof CraftingRecipe recipe)) {
				continue;
			}
			PlacementInfo info = recipe.placementInfo();
			if (info.isImpossibleToPlace()) {
				continue;
			}
			int width;
			int height;
			if (recipe instanceof ShapedRecipe shaped) {
				width = shaped.getWidth();
				height = shaped.getHeight();
			} else {
				width = info.ingredients().size() <= 4 ? 2 : 3;
				height = width;
			}
			IntList slotToIngredient = info.slotsToIngredientIndex();
			int[] used = new int[inventory.getContainerSize()];
			int[] source = new int[width * height];
			List<ItemStack> grid = new ArrayList<>(width * height);
			boolean ok = true;
			for (int s = 0; s < width * height; s++) {
				int index = s < slotToIngredient.size() ? slotToIngredient.getInt(s) : -1;
				source[s] = -1;
				if (index < 0) {
					grid.add(ItemStack.EMPTY);
					continue;
				}
				Ingredient ingredient = info.ingredients().get(index);
				int found = -1;
				for (int i = 0; i < inventory.getContainerSize(); i++) {
					ItemStack stack = inventory.getItem(i);
					if (!stack.isEmpty() && ingredient.test(stack) && used[i] < stack.getCount()) {
						found = i;
						break;
					}
				}
				if (found < 0) {
					ok = false;
					break;
				}
				used[found]++;
				source[s] = found;
				grid.add(inventory.getItem(found).copyWithCount(1));
			}
			if (!ok) {
				continue;
			}
			CraftingInput input = CraftingInput.of(width, height, grid);
			if (!recipe.matches(input, level)) {
				continue;
			}
			ItemStack result = recipe.assemble(input);
			if (!result.is(wanted)) {
				continue;
			}
			if ((width > 2 || height > 2) && !tableAvailable) {
				needsTable = true;
				continue;
			}
			for (int s = 0; s < source.length; s++) {
				if (source[s] >= 0) {
					ItemStack stack = inventory.getItem(source[s]);
					stack.shrink(1);
					if (stack.isEmpty()) {
						inventory.setItem(source[s], ItemStack.EMPTY);
					}
				}
			}
			for (ItemStack remaining : recipe.getRemainingItems(input)) {
				if (!remaining.isEmpty()) {
					inventory.addItem(remaining);
				}
			}
			return new Outcome(result, false);
		}
		return new Outcome(null, needsTable);
	}
}
