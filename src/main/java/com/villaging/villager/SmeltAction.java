package com.villaging.villager;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;

/** Load a nearby furnace with the item and fuel from the villager's inventory, wait for it to cook, take the product. */
public final class SmeltAction implements VillagerAction {
	private static final double REACH = 4.5;
	private static final int WALK_TIMEOUT_TICKS = 600;
	private static final int SLOT_INPUT = 0;
	private static final int SLOT_FUEL = 1;
	private static final int SLOT_OUTPUT = 2;

	private final Item item;
	private final int count;
	private int ticks;
	private BlockPos pos;
	private boolean loaded;
	private long deadline;
	private int expected;

	public SmeltAction(Item item, int count) {
		this.item = item;
		this.count = count;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		ticks++;
		if (!loaded) {
			return prepare(villager, level);
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof AbstractFurnaceBlockEntity furnace)) {
			return Status.failed("the furnace is gone");
		}
		boolean finished = furnace.getItem(SLOT_INPUT).isEmpty() && furnace.getItem(SLOT_OUTPUT).getCount() >= expected;
		if (finished || ticks > deadline) {
			int got = reclaim(villager, level, furnace);
			String name = new ItemStack(item).getHoverName().getString();
			if (got == 0) {
				return Status.failed("the furnace did not finish smelting " + name);
			}
			return Status.done("smelted " + name + " into " + got + " item" + (got == 1 ? "" : "s")
					+ (finished ? "" : " (it was not all done in time; took what was ready)"));
		}
		return Status.RUNNING;
	}

	private Status prepare(PathfinderMob villager, ServerLevel level) {
		String name = new ItemStack(item).getHoverName().getString();
		if (pos == null) {
			pos = Stations.find(level, villager.blockPosition(), Stations.RADIUS, s -> s.is(Blocks.FURNACE));
			if (pos == null) {
				return Status.failed("I need a furnace nearby to smelt " + name);
			}
		}
		if (ticks > WALK_TIMEOUT_TICKS) {
			return Status.failed("took too long to reach the furnace");
		}
		Status walking = Approach.toward(villager, pos, REACH, ticks);
		if (walking != null) {
			return walking;
		}
		if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
			return Status.failed("that is not a furnace");
		}
		for (int slot = 0; slot <= SLOT_OUTPUT; slot++) {
			if (!furnace.getItem(slot).isEmpty()) {
				return Status.failed("the furnace is busy");
			}
		}
		Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING,
				new SingleRecipeInput(new ItemStack(item)), level);
		if (recipe.isEmpty()) {
			return Status.failed("I can't smelt " + name);
		}
		VillagerInventory inventory = VillagerInventories.get(villager);
		if (inventory.count(item) < count) {
			return Status.failed("I only have " + inventory.count(item) + " " + name);
		}
		int cookTime = recipe.get().value().cookingTime();
		int perItem = recipe.get().value().assemble(new SingleRecipeInput(new ItemStack(item))).getCount();
		FuelValues fuels = level.fuelValues();
		ItemStack fuel = null;
		int need = 0;
		int bestHeld = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || stack.is(item) || !fuels.isFuel(stack)) {
				continue;
			}
			int required = (int) Math.ceil((double) cookTime * count / fuels.burnDuration(stack));
			int held = inventory.count(stack.getItem());
			if (held >= required) {
				fuel = stack;
				need = required;
				break;
			}
			bestHeld = Math.max(bestHeld, held);
		}
		if (fuel == null) {
			return Status.failed("I don't have enough fuel to smelt " + count + " " + name);
		}
		inventory.remove(item, count);
		Item fuelItem = fuel.getItem();
		inventory.remove(fuelItem, need);
		furnace.setItem(SLOT_INPUT, new ItemStack(item, count));
		furnace.setItem(SLOT_FUEL, new ItemStack(fuelItem, need));
		furnace.setChanged();
		expected = count * perItem;
		deadline = ticks + (long) cookTime * count + 400;
		loaded = true;
		return Status.RUNNING;
	}

	/** Moves everything left in the furnace back to the villager; returns how many products it took. */
	private int reclaim(PathfinderMob villager, ServerLevel level, AbstractFurnaceBlockEntity furnace) {
		VillagerInventory inventory = VillagerInventories.get(villager);
		int products = furnace.getItem(SLOT_OUTPUT).getCount();
		for (int slot : new int[] {SLOT_OUTPUT, SLOT_FUEL, SLOT_INPUT}) {
			ItemStack stack = furnace.getItem(slot);
			if (!stack.isEmpty()) {
				ItemStack left = inventory.addItem(stack.copy());
				if (!left.isEmpty()) {
					villager.spawnAtLocation(level, left);
				}
				furnace.setItem(slot, ItemStack.EMPTY);
			}
		}
		furnace.setChanged();
		return products;
	}

	@Override
	public void cancel(PathfinderMob villager) {
		villager.getNavigation().stop();
		if (loaded && villager.level() instanceof ServerLevel level
				&& level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
			reclaim(villager, level, furnace);
		}
	}
}
