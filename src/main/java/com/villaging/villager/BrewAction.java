package com.villaging.villager;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** Fill a nearby brewing stand with potion bottles, an ingredient and fuel, wait, and take the potions. */
public final class BrewAction implements VillagerAction {
	private static final double REACH = 4.5;
	private static final int WALK_TIMEOUT_TICKS = 600;
	private static final int BREW_TIMEOUT_TICKS = 600;
	private static final int MAX_BOTTLES = 3;
	private static final int SLOT_INGREDIENT = 3;
	private static final int SLOT_FUEL = 4;

	private final Item ingredient;
	private int ticks;
	private BlockPos pos;
	private boolean loaded;
	private long deadline;

	public BrewAction(Item ingredient) {
		this.ingredient = ingredient;
	}

	@Override
	public Status tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		ticks++;
		if (!loaded) {
			return prepare(villager, level);
		}
		if (!(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)) {
			return Status.failed("the brewing stand is gone");
		}
		boolean finished = stand.getItem(SLOT_INGREDIENT).isEmpty();
		if (!finished && ticks <= deadline) {
			return Status.RUNNING;
		}
		List<String> potions = reclaim(villager, level, stand);
		if (!finished) {
			return Status.failed("that ingredient did not brew with these bottles (I took everything back)");
		}
		return Status.done("brewed " + potions.size() + " potion" + (potions.size() == 1 ? "" : "s") + ": " + String.join(", ", potions));
	}

	private Status prepare(Villager villager, ServerLevel level) {
		String name = new ItemStack(ingredient).getHoverName().getString();
		if (pos == null) {
			pos = Stations.find(level, villager.blockPosition(), Stations.RADIUS, s -> s.is(Blocks.BREWING_STAND));
			if (pos == null) {
				return Status.failed("I need a brewing stand nearby");
			}
		}
		if (ticks > WALK_TIMEOUT_TICKS) {
			return Status.failed("took too long to reach the brewing stand");
		}
		Status walking = Approach.toward(villager, pos, REACH, ticks);
		if (walking != null) {
			return walking;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof BrewingStandBlockEntity stand)) {
			return Status.failed("that is not a brewing stand");
		}
		for (int slot = 0; slot <= SLOT_INGREDIENT; slot++) {
			if (!stand.getItem(slot).isEmpty()) {
				return Status.failed("the brewing stand is busy");
			}
		}
		VillagerInventory inventory = VillagerInventories.get(villager);
		int bottles = Math.min(MAX_BOTTLES, inventory.count(Items.POTION));
		if (bottles == 0) {
			return Status.failed("I have no potion bottles to brew with");
		}
		if (inventory.count(ingredient) < 1) {
			return Status.failed("I don't have any " + name);
		}
		boolean needsFuel = stand.getItem(SLOT_FUEL).isEmpty();
		if (needsFuel && inventory.count(Items.BLAZE_POWDER) < 1) {
			return Status.failed("the brewing stand needs blaze powder and I have none");
		}
		// Move the villager's actual bottles (keeping what is in them), not fresh default ones.
		int slot = 0;
		for (int i = 0; i < inventory.getContainerSize() && slot < bottles; i++) {
			ItemStack held = inventory.getItem(i);
			while (held.is(Items.POTION) && !held.isEmpty() && slot < bottles) {
				stand.setItem(slot++, held.copyWithCount(1));
				held.shrink(1);
			}
			if (held.isEmpty()) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		inventory.remove(ingredient, 1);
		stand.setItem(SLOT_INGREDIENT, new ItemStack(ingredient));
		if (needsFuel) {
			inventory.remove(Items.BLAZE_POWDER, 1);
			stand.setItem(SLOT_FUEL, new ItemStack(Items.BLAZE_POWDER));
		}
		stand.setChanged();
		deadline = ticks + BREW_TIMEOUT_TICKS;
		loaded = true;
		return Status.RUNNING;
	}

	/** Takes the bottles and any unused ingredient back to the villager; returns the potion names. */
	private List<String> reclaim(Villager villager, ServerLevel level, BrewingStandBlockEntity stand) {
		VillagerInventory inventory = VillagerInventories.get(villager);
		List<String> names = new ArrayList<>();
		for (int slot = 0; slot <= SLOT_INGREDIENT; slot++) {
			ItemStack stack = stand.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (slot < MAX_BOTTLES) {
				names.add(stack.getHoverName().getString());
			}
			ItemStack left = inventory.addItem(stack.copy());
			if (!left.isEmpty()) {
				villager.spawnAtLocation(level, left);
			}
			stand.setItem(slot, ItemStack.EMPTY);
		}
		stand.setChanged();
		return names;
	}

	@Override
	public void cancel(Villager villager) {
		villager.getNavigation().stop();
		if (loaded && villager.level() instanceof ServerLevel level
				&& level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand) {
			reclaim(villager, level, stand);
		}
	}
}
