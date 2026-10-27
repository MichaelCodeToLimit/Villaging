package com.villaging.villager;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Walk to the king and hand over some of an item the villager carries. */
public final class GiveToKingAction implements VillagerAction {
	private static final double HAND_OVER_DISTANCE = 2.5;
	private static final int TIMEOUT_TICKS = 1200;

	private final Item item;
	private final int count;
	private final UUID kingId;
	private int ticks;

	public GiveToKingAction(Item item, int count, UUID kingId) {
		this.item = item;
		this.count = count;
		this.kingId = kingId;
	}

	@Override
	public Status tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (ticks++ > TIMEOUT_TICKS) {
			return Status.failed("I couldn't get to you in time");
		}
		VillagerInventory inventory = VillagerInventories.get(villager);
		String name = new ItemStack(item).getHoverName().getString();
		if (inventory.count(item) < count) {
			return Status.failed("I only have " + inventory.count(item) + " " + name);
		}
		ServerPlayer king = level.getServer().getPlayerList().getPlayer(kingId);
		if (king == null || king.level() != level) {
			return Status.failed("the king is not here");
		}
		if (villager.distanceTo(king) > HAND_OVER_DISTANCE) {
			if (villager.getNavigation().isDone() || ticks % 20 == 1) {
				villager.getNavigation().moveTo(king, Approach.SPEED);
			}
			return Status.RUNNING;
		}
		inventory.remove(item, count);
		int left = count;
		while (left > 0) {
			int part = Math.min(left, new ItemStack(item).getMaxStackSize());
			ItemStack stack = new ItemStack(item, part);
			if (!king.getInventory().add(stack) || !stack.isEmpty()) {
				king.drop(stack, false);
			}
			left -= part;
		}
		return Status.done("gave the king " + count + " " + name);
	}
}
