package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Walk to a container and look inside, put items in, or take items out. The fake player cannot open menus, so this works
 * on the container itself (a double chest counts as one), under the same protection rules as everything else.
 */
public final class ContainerAction implements VillagerAction {
	public enum Mode { OPEN, PUT, TAKE }

	private static final double REACH = 4.5;
	private static final int TIMEOUT_TICKS = 600;

	private final Mode mode;
	private final BlockPos pos;
	private final Item item;
	private final int count;
	private final UUID kingId;
	private final VillagingConfig config;
	private int ticks;

	public ContainerAction(Mode mode, BlockPos pos, Item item, int count, UUID kingId, VillagingConfig config) {
		this.mode = mode;
		this.pos = pos;
		this.item = item;
		this.count = count;
		this.kingId = kingId;
		this.config = config;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (ticks++ > TIMEOUT_TICKS) {
			return Status.failed("took too long to get there");
		}
		if (!level.isLoaded(pos)) {
			return Status.failed("that container is not loaded");
		}
		Container container = HopperBlockEntity.getContainerAt(level, pos);
		if (container == null) {
			return Status.failed("there is no container at " + pos.toShortString());
		}
		String protection = Protection.interactProblem(level, kingId, pos, config.respectProtection());
		if (protection != null) {
			return Status.failed(protection);
		}
		Status walking = Approach.toward(villager, pos, REACH, ticks);
		if (walking != null) {
			return walking;
		}
		villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		villager.swing(InteractionHand.MAIN_HAND);
		String where = level.getBlockState(pos).getBlock().getName().getString() + " at " + pos.toShortString();
		return switch (mode) {
			case OPEN -> Status.done("the " + where + " holds " + contents(container));
			case PUT -> put(villager, container, where);
			case TAKE -> take(villager, container, where);
		};
	}

	private Status put(PathfinderMob villager, Container container, String where) {
		VillagerInventory inventory = VillagerInventories.get(villager);
		String name = new ItemStack(item).getHoverName().getString();
		java.util.List<ItemStack> taken = inventory.take(item, count);
		if (taken == null) {
			return Status.failed("I only have " + inventory.count(item) + " " + name);
		}
		// Move the real stacks (keeping names, enchantments and contents) and keep whatever does not fit.
		int stored = 0;
		boolean full = false;
		for (ItemStack stack : taken) {
			if (full) {
				inventory.addItem(stack);
				continue;
			}
			int size = stack.getCount();
			ItemStack remainder = HopperBlockEntity.addItem(null, container, stack, null);
			stored += size - remainder.getCount();
			if (!remainder.isEmpty()) {
				inventory.addItem(remainder);
				full = true;
			}
		}
		container.setChanged();
		if (stored == 0) {
			return Status.failed("the " + where + " is full");
		}
		return Status.done("put " + stored + " " + name + " in the " + where
				+ (stored < count ? " (it filled up; kept the other " + (count - stored) + ")" : ""));
	}

	private Status take(PathfinderMob villager, Container container, String where) {
		String name = new ItemStack(item).getHoverName().getString();
		int available = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				available += container.getItem(i).getCount();
			}
		}
		if (available < count) {
			return Status.failed("the " + where + " only holds " + available + " " + name);
		}
		VillagerInventory inventory = VillagerInventories.get(villager);
		int left = count;
		for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.is(item)) {
				continue;
			}
			int take = Math.min(left, stack.getCount());
			ItemStack moved = stack.split(take);
			ItemStack overflow = inventory.addItem(moved);
			if (!overflow.isEmpty()) {
				stack.grow(overflow.getCount());
				container.setChanged();
				return Status.failed("my inventory is full after taking " + (count - left + take - overflow.getCount()) + " " + name);
			}
			left -= take;
		}
		container.setChanged();
		return Status.done("took " + count + " " + name + " from the " + where);
	}

	private static String contents(Container container) {
		Map<String, Integer> items = new TreeMap<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
			}
		}
		return items.isEmpty() ? "nothing" : items.toString();
	}
}
