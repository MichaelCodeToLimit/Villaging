package com.villaging.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Craft an item (at least {@code count} of it), walking to a nearby crafting table first when the recipe needs one. */
public final class CraftAction implements VillagerAction {
	private static final double REACH = 4.0;
	private static final int TIMEOUT_TICKS = 600;

	private final Item item;
	private final int count;
	private int ticks;
	private BlockPos table;

	public CraftAction(Item item, int count) {
		this.item = item;
		this.count = count;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (ticks++ > TIMEOUT_TICKS) {
			return Status.failed("took too long to reach the crafting table");
		}
		VillagerInventory inventory = VillagerInventories.get(villager);
		String name = new ItemStack(item).getHoverName().getString();
		boolean atTable = false;
		if (table != null) {
			Status walking = Approach.toward(villager, table, REACH, ticks);
			if (walking != null) {
				return walking;
			}
			atTable = true;
		}
		int made = 0;
		while (made < count) {
			CraftPlanner.Outcome outcome = CraftPlanner.craftOnce(level, inventory, item, atTable);
			if (outcome.result() == null) {
				if (made == 0 && outcome.needsTable() && !atTable) {
					table = Stations.find(level, villager.blockPosition(), Stations.RADIUS, s -> s.is(Blocks.CRAFTING_TABLE));
					if (table == null) {
						return Status.failed("I need a crafting table nearby to make " + name);
					}
					return Status.RUNNING;
				}
				break;
			}
			made += outcome.result().getCount();
			ItemStack left = inventory.addItem(outcome.result());
			if (!left.isEmpty()) {
				villager.spawnAtLocation(level, left);
			}
		}
		if (made == 0) {
			return Status.failed("I can't make " + name + " (no recipe, or I lack the materials)");
		}
		villager.swing(InteractionHand.MAIN_HAND);
		return Status.done("crafted " + made + " " + name + (made < count ? " (ran out of materials for the rest)" : ""));
	}
}
