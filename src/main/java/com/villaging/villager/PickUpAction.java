package com.villaging.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Walk to dropped items nearby and put them in the villager's inventory. */
public final class PickUpAction implements VillagerAction {
	private static final double RADIUS = 12.0;
	private static final double GRAB_DISTANCE = 1.5;
	private static final int TIMEOUT_TICKS = 1200;

	private final Item only;
	private int ticks;
	private int picked;
	private String lastName = "";

	/** @param only the one kind to pick up, or null for anything */
	public PickUpAction(Item only) {
		this.only = only;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (ticks++ > TIMEOUT_TICKS) {
			return picked > 0 ? Status.done(summary()) : Status.failed("took too long to collect that");
		}
		ItemEntity nearest = null;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(RADIUS))) {
			if (e.isRemoved() || (only != null && !e.getItem().is(only))) {
				continue;
			}
			if (nearest == null || e.distanceToSqr(villager) < nearest.distanceToSqr(villager)) {
				nearest = e;
			}
		}
		if (nearest == null) {
			return picked > 0 ? Status.done(summary()) : Status.failed("I don't see anything to pick up");
		}
		if (nearest.distanceTo(villager) > GRAB_DISTANCE) {
			if (villager.getNavigation().isDone() || ticks % 20 == 1) {
				boolean moving = villager.getNavigation().moveTo(nearest, Approach.SPEED);
				if (!moving && ticks > Approach.START_GRACE_TICKS) {
					return picked > 0 ? Status.done(summary()) : Status.failed("I can't reach that");
				}
			}
			return Status.RUNNING;
		}
		ItemStack stack = nearest.getItem();
		int before = stack.getCount();
		lastName = stack.getHoverName().getString();
		ItemStack left = VillagerInventories.get(villager).addItem(stack.copy());
		int taken = before - left.getCount();
		if (taken <= 0) {
			return picked > 0 ? Status.done(summary() + " (my inventory is full)") : Status.failed("my inventory is full");
		}
		picked += taken;
		if (left.isEmpty()) {
			nearest.discard();
		} else {
			nearest.setItem(left);
		}
		return Status.RUNNING;
	}

	private String summary() {
		return "picked up " + picked + " item" + (picked == 1 ? "" : "s") + (only == null ? "" : " (" + lastName + ")");
	}
}
